#!/usr/bin/env python3
"""PLAN 18.5 / 18.11 — the cross-bike leaderboard, checked from a second account.

    python3 supabase/verify_leaderboard.py <token-A> <uuid-A> <token-B> <uuid-B>
    # mint tokens with: python3 supabase/mint_session.py <email>

The owner's rule is that every registered rider sees every other one's scores
(no friend graph, 3–4 users). That makes the *interesting* checks the ones about
what visibility still does **not** include — because "show everyone" is easy to
implement by relaxing a policy, and a relaxed policy shows everything the table
will ever hold rather than the five columns anybody agreed to.

So this asserts the boundary as hard as it asserts the feature.
"""
import json
import pathlib
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent


def prop(name):
    for line in (REPO / "local.properties").read_text().splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    raise SystemExit(f"{name} is not in local.properties")


URL, ANON = prop("supabase.url"), prop("supabase.anonKey")

if len(sys.argv) != 5:
    raise SystemExit(__doc__)
A, A_ID, B, B_ID = sys.argv[1:5]

CLASS = "CLB-01"
OTHER = "CLB-02"
RIDE_A = "d0000000-0000-4000-8000-00000000000a"
RIDE_B = "d0000000-0000-4000-8000-00000000000b"
RIDE_MODELLED = "d0000000-0000-4000-8000-00000000000c"

passed = failed = 0


def rest(token, method, path, body=None):
    cmd = ["curl", "-s", "-X", method, f"{URL}/rest/v1/{path}",
           "-H", f"apikey: {ANON}", "-H", f"Authorization: Bearer {token or ANON}",
           "-H", "Content-Type: application/json",
           "-H", "Prefer: return=representation", "-w", "\n%{http_code}"]
    if body is not None:
        cmd += ["--data-binary", json.dumps(body)]
    out = subprocess.run(cmd, capture_output=True, text=True).stdout
    text, _, status = out.rpartition("\n")
    return int(status), text


def rows(text):
    try:
        v = json.loads(text)
        return v if isinstance(v, list) else [v]
    except Exception:
        return []


def refused(status, text):
    return status in (401, 403) or "42501" in text


def check(label, ok, detail=""):
    global passed, failed
    if ok:
        passed += 1
        print(f"  PASS  {label}")
    else:
        failed += 1
        print(f"  FAIL  {label} — {detail[:260]}")


def ride(rid, owner, class_id, kj, provenance):
    return {
        "id": rid, "user_id": owner, "class_id": class_id, "duration_sec": 600,
        "total_output_kj": kj, "total_distance_km": 5, "intent_modifier": 1.0,
        "recorded_at": "2026-08-03T20:00:00Z", "power_provenance": provenance,
        "metrics_payload": {
            "v": 1, "t": [1, 2], "c": [80, 82], "r": [30, 31],
            "p": [150, 160], "hr": [140, 142],
        },
    }


print("== two riders, one class, measured power ==")
for token, owner, rid, kj in ((A, A_ID, RIDE_A, 300.0), (B, B_ID, RIDE_B, 200.0)):
    rest(token, "DELETE", f"workouts?id=eq.{rid}")
    s, t = rest(token, "POST", "workouts", ride(rid, owner, CLASS, kj, "Measured"))
    check(f"ride recorded for {'A' if owner == A_ID else 'B'}", s in (200, 201), f"{s} {t}")

print("== the board ==")
s, t = rest(A, "POST", "rpc/class_leaderboard", {"p_class_id": CLASS})
board = rows(t)
ids = {r["account_id"] for r in board}
check("A's board has both riders on it, with no friending", {A_ID, B_ID} <= ids, f"{s} {t}")
check("A is marked as you and B is not",
      all(r["is_you"] == (r["account_id"] == A_ID) for r in board), t)
check("it carries a name and a weight, so a board can be drawn",
      board and all(r["name"] and r["weight_kg"] is not None for r in board), t)
check("and nothing else at all",
      board and set(board[0]) == {"account_id", "name", "output_kj", "weight_kg", "is_you"},
      f"{sorted(board[0]) if board else board}")

s, t = rest(B, "POST", "rpc/class_leaderboard", {"p_class_id": CLASS})
check("it is symmetric — B sees A too", A_ID in {r["account_id"] for r in rows(t)}, f"{s} {t}")

print("== only measured power is ranked (24.4.2, 18.7) ==")
rest(B, "DELETE", f"workouts?id=eq.{RIDE_MODELLED}")
rest(B, "POST", "workouts", ride(RIDE_MODELLED, B_ID, OTHER, 999.0, "Modelled"))
s, t = rest(A, "POST", "rpc/class_leaderboard", {"p_class_id": OTHER})
check("a modelled ride is on nobody's board", B_ID not in {r["account_id"] for r in rows(t)},
      f"{s} {t}")

print("== the ghost ==")
s, t = rest(A, "POST", "rpc/class_ghost", {"p_class_id": CLASS, "p_account_id": B_ID})
ghost = rows(t)
check("A can fetch B's trace for a class they both rode", len(ghost) == 1, f"{s} {t}")
check("it carries the series", ghost and ghost[0]["metrics"].get("p"), t)
check("and heart rate is stripped from it", ghost and "hr" not in ghost[0]["metrics"], t)

print("== what visibility still does NOT include ==")
s, t = rest(A, "GET", f"workouts?select=id,recorded_at,rpe_rating&user_id=eq.{B_ID}")
check("A cannot read B's workout rows", rows(t) == [] or refused(s, t), f"{s} {t}")

s, t = rest(A, "GET", f"profiles?select=id,name&id=eq.{B_ID}")
check("A cannot read B's profile row", rows(t) == [] or refused(s, t), f"{s} {t}")

print("== and none of it without a session ==")
s, t = rest(None, "POST", "rpc/class_leaderboard", {"p_class_id": CLASS})
check("the anon key gets no board", refused(s, t) or rows(t) == [], f"{s} {t}")
s, t = rest(None, "POST", "rpc/class_ghost", {"p_class_id": CLASS, "p_account_id": B_ID})
check("the anon key gets no ghost", refused(s, t) or rows(t) == [], f"{s} {t}")

for token, rid in ((A, RIDE_A), (B, RIDE_B), (B, RIDE_MODELLED)):
    rest(token, "DELETE", f"workouts?id=eq.{rid}")

print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)
