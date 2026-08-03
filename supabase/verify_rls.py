#!/usr/bin/env python3
"""PLAN 15.5.4 — every policy checked FROM A SECOND ACCOUNT.

Two real sessions, pointed at each other's rows, bouncing. Reading `003` is not
this check and neither is running it successfully.

Every request sends `Prefer: return=representation`, including DELETE and
PATCH — without it PostgREST answers 204 with an empty body whether it touched
a row or not, so a policy that silently allowed a cross-account delete would
look exactly like one that refused it. That is the difference between this
being a check and being theatre.
"""
import json
import subprocess
import sys

import pathlib
REPO = pathlib.Path(__file__).resolve().parent.parent


def prop(name):
    for line in open(REPO / "local.properties"):
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    raise SystemExit(f"{name} missing")


URL = prop("supabase.url")
ANON = prop("supabase.anonKey")
# Two access tokens and the account ids they belong to. Mint them with
# `mint_session.py`, which uses the admin API rather than a password — so this
# whole check is repeatable in CI against a throwaway project rather than being
# something a person has to sit and do.
if len(sys.argv) != 5:
    raise SystemExit(
        "usage: verify_rls.py <token-A> <uuid-A> <token-B> <uuid-B>\n"
        "  mint tokens with: python3 supabase/mint_session.py <email>"
    )
A, A_ID, B, B_ID = sys.argv[1:5]

passed = failed = 0


def request(token, method, path, body=None):
    cmd = [
        "curl", "-s", "-X", method, f"{URL}/rest/v1/{path}",
        "-H", f"apikey: {ANON}",
        "-H", f"Authorization: Bearer {token}",
        "-H", "Content-Type: application/json",
        "-H", "Prefer: return=representation",
        "-w", "\n%{http_code}",
    ]
    if body is not None:
        cmd += ["--data-binary", json.dumps(body)]
    out = subprocess.run(cmd, capture_output=True, text=True).stdout
    text, _, status = out.rpartition("\n")
    return int(status), text


def check(label, ok, detail=""):
    global passed, failed
    if ok:
        passed += 1
        print(f"  PASS  {label}")
    else:
        failed += 1
        print(f"  FAIL  {label}  — {detail[:300]}")


def refused(status, text):
    """A write RLS turned away: 401/403 with 42501, or 409 on a conflict."""
    return status in (401, 403) or "42501" in text


def rows(text):
    try:
        parsed = json.loads(text)
        return parsed if isinstance(parsed, list) else [parsed]
    except Exception:
        return []


print("== profiles ==")
s, t = request(A, "POST", "profiles",
               {"id": A_ID, "name": "A", "ftp_watts": 200, "weight_kg": 70})
check("A creates their own profile", s in (200, 201, 409), f"{s} {t}")

s, t = request(B, "POST", "profiles",
               {"id": B_ID, "name": "B", "ftp_watts": 210, "weight_kg": 80})
check("B creates their own profile", s in (200, 201, 409), f"{s} {t}")

s, t = request(A, "POST", "profiles",
               {"id": B_ID, "name": "stolen", "ftp_watts": 1, "weight_kg": 1})
check("A CANNOT create a profile owned by B", refused(s, t), f"{s} {t}")

# The property, not the fixture: every row A can see is A's. Asserting on a
# name instead was a test of the test — it broke the day a real bike synced a
# real profile over the one this script had written, which is not a security
# event and should not read like one.
s, t = request(A, "GET", "profiles?select=id,name")
seen = rows(t)
check(
    "A reads profiles and sees only their own",
    seen and all(r["id"] == A_ID for r in seen),
    f"{s} saw {[r.get('id') for r in seen]}",
)

s, t = request(A, "PATCH", f"profiles?id=eq.{B_ID}", {"name": "pwned"})
check("A CANNOT rename B", rows(t) == [] or refused(s, t), f"{s} {t}")

s, t = request(A, "DELETE", f"profiles?id=eq.{B_ID}")
check("A CANNOT delete B's profile", rows(t) == [] or refused(s, t), f"{s} {t}")

s, t = request(B, "GET", f"profiles?select=id,name&id=eq.{B_ID}")
check("B's profile survived all of that", len(rows(t)) == 1, f"{s} {t}")

print("== workouts ==")
W_A = "aaaaaaaa-0000-4000-8000-00000000000a"
W_B = "bbbbbbbb-0000-4000-8000-00000000000b"
payload = {"v": 1, "t": [1, 2], "c": [80, 81], "r": [30, 31], "p": [150, 151]}


def ride(rid, owner):
    return {
        "id": rid, "user_id": owner, "duration_sec": 600,
        "total_output_kj": 100, "total_distance_km": 5, "intent_modifier": 1.0,
        "recorded_at": "2026-08-03T20:00:00Z", "metrics_payload": payload,
    }


s, t = request(A, "POST", "workouts", ride(W_A, A_ID))
check("A records their own ride", s in (200, 201), f"{s} {t}")

s, t = request(B, "POST", "workouts", ride(W_B, B_ID))
check("B records their own ride", s in (200, 201), f"{s} {t}")

s, t = request(A, "POST", "workouts", ride("cccccccc-0000-4000-8000-00000000000c", B_ID))
check("A CANNOT record a ride owned by B", refused(s, t), f"{s} {t}")

s, t = request(A, "GET", "workouts?select=id,user_id")
mine = rows(t)
ids = {r["id"] for r in mine}
check("A sees their own ride", W_A in ids, f"{s} saw {ids}")
check("A does NOT see B's ride", W_B not in ids, f"{s} saw {ids}")
# Again the property rather than a count — the table also holds rides with a
# NULL owner (pre-consent uploads) and whatever a real bike has since sent, and
# the thing that matters is that **not one row A can see belongs to anybody
# else**, including nobody.
check(
    "every ride A can see is A's",
    mine and all(r["user_id"] == A_ID for r in mine),
    f"owners seen: {sorted({r['user_id'] for r in mine})}",
)

s, t = request(A, "PATCH", f"workouts?id=eq.{W_B}", {"total_output_kj": 0})
check("A CANNOT edit B's ride", rows(t) == [] or refused(s, t), f"{s} {t}")

s, t = request(A, "PATCH", f"workouts?id=eq.{W_A}", {"user_id": B_ID})
check("A CANNOT hand their ride to B", refused(s, t) or rows(t) == [], f"{s} {t}")

s, t = request(A, "DELETE", f"workouts?id=eq.{W_B}")
check("A CANNOT delete B's ride", rows(t) == [] or refused(s, t), f"{s} {t}")

s, t = request(B, "GET", f"workouts?select=id&id=eq.{W_B}")
check("B's ride survived all of that", len(rows(t)) == 1, f"{s} {t}")

print("== the public table, and the private one ==")
s, t = request(A, "GET", "class_templates?select=id&limit=1")
check("A can read the class library", s == 200 and len(rows(t)) == 1, f"{s} {t}")

s, t = request(A, "POST", "class_templates",
               {"id": "HACK-01", "title": "x", "category": "x",
                "duration_sec": 1, "intervals_json": []})
check("A CANNOT write the class library", refused(s, t), f"{s} {t}")

s, t = request(A, "GET", "device_link?select=code")
check("A CANNOT read the pairing table", refused(s, t), f"{s} {t}")

print("== cleanup ==")
for token, rid in ((A, W_A), (B, W_B)):
    request(token, "DELETE", f"workouts?id=eq.{rid}")
s, t = request(A, "GET", f"workouts?select=id&id=eq.{W_A}")
check("A can delete their own ride", rows(t) == [], f"{s} {t}")

print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)
