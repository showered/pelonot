#!/usr/bin/env python3
"""Read, back up and narrowly patch the project's auth config (PLAN 15.7.6).

    python3 supabase/auth_config.py show
    python3 supabase/auth_config.py backup            # writes supabase/_auth_config.json
    python3 supabase/auth_config.py set-site-url      # the 15.7.6 fix, backup first
    python3 supabase/auth_config.py diff              # against the backup

WHY IT EXISTS
=============

The confirmation email a first-time rider receives points at
`http://localhost:3000` — Supabase's own scaffold value for `site_url`, never
changed. A rider signs up on their phone, taps the link, and lands on a page
that does not exist on the device they are holding. There is no recovery from
that inside the flow (15.7.6).

The fix is two fields. The danger is that `PATCH /v1/projects/{ref}/config/auth`
takes the **whole** auth config object, and these two fields are the ones that
decide whether anybody can complete a sign-in or a password reset. So this is a
script rather than a curl in a plan file: it reads first, keeps the copy, sends
back **only** the keys named on the command line, and can diff the result
against the backup so "nothing else moved" is checked rather than assumed.

CREDENTIALS
===========

Reads `supabase.accessToken` from `local.properties`, the same account-wide
`sbp_` token `mint_session.py` and `publish_class_library.py` use (14.11.2).
Held in memory, never written anywhere. The backup file is git-ignored: an auth
config carries no secret today, but it is not this script's business to be the
first thing that puts one in the repo.
"""

import json
import pathlib
import subprocess
import sys
from urllib.parse import unquote

REPO = pathlib.Path(__file__).resolve().parent.parent
BACKUP = REPO / "supabase" / "_auth_config.json"


def local_property(name):
    for line in (REPO / "local.properties").read_text().splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    sys.exit(f"{name} is not in local.properties — see supabase/README.md")


def project():
    url = local_property("supabase.url")
    return url.removeprefix("https://").split(".")[0], local_property("supabase.accessToken")


def call(method, ref, token, body=None):
    cmd = ["curl", "-s", "-X", method,
           f"https://api.supabase.com/v1/projects/{ref}/config/auth",
           "-H", f"Authorization: Bearer {token}"]
    if body is not None:
        cmd += ["-H", "Content-Type: application/json", "--data-binary", json.dumps(body)]
    out = subprocess.run(cmd, capture_output=True, text=True).stdout
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        sys.exit(f"unexpected answer:\n{out[:400]}")


def read():
    ref, token = project()
    config = call("GET", ref, token)
    if "site_url" not in config:
        sys.exit(f"that does not look like an auth config:\n{json.dumps(config)[:400]}")
    return config


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else "show"

    if action == "show":
        config = read()
        for key in ("site_url", "uri_allow_list", "mailer_autoconfirm",
                    "external_email_enabled", "disable_signup"):
            print(f"{key} = {config.get(key)!r}")

    elif action == "backup":
        BACKUP.write_text(json.dumps(read(), indent=2, sort_keys=True) + "\n")
        print(f"wrote {BACKUP} ({len(BACKUP.read_text())} bytes)")

    elif action == "set-site-url":
        if not BACKUP.exists():
            sys.exit("run `backup` first — the saved config is the rollback")
        web = local_property("pelonot.webUrl").rstrip("/")
        # Every redirect the app may ask for. Supabase matches these as globs
        # and silently falls back to `site_url` for anything not listed, so a
        # `site_url` fix that does not widen this looks like it worked and then
        # does not (15.7.6, point 2).
        allowed = ",".join([
            web,
            f"{web}/**",
        ])
        ref, token = project()
        answer = call("PATCH", ref, token, {"site_url": web, "uri_allow_list": allowed})
        print(json.dumps({k: answer.get(k) for k in ("site_url", "uri_allow_list")}, indent=2))

    elif action == "diff":
        if not BACKUP.exists():
            sys.exit("no backup to compare against")
        before = json.loads(BACKUP.read_text())
        after = read()
        changed = {k: (before.get(k), after.get(k))
                   for k in sorted(set(before) | set(after))
                   if before.get(k) != after.get(k)}
        if not changed:
            print("identical")
        for key, (was, now) in changed.items():
            print(f"{key}\n  was {was!r}\n  now {now!r}")

    elif action == "check-link":
        # A 200 from the PATCH says the value was *stored*. This says the value
        # is what a rider's confirmation link is actually built from, without
        # waiting for mail to arrive: `generate_link` mints the same link the
        # mailer would send and does not send it (the trick `mint_session.py`
        # already relies on). The throwaway account it has to create is deleted
        # again on the way out — this is the owner's live project.
        url = local_property("supabase.url")
        ref, token = project()
        keys = json.loads(subprocess.run(
            ["curl", "-s", f"https://api.supabase.com/v1/projects/{ref}/api-keys?reveal=true",
             "-H", f"Authorization: Bearer {token}"],
            capture_output=True, text=True).stdout)
        service = next(k["api_key"] for k in keys if k.get("name") == "service_role")
        auth = ["-H", f"apikey: {service}", "-H", f"Authorization: Bearer {service}"]

        email = sys.argv[2] if len(sys.argv) > 2 else "link-check@pelonot.invalid"
        redirect = sys.argv[3] if len(sys.argv) > 3 else None
        body = {"type": "signup", "email": email, "password": "check-only-not-a-login"}
        if redirect:
            body["redirect_to"] = redirect
        minted = json.loads(subprocess.run(
            ["curl", "-s", f"{url}/auth/v1/admin/generate_link",
             "-H", "Content-Type: application/json",
             "--data-binary", json.dumps(body)] + auth,
            capture_output=True, text=True).stdout)

        link = minted.get("action_link") or minted.get("properties", {}).get("action_link")
        user_id = (minted.get("user") or minted).get("id")

        # **Print the shape of the link, never the link.** What is being checked
        # is the host it points at and the `redirect_to` it carries; the token
        # beside them is a live, single-use credential for the account just
        # created, and a check that leaves one in a terminal scrollback is a
        # worse habit than the fault it is checking for.
        if link:
            head, _, query = link.partition("?")
            fields = dict(
                part.split("=", 1) for part in query.split("&") if "=" in part
            )
            print(head)
            for key in sorted(fields):
                value = fields[key]
                print(f"  {key} = " + (f"<{len(value)} chars, hidden>"
                                       if "token" in key else unquote(value)))
        else:
            print(json.dumps({k: v for k, v in minted.items() if "token" not in k})[:400])

        if user_id:
            subprocess.run(["curl", "-s", "-X", "DELETE",
                            f"{url}/auth/v1/admin/users/{user_id}"] + auth,
                           capture_output=True, text=True)
            print(f"(removed the throwaway account {user_id})")

    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
