#!/usr/bin/env python3
"""A session for an existing, confirmed account — without a password.

    python3 supabase/mint_session.py someone@example.com

Prints `{"access_token": …, "refresh_token": …}`.

WHY IT EXISTS
=============

`verify_rls.py` (PLAN 15.5.4) needs two real sessions pointed at each other's
rows. Getting them by typing two passwords makes the check something a person
has to sit and do, which means it happens once and then never again — and 17.5
adds the first schema where a rider may see somebody else's data, which is
exactly when it needs re-running.

So the sessions are minted instead: the admin API generates a one-time
magic-link token for an account that already exists, and `/auth/v1/verify`
exchanges it for a session. No password is typed, stored or transmitted, and
nothing is emailed — `generate_link` mints, it does not send.

CREDENTIALS
===========

Reads `supabase.accessToken` from `local.properties` and fetches the
service-role key through the Management API at run time. Both are held in
memory and neither is written anywhere. **Do not adapt this to take a
service-role key as an argument** — an argument ends up in a shell history.
"""

import json
import pathlib
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent


def local_property(name):
    for line in (REPO / "local.properties").read_text().splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    sys.exit(f"{name} is not in local.properties — see supabase/README.md")


def post(url, headers, body):
    cmd = ["curl", "-s", url, "-H", "Content-Type: application/json",
           "--data-binary", json.dumps(body)]
    for key, value in headers.items():
        cmd += ["-H", f"{key}: {value}"]
    out = subprocess.run(cmd, capture_output=True, text=True).stdout
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        sys.exit(f"unexpected answer:\n{out[:400]}")


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: mint_session.py <email of an existing, confirmed account>")
    email = sys.argv[1]

    url = local_property("supabase.url")
    anon = local_property("supabase.anonKey")
    management = local_property("supabase.accessToken")
    ref = url.removeprefix("https://").split(".")[0]

    keys = json.loads(subprocess.run(
        ["curl", "-s", f"https://api.supabase.com/v1/projects/{ref}/api-keys?reveal=true",
         "-H", f"Authorization: Bearer {management}"],
        capture_output=True, text=True).stdout)
    service_role = next(k["api_key"] for k in keys if k.get("name") == "service_role")

    link = post(
        f"{url}/auth/v1/admin/generate_link",
        {"apikey": service_role, "Authorization": f"Bearer {service_role}"},
        {"type": "magiclink", "email": email},
    )
    hashed = link.get("hashed_token") or link.get("properties", {}).get("hashed_token")
    if not hashed:
        sys.exit(f"no token came back for {email}: {json.dumps(link)[:300]}")

    session = post(
        f"{url}/auth/v1/verify",
        {"apikey": anon},
        {"type": "magiclink", "token_hash": hashed},
    )
    if not session.get("access_token"):
        sys.exit(f"could not exchange the token: {json.dumps(session)[:300]}")

    print(json.dumps({
        "access_token": session["access_token"],
        "refresh_token": session["refresh_token"],
        "user_id": session["user"]["id"],
    }))


if __name__ == "__main__":
    main()
