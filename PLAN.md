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

### Latest session — 3 August 2026 (seventeenth sitting): three quick items, then Phase 16 finished

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
| 14 | Cloud sync that actually reaches the cloud | 🔶 Built and now **gated shut** — every call goes through `CloudAccess` and no profile has an account, so nothing reaches the cloud until Phase 15 exists. 14.1.6's sighting is still missing and is no longer drivable from the app. **The endpoint is configurable from a clone now (14.10)** — checked-in `cloud.properties`, empty and fenced that way. **The payload format is changed (14.4)** while the cloud still held one row: columnar, versioned inside itself, 228 KB → 49 KB measured — 54 KB since provenance joined it, which is **14.4.7 closed**: `pm` is per sample, because a scalar on the row would have to pick a side in a ride the board dropped out of |
| 15 | Accounts, login and multi-device sync | ❌ Not started — *the thing that unlocks the cloud tier*, and since the ninth sitting **the only thing that can**: `auth_user_id` exists, is the gate, and nothing sets it |
| 16 | Data visualisation | ✅ **Complete.** Post-ride charts done, the power caption says where the watts came from (16.1.6), and every trace now carries a scale decided once for all four (16.1.7 / 16.1.8). **The first trend is built (16.3.1)** — FTP over time on its own screen, with the ride behind each change one tap away — which also settles where a trend lives. **Three more landed in the seventeenth sitting**: the prescribed cadence finally has a chart (16.1.5a), weekly volume and the ride-day calendar share a second screen — *Your riding* (16.3.2, 16.3.5) — and a ride can be drawn against the rider's own previous best at the same class (16.3.4). **Phase 16 is complete**: 16.3.3 is mean-maximal power on *Your FTP*, measured rides only, with a gap breaking the window. What is left is **16.3.3a**, the scan's ceiling |
| 17 | Companion web application | ❌ Not started — *nice to have*, and account-tier only: a household-only profile does not exist in the cloud and never appears there |
| 18 | Social **across bikes** — the networked tier | ❌ Not started — *nice to have*, and it sits on 15. Phase 24 is the half that does not |
| 19 | Ideas worth having, ranked | 🔶 Mixed, and not untouched: screen-on lock, auto-pause, local backup/restore and the README are done (19.1.1–19.1.3, 19.1.5), and **CI is written and waiting on its first green run** (19.1.4) |
| 20 | Who's riding — profile selector & avatars | 🔶 Selector rebuilt for the tablet (20.1, incl. rename/remove); avatars (20.2) not started |
| 21 | Heart-rate zones | ❌ Not started — *the one metric that is measured for every rider whatever the power model does* |
| 22 | The dashboard | 🔶 **A *This Week* card now opens the progress section** — rides, minutes and the streak, and the door to *Your riding* (16.3.2/16.3.5). It is the number **22.1.2** has been asking for since the sixth sitting, in the place it asked for it, though that item is still open: the two kJ cards below it are unchanged. **The FTP card is now a progress card (22.1.4)** — the number, a stepped sparkline of every value it has held, and how far it moved and who moved it. That is the first thing in the section that is a trend rather than a total; the two kJ cards below it are still what they were (22.1.2). The width cap is a theme token applied across the app rather than one screen's fix (22.2.6); what goes in the rails it opens up (22.2.2, 22.2.3) is still undecided |
| 23 | Offline by default — making the ungated tier complete | 🔶 **The consent gate (23.1), the class library (23.2) and the backup reminder (23.3.1) are done and observed** — rule 1 is true rather than intended, the 72 classes are designed rather than generated (23.2.6) and reach an already-seeded tablet by reconcile-and-retire (23.2.6c), and the offline rider is now told when ten rides have gone by unprotected. The cloud as an update channel (23.2.3/23.2.4) and retention (23.4, deliberately not yet) remain |
| 24 | Household social — the tier that needs no cloud | 🔶 **24.1, 24.2 and 24.3.1 built and observed** — the per-class board, the household's week with streaks and an opt-out, and a housemate's trace drawn behind your own on ride detail. What remains is **24.3.2**, the live pace target during a ride, which is a ride-screen design problem rather than a data one |
| 25 | Out of the saddle | 🔶 **The field, the ride screen, the spoken coach, the overlay's cue and the library's own use of it are done and observed (25.1–25.4.2).** The titles no longer claim a position the intervals do not give. What is left is how the cue reads over a playing film (25.3.4, needs the rider). **25.4.3 is closed**: the two near-twins the rename exposed are separated by their work as well as their titles, as `SWT-13` rather than an edited `SWT-05` — the id is the foreign key |
