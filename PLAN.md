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

### Latest session — 1 August 2026 (thirteenth sitting): a library that was designed, and the instruction it could not give

No bike, no rider — the tablet AVD throughout. Closed: **23.2.6**, **23.2.6c**
(new), **22.2.6**, **25.1**, **25.2** (both new) and **24.2**. 405 JVM tests, 0
failures; 5 migration tests and 23 DAO tests green. **That clears the last two
items off the first real ride's snag list** — and turns up a data-loss bug
older than all of it, described at the end.

**The class library was rebuilt, and the case against the old one is
measurements rather than taste.** The 72 were generated by slicing percentages
off a duration: 770 intervals carrying **101 distinct lengths**, of which only
213 were a whole number of minutes; **twelve distinct sequences of zones across
all 72 classes**, with ten of them shared between Sweet Spot and Threshold, so
those were not two categories; cadence a pure lookup from the zone; and `TB-01`
prescribing sixteen consecutive Tabata rounds with no set break. The
replacement is `classlibrary/` — eleven written-down rules, 72 sessions
authored in blocks of real time, and a generator that **refuses to write** if
one breaks a rule. 20 block lengths, 51 zone sequences, four zones ridden at
three or more cadences. Read `classlibrary/README.md` before touching a class.

**The ids are new, and that is the substantive decision.** Reusing them would
have kept the foreign key intact and quietly rewritten what the bike's first
real ride *was* — same family as 7.8. So the old ones are **retired**, not
deleted, and only the ones a ride actually points at survive.

**Which turned up the find of the sitting, and it is not the feature.**
`ClassTemplateDao` used `OnConflictStrategy.REPLACE`. SQLite implements REPLACE
as a delete plus an insert, **and the delete fires foreign-key actions** — so
re-inserting a class somebody had ridden would have run `class_id`'s
`ON DELETE SET NULL` and detached every one of those rides. It was harmless
only because seeding had never run against a populated table, and **23.2.3 was
going to make it run against one**. Measured against `sqlite3` in four lines
rather than reasoned about, which is the technique: a claim about what the
database does is cheap to check and expensive to be wrong about.

**Then the owner's inbox, which is new and now permanent.** He had left a note
in PLAN.md between sessions asking for a home for exactly that habit; it is now
a section at the head of this file with a rule attached — an entry that is
still sitting there has not been dealt with. His idea was **standing and
seated**, and he is right that it is the one instruction a bike class gives
that neither zone nor cadence can express. It is **Phase 25**, and the field
and the ride screen are built: `target_position`, optional, **absent means the
rider chooses**. `CLB-02` was called "Standing Attacks" and its title was the
only thing making it standing.

**The part worth carrying from 25.2 is a rule about attention.** The ride
screen's cue is keyed on the *value* rather than on the interval index, because
`CLB-06` alternates climb and attack six times and would otherwise announce
"stay seated" twelve. **The change is the message, not the state** — and that
is the rule 25.3 hands the overlay, where the owner actually wants this and
where a persistent flashing arrow would undo everything 11.1b is for.

**And 22.2.6 was small and overdue.** 22.2.1's 760 dp cap was right and was the
only one; Settings, History, ride detail and the class library each ran edge to
edge on a 1280 dp panel. One token, one modifier, four screens — and explicitly
*not* the ride screen or the overlay, which are full-bleed on purpose.

**A note on verifying 25.2:** every class has a five-minute warmup by rule
(R2), so the first prescribed position is five minutes into a ride. That is a
real wait with no way to skip it, and the honest approach was to start the ride
and do the 22.2.6 work while it ran.

**Then 24.2, the household seen — and the bug it found, which outranks it.**
The dashboard now says who on this bike has ridden this week, with streaks and
with the per-profile opt-out that 24.2.3 said belonged in the first version.
Two of its rules are structural rather than remembered: 24.2.4 is enforced by
the query being an *inner* join, so a rider who has not ridden is absent rather
than present with a zero and there is no row that could ever read as "Sam
hasn't ridden this week"; and the opt-out gates the week and the per-class
board through one column, because nobody asks to be hidden from half of it.

**Then the find of the sitting.** Toggling that opt-out emptied the rider's own
dashboard. `UserDao.insertUser` was `@Insert(onConflict = REPLACE)`, SQLite
implements REPLACE as a delete plus an insert **with foreign-key actions
firing**, and `workouts.user_id` is `ON DELETE SET NULL` — so **every FTP
change, weight change and rename has been silently unattributing that rider's
entire history for the life of the project.** Seven rides, one toggle, all
orphaned. It was invisible because the rides were still there.

That is the same defect the class library carried (23.2.6c) in a far busier
path, and `workouts` was the third instance waiting to go off over
`workout_metrics`' CASCADE. All three are `@Upsert` now. **Three techniques
worth keeping from it:**

- **Build the feature that reads the data, then look at the data.** Nothing
  about the code said this; a dashboard that had gone empty did.
- **A claim about what SQLite does is four lines to check and expensive to be
  wrong about.** The REPLACE behaviour was confirmed against `sqlite3` before
  either fix, not reasoned about.
- **Check the regression test against the bug, not only against the fix.**
  `UserDaoTest` was run with REPLACE restored and fails with "saving a profile
  detached its rider's rides expected:<1> but was:<null>". This project has
  shipped a test that passed against its own bug before — the backup magic
  bytes — and that is the only reason this step is a habit.

**And one Room fact that cost a wrong conclusion on the way:** a `Flow` from a
DAO only re-emits when a table its query **mentions** is written. The household
panel was keyed on a count over `workouts`, so opting out changed nothing on
screen until somebody rode. It joins `profiles` now for no other reason.

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
| **25.4.2** Three classes named after a position they cannot state | `END-08`, `SWT-05`, `THR-06` are each entirely about staying in the saddle and only their titles say so — and R11's half-a-class cap will not let them. A taste call on the rule, so it is the owner's |
| ~~**24.2** The household, seen~~ | **Done and observed**, opt-out included |
| ~~**24.3.1** Riding against a housemate~~ | **Done and observed.** One query and no schema, as advertised. **24.3.2** — the live pace target *during* a ride — is the interesting half and is still open; read 11.6 first |
| **7.9** FTP history | Now that a simulated ride cannot propose an FTP (7.10.7), the proposals that arrive are trustworthy — and nothing keeps them. Blocks 16.3 |
| **23.2.3 / 23.2.4** The class library as an update channel | The only remaining reason to read the cloud at all. Additive only — deleting a class takes a rider's history link with it |
| **14.4** The payload format | Only while the cloud holds one row. **228 KB → 49 KB** per ride on the wire |
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
| 7 | Auto-FTP, workload JSON, cloud sync | 🔶 Detection and the update flow complete, and **a simulated ride can no longer propose an FTP (7.10.7)**. Still open: the FTP a ride was ridden at is discarded (7.8) and no history of FTP changes is kept (7.9) |
| 8 | Polish, testing, edge cases | 🔶 Functional items done; cosmetic backlog remains — **plus 8.3b, newly opened: the recovery prompt cannot tell a crashed ride from a live one** |
| 9 | Ride integration | ✅ Complete — a class runs |
| 10 | Hardware validation | 🔶 A **full 20-minute ride is done** — and it is what found 2.7. 10.6's remaining questions (battery, thermals, memory) are unanswered because the ride's telemetry was the story |
| 11 | **HUD-first experience — the current priority** | 🔶 11.1 and 11.1a complete; volume (11.5) done. The HUD is now chips on a transparent band with the timeline on the opposite edge (11.1b.1, 11.1b.2, 11.1b.7); resizing and side docking (11.1b.3–11.1b.5) and the rest of 11.2 remain |
| 12 | Ride history & the rider's own record | 🔶 History, detail, delete and migrations done; export and housekeeping remain |
| 13 | Units and display preferences | ✅ Complete — miles, and the locale default that goes with them |
| 14 | Cloud sync that actually reaches the cloud | 🔶 Built and now **gated shut** — every call goes through `CloudAccess` and no profile has an account, so nothing reaches the cloud until Phase 15 exists. 14.1.6's sighting is still missing and is no longer drivable from the app. The payload format should still change before rides accumulate (14.4) |
| 15 | Accounts, login and multi-device sync | ❌ Not started — *the thing that unlocks the cloud tier*, and since the ninth sitting **the only thing that can**: `auth_user_id` exists, is the gate, and nothing sets it |
| 16 | Data visualisation | 🔶 Post-ride charts done, the power caption says where the watts came from (16.1.6), and every trace now carries a scale decided once for all four (16.1.7 / 16.1.8). Trends (16.3) remain, blocked on 7.9 |
| 17 | Companion web application | ❌ Not started — *nice to have*, and account-tier only: a household-only profile does not exist in the cloud and never appears there |
| 18 | Social **across bikes** — the networked tier | ❌ Not started — *nice to have*, and it sits on 15. Phase 24 is the half that does not |
| 19 | Ideas worth having, ranked | ❌ Not started — mixed |
| 20 | Who's riding — profile selector & avatars | 🔶 Selector rebuilt for the tablet (20.1, incl. rename/remove); avatars (20.2) not started |
| 21 | Heart-rate zones | ❌ Not started — *the one metric that is measured for every rider whatever the power model does* |
| 22 | The dashboard | 🔶 Barely started — *"Your Progress" shows no progress. The width cap is now a theme token applied across the app rather than one screen's fix (22.2.6); what goes in the rails it opens up (22.2.2, 22.2.3) is still undecided* |
| 23 | Offline by default — making the ungated tier complete | 🔶 **The consent gate (23.1) and the class library (23.2) are done and observed** — rule 1 is true rather than intended, and the 72 classes are now designed rather than generated (23.2.6), reaching an already-seeded tablet by reconcile-and-retire (23.2.6c). The cloud as an update channel (23.2.3/23.2.4), the backup reminder (23.3.1) and retention (23.4, deliberately not yet) remain |
| 24 | Household social — the tier that needs no cloud | 🔶 **24.1, 24.2 and 24.3.1 built and observed** — the per-class board, the household's week with streaks and an opt-out, and a housemate's trace drawn behind your own on ride detail. What remains is **24.3.2**, the live pace target during a ride, which is a ride-screen design problem rather than a data one |
| 25 | Out of the saddle | 🔶 **The field, the ride screen, the spoken coach and now the overlay's cue are done and observed (25.1, 25.2, 25.3).** What is left is one judgement each way: how the cue reads over a playing film (25.3.4, needs the rider), and whether R11's cap should let a class whose identity *is* a position say so (25.4.2, the owner's call) |
