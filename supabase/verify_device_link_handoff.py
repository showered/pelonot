#!/usr/bin/env python3
"""Does the fallback pairing hand-off (15.6.9) actually work end to end?

    python3 supabase/verify_device_link_handoff.py <email of an existing, confirmed account>

Plays out the whole exchange against a session minted for a real account —
`device_link_begin` as the bike, `_claim` as the phone, `_poll` as the bike
again, then redeems the handed-over refresh token exactly as `refreshSession()`
does on the tablet. No UI, no timing, no phone involved.

WHY THIS EXISTS
================

15.6.16c (PLAN) found that `client.auth.signOut({ scope: 'local' })` — the
15.6.16b fix, called from `link.js`'s `finish()` right after handing the token
to the bike — still calls `/logout` on the server, and GoTrue's own reading of
`scope=local` is "revoke *this session*", which is exactly the session whose
refresh token the bike is racing to redeem. It failed the redemption **every
time**, with no timing involved — proven here before it was believed, by
running this same sequence with that call still in it.

`finish()` now calls `client.auth.stopAutoRefresh()` instead, which is a real
local-only primitive: no network call, so nothing here needs to simulate it —
the check below is just the hand-off working, because a future change to
`finish()` or a supabase-js upgrade is what would put a network call back in
the way. If this ever fails, the first thing to check is what the phone's
sign-out step now does.

CREDENTIALS
===========

Same as `mint_session.py`: reads `supabase.accessToken` from
`local.properties` and fetches the service-role key through the Management API
at run time. Neither is written anywhere.
"""

import hashlib
import json
import pathlib
import secrets
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent


def local_property(name):
    for line in (REPO / "local.properties").read_text().splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    sys.exit(f"{name} is not in local.properties")


def post(url, headers, body):
    cmd = ["curl", "-s", "-w", "\n%{http_code}", url,
           "-H", "Content-Type: application/json", "--data-binary", json.dumps(body)]
    for k, v in headers.items():
        cmd += ["-H", f"{k}: {v}"]
    out = subprocess.run(cmd, capture_output=True, text=True).stdout
    body_text, _, code = out.rpartition("\n")
    try:
        return int(code), json.loads(body_text)
    except json.JSONDecodeError:
        return int(code), {"_raw": body_text[:400]}


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: verify_device_link_handoff.py <email>")
    email = sys.argv[1]

    url = local_property("supabase.url")
    anon = local_property("supabase.anonKey")

    print(f"Minting a phone-side session for {email} …")
    mint = subprocess.run(["python3", str(REPO / "supabase" / "mint_session.py"), email],
                           capture_output=True, text=True)
    if mint.returncode != 0:
        sys.exit(f"mint_session.py failed:\n{mint.stdout}\n{mint.stderr}")
    session = json.loads(mint.stdout)
    refresh_token = session["refresh_token"]

    print("Bike: device_link_begin …")
    secret = secrets.token_hex(32)
    secret_hash = hashlib.sha256(secret.encode()).hexdigest()
    _, begin = post(f"{url}/rest/v1/rpc/device_link_begin",
                     {"apikey": anon, "Authorization": f"Bearer {anon}"},
                     {"p_secret_hash": secret_hash, "p_label": "verify_device_link_handoff.py"})
    code = begin.get("code")
    if not code:
        sys.exit(f"device_link_begin failed: {begin}")

    print("Phone: device_link_claim, handing over the refresh token …")
    claim_status, claim = post(f"{url}/rest/v1/rpc/device_link_claim",
                                {"apikey": anon, "Authorization": f"Bearer {session['access_token']}"},
                                {"p_code": code, "p_payload": {"kind": "refresh", "token": refresh_token}})
    if claim.get("status") != "linked":
        sys.exit(f"device_link_claim failed ({claim_status}): {claim}")

    print("Bike: device_link_poll …")
    _, poll = post(f"{url}/rest/v1/rpc/device_link_poll",
                    {"apikey": anon, "Authorization": f"Bearer {anon}"},
                    {"p_code": code, "p_secret": secret})
    if poll.get("status") != "linked":
        sys.exit(f"device_link_poll did not collect a handover: {poll}")

    print("Bike: redeeming the handed-over token …")
    status, body = post(f"{url}/auth/v1/token?grant_type=refresh_token",
                         {"apikey": anon, "Authorization": f"Bearer {anon}"},
                         {"refresh_token": poll["payload"]["token"]})
    print(f"  {status}: {'ok' if status == 200 else body}")

    if status == 200:
        print("\nPASS — the fallback hand-off redeems cleanly end to end.")
    else:
        sys.exit("\nFAIL — read PLAN 15.6.16c before assuming this is the old bug back again.")


if __name__ == "__main__":
    main()
