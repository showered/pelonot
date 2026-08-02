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

*Empty. The one entry it has held so far — standing and seated riding — is now
**Phase 25**, and the first two sections of it are built.*

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

### Latest session — 2 August 2026 (fourteenth sitting): the record stops editing itself

No bike, no rider, no HITL at all — the tablet AVD throughout, with the real
bike left connected over adb and untouched. Closed: **25.3**, **25.4.1**,
**24.3.1**, **7.8**, **7.9** and **7.10.3** and **7.10.2 / 22.1.4**. 421 JVM tests and **50
instrumented tests**, 0 failures. One new plan item came out of it (**25.4.2**, which is
the owner's call) and one live bug was found and fixed.

**PLAN.md is an index now.** 4527 lines read start to finish by every session,
of which four sections change. The phases are one file each under `plan/`, the
split was mechanical, and item numbers — which is what forty-odd pointers in
`CLAUDE.md` depend on — are unchanged. 309 lines instead of 4527.

**The overlay says get out of the saddle, and then goes quiet (25.3).** The
owner's own idea and the part he was specific about. The rule was written before
the code: animate for the transition, then stop — which makes the *edge*, not
the state, the thing to build on. `PositionCallTracker` answers one question,
is this interval boundary a call, and **the spoken coach now asks the same
object**, so the voice and the arrow cannot disagree about what a change is.
Amber rather than the zone accent, because the zone colour is already the
interval-change flash and **Zone 1's colour is grey** (11.1b.10) — a warm-up
that prescribed a position would announce it in the colour of a stray divider.
*Observed riding `CLB-02` with the overlay up: "STAY SEATED" at 05:01, gone by
05:08; "OUT OF THE SADDLE" at 11:03, gone by 11:14, and called again at 12:31
for the second standing attack.* What is left is 25.3.4 — how it reads over a
playing film — and that needs the rider, because `screencap` returns black over
DRM.

**25.4.1 was an audit, not a sweep, and that is the finding.** Every
heavy-torque and standing block in the library listed with its cadence: one
wanted a position and did not have it (`CLB-05`'s grind ladder, and at 50–60 rpm
that is not decoration). The sprint efforts resolve to *seated* and therefore to
nothing — a 120 rpm sprint is a seated sprint, and absence already says so. The
climb blocks were left alone on purpose: a five-minute climb at 60–70 rpm is
exactly where a rider *should* stand up when they feel like it.

It also turned up a rule that is not arithmetic and now lives in
`classlibrary/README.md`: **a positioned effort at Tabata spacing re-announces
every rep.** Eight "stay seated"s in four minutes. Roughly 30 seconds of
recovery between positioned reps, or position the set rather than each rep of
it. And **25.4.2**, which is the owner's: three classes — `END-08`, `SWT-05`,
`THR-06` — are *entirely* about being in the saddle in a big gear, only their
titles say so, and R11's half-a-class cap will not let them say it properly.
That is the same defect 25.1 opened with, and the fix is a taste call about a
rule he settled.

**Riding against a housemate (24.3.1) cost one query and no schema**, as
advertised. Their trace behind yours on ride detail, a bare dashed line and
nothing else — the chart already carries one rider's zones and a second full
record on the same axes is a graph rather than a comparison. **Aligned by
absolute elapsed seconds, never stretched to fit**: rescaling a ride that ran
forty seconds longer moves every one of their efforts off the block it was
ridden in. The measured-power gate applies to **both** sides, the symmetric half
checked in the ViewModel, because a modelled trace of mine against a measured
one of theirs is the same lie facing the other way.

**Then 7.8 and 7.9, which are the same bug seen from two ends.**
`profiles.ftp_watts` moves — by hand, and by itself when an auto-breakthrough is
accepted — and everything that drew a past ride read that current value. So a
ride ridden in Zone 5 in January was redrawn as Zone 4 in March with nothing
saying anything had changed: a record editing itself. `workouts.ftp_watts`
(migration 6→7) fixes the reading; `ftp_history` (7→8) fixes the forgetting, and
the migration **seeds itself from the profiles that already exist** so a rider's
chart does not begin at their second change.

Three decisions in 7.9 worth carrying:

- **The funnel is `UserRepository.save`, not `updateFtp`.** Every path already
  ends there. A caller that changes FTP without naming a reason still gets a
  row, marked `Unknown` — losing the reason is survivable, losing the change is
  not, because it cannot be recovered from a column that was overwritten.
- **The two foreign keys go opposite ways and both are tested against the
  database.** `workout_id` is SET NULL: deleting a ride must not delete the fact
  that the rider's FTP changed. The profile is CASCADE: unlike a ride, which is
  a record of something that happened, an FTP history is a statement about
  somebody.
- **The seed is marked `Unknown`, not `ProfileCreated`.** A profile whose FTP
  has been edited four times since is described accurately by neither.

**And the find of the sitting, which the history itself produced.** Settings
fired two coroutines off one tap of Save — one for FTP, one for weight — each
doing read-modify-write on the same profile row. The weight write read the
profile *before* the FTP write committed and carried the old FTP back past it,
so **typing 215 and pressing Save left 200 in the database**, with the screen
showing 215 until the next launch. Nothing on any screen was wrong, which is why
it survived the whole life of the project. What made it visible was two
`ManualEdit` rows for the same value twenty-three seconds apart — impossible
unless the number went back in between. Same two techniques as the twelfth and
thirteenth sittings': **build the feature that reads the data, then look at the
data**, and **the database is the witness, not the screenshots**.

**And the payoff of 7.9 landed in the same sitting.** The dashboard's FTP card
is a progress card now — the number, a **stepped** sparkline of every value it
has held, and how far it moved, when, and who moved it. Stepped rather than
interpolated because FTP does not drift between two rides: a diagonal from 200
to 215 would say the rider passed through 207 on a Tuesday, which nothing
measured. The direction is read against the *previous* value rather than the
lowest, so 200 → 240 → 225 is a fall of 15 and not a rise of 25 — and a fall is
shown, because a progress card that could only go up would be lying by
omission. *Observed both ways: Simon with "+15 W since Aug 2, 2026 · you set
it", and Kilo, whose FTP has never moved, with nothing but the number.*

**One test was a statement about ordering rather than about the code.**
`WorkoutService` is one instance per process, so
`stoppingWithoutStartingIsHarmless` asserting `Idle` only held while no earlier
test had finished a ride — adding a class ahead of it alphabetically was enough
to fail it, twice, non-deterministically. It asserts against the state before
the call now. Worth knowing before trusting a red instrumented run.

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
- **14.1.6** — see below; it needs no bike, but it does need one query.

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
| **23.3.1** The backup reminder | Backup is the offline rider's only durability story and it is entirely manual |

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
| 7 | Auto-FTP, workload JSON, cloud sync | 🔶 Detection, the update flow, **the FTP a ride was ridden at (7.8), the history of every change (7.9) and both ways of showing it — the dashboard card (7.10.2) and the full trend (7.10.1)** are complete and observed, and a simulated ride can no longer propose an FTP (7.10.7). What remains is reverting an auto change (7.10.4), not re-offering a declined one (7.10.5), the simulated-watts caveat on the trend (7.10.6) and whether any of it syncs (7.10.8) |
| 8 | Polish, testing, edge cases | 🔶 Functional items done; cosmetic backlog remains — **plus 8.3b, newly opened: the recovery prompt cannot tell a crashed ride from a live one** |
| 9 | Ride integration | ✅ Complete — a class runs |
| 10 | Hardware validation | 🔶 A **full 20-minute ride is done** — and it is what found 2.7. 10.6's remaining questions (battery, thermals, memory) are unanswered because the ride's telemetry was the story |
| 11 | **HUD-first experience — the current priority** | 🔶 11.1 and 11.1a complete; volume (11.5) done. The HUD is now chips on a transparent band with the timeline on the opposite edge (11.1b.1, 11.1b.2, 11.1b.7); resizing and side docking (11.1b.3–11.1b.5) and the rest of 11.2 remain |
| 12 | Ride history & the rider's own record | 🔶 History, detail, delete and migrations done; export and housekeeping remain |
| 13 | Units and display preferences | ✅ Complete — miles, and the locale default that goes with them |
| 14 | Cloud sync that actually reaches the cloud | 🔶 Built and now **gated shut** — every call goes through `CloudAccess` and no profile has an account, so nothing reaches the cloud until Phase 15 exists. 14.1.6's sighting is still missing and is no longer drivable from the app. **The payload format is changed (14.4)** while the cloud still held one row: columnar, versioned inside itself, 228 KB → 49 KB measured. What it drops is `power_is_measured` (14.4.7) |
| 15 | Accounts, login and multi-device sync | ❌ Not started — *the thing that unlocks the cloud tier*, and since the ninth sitting **the only thing that can**: `auth_user_id` exists, is the gate, and nothing sets it |
| 16 | Data visualisation | 🔶 Post-ride charts done, the power caption says where the watts came from (16.1.6), and every trace now carries a scale decided once for all four (16.1.7 / 16.1.8). **The first trend is built (16.3.1)** — FTP over time on its own screen, with the ride behind each change one tap away — which also settles where a trend lives. The other four (16.3.2–16.3.5) remain |
| 17 | Companion web application | ❌ Not started — *nice to have*, and account-tier only: a household-only profile does not exist in the cloud and never appears there |
| 18 | Social **across bikes** — the networked tier | ❌ Not started — *nice to have*, and it sits on 15. Phase 24 is the half that does not |
| 19 | Ideas worth having, ranked | ❌ Not started — mixed |
| 20 | Who's riding — profile selector & avatars | 🔶 Selector rebuilt for the tablet (20.1, incl. rename/remove); avatars (20.2) not started |
| 21 | Heart-rate zones | ❌ Not started — *the one metric that is measured for every rider whatever the power model does* |
| 22 | The dashboard | 🔶 **The FTP card is now a progress card (22.1.4)** — the number, a stepped sparkline of every value it has held, and how far it moved and who moved it. That is the first thing in the section that is a trend rather than a total; the two kJ cards below it are still what they were (22.1.2). The width cap is a theme token applied across the app rather than one screen's fix (22.2.6); what goes in the rails it opens up (22.2.2, 22.2.3) is still undecided |
| 23 | Offline by default — making the ungated tier complete | 🔶 **The consent gate (23.1) and the class library (23.2) are done and observed** — rule 1 is true rather than intended, and the 72 classes are now designed rather than generated (23.2.6), reaching an already-seeded tablet by reconcile-and-retire (23.2.6c). The cloud as an update channel (23.2.3/23.2.4), the backup reminder (23.3.1) and retention (23.4, deliberately not yet) remain |
| 24 | Household social — the tier that needs no cloud | 🔶 **24.1, 24.2 and 24.3.1 built and observed** — the per-class board, the household's week with streaks and an opt-out, and a housemate's trace drawn behind your own on ride detail. What remains is **24.3.2**, the live pace target during a ride, which is a ride-screen design problem rather than a data one |
| 25 | Out of the saddle | 🔶 **The field, the ride screen, the spoken coach, the overlay's cue and the library's own use of it are done and observed (25.1–25.4.2).** The titles no longer claim a position the intervals do not give. What is left is how the cue reads over a playing film (25.3.4, needs the rider), and 25.4.3 — two classes the rename showed to be near-twins |
