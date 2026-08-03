#!/usr/bin/env python3
"""Push the bundled class library to a Supabase project (PLAN 23.2.3, 14.2.9).

    python3 supabase/publish_class_library.py            # show what would change
    python3 supabase/publish_class_library.py --apply    # do it

WHY THIS EXISTS
===============

The cloud's `class_templates` and the 72 classes in the APK had drifted into
two different libraries, and nothing noticed until a ride tried to reference
one. 23.2.6 rebuilt the catalogue with a **new id series** — `CLB-01`, `END-01`,
`SWT-05` — and retired the old one locally; the cloud still held the pre-rebuild
series (`HC-01`, `PZE-02`, …). Since `workouts.class_id` has a foreign key onto
`class_templates`, the consequence was total and silent:

    insert or update on table "workouts" violates foreign key constraint
    "workouts_class_id_fkey" (Key is not present in table "class_templates".)

**No ride against any bundled class could ever be backed up.** Not a subset —
every one of them, for every rider, for ever, reported to the rider as "backup
is failing". It was found by signing a tablet in and watching the backlog drain,
which is the only way it could have been found: the app builds, the tests pass,
and the local database is perfectly consistent with itself.

ADDITIVE, NEVER DESTRUCTIVE
===========================

This upserts and **never deletes**, which is 23.2.3's rule and it has teeth
here: a ride already in the cloud points at `HC-02`, and removing that row would
either break the foreign key or — worse, if the constraint were ever relaxed —
quietly detach a rider's ride from the class they rode. A class the bundle has
dropped is *retired*, not deleted, exactly as `ClassTemplateSeeder` treats it on
the tablet (`class_templates.retired_at`, 23.2.6c).

So the cloud accumulates. That is correct and cheap: 72 rows of JSON is a
rounding error against one ride's samples.

CREDENTIALS
===========

Reads `local.properties` for `supabase.accessToken` (an `sbp_` personal access
token) and uses the Management API, so it needs no service-role key in a file
and no Supabase CLI. The token is account-wide — it is read into memory, used,
and never written anywhere.
"""

import argparse
import json
import pathlib
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
ASSETS = REPO / "app/src/main/assets/classes"


def local_property(name):
    for line in (REPO / "local.properties").read_text().splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    sys.exit(f"{name} is not in local.properties — see supabase/README.md")


def project_ref():
    url = local_property("supabase.url")
    return url.removeprefix("https://").split(".")[0]


def query(sql):
    """One statement through the Management API. Returns parsed rows."""
    token = local_property("supabase.accessToken")
    out = subprocess.run(
        [
            "curl", "-s",
            f"https://api.supabase.com/v1/projects/{project_ref()}/database/query",
            "-H", f"Authorization: Bearer {token}",
            "-H", "Content-Type: application/json",
            "--data-binary", json.dumps({"query": sql}),
        ],
        capture_output=True, text=True,
    ).stdout
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        sys.exit(f"the endpoint did not answer with JSON:\n{out[:500]}")


def bundled():
    """The 72 classes as they ship, keyed by id."""
    classes = {}
    for path in sorted(ASSETS.glob("*/*.json")):
        c = json.loads(path.read_text())
        classes[c["id"]] = c
    if not classes:
        sys.exit(f"no classes found under {ASSETS}")
    return classes


def sql_literal(value):
    """A Postgres string literal. Doubling the quote is the whole escape."""
    return "'" + str(value).replace("'", "''") + "'"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true",
                        help="write the changes (default is to show them)")
    args = parser.parse_args()

    classes = bundled()
    existing = {row["id"] for row in query("select id from public.class_templates;")}

    new = sorted(set(classes) - existing)
    updating = sorted(set(classes) & existing)
    orphaned = sorted(existing - set(classes))

    print(f"bundled: {len(classes)}   already up there: {len(existing)}")
    print(f"  to insert : {len(new)}  {new[:6]}{' …' if len(new) > 6 else ''}")
    print(f"  to refresh: {len(updating)}")
    print(f"  kept, no longer bundled: {len(orphaned)}  "
          f"{orphaned[:6]}{' …' if len(orphaned) > 6 else ''}")
    print("  (kept deliberately — a ride may point at one; 23.2.3 is additive)")

    if not args.apply:
        print("\nNothing written. Re-run with --apply.")
        return

    # `intervals_json` is JSONB in the cloud and an escaped JSON *string* in the
    # assets — the mismatch that made every cloud class fetch throw for the
    # project's whole history (14.2.2a). Cast explicitly rather than hoping.
    values = ",\n".join(
        "({}, {}, {}, {}, {}::jsonb)".format(
            sql_literal(c["id"]),
            sql_literal(c["title"]),
            sql_literal(c["category"]),
            int(c["duration_sec"]),
            # Already a JSON *string* in the assets, so it is passed through
            # rather than re-encoded — `json.dumps` on it would produce a
            # quoted string inside JSONB rather than an array, which is the
            # 14.2.2a mismatch in the other direction.
            sql_literal(c["intervals_json"]),
        )
        for c in classes.values()
    )

    statement = f"""
INSERT INTO public.class_templates (id, title, category, duration_sec, intervals_json)
VALUES
{values}
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    category = EXCLUDED.category,
    duration_sec = EXCLUDED.duration_sec,
    intervals_json = EXCLUDED.intervals_json;
"""
    query(statement)

    after = {row["id"] for row in query("select id from public.class_templates;")}
    missing = sorted(set(classes) - after)
    if missing:
        sys.exit(f"FAILED — these are still not up there: {missing}")
    print(f"\nDone. {len(after)} classes in the cloud; all {len(classes)} bundled ones present.")


if __name__ == "__main__":
    main()
