# Pelonot — Implementation Plan

> **Open-Source Peloton Client** — A subscription-free fitness app for Peloton
> bikes (Gen 1/Gen 2). **A stock, un-jailbroken bike is the supported target**;
> telemetry comes from Peloton's own sensor service, not from root (see 2.1a).

---

## How to use this plan

1. **Each checkbox is one focused task** — small enough for a single session, large enough to matter.
2. **A box is only ticked when the behaviour has been observed working**, not when the code was written. Several items in this plan were previously ticked while the feature was non-functional (see [plan/corrections.md](plan/corrections.md)); that is the failure mode this rule exists to prevent.
3. **Work phases in order** where later ones build on earlier ones. See *Where the work stands* immediately below for the current priority.
4. When switching models or sessions, paste the current plan state so the next session knows where to pick up.

---

## The owner's inbox — ideas between sessions

**The owner writes here directly, without opening a session.** It is a way of
handing over a thought at the moment of having it rather than at the moment of
being able to act on it: an idea does not have to wait for a prompt, and it does
not have to interrupt work already in flight. One heading per idea.

**How a session handles it.** Read this section before picking work. What is in
it is the owner speaking, and it outranks the *What to do next* ordering below.
Take an entry, decide where in the plan it belongs, write it up as numbered
items with the reasoning kept rather than summarised — and then **empty the
entry out of this section**. An idea still sitting here has not been dealt with;
an idea that has moved has.

*Empty. Three entries have passed through it. Standing and seated riding is now
**Phase 25**, built but for one judgement that needs a rider (25.3.4). The two
left in the eighteenth sitting are written up with the owner's words kept
verbatim at the head of each:*

- ***Max panel width** → **22.4**. The point is that `readableWidth` is a rule
  about a **line**, not about the **screen**, and every surface that took the
  token in 22.2.6 chose "cap it" by default because that was the only answer on
  offer. The rule it lands on: **cap what is read at arm's length, tile what is
  looked at.** The owner's own example — ride detail's charts in one column — is
  22.4.2.*
- ***Initial FTP** → **20.3**. "Nobody in their right mind would know this",
  and the field is the third thing the app ever says to a rider. Two facts
  constrain it and pull against each other: the app **cannot** have no number
  (FTP is the denominator of the whole zone system and is written onto the ride
  at its start), and inferring one from the first ride is gated on measured
  power (7.10.7), so it cannot be the **first** act. The owner's two routes are
  a real choice and 20.3.2 is where it gets made rather than assumed.*

### In-ride targets

UX-wise it's difficult to know what to focus on. It says Zone 2, but then it also prescribes cadence and resistance. I think it needs to be one or the other. Please have a think about best UX and provide me a HITL suggestion. I love Power Zones and it means it scales to a person's fitness. However there is a time and place for prescribing cadence, because spin-ups and climbs are very different exercises. Perhaps there's a way we can use both? But overall the impression I get when i'm riding is: "what do i do? do i focus on zone, cadence, or resistance?"

### Resume session

I recently had a crash (it's beng fixed right now in a worktree) but this made me think -- in addition to just "saving" an interrupted ride, we should be able to RESUME it.

---

## Where the rest of this plan lives

**This file is the index.** It holds the owner's inbox, where the work stands,
what to do next and the status table — the things every session reads. The
phases themselves live in `plan/`, one file each, so a session can open the two
it needs instead of sixty thousand tokens of everything.

Item numbers are unchanged and still the way to refer to anything: **2.7c**,
**11.1b.9**, **24.3.1** mean what they always did, and the table says which
file to open for each.

| File | What is in it |
|------|---------------|
| [plan/session-log.md](plan/session-log.md) | Every sitting before the latest one, the 31 July snag list, and the three narratives that changed the shape of the project |
| [plan/connectivity.md](plan/connectivity.md) | **The connectivity model** — the four rules, the identity ladder, what the model makes false today |
| [plan/fundamentals.md](plan/fundamentals.md) | What is fundamental and what is not |
| [plan/corrections.md](plan/corrections.md) | Items previously ticked that were not working — and why the house rule exists |
| [plan/storage-budget.md](plan/storage-budget.md) | What a workout costs, and whether 500 MB runs out |
| [plan/phases-complete.md](plan/phases-complete.md) | Phases **0, 1, 3, 4, 5, 6, 9** — scaffolding, Room, the service, the overlay window, the HUD UI, the main app UI, ride integration |
| [plan/phase-02-telemetry.md](plan/phase-02-telemetry.md) | **Phase 2** — the sensor service, the frame parser, BLE, calibration, and **2.7**, the corruption defect and its fix |
| [plan/phase-07-ftp.md](plan/phase-07-ftp.md) | **Phase 7** — auto-FTP, the FTP a ride was ridden at (7.8), FTP history (7.9) |
| [plan/phase-08-polish.md](plan/phase-08-polish.md) | **Phase 8** — polish, testing, edge cases, Material Expressive |
| [plan/phase-10-hardware.md](plan/phase-10-hardware.md) | **Phase 10** — hardware validation on the real bike |
| [plan/phase-11-hud.md](plan/phase-11-hud.md) | **Phase 11** — the HUD-first experience, the overlay's design, volume, and the full ride screen (11.6) |
| [plan/phase-12-history.md](plan/phase-12-history.md) | **Phase 12** — ride history, the rider's record, migrations (12.5) |
| [plan/phase-13-units.md](plan/phase-13-units.md) | **Phase 13** — units and display preferences |
| [plan/phase-14-cloud.md](plan/phase-14-cloud.md) | **Phase 14** — cloud sync that actually reaches the cloud |
| [plan/phase-15-accounts.md](plan/phase-15-accounts.md) | **Phase 15** — accounts, the thing that unlocks the cloud tier |
| [plan/phase-16-visualisation.md](plan/phase-16-visualisation.md) | **Phase 16** — data visualisation |
| [plan/phase-17-18-across-bikes.md](plan/phase-17-18-across-bikes.md) | **Phases 17 and 18** — the companion web app, and social across bikes |
| [plan/phase-19-ideas.md](plan/phase-19-ideas.md) | **Phase 19** — ideas worth having, ranked |
| [plan/phase-20-profiles.md](plan/phase-20-profiles.md) | **Phase 20** — who's riding: the profile selector and avatars |
| [plan/phase-21-hr-zones.md](plan/phase-21-hr-zones.md) | **Phase 21** — heart-rate zones |
| [plan/phase-22-dashboard.md](plan/phase-22-dashboard.md) | **Phase 22** — the dashboard |
| [plan/phase-23-offline.md](plan/phase-23-offline.md) | **Phase 23** — offline by default, and the bundled class library |
| [plan/phase-24-household.md](plan/phase-24-household.md) | **Phase 24** — household social, the tier that needs no cloud |
| [plan/phase-25-position.md](plan/phase-25-position.md) | **Phase 25** — out of the saddle |
| [plan/reference.md](plan/reference.md) | The Coggan zone table and the ride-intent multipliers |

**Adding to the plan:** put the item in its phase's file. Only the four
sections of *this* file move with each session — the inbox, the latest sitting,
*What to do next*, and the status table. When a sitting's narrative stops being
the latest, it goes to the top of `plan/session-log.md`.

---

## Where the work stands — read this first

### Latest session — 3 August 2026 (eighteenth sitting): what has to be true before the app goes online

**The owner's question was the assessment one** — what blocks the online tier
(accounts, friends, a leaderboard, a companion web app), and what ought to be
done *before* it. The answer turned out to be concrete rather than a list of
phases: **the cloud had no idea who anything belonged to**, in two separate
ways, and both are schema decisions that are free today and a
migration-with-backfill once real histories are up there. That is the 14.4
argument — change the shape while the cloud is empty — applied to identity.

Closed **14.2.1**, **14.2.4**, **14.2.5** and **14.2.6**; **15.5.1–15.5.3** are
written but deliberately unticked. The owner's inbox is emptied into **22.4**
and **20.3**. 477 JVM tests, 0 failures, and 40 instrumented tests green on the
tablet AVD.

**Hole one: every ride this app has ever uploaded arrived anonymous.**
`WorkoutDto` carried no `user_id`. The column existed, was nullable, and was
never sent — so the insert returned 201, the log said `Synced`, and the row
belonged to nobody. An anonymous ride cannot be restored onto a second bike,
cannot stand on a leaderboard, and is invisible to any policy written against
`auth.uid()`. It is **non-null on the DTO** now, so the anonymous shape does not
compile.

**Hole two, which was worse, and nobody had looked at it.** `profiles` was keyed
by `local_user_id`, `UNIQUE`, and that was the upsert's `onConflict` target.
`local_user_id` is a **per-device autoincrement**. Bike A's first profile is
`1`; bike B's first profile is `1`. The second tablet to sign in would not have
collided harmlessly — **it would have updated the first rider's row**, name,
weight and FTP. Not an edge case reachable by an unusual sequence: the first
thing that happens on the day a second bike appears, which is the day the online
tier exists at all.

**The fix departs from what 14.2.1 asked for, and the reason is rule 2.** The
item wanted a generated cloud UUID read back and stored locally — a two-step
sync that can half-fail and leave a tablet holding a cloud profile whose name it
does not know. But a profile is in the cloud *if and only if* it has an account,
so **a cloud profile and an auth user are 1:1 by construction**. So
`profiles.id` **is** the auth user id: known at the moment of signing in, no
round trip to learn it, and every RLS policy becomes one line.

**`CloudAccess.accountIdFor` collapses the gate and the identity into one
lookup**, because "may this rider talk to the cloud?" and "who are they up
there?" have the same answer. Asking twice means two lookups that can disagree,
and the shape where they disagree is a call that passes the gate and then writes
a row belonging to somebody else. The choke point hands the account id *to the
block*, so a request that does not know whose it is can no longer be written.
Two new fences hold it — and both scan the source **with comments stripped**,
because the names they forbid are exactly the ones the KDoc must say out loud to
explain the rule, and a fence that documenting it breaks teaches the next person
to delete the explanation rather than keep the rule.

**Then the thing that turns a curiosity into a backup (14.2.4–14.2.6).** The app
has never known what it had *not* uploaded. The worker fired once at the end of
a ride, got three attempts, and the question closed forever — a ride that failed
while the router rebooted was indistinguishable from one that succeeded, because
neither fact was written anywhere. The only trace was a `Log.i` line on a tablet
whose `log.tag` is `W`. `workouts.synced_at` (migration 9 → 10) is that fact,
**nullable and not backfilled**: stamping `NOW()` on every existing row is one
line and would claim the whole local history was safe, which is the exact false
reassurance the column exists to prevent. Same family as 6 → 7's `ftp_watts`.

The worker is a **backlog drain keyed by profile** now rather than a post keyed
by ride, and the property that buys is the one that matters: **a ride that
exhausts its retries is not lost** — it is still in the backlog and the next
ride sweeps it up. Nothing is permanently forgotten, which is what lets this be
called a backup rather than an attempt. Oldest-first, because newest-first
leaves a rider's first month permanently behind every ride they do next. And it
**is** 15.3.1: a rider who has just attached an account has a history where
every row is unsynced, so "backfill on sign-in" and "drain the backlog" are one
implementation rather than two that drift.

**What is written and deliberately not ticked.** `supabase/003_cloud_identity
.sql` carries the schema, the RLS rewrite against `auth.uid()` and the grants
moved from `anon` to `authenticated`. It is **not applied**. Two things in it
need a decision rather than a run: it **deletes every `profiles` row** (all of
them were written by the consent defect, belong to riders who never signed in,
and have `gen_random_uuid()` ids with no auth user behind them), and it
**revokes `anon` from `profiles` and `workouts` entirely** — which means 14.1.6
can no longer be driven by hand-setting a column, because the app would still be
sending an anon key with no session. That is the right trade: a round trip
proved with a key that bypasses RLS proves the path a real rider does *not*
take. And **15.5.4 stands unchanged and is now the most important item in
Phase 15** — two real sessions bouncing off each other's rows. Reading the file
is not that check and neither is running it successfully.

One thing about the cascade worth carrying, because it is backwards from the
local rule and getting it wrong either way is serious. **Locally, deleting a
profile keeps its rides** — a housemate leaving does not erase their history off
the bike. **In the cloud, deleting the account must take the rows with it**,
because that is what 15.4.3 means. `SET NULL` up there would leave orphans no
`auth.uid()` matches: invisible to every policy and therefore undeletable by the
rider they belonged to, so "delete my cloud data" would not have.

**And the inbox, emptied.** *Max panel width* → **22.4**: `readableWidth` is a
rule about a **line**, not about the **screen**, and every surface that took the
token in 22.2.6 chose "cap it" because that was the only answer on offer. The
rule it lands on is *cap what is read at arm's length, tile what is looked at*,
and the owner's own example — ride detail's charts in one column — is 22.4.2.
*Initial FTP* → **20.3**: the field is the third thing the app ever says to a
rider and nobody can answer it. Two facts constrain the fix and pull against
each other — the app **cannot** have no number (FTP is the denominator of the
whole zone system and is written onto the ride at its *start*), and a first-ride
inference is gated on measured power (7.10.7) so it cannot be the *first* act.
The owner offered two routes and 20.3.2 is where the choice gets made rather
than assumed.

### Previously — 3 August 2026 (seventeenth sitting): three quick items, then Phase 16 finished

No bike and no rider, and nothing here needed either: a wire format, a
workflow file, a class and the build's own hygiene. Closed **14.4.7**,
**25.4.3**, **14.10.1 / 14.10.2 / 14.10.3 / 14.10.5** and **14.11.3**;
**19.1.4** is written but stays unticked until a run is green on GitHub, which
is the house rule doing its job rather than paperwork. 452 JVM tests, 0
failures.

**Then Phase 16, which is finished.** Closed **16.1.5a**, **16.3.2**,
**16.3.3**, **16.3.4** and **16.3.5**, all five observed on the tablet AVD.
471 JVM tests, 0 failures.

**The cadence the class asked for has somewhere to be drawn (16.1.5a).** An
interval prescribes a cadence range as well as a zone and the distribution has
no time axis to lay one on, so the data was parsed and thrown away. There is a
*Cadence over time* card now with the blocks under the trace, and the old
histogram beside it as *Cadence spread*. The blocks are **absolute rpm** —
riding a class easier means fewer watts, not slower legs — and **zeros are
drawn**, unlike the spread, because a coast is measured and happened at a
moment. Its compliance is counted separately from the power's, which is the
whole point: the fixture ride reads *63%* on the power card and *0%* on this
one, obedient in watts and not in legs.

**Your riding (16.3.2 + 16.3.5), which also settles where a trend lives.** The
question 16.3.1 left open — do these join *Your FTP* or get their own screen —
answered by use: that screen is about the **rider**, this one about the
**riding**, and volume and consistency are the same subject seen twice. Three
rules, all about not asserting what the data does not say. The **current week is
hollow and never in the scale**, because a Monday with one ride on it is not a
bad week yet. **A week with no riding is a bar of nothing, not a missing bar** —
the fortnight off should be a fortnight wide. **A day that has not happened is
absent, not empty.** Minutes and kJ are two bar rows rather than one chart with
two axes: a second axis can be scaled to make any two series agree, which is a
claim made by the drawing. The week and day arithmetic is `Calendar` with the
timezone injected and is tested across the October clock change, where adding
604,800,000 ms moves a Sunday ride into the next week invisibly.

**And this ride against your own previous best (16.3.4).** It went into the
picker the housemates are in, because from the chart's point of view they are
the same thing — another ride of this class, on these axes, under the same
measured-power rule. **Previous best, not best-ever**: a ride is compared with
what the rider had already done when they rode it, so a ride from March says the
same thing next year and a personal best is never quietly drawn against the ride
that beat it.

**Personal bests by duration (16.3.3), which is mean-maximal power.** Not "best
output for a 45-minute ride" — that mostly measures how long the class was — but
the best average watts held for 5 seconds, a minute, 5, 20 and 60, which are
comparable across every ride the rider has done. It lives on *Your FTP* by the
rule the other three settled (a best is a claim about the **rider**), and beside
the FTP for a plainer reason: the twenty-minute row is what every FTP protocol
is built on. **A gap breaks the window** — averaging across seconds nobody
recorded would award a twenty-minute best to a ride that stopped for four of
them, so a ride with a bottle stop has two shorter efforts rather than one long
one. **Measured rides only**, with the skipped count travelling alongside, so an
empty list can say why it is empty instead of implying the rider has never
ridden. A window never held is absent, not zero. **16.3.3a** is opened against
it: the scan reads every measured ride, which is instant at 22 rides and is a
year of daily riding away from not being — the fix is per-ride bests computed
once at recording, and it is not a schema change to make on a guess.

**One thing the AVD changed, and it is the reason for driving it.** The
calendar's not-ridden tile and its not-yet cell were both invisible, so
"absent, not empty" — a distinction the code documents at length — existed only
in the source. The empty tile is heavier now.

**The payload carries where the watts came from (14.4.7).** The tempting shape
was a scalar on the row, because `PowerProvenance` reduces a ride's samples to
one answer anyway — but it reduces it *from* the samples, and `Mixed` exists
precisely because a board that drops out mid-ride leaves them disagreeing. A
scalar has to pick a side, which is the fabrication `t` already refuses to
commit when it declines to imply the second from the array index. So `pm`, per
sample, absent meaning every sample unknown exactly as `hr` does. The 13 KB
that made the row look attractive turned out to be 5 KB: `CompactBoolean`
writes `1` and `0` rather than `true` and `false`, three characters a sample
across 2,700 of them, so the **cheap encoding buys the honest shape**. A
45-minute ride measures 55,635 bytes and the budget moves 56 → 60 KB to keep
its headroom. Without the column every restored ride comes back `Unknown`,
which fails `isTrustworthyAsMeasured` — a cloud copy of a real bike ride could
not propose an FTP or stand on a leaderboard the original qualified for.

**CI (19.1.4).** `assembleDebug` then `testDebugUnitTest` on every PR, with the
HTML report kept as an artifact on failure so a contributor sees *which* test.
Two deliberate omissions: **no secret and no `local.properties`**, because the
cloud credentials are optional by design and the day this workflow needs one is
the day offline-first broke; and **not `connectedDebugAndroidTest`**, whose
suite is order-dependent — a red run that means "re-run it" trains everyone to
ignore the whole thing.

**And the near-twin classes (25.4.3), where the interesting part is not the
class.** `SWT-05` was 4×4 at Z4 over the gear, which is `THR-06` block for
block; they differed only in the recovery. The fix is both halves of what the
item offered — a different work interval *and* titles that say so: *Low Cadence
Sweet Spot **4-5-6*** against *Low Cadence Threshold **4×4***, distinguishable
from the library list without opening either. **But it is `SWT-13`, not a
rewritten `SWT-05`.** `workouts.class_id` is a foreign key and the library's own
non-negotiable is that changing what an id *is* while a ride points at it
rewrites what that ride was — the argument 23.2.6 took a whole new id series
for, and editing in place would have been that rule broken at one class instead
of seventy-two. 25.4.2's renames last sitting were **not** this: a title is not
the foreign key. `SWT-05` leaves the bundle and the seeder retires it if
anyone rode it.

Two smaller things worth carrying. **R4 refused the obvious 3×6** — 120 s after
a 360 s Z4 effort is under the half it demands, and 180 s rests make it a
32-minute class — which is the generator doing what it was built for. And the
library is down to **50 distinct zone sequences from 51**, because `SWT-13`
shares one with `SWT-12`: right, and recorded in `classlibrary/README.md`,
because that count measures variety and is not a target to defend.

**And what a fresh clone finds (14.10, 14.11.3).** `local.properties` is
git-ignored, so an open-source project's only record of its own endpoint was in
a file nobody receives. `cloud.properties` is checked in and **empty**, which is
14.10.4's answer moved to where a contributor meets it: every RLS policy is
still `USING (true)`, and a shared endpoint is a bill — about 13,000 rides of
free tier before it fails for everyone at once, including the riders whose only
backup it was. Precedence is env → `local.properties` → `cloud.properties` →
offline, with a blank counting as absent at every level so an exported-but-empty
variable falls through rather than blanking the build.

The fence is the part worth keeping. Two mistakes here are one line each and
invisible in review: a key committed to `cloud.properties`, and a third
`secret()` call — `local.properties` holds an `sbp_` token that can delete every
project on the account, one `buildConfigField` from an APK. `CloudConfigFence
Test` asserts the checked-in values are blank and that the `secret()` calls are
exactly the URL and the anon key, in that order. Same idea as the `CloudAccess`
and `PowerModel` fences: **the danger is the line nobody has written yet.**

### Still needing a rider on the bike

- ~~**2.7.1b / 2.7.1c**~~ — **done, 1 August 2026.** Root cause found and the
  fix verified on the bike; see 2.7c. Two things worth carrying from how it
  went: the whole diagnosis needed **90 seconds of pedalling in total**,
  because the decisive captures were all taken with the rider stationary
  (resistance polls regardless, and a non-zero cadence with nobody pedalling is
  unmistakable evidence); and **this tablet has `log.tag=W` set globally**, so
  three attempts produced no output at all until per-tag levels were raised.
- **10.6** — a full-length ride (battery, thermals, memory, dropped samples).
- **2.2a.1** — watch a Hardware-mode ride actually land in the calibration
  grid. Everything downstream of it is written and tested; nothing has seen the
  accumulation happen. Settings → *This bike's power curve* reports it.
- **11.5.2** — whether the coach volume slider audibly does anything.
  `com.onepeloton.tts` is the only engine this ever runs against.
- **11.5.8** — ten seconds of `getevent -l` to settle whether the tablet has
  volume keys at all.
- **25.3.4** — how the stand/sit cue reads over a *playing film*. The AVD shows
  everything except that, because `screencap` returns black over DRM.
- **14.1.6 / 14.4.5** — neither needs the bike; both need one query against the
  cloud. Note 14.4.5 now measures the *old* payload shape, since the single row
  up there predates 14.4.

**One came off this list without a rider, and it is worth remembering why.**
14.4.6 sat here as a hardware question for a fortnight. It was a question about
data the bike had *already recorded* — 1,661 rows, answerable by pulling the
database over adb while nobody was near the bike. Before adding something here,
check whether the bike is holding the answer already.

---

### What to do next, in order

**The road to online — settled in the eighteenth sitting.** The owner's stated
destination is accounts, friends, a leaderboard and a companion web app. Those
are Phases **15**, **18** and **17**, and they were already in the plan. What
was *not* written down is the order and what has to be true first, so it is
here:

| # | What | Why it is where it is |
|---|------|----------------------|
| 1 | ~~**14.2.1** identity, **14.2.4–14.2.6** the backlog~~ | **Done.** Both are schema shapes that are free while the cloud is empty and a migration-with-backfill once four riders have a year up there. Everything below writes rows; these decide whose they are and whether losing one is noticed |
| 2 | **14.2.1a** apply `003`, **15.5.4** verify it from a second account | The only step in this list where being wrong is a **breach** rather than a bug. Nothing else may go online first, and "the SQL looks right" is not the check |
| 3 | **15.1** auth, **15.2** identity, **15.3** sync both ways | The phase that unlocks everything. 15.3.1 is mostly built already — it is 14.2.6's drain with a sign-in trigger on it |
| 4 | ~~**14.2.3** sync state in Settings~~ | **Done in the same sitting**, because it belongs before riders trust the thing rather than after. Two of three states seen on the AVD; the failing one is tested and will be seen for free the first time 14.2.1a's endpoint refuses something |
| 5 | **20.3** the initial FTP, **22.4** use the width | The owner's own two, and 20.3's own words are that the current shape **cannot go into production**. Onboarding is the first thing a new rider meets and the online tier is what brings new riders |
| 6 | **17** the web app, **18** social across bikes | Last, and in that order: the bike's tablet is a bad place to type, so the web app is the natural home for friend requests and display names, and 18 is those features arriving back on the bike. **Read Phase 24 first** — the household half is built, needs no account from anybody, and 18.9 says every screen here is built *on top of* its 24 equivalent rather than beside it |

Two things that are **not** blockers and were checked rather than assumed: the
payload format is settled and versioned inside itself (14.4, incl. 14.4.7's
provenance), so 17.3 has something stable to read; and household social (24) is
complete enough that 18 has a floor to build on.

One thing that **is** a blocker and is the owner's, not a session's:
**14.10.4** — whether there is a community endpoint at all, who pays for it, or
whether every self-hoster stands up their own. At ~30 KB a stored ride the free
tier is about 13,000 rides, so a shared endpoint fills up in its first year and
then fails for everyone at once, including the riders whose only backup it was.
`cloud.properties` ships empty and `CloudConfigFenceTest` keeps it that way
until somebody decides.

---

**Everything below is reordered by the first real ride, 1 August 2026.** The
triage is the owner's snag list plus what the ride measured.

**Still first, but no longer stop-everything — the fence and the watchdog
landed in the tenth sitting and nothing impossible reaches the record now:**

| Next | Why now |
|------|---------|
| ~~**2.7.1b** Which side mislabels the stream~~ | **Done — root cause found and fixed, verified on the bike.** `msg.what` is assigned by position and slides; the frame in `responseHexString` is self-identifying and now decides the metric. Read **2.7c**. What is left of 2.7 is 2.7.7 / 2.7.8, from the serial-port leak underneath it (2.7d) |
| ~~**2.7.5** What to do about the rides already recorded~~ | **Done and observed on the bike's own database.** Marked, not rewritten: three corrupted rides found, the fourth (post-fix) clean. Read 2.7.5 |
| ~~**2.7.3** A plausibility fence~~ | Done and observed: 0 impossible values in 188 recorded samples across a ride carrying 30 s of the bike's own corruption signature |
| ~~**2.7.4** Telemetry dies and never recovers~~ | Done and observed: silence is an `IOException` now, the one retry policy rebuilds the source, and the ride picked up again at 122 s with no app restart |

**Then the ride-screen snags, which are what the owner actually feels:**

| Next | Why now |
|------|---------|
| ~~**11.6.7** The numbers change too fast to read~~ | **Done and observed.** `SensorRepository.displayReading`, 2 Hz, both surfaces, recorder untouched |
| ~~**11.6.8** The zone ladder shifts sideways~~ | **Done and observed.** Not the border — the zone name and the FTP percentage sized themselves to their own text. Both now reserve their widest string |
| ~~**11.6.9 / 11.6.10** The heart-rate dead end, and Settings mid-ride~~ | **Done and observed.** A sheet over the ride, a gear beside the telemetry chip, and the overlay's way in inside the volume panel. One snag found and left open — see the end of 11.6.10 |
| ~~**16.1.7 / 16.1.8** Axes on the charts~~ | **Done and observed.** `ChartScale` picks the round numbers, `ChartFrame` draws them for every trace |
| ~~**13.8** kg/lb at signup~~ | **Done and observed.** The old dialog stored a 77 kg rider as 34.9 kg |

**Then the substantial ones:**

| Next | Why now |
|------|---------|
| ~~**23.2.6** Rebuild the class library~~ | **Done and observed.** Designed rather than sliced: 20 block lengths against 101, 51 zone sequences against 12, cadence a real second axis. New id series, old ones retired rather than deleted (23.2.6c). Read `classlibrary/README.md` before touching a class |
| ~~**22.2.6** Width cap as a rule~~ | **Done and observed.** `Layout.readableWidth` + `Modifier.readableColumn()`, applied to Settings, History, ride detail and the class library |
| ~~**25.3** The overlay's stand/sit cue~~ | **Built and observed on the AVD.** Amber lozenge, arrow travelling in the direction of the instruction, six seconds and gone; driven by `PositionCallTracker`, which the spoken coach now asks too. **25.3.4 is still open and needs the owner** — how it reads over a *film* cannot be screenshotted (`FLAG_SECURE`) |
| ~~**25.4.1** Positions across the rest of the library~~ | **Done — an audit, and one block wanted one.** It also turned up 25.4.2, below |
| ~~**25.4.2** Three classes named after a position they cannot state~~ | **Done — the owner chose renaming, and it was four classes, not three.** R11's cap is untouched; `END-08`, `END-12`, `SWT-05` and `THR-06` are named off the axis the data carries. The rule it leaves behind is in `classlibrary/README.md` under R10: **a position word in a title is a promise that the blocks say it too**, and "big gear" is a position word. It opened **25.4.3** |
| ~~**24.2** The household, seen~~ | **Done and observed**, opt-out included |
| ~~**24.3.1** Riding against a housemate~~ | **Done and observed.** One query and no schema, as advertised. **24.3.2** — the live pace target *during* a ride — is the interesting half and is still open; read 11.6 first |
| ~~**7.8 / 7.9** The FTP a ride was ridden at, and FTP history~~ | **Both done and observed.** `workouts.ftp_watts` (migration 6→7) and `ftp_history` (7→8, seeded from the profiles that already exist). 16.3 is unblocked. Between them they found a live bug: **saving your FTP in Settings put the old one back**, invisibly, because a second coroutine carried a stale copy past it |
| ~~**16.3.1 / 7.10.1** The full FTP trend~~ | **Done and observed.** The screen it wanted exists — *Your FTP*, off the dashboard's card — with a mark per change that says whether the app measured it or the rider claimed it, and the ride behind each one a tap away. Read 7.10.1 for why the axis runs to *now* |
| **23.2.3 / 23.2.4** The class library as an update channel | The only remaining reason to read the cloud at all. Additive only — deleting a class takes a rider's history link with it |
| ~~**14.4** The payload format~~ | **Done, and the numbers are measured now rather than modelled: 228 KB → 49 KB** per ride, asserted in the round trip against the old shape built from the same samples. It also settled **14.4.6** off the bike's own database with no rider: the board's fractional power is real data, and the float-widening noise it feared went away with 2.7c. 14.4.5 still wants the cloud trip; **14.4.7** is new — the payload drops `power_is_measured` |
| ~~**23.3.1** The backup reminder~~ | **Done and observed.** Ten rides, counted across the whole tablet because the file is; "Not now" moves the line rather than silencing it; and the mark is written only when a backup actually succeeds. **23.3.1a** is new and belongs to Phase 15: cloud backup is per profile and the file is per tablet |

| ~~**14.4.7** The payload's last missing field~~ | **Done.** `pm`, per sample rather than a scalar on the row, because `PowerProvenance` reduces the samples to one answer *from* them and `Mixed` is samples disagreeing. `CompactBoolean` writes 1/0, which is what makes a per-sample column affordable: 55,635 bytes a 45-minute ride, budget 56 → 60 KB |
| ~~**25.4.3** The two near-twin classes~~ | **Done, and as `SWT-13` rather than an edited `SWT-05`** — changing what an id *is* while a ride points at it rewrites what that ride was, which is why 23.2.6 took a new series. Titles now separate them from the library list: *4-5-6* against *4×4* |
| ~~**14.10 / 14.11.3** What a fresh clone finds~~ | **Done.** `cloud.properties`, checked in and empty; precedence env → `local.properties` → it → offline; `CloudConfigFenceTest` fails the build if a key lands in it or a third `secret()` call appears. 14.10.4 stays open because the *decision* is 15.5's, but it is now fenced rather than remembered |
| ~~**16.1.5a** The prescribed cadence~~ | **Done and observed.** A cadence trace with the class's rpm blocks under it, and a compliance count of its own — the fixture ride is 63% on power and 0% on cadence, which is the case the item existed for |
| ~~**16.3.2 / 16.3.5** Weekly volume, and the calendar~~ | **Done and observed**, on one screen — *Your riding*, off a **This Week** card. It also settles where a trend lives: *Your FTP* is about the rider, this is about the riding |
| ~~**16.3.4** This ride against your previous best~~ | **Done and observed.** In the housemates' own picker, drawn in the power colour because it is still you. Previous best, not best-ever |
| ~~**16.3.3** Personal bests by duration~~ | **Done and observed — and it finishes Phase 16.** Mean-maximal power on *Your FTP*, measured rides only, and a gap breaks the window. **16.3.3a** carries what is left: the scan is instant now and has a ceiling |
| **19.1.4** CI on every PR | **Written, not yet green.** `.github/workflows/ci.yml` — build then the JVM tests, no secret (offline-first is the reason), no instrumented suite (order-dependent). One green run on GitHub ticks it |
| ~~**14.2.1** Who a cloud row belongs to~~ | **Done in the app.** Two holes: every ride ever uploaded arrived anonymous, and `profiles` was keyed by a per-device autoincrement so the **second bike to sign in would have overwritten the first rider's profile**. `profiles.id` **is** the auth user id now — 1:1 with an account by construction, so no cloud id has to be read back and stored. **14.2.1a** carries applying the SQL |
| ~~**14.2.3** Sync state in Settings~~ | **Done and observed on the tablet AVD** — *"3 rides waiting to go up since Jul 23"* and *"Nothing is waiting to go up"*. `SyncOutcome.Failed` no longer dies in a `Log.w`, which is how three cloud defects survived the project's whole history. What is true lives in `CloudSyncStatus`, pure and tested; the AVD changed one sentence — *"No rides have gone up yet"* is a claim the app cannot support, because that state is also a restored backup |
| ~~**14.2.4 / 14.2.5 / 14.2.6** The backlog~~ | **Done and observed on the tablet AVD.** `workouts.synced_at` (migration 9 → 10), not backfilled; the worker drains a profile's backlog oldest-first instead of posting one ride, so **a ride that exhausts its retries is not lost**. It is also 15.3.1's backfill, one implementation rather than two |

| ~~**7.10.4 / 7.10.5** The two halves of not editing the rider's record behind them~~ | **Both done and observed.** A declined breakthrough is written down on the ride (migration 8→9) instead of forgotten when the screen closes, and an accepted one can be put back in one action that **appends** a row — `AutoBreakthroughReverted`, its own source, because "I set this" and "the app was wrong" are different events |

One of the owner's own is still open and unchanged:

| Next | Why now |
|------|---------|
| **11.1b.10** The grey line on the overlay | Diagnosed, not decided. One of three candidate fixes, and picking is the owner's call — read the item |

Then the table below, which was written before the fifth sitting and is kept
because its reasoning is still good:

| Next | Why now |
|------|---------|
| **14.1.6** Finish the cloud round trip | **Blocked as of the ninth sitting, and deliberately.** The app can no longer make the call: nothing sets `auth_user_id`, so every profile is offline and every cloud method returns `Disabled`. The row that was posted in the seventh sitting is still up there unseen; seeing it needs one `select count(*) from workouts` through the Management API, and *re-driving* it needs Phase 15 or the column set by hand |
| **11.1b.3 / 11.1b.4** Resizing and side docking | The half of 11.1b still outstanding. Opacity and the two-band layout landed; a vertical dock down one side is probably the better default on a 16:9 tablet and needs a genuine re-flow, not a rotation |
| **11.2.2 / 11.2.3** Time in zone, and "ahead of your usual" | The two things still missing from the strip that are about the next sixty seconds |
| **11.1b.9** The chips as a piece of design | The HUD redesign is correct and not yet beautiful, and the owner has said he will come back to it. Read 11.1b.8 and 11.1b.4a first — they are the same conversation |

Still blocked on things not to hand: **10.6** needs a full-length ride, and the
four bike items listed above.

Two notes worth carrying into the next bike session:

- **The dashboard is fine in landscape** — re-confirmed on the matching AVD this
  session. It fills the width and shows none of the empty right-hand side
  11.3.1 describes. **11.3.1 is stale**; do not spend a session on it without
  re-checking first. (The sixth sitting then capped that width at 760 dp for a
  different reason — 22.2.1 — which does not contradict it: 11.3.1 is about
  dead space and 22.2.1 is about a card being too wide to read.)
- **Every ride is now a guest ride no longer**: the emulator has real profiles
  and the sync path runs for them. The tablet still has none, and a guest ride
  never syncs by design, so make a profile on the bike before expecting 14 to
  do anything there.

---

## Status at a glance

| Phase | Area | State |
|-------|------|-------|
| 0 | Scaffolding & build system | ✅ Complete |
| 1 | Local database (Room) + Supabase | 🔶 Room complete at schema version 3. The class library is bundled, not fetched (23.2), and the cloud is gated behind an account that nothing yet grants (23.1) |
| 2 | Telemetry engine (sensor service, BLE, simulated) | ✅ **2.7 solved and verified on the bike (2.7c).** The board's own frame decides the metric, so the service's positional `msg.what` can no longer mislabel anything; the raw-resistance intruder is dropped by identity. 1609 + 464 messages captured with zero mislabels, a recorded ride with zero impossible values and zero gaps. The three rides recorded before the fix are marked rather than rewritten (2.7.5). Open underneath it: the exclusive serial port leaks (2.7d → 2.7.7, 2.7.8) |
| 3 | Foreground service & workout lifecycle | ✅ Complete |
| 4 | Floating HUD overlay | ✅ **Exonerated.** It never corrupted anything: 464 messages captured with the overlay up and a rider pedalling, zero mislabels and zero dropouts (2.7c). What it correlated with was *leaving the app*, and on this tablet that can mean a second bike app taking the sensor's serial port (2.7d) |
| 5 | HUD Compose UI & power zones | ✅ Complete |
| 6 | Main app UI | ✅ Complete |
| 7 | Auto-FTP, workload JSON, cloud sync | ✅ **Complete and observed.** Detection, the update flow, the FTP a ride was ridden at (7.8), the history of every change (7.9), both ways of showing it — the dashboard card (7.10.2) and the full trend (7.10.1) — and now both halves of *the app must not edit the rider's record behind them*: a declined breakthrough stays declined (7.10.5) and an accepted one can be put back in one action that appends rather than erases (7.10.4). A simulated ride cannot propose an FTP at all (7.10.7). Open only where it depends on phases that do not exist: the simulated-watts mark on the trend (7.10.6) and whether the history syncs (7.10.8, with 15) |
| 8 | Polish, testing, edge cases | 🔶 Functional items done; cosmetic backlog remains — **plus 8.3b, newly opened: the recovery prompt cannot tell a crashed ride from a live one** |
| 9 | Ride integration | ✅ Complete — a class runs |
| 10 | Hardware validation | 🔶 A **full 20-minute ride is done** — and it is what found 2.7. 10.6's remaining questions (battery, thermals, memory) are unanswered because the ride's telemetry was the story |
| 11 | **HUD-first experience — the current priority** | 🔶 11.1 and 11.1a complete; volume (11.5) done. The HUD is now chips on a transparent band with the timeline on the opposite edge (11.1b.1, 11.1b.2, 11.1b.7); resizing and side docking (11.1b.3–11.1b.5) and the rest of 11.2 remain |
| 12 | Ride history & the rider's own record | 🔶 History, detail, delete and migrations done; export and housekeeping remain |
| 13 | Units and display preferences | ✅ Complete — miles, and the locale default that goes with them |
| 14 | Cloud sync that actually reaches the cloud | 🔶 **A row knows whose it is now (14.2.1)** — every ride the app ever uploaded arrived anonymous, and `profiles` was keyed by a per-device autoincrement, so the second bike to sign in would have overwritten the first rider's profile rather than creating its own. `profiles.id` **is** the auth user id; `CloudAccess.accountIdFor` answers the gate and the identity in one lookup because they are one question. **And the app knows what it has not backed up (14.2.4–14.2.6)**: `synced_at`, not backfilled, with the worker draining a profile's backlog oldest-first so a ride that exhausts its retries is still in the queue rather than lost. What is left is **14.2.1a** — `003_cloud_identity.sql` is written and not applied — — and **Settings now says whether the rides are actually arriving (14.2.3)**, which is the item that would have caught all three of the defects in 14.0 the day they appeared. Otherwise: built and **gated shut** — every call goes through `CloudAccess` and no profile has an account, so nothing reaches the cloud until Phase 15 exists. 14.1.6's sighting is still missing and is no longer drivable from the app. **The endpoint is configurable from a clone now (14.10)** — checked-in `cloud.properties`, empty and fenced that way. **The payload format is changed (14.4)** while the cloud still held one row: columnar, versioned inside itself, 228 KB → 49 KB measured — 54 KB since provenance joined it, which is **14.4.7 closed**: `pm` is per sample, because a scalar on the row would have to pick a side in a ride the board dropped out of |
| 15 | Accounts, login and multi-device sync | ❌ Not started, but **no longer starting from nothing** — the eighteenth sitting built the two things underneath it. `profiles.id` **is** the auth user id (14.2.1), so signing in needs no cloud-id round trip; and 15.3.1's first-sign-in backfill is 14.2.6's backlog drain with a trigger on it. **15.5 is written in `supabase/003_cloud_identity.sql` and deliberately unticked**: the policies exist in a file, and 15.5.4 says they are verified from a second account, which reading the SQL is not. `auth_user_id` is still the gate and still nothing sets it |
| 16 | Data visualisation | ✅ **Complete.** Post-ride charts done, the power caption says where the watts came from (16.1.6), and every trace now carries a scale decided once for all four (16.1.7 / 16.1.8). **The first trend is built (16.3.1)** — FTP over time on its own screen, with the ride behind each change one tap away — which also settles where a trend lives. **Three more landed in the seventeenth sitting**: the prescribed cadence finally has a chart (16.1.5a), weekly volume and the ride-day calendar share a second screen — *Your riding* (16.3.2, 16.3.5) — and a ride can be drawn against the rider's own previous best at the same class (16.3.4). **Phase 16 is complete**: 16.3.3 is mean-maximal power on *Your FTP*, measured rides only, with a gap breaking the window. What is left is **16.3.3a**, the scan's ceiling |
| 17 | Companion web application | ❌ Not started, and **now sixth on a written road rather than an undated *nice to have*** — the owner named it as a destination in the eighteenth sitting. Still account-tier only: a household-only profile does not exist in the cloud and never appears there, which is 17.10's copy problem with a data-model cause. It reads `metrics_payload`, which is settled and versioned inside itself since 14.4, so it has something stable to build against |
| 18 | Social **across bikes** — the networked tier | ❌ Not started, and it sits on 15. **Phase 24 is the half that does not, and it is largely built** — which is 18.9's whole point: every screen here goes *on top of* its 24 equivalent rather than beside it, or one of the two leaderboards drifts and it will be the one nobody rides against |
| 19 | Ideas worth having, ranked | 🔶 Mixed, and not untouched: screen-on lock, auto-pause, local backup/restore and the README are done (19.1.1–19.1.3, 19.1.5), and **CI is written and waiting on its first green run** (19.1.4) |
| 20 | Who's riding — profile selector & avatars | 🔶 Selector rebuilt for the tablet (20.1, incl. rename/remove); avatars (20.2) not started. **20.3 is new and is the owner's**: profile creation asks a rider for their FTP in a text box prefilled with `200`, which by their own words **cannot go into production**. The constraint that makes it interesting is that the app cannot simply stop having a number — FTP is the denominator of the whole zone system and is written onto the ride at its start |
| 21 | Heart-rate zones | ❌ Not started — *the one metric that is measured for every rider whatever the power model does* |
| 22 | The dashboard | 🔶 **A *This Week* card now opens the progress section** — rides, minutes and the streak, and the door to *Your riding* (16.3.2/16.3.5). It is the number **22.1.2** has been asking for since the sixth sitting, in the place it asked for it, though that item is still open: the two kJ cards below it are unchanged. **The FTP card is now a progress card (22.1.4)** — the number, a stepped sparkline of every value it has held, and how far it moved and who moved it. That is the first thing in the section that is a trend rather than a total; the two kJ cards below it are still what they were (22.1.2). The width cap is a theme token applied across the app rather than one screen's fix (22.2.6); what goes in the rails it opens up (22.2.2, 22.2.3) is still undecided |
| 23 | Offline by default — making the ungated tier complete | 🔶 **The consent gate (23.1), the class library (23.2) and the backup reminder (23.3.1) are done and observed** — rule 1 is true rather than intended, the 72 classes are designed rather than generated (23.2.6) and reach an already-seeded tablet by reconcile-and-retire (23.2.6c), and the offline rider is now told when ten rides have gone by unprotected. The cloud as an update channel (23.2.3/23.2.4) and retention (23.4, deliberately not yet) remain |
| 24 | Household social — the tier that needs no cloud | 🔶 **24.1, 24.2 and 24.3.1 built and observed** — the per-class board, the household's week with streaks and an opt-out, and a housemate's trace drawn behind your own on ride detail. What remains is **24.3.2**, the live pace target during a ride, which is a ride-screen design problem rather than a data one |
| 25 | Out of the saddle | 🔶 **The field, the ride screen, the spoken coach, the overlay's cue and the library's own use of it are done and observed (25.1–25.4.2).** The titles no longer claim a position the intervals do not give. What is left is how the cue reads over a playing film (25.3.4, needs the rider). **25.4.3 is closed**: the two near-twins the rename exposed are separated by their work as well as their titles, as `SWT-13` rather than an edited `SWT-05` — the id is the foreign key |
