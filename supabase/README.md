# Pelonot — Supabase

The cloud is a **mirror, never a dependency**. With nothing configured,
`SupabaseModule.client` is null, every call returns `SyncOutcome.Disabled`, and
the app is fully functional offline. Everything here is optional.

---

## Migrations, in order

| File | What it does |
|------|--------------|
| `migration.sql` | Creates `profiles`, `class_templates`, `workouts`; enables RLS; seeds 72 class templates |
| `002_grants_and_sync_fix.sql` | Grants `anon` the privileges the app needs, adds the missing `profiles` INSERT policy, renames `workouts.timestamp` → `recorded_at` |

Run them in that order in the SQL Editor. `002` is non-destructive: it drops no
table and leaves the seeded class templates alone.

> **`migration.sql` alone does not produce a working project.** It enables RLS
> and writes policies but never `GRANT`s anything to `anon`. RLS narrows access
> a role already has; it cannot confer any. Without `002` every request fails
> `42501 permission denied for table` before a policy is evaluated, and
> PostgREST returns 401 — which the app logs and swallows.

## Configuring a build

```properties
# local.properties — gitignored, never committed
supabase.url=https://<project-ref>.supabase.co
supabase.anonKey=sb_publishable_...
```

Omit both and the app runs offline. These are the **only** two values that may
ever become `buildConfigField`s.

### Do not put an access token in a build

`local.properties` may also hold an `sbp_...` **personal access token** for
running migrations from the command line. It is account-wide — it can create,
modify and delete every project on the account. Nothing in
`app/build.gradle.kts` reads it and nothing in the source references it. Keep
it that way: a service-role key in a client app would be bad, and this is
worse.

## Running SQL without the CLI

The Management API needs only `curl` and an `sbp_` token:

```bash
curl -s "https://api.supabase.com/v1/projects/$REF/database/query" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary "$(python3 -c "import json;print(json.dumps({'query':open('supabase/002_grants_and_sync_fix.sql').read()}))")"
```

## Verifying a round trip

Reading the code is not sufficient and has twice given the wrong answer. Three
of the five defects found in this schema were invisible to `assembleDebug` and
to all 158 JVM tests, and one of them — epoch millis into a `TIMESTAMPTZ` —
only appeared on a real insert. Hit the endpoint.

```bash
# Should return 201. A 401 with 42501 means the grants in 002 are missing.
curl -s "$URL/rest/v1/workouts" \
  -H "apikey: $ANON" -H "Authorization: Bearer $ANON" \
  -H "Content-Type: application/json" -H "Prefer: return=minimal" \
  -w '\nHTTP %{http_code}\n' -d '{
    "id":"11111111-2222-3333-4444-555555555555","duration_sec":1200,
    "total_output_kj":142.5,"total_distance_km":8.1,"intent_modifier":1.0,
    "recorded_at":"2025-07-30T18:26:40Z","metrics_payload":[]
  }'
```

Distinguishing the failure modes:

| Response | Meaning |
|----------|---------|
| `42501 permission denied` | Grants missing — run `002` |
| `PGRST205 could not find the table` | Schema never applied — run `migration.sql` |
| `22008 date/time field value out of range` | Epoch millis sent to a `TIMESTAMPTZ` |
| `PGRST204 column ... does not exist` | DTO and schema have drifted apart |
| 401 with no body | The key itself is wrong or absent |

Delete the test row afterwards.

## What the grants deliberately allow

| Table | `anon` |
|-------|--------|
| `class_templates` | `SELECT` — public data |
| `profiles` | `SELECT`, `INSERT`, `UPDATE` |
| `workouts` | **`INSERT` only** |

`workouts` has no `SELECT` grant on purpose. The app uploads rides and never
reads them back, so withholding it means a leaked publishable key cannot
enumerate anyone's training history. This is the only thing currently limiting
exposure: **every RLS policy is still `USING (true)`**, because until accounts
exist (PLAN phase 15) there is no `auth.uid()` to scope them against. PLAN
15.5 rewrites them, and 14.10.4 says no key is checked into this repository
until that is done.
