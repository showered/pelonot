> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 14: Cloud sync that actually reaches the cloud — fundamental to the cloud tier

**Re-scoped by *The connectivity model*, 1 August 2026.** Everything here is
now behind an account (23.1): the app is complete without it, and a rider who
never signs in must never reach a single line of it. That is a demotion in
urgency and a promotion in how carefully it is gated. Two items in this phase
run today for riders who have consented to nothing — see the model's *What the
model makes false today*.

### 14.0 Are we connected? — findings, 31 July 2026

Short answer at the time of asking: **no, and not for the reason the code
suggested.** Established against the live project (`podsmtujqarlqhvorpdh`,
eu-west-1) rather than by reading, which changed the answer twice.

**The first failure was missing `GRANT`s, not the payload.** `migration.sql`
creates three tables, enables RLS and writes six policies, and never grants a
single table privilege to `anon`. RLS *narrows* access a role already has; it
cannot confer any. So every request — read and write — died with `42501
permission denied for table` before a policy was ever evaluated, and PostgREST
returned 401. The `anon` role held only `REFERENCES`, `TRIGGER` and `TRUNCATE`:
no `SELECT`, no `INSERT`.

Proof the tables themselves were fine: a genuinely missing table returns
`PGRST205`, an absent key returns a different 401, and `class_templates` held
all 72 seeded rows. `profiles` and `workouts` held **0 rows each** — nothing had
ever synced, confirmed by count rather than inferred.

| Path | What was wrong | State |
|------|---------------|-------|
| everything | `anon` had no DML grants at all. First and total blocker. | ✅ fixed in `002` |
| `syncWorkout` | `WorkoutDto` sends `recorded_at`; the table's column was `timestamp`. | ✅ column renamed in `002` |
| `syncWorkout` | `recordedAtEpochMs: Long` serialises as `1753900000000` into a `TIMESTAMPTZ` → `22008 date/time field value out of range`. **Found only by attempting a real insert** — invisible to every code reading. | ✅ DTO now emits ISO-8601 UTC |
| `syncProfile` | `profiles` had policies for `SELECT` and `UPDATE`, none for `INSERT`. | ✅ policy added in `002` |
| `syncProfile` | Upsert with no `onConflict` targets the primary key `id`, a UUID the DTO never sends — so every call inserts instead of updating. | ✅ `onConflict = "local_user_id"` |
| `syncWorkout` | The DTO carries **no `user_id`**, so a synced ride is anonymous and unattributable. | ❌ 14.3 |
| `fetchClassTemplates` | Cloud `intervals_json` is `JSONB` holding an array; `ClassTemplateDto` reads it as `String`. Decode throws — and the seeder reads the resulting `Failed` as "no cloud" and silently serves 5 bundled classes instead of the cloud's 72. | ✅ fixed in 14.2.2a |
| all policies | Every one is `USING (true)`. **Not currently an exposure** — the grants in `002` are narrow and `workouts` has no `SELECT` grant at all — but they activate the moment anyone widens a grant. | ❌ 15.5 |

> An earlier draft of this section claimed any client could read every rider's
> data. That was wrong: with no grants, nothing was readable by anyone. The
> `USING (true)` policies are a loaded gun rather than a fired one, and 15.5 is
> still where they get fixed.

### 14.1 Verified working

- [x] **14.1.1** `002_grants_and_sync_fix.sql` applied to the live project. Narrow grants by design: `class_templates` SELECT, `profiles` SELECT/INSERT/UPDATE, `workouts` **INSERT only** — a leaked publishable key cannot enumerate ride history
- [x] **14.1.2** A `workouts` row inserted with the anon key using the app's exact `WorkoutDto` shape — **HTTP 201**, `metrics_payload` intact as JSONB. The first row this project has ever accepted. Test row deleted afterwards
- [x] **14.1.3** Profile upsert round trip: `201` then `200` on repeat, one row, FTP updated in place rather than duplicated. Test row deleted afterwards
- [x] **14.1.4** `WorkoutDto` emits ISO-8601 UTC, with JVM tests covering the timezone drift and locale (`th-TH-u-ca-buddhist`) cases that would silently corrupt it
- [x] **14.1.5** A test asserting the serialised keys are a subset of the real column list — the failure mode that started all of this

- [ ] **14.1.6** **The round trip from the app itself.** Everything above was driven by `curl` with a hand-built payload; it proves the schema, the grants and the wire format, and it proves *nothing* about `WorkoutSyncWorker` enqueueing, running and posting. Install, ride, and see the row appear. **Per the house rule this phase is not complete until this box is ticked** — the whole point of the Corrections table is that "the pieces are right" has repeatedly not meant "it works".

      **Half done, 31 July 2026 (third sitting).** Driven from the app on the
      tablet AVD: a profile ride against the live project, `WorkoutSyncWorker`
      enqueued and ran, and it logged
      `Synced workout 992a6e8c-6dc7-45c3-9463-cf1f26247aa9 (135 samples)`.
      That is a real HTTP success — postgrest-kt throws a `RestException` on
      any non-2xx, so `SyncOutcome.Success` cannot be reached without one.
      **What is missing is the sighting.** `workouts` deliberately has no
      `SELECT` grant (14.1.1), so neither the app nor the anon key can read the
      row back, and this box asks to see it. Finish it with one Management API
      query — the recipe is in `supabase/README.md`:

      ```sql
      select id, duration_sec, jsonb_array_length(metrics_payload) as samples
      from workouts order by recorded_at desc limit 5;
      ```

      Expect that workout id with 135 samples. It is the only thing still
      standing between Phase 14 and done.

      One thing this half **did** prove, and it is not small: the app's *read*
      path to the cloud works. `ClassTemplateSeeder` now logs `Seeded 72 class
      templates from Supabase` — a live PostgREST `SELECT`, decoded and written
      into Room. See 14.2.2a

### 14.2 The rest of the path to full connectivity

- [x] **14.2.1** Carry the rider through: local `user_id` (Int) → cloud `profiles.id` (UUID). Requires the profile to sync first and its cloud id to be stored locally. Until this lands, every uploaded ride is anonymous.
      ***Done in the app and in `003_cloud_identity.sql`, and it found a second
      hole beside the one the item names. Not ticked as observed — the SQL is
      written, not applied; see 14.2.1a.***

      **The item's own hole.** `WorkoutDto` carried no `user_id`. The column
      existed, was nullable, and was simply never sent — so **every ride this
      app has ever uploaded arrived anonymous**, with the insert returning 201
      and the log saying `Synced`. It is non-null on the DTO now, so the
      anonymous shape does not compile.

      **The second one, which was worse.** `profiles` was keyed by
      `local_user_id`, `UNIQUE`, and that was the upsert's `onConflict` target.
      `local_user_id` is a **per-device autoincrement**: bike A's first profile
      is `1` and bike B's first profile is `1`, so the second tablet to sign in
      would not have collided harmlessly — it would have **updated the first
      rider's row**, overwriting their name, weight and FTP. That is not an
      edge case reachable by an unusual sequence; it is the first thing that
      happens on the day a second bike appears. The column is dropped from the
      cloud entirely: a column that looks like an identity and is not one is
      how this went wrong the first time.

      **The design departs from the item, deliberately.** 14.2.1 asks for a
      generated cloud UUID read back and stored locally, which is a two-step
      sync that can half-fail and leave a tablet holding a cloud profile whose
      name it does not know. It is also unnecessary: under **rule 2** a profile
      is in the cloud *if and only if* it has an account, so a cloud profile and
      an auth user are **1:1 by construction**. So `profiles.id` **is** the auth
      user id — which the app knows at the moment of signing in, needs no round
      trip to learn, and makes every RLS policy in 15.5 one line.

      **`CloudAccess.accountIdFor` collapses the gate and the identity into one
      lookup**, because "may this rider talk to the cloud?" and "who are they up
      there?" have the same answer. Asking twice means two lookups that can
      disagree, and the shape where they disagree is a call that passes the gate
      and then writes a row belonging to nobody — which is what the app did for
      its whole history. The choke point hands the account id *to the block*, so
      a request that does not know whose it is can no longer be written.

      Two new fences in `CloudAccessFenceTest`: the account id must come from
      the gate rather than off an entity, and `local_user_id` must never reach
      the wire. Both scan the source with comments stripped — the names they
      forbid are exactly the ones the KDoc has to say out loud to explain the
      rule, and a fence that documenting it breaks teaches the next person to
      delete the explanation
- [ ] **14.2.1a** **Apply `003_cloud_identity.sql`**, and note the two things in
      it that need a decision rather than a run. It **deletes every `profiles`
      row** — they were all written by the consent defect in the connectivity
      model's fourth row, they belong to riders who never signed in to anything,
      and their `gen_random_uuid()` ids have no auth user behind them so they
      cannot survive the new foreign key. And it **revokes anon's access to
      `profiles` and `workouts` entirely**, which means 14.1.6 can no longer be
      driven by hand-setting `auth_user_id` on the tablet: the app would still
      be sending an anon key with no session behind it. That is the right trade
      and worth stating — a round trip proved with a key that bypasses RLS
      proves the path a real rider does *not* take.

      ***Authorised by the owner, 3 August 2026:*** *"I'm happy for you to
      delete all data on new installs of the APK. We are still building the
      app."* So the `DELETE FROM public.profiles` is agreed rather than
      outstanding, and the same licence covers a local wipe if one is ever
      needed — the app is pre-release and there is no rider history to protect
      yet. **It was not applied in the eighteenth sitting** only because the
      owner asked for the plan to be updated and the session to stop.

      Two things still to decide *when* it is run, neither of which the
      authorisation covers:

      - **Run `003` before or after 15.1?** After is easier — `003` makes the
        anon key unable to reach `profiles` or `workouts`, so between running it
        and having `auth-kt` installed the cloud is unreachable by anything.
        Before is *safer* — it means no window in which a session exists and
        `USING (true)` is still live. **Prefer before**, and accept the dead
        window: nothing reaches the cloud today anyway (no profile has
        `auth_user_id`), so the window costs nothing real.
      - **The one `workouts` row is kept deliberately**, because 14.4.5 wants to
        `pg_column_size()` the pre-14.4 payload shape and that row is the only
        specimen. It has `user_id` NULL, so it breaks no new constraint; it
        simply becomes invisible to PostgREST, which is correct for an
        unattributed ride and still readable through the Management API
- [ ] **14.2.4a** **Nothing nulls `synced_at` when a ride is edited**, because
      nothing in the payload is editable today — `rpeRating` and the FTP
      proposal flag are the only things that change after a ride ends and
      neither travels. The moment something in the payload becomes editable,
      that edit has to clear this column or the cloud keeps a copy the rider has
      since corrected. Opened now rather than when it bites, because the failure
      is silent and the fix is one line at a call site nobody will be looking at
- [x] **14.2.2a** **The app now reads both shapes**, which is what actually
      unblocked this. `intervals_json` is an escaped JSON *string* in the
      bundled assets and a `JSONB` *array* in the cloud; `ClassTemplateDto`
      typed it `String`, so every cloud read threw
      `JsonDecodingException: Expected beginning of the string, but got [`.
      That went into `SyncOutcome.Failed`, which `ClassTemplateSeeder` reads as
      "cloud unavailable" and answers by falling back to assets — so the
      failure was silent and its **only symptom was a class library with 5
      classes in it instead of 72**, which nobody had connected to the cloud at
      all. `IntervalsJsonSerializer` accepts either shape and yields the string
      form; it re-encodes an array as an array so a JSONB value cannot be
      written back as a quoted blob. *Observed: `Seeded 72 class templates from
      Supabase`, eight categories where there were four, and a cloud-sourced
      class rendering its seven intervals on the detail screen.* Four JVM tests
- [ ] **14.2.2** Settle `intervals_json` as one type on both sides — `TEXT` holding the JSON is the honest choice, since the app treats it as an opaque string it hands to `IntervalParser`. Less urgent now that 14.2.2a makes the app correct either way, and correct against whichever way a self-hoster sets theirs up
- [x] **14.2.3** **Surface sync state in Settings**: configured or not, last successful sync, count pending, and the actual error text of the last failure. `SyncOutcome.Failed` dies in `Log.w` today, which is precisely why this went unnoticed for the project's whole history.
      *Done, and **two of the three states observed on the tablet AVD** —
      *"3 rides waiting to go up since Jul 23, 2026 12:59 PM"* and *"Nothing is
      waiting to go up"*. The failing state is covered by
      `CloudSyncStatusTest` and has **not** been seen rendered, because
      producing one on the AVD means a real request to the live project and
      that is not a thing to do casually; it will be seen for free the first
      time 14.2.1a's endpoint refuses something.*

      *The decision of what is **true** is `domain/cloud/CloudSyncStatus`, pure
      and JVM-tested, because a `@Composable` cannot be asked "what would you
      say if the last success were older than the oldest waiting ride?". Four
      rules came out of writing it, all of them the same rule — **never imply
      the rider is covered when they are not**:*

      - ***No account is not a failure state.** Most riders live on the middle
        rung, and drawing it red is how signing in becomes the way to make a
        warning go away — the opposite of rule 2, where signing in *is* the
        consent and consent extracted by nagging is not consent*
      - ***A failure the app has recovered from is not news.** The drain clears
        the record when it finishes with an empty backlog. A red line with
        nothing wrong behind it teaches the rider to ignore the line, and then
        it is worth nothing on the day it matters*
      - ***A failure names the rides it stranded.** Three waiting since this
        morning and three waiting since March are the same count and completely
        different news*
      - ***"Never" is not a date.** A null last-sync stays null; formatted as 0
        it puts the rider's backup in January 1970*

      ***The AVD changed one sentence, which is the reason for driving it.***
      The empty-backlog line first read *"No rides have gone up yet"* — a claim
      the app cannot support, because an empty backlog with no recorded sync is
      **also** what a rider sees after restoring a backup file made on another
      tablet: the rides arrive already marked and the DataStore mark does not
      travel with them. It says *"Nothing is waiting to go up"* now, which is
      true in both cases
- [x] **14.2.4** `synced_at` on `workouts` locally, so a ride uploads once and a backlog is knowable.
      *Done and observed — migration 9 → 10, verified on the tablet AVD against
      real SQLite. **Nullable, and not backfilled.** Stamping the migration's
      own clock onto every existing row is one line and would claim the rider's
      entire local history was safely in the cloud, which is the exact false
      reassurance the column exists to prevent — the same family as 6 → 7's
      `ftp_watts` and 3 → 4's `power_is_measured`, where a backfilled guess is
      indistinguishable from a recorded fact and the indistinguishable part is
      what makes it dangerous. Nullable rather than a boolean for three
      reasons, in order: a backlog has to be **ordered** to be drained
      oldest-first; **"how stale is my backup?"** is a question a flag cannot
      answer; and the payload is versioned inside itself (14.4.3), so a future
      reader needs a date to compare against. `MIN()` over an empty backlog is
      SQL NULL and is kept as null — read as 0 it would tell the rider their
      backup was 56 years behind, on the screen whose whole job is to say
      whether their history is safe*
- [x] **14.2.5** Retry the backlog when connectivity returns, not only at ride end.
      *Done as a **change of shape** rather than a retry policy on top of the
      old one. `WorkoutSyncWorker` takes a **profile** now, not a workout id,
      and drains `synced_at IS NULL` oldest-first in batches of 20. The
      property that matters: **a ride that exhausts its retries is not lost** —
      it is simply still in the backlog, and the next ride the rider finishes
      sweeps it up. Nothing is permanently forgotten, which is what lets this
      be called a backup rather than an attempt. Oldest-first is not a
      preference: newest-first leaves the oldest rides permanently at the back
      of a queue that every subsequent ride overtakes, and a rider's first
      month is the part they would most miss. `APPEND_OR_REPLACE` rather than
      `KEEP`, because a drain already running has taken its snapshot and `KEEP`
      would silently drop the ride that just finished*
- [x] **14.2.6** Upload the rides already sitting in the local database — there is a real history on the tablet that predates sync working.
      *Falls out of 14.2.5 rather than being built beside it: a history that
      predates sync is a history where every row has `synced_at IS NULL`, which
      is the backlog query with no special case. **It is also 15.3.1** — a
      rider who has just attached an account is in exactly this state, so
      "backfill everything on first sign-in" and "drain the backlog" are one
      implementation rather than two that drift (the 18.9 argument, applied a
      phase early). The one thing still owed is the trigger: nothing calls it
      at sign-in because there is no sign-in, which is 15.3.1's half*
- [ ] **14.2.7** Decide the metrics payload ceiling. A 45-minute ride is ~2,700 samples in one JSONB column; a 90-minute ride is double that. Find the point where the insert starts failing before a rider does. **Partly answered, 1 August 2026** — the sizes are measured in *What a workout costs*: 228 KB on the wire for 45 minutes, 457 KB for 90. That is not near any hard PostgREST limit, but it is a large single body from a bike tablet on household wifi, and **14.4** halves it four times over
- [ ] **14.2.8** `supabase/003_*.sql` for whatever 14.2.1 and 14.2.2 need, keeping migrations incremental and non-destructive — `002` deliberately did not drop or recreate anything, and the 72 class templates are still the originals

### 14.3 Keeping it working
- [ ] **14.3.1** A round-trip check that can be re-run against a throwaway project, scripted and documented in `supabase/README.md`. Three of the five defects above were invisible to `assembleDebug` and to all 158 JVM tests
- [ ] **14.3.2** Keep `supabase/*.sql` and the DTOs verifiably in step — the column-name test in `WorkoutDtoTest` is a start, but it hardcodes the column list and nothing forces it to match the live schema
- [ ] **14.3.3** Fold the schema into CI (19.1.4) once there is a CI to fold it into

### 14.4 The payload format — change it now, while the cloud is empty

The sizes and the reasoning are in *What a workout costs*. The reason this is
an item rather than an optimisation is timing: `workouts` holds **one row**,
and the format is free to change today and a migration-with-backfill to change
once four riders have a year of history up there.

- [x] **14.4.1** `metrics_payload` becomes **columnar**: `{"t":[…],"c":[…],"r":[…],"p":[…],"hr":[…]}` rather than an array of 2,700 five-key objects. **228 KB → 49 KB** on the wire for a 45-minute ride; ~30 KB → ~19 KB stored. Same data, same nullability, one twentieth of the key strings.
      *Done. **The two numbers are measured rather than modelled** — the test
      builds the old shape beside the new one from the same 2,700 samples and
      reports `columnar 49 KB against 228 KB per-sample`, which is the storage
      budget's estimate confirmed to the kilobyte. Getting there needed one
      thing the estimate did not mention, below*
- [x] **14.4.1a** **11 KB of a 45-minute ride was trailing zeroes.** The first
      columnar draft measured **64 KB**, not 49: `cadence` and `resistance` are
      `Double`, the board reports them whole, and `80.0` is two characters more
      than `80` across three columns and 2,700 samples. `CompactDouble` writes a
      whole number without its decimal and everything else unchanged — not a
      rounding and not a precision decision, since `80` parses back to exactly
      `80.0`. Power keeps its tenth, because 14.4.6 says that tenth is real
- [x] **14.4.2** **`t` stays explicit.** Implying the timestamp from the array index saves another 12 KB and silently closes the gaps that 2.4.4 deliberately leaves, that 16.1.2 and 16.2.2 deliberately draw, and that a ride with a two-minute bottle stop in it genuinely has. A cloud copy that looks continuous when the ride was not is a fabricated record, which is the one thing this project does not do. *Kept, and tested with a ride that jumps 1 → 120*
- [x] **14.4.3** A `payload_version` on the row, or the shape is undecidable for any future reader — the web app (17.3) is the reader that will care.
      ***Inside the payload rather than beside it, which is a deliberate
      departure from the item.*** A column and the JSON it describes are written
      by different code and can drift, and a version that disagrees with its
      payload is worse than no version at all; inside the object they cannot
      come apart. Nothing is lost by it — `payload->>'v'` is queryable in
      Postgres, and a reader holding only the JSON can still decide what it has.
      **An absent `v` means the pre-14.4 array-of-objects**, which is exactly
      the one row already in the cloud
- [x] **14.4.4** A round-trip test: entity list → payload → entity list, asserting a null heart rate survives as null and a gapped series comes back gapped. Both are failures this project has already had in other places.
      *Both asserted, plus the two the writing turned up: a ride nobody wore a
      strap for **omits `hr` entirely** — an absent column and 2,700 nulls are
      the same claim, and it is emphatically not the claim a zero would make —
      and **columns of unequal length are rejected rather than repaired**,
      because past that point sample 900's power lines up with sample 900's
      cadence only by luck. Same argument as the telemetry fence*
- [ ] **14.4.5** Confirm the stored size with `pg_column_size()` rather than trusting the model in *What a workout costs*. Same trip as 14.1.6; the query is in that section. **Still open and still needs the trip** — and note it now measures the *old* shape, since the one row up there predates 14.4
- [x] **14.4.6** Settle the `getFloat().toDouble()` question first — finding 3 in that section. If the board reports fractional values, the noise digits are in the payload, in the exports, in the charts and in the calibration grid, and they are a bigger problem than the format.
      **Settled on the bike's own database, and it needed no rider** — one
      `sqlite3` query over 1,661 recorded rows across its four rides. Two
      halves to the answer:
      - **The board does report fractional power, and the digits are real.**
        Power arrives in tenths of a watt (`0x44`, and `PelotonFrameParser`
        does `magnitude / 10.0`), so `29.7` and `1.4` are measurements. 1,360
        of the 1,661 rows are fractional
      - **The noise the finding feared existed, and is already gone.**
        `29.2000007629395` is `29.2f` widened to a double, and it appears only
        in the three rides recorded *before* 2.7c: the fix that made the frame
        decide the metric also took the value off `getFloat()`. The post-fix
        ride carries clean tenths. Cadence and resistance are integral in every
        row of every ride, which is what 14.4.1a then cashes in.
        **Nothing is rewritten** — those three rides are already marked as
        suspect by 2.7.5, and marking rather than editing is the rule
- [x] **14.4.7** **The payload does not carry `power_is_measured`, and that is now the only thing it drops.** Opened by 14.4.1 rather than solved by it: the column is per sample and nullable, so honestly it is a sixth array — but on the bike every entry is the same value, and 2,700 `true`s cost 13 KB against a 49 KB payload. The distinction is not decorative (`PowerProvenance` gates the FTP proposal and the household leaderboard), so a cloud copy without it cannot tell a measured ride from a modelled one. Three ways: the sixth array; a scalar when the whole ride agrees and an array when it does not, which is two shapes for one field and how readers start guessing; or on the row, where the *ride's* provenance arguably belongs anyway since `PowerProvenance` already reduces the series to one answer

      **The sixth array, and the third option is the one worth arguing with.**
      Putting the ride's provenance on the row is tempting precisely because
      `PowerProvenance` does reduce the series to one answer — but it reduces
      it *from the samples*, and `Mixed` exists because a board that drops out
      mid-ride leaves samples that disagree. A scalar has to pick a side, which
      is the same fabrication `t` refuses to commit when it declines to imply
      the second from the array index. So: `pm`, per sample, following `hr`'s
      rule for absence — **no array means every sample unknown**, which is
      exactly what every ride recorded before the column existed is, and what a
      restored ride must come back as rather than as `Modelled`.

      **The 13 KB that made the row look attractive is 5 KB.** `CompactBoolean`
      writes `1` and `0` rather than `true` and `false` — the same trick as
      `CompactDouble` one field up, and worth three characters a sample across
      2,700 of them. Measured: a 45-minute ride is **55,635 bytes**, against
      49 KB before and a budget that moves 56 → 60 KB to keep its headroom.
      Written as JSON booleans it would not have fitted at all. So the cheap
      encoding buys the honest shape rather than only bytes.

      *Five tests, and two of them are the argument rather than the code: a
      measured ride comes back `Measured` (without the column every restored
      ride is `Unknown`, and `Unknown` fails `isTrustworthyAsMeasured` — so a
      cloud copy of a real bike ride could not propose an FTP or stand on a
      household leaderboard the original qualified for), and a ride the board
      dropped out of comes back `Mixed` sample for sample. A short `pm` column
      is rejected by the same length check as the rest. 448 JVM tests, 0
      failures.*

### 14.10 Configuring the endpoint — open-source hygiene

The endpoint must be configurable **in code, not in the app's UI**: a rider
should never be asked to type a URL, and a self-hoster should not need to fork
a screen.

- [x] **14.10.1** A checked-in `cloud.properties` (or `CloudConfig.kt`) holding the default endpoint and publishable key, overridable by `local.properties` and then by env vars. Today the only source is `local.properties`, which is **gitignored** — so a fresh clone of an open-source project has no cloud at all and no in-repo record of what the community endpoint even is
- [x] **14.10.2** Precedence documented in the README: env → `local.properties` → checked-in default → offline
- [x] **14.10.3** Keep `SupabaseModule.client == null` and `SyncOutcome.Disabled` as the behaviour when nothing is configured. **Offline-first is not negotiable**; the cloud stays a mirror
- [x] **14.10.4** Only publish a default key **after 15.5**.

      ***Answered by the owner, 3 August 2026, and the answer closes the item
      by removing the thing it was worried about.*** In the owner's words: this
      build points, **through environment variables**, at a Supabase endpoint
      used by *"my household and one or two friends"*, and *"data is not an
      issue"*.

      **So there is no community endpoint to fund, and there never was going to
      be one.** The bill argument below was sized for a published default
      serving strangers; a household endpoint at four riders is ~6 MB of
      Supabase a year against a 500 MB allowance (*What a workout costs*), which
      is three orders of magnitude of headroom. The owner also intends
      **retention** on top of that — old rides condensed to their aggregates
      rather than kept sample by sample — which is **23.4**, now wanted rather
      than deferred.

      **Nothing about the shipped configuration changes, and that is the point
      of recording it here.** `cloud.properties` stays checked in and **empty**,
      and `CloudConfigFenceTest` keeps failing the build if it stops being. The
      reason is now the stronger one rather than the speculative one: the
      endpoint is the **owner's household's**, so checking it in would hand a
      private project to every clone of a public repository. Precedence is
      unchanged — env → `local.properties` → `cloud.properties` → offline — and
      the env layer is exactly the one the owner is using, which is what it was
      built for (14.10.2). A self-hoster still stands up their own (14.10.5).

      **One thing the answer does not change, and it is worth separating.**
      *Volume* is settled; *isolation* is not. "One or two friends" means real
      people's rides sharing one project, which makes **15.5.4** more load-
      bearing rather than less — every policy is `USING (true)` until `003` is
      applied, and on a shared endpoint that is every rider able to read every
      other rider. The two questions were only ever adjacent, and the owner
      answered the first.

      *The original reasoning, kept because it is still the right answer for
      anyone who does publish a default:* A publishable key is safe to check in exactly when RLS is correct, and right now it is `USING (true)` — publishing it today would publish everyone's data with it.
      **A second reason, added 1 August 2026 and less obvious than the first:
      a published endpoint is a bill.** At the measured ~30 KB a stored ride
      (*What a workout costs*), Supabase's 500 MB free tier is about 13,000
      rides — **250 riders riding once a week for a year**, or sixty riding
      properly. The community endpoint fills up in its first year and then
      fails for everyone at once, including the riders who trusted it with
      their only backup. Decide who pays, or decide that the default is
      **no endpoint** and a self-hoster stands up their own (14.10.5).
      *Not ticked, because the decision it asks for is still 15.5's to make —
      but it is **written down and fenced** now rather than living here:
      `cloud.properties` ships with both values empty and its own comments
      carrying both arguments, and `CloudConfigFenceTest` fails the build if
      either stops being blank. So filling it in has to be a decision somebody
      takes, which is all this item was ever protecting*
- [x] **14.10.5** `supabase/README.md`: how to stand up your own project, run the migrations in order, and point a build at it.
      *It had the migrations and the build lines already; what it lacked was
      the four steps before them and the reason there is no endpoint to join —
      **there is deliberately no community project**, so "stand up your own" is
      the only path and the README now opens with it*

      *The four above landed together, because they are one change: the
      precedence is `env → local.properties → cloud.properties → offline`, a
      blank counts as absent at every level (an exported-but-empty variable
      falls through rather than blanking the build), and the root README carries
      the table. `cloud.properties` ships **empty**, which is 14.10.4's answer
      written down where a contributor will meet it rather than left in a plan
      file. Verified: with `local.properties` present the URL still reaches
      `BuildConfig`, so the new layer changed nothing for an existing checkout*

### 14.11 Credential hygiene

`local.properties` currently holds three values, and one of them is far more
dangerous than its name suggests.

- [x] **14.11.1** `local.properties`' third Supabase value (was `supabase.serviceKey`) is **not** a service-role key — it is an `sbp_` **personal access token**, which is account-wide and can create, modify and delete *every project on the account*, not just this one. It is correctly gitignored and, verified, is read by nothing in `app/build.gradle.kts` and referenced nowhere in the source, so it cannot reach `BuildConfig` or an APK
- [x] **14.11.2** Renamed to `supabase.accessToken` so nobody wires it into `BuildConfig` on the assumption that it belongs there. A service-role key in a client app would be bad; **this one is worse**
- [x] **14.11.3** *Fenced, not merely intended: `CloudConfigFenceTest` reads
      `app/build.gradle.kts` and asserts the `secret()` calls are exactly
      `supabase.url` and `supabase.anonKey`, in that order, and that the string
      `accessToken` appears in the build script nowhere at all. A third call is
      one line and reaches an APK; this is the line that has to fail first.*
      Never add a `secret()` call for it. The two that exist (`supabase.url`, `supabase.anonKey`) are the only two that may ever become `buildConfigField`s
- [ ] **14.11.4** Rotate it when the schema work is done — it has been used from a shell and lives in a plaintext file
- [x] **14.11.5** Said in `supabase/README.md`, since a contributor following the setup will otherwise put whatever key they find into the same file
