# Pelonot — Implementation Plan

> **Open-Source Peloton Client** — A subscription-free fitness app for Peloton
> bikes (Gen 1/Gen 2). **A stock, un-jailbroken bike is the supported target**;
> telemetry comes from Peloton's own sensor service, not from root (see 2.1a).

---

## How to use this plan

1. **Each checkbox is one focused task** — small enough for a single session, large enough to matter.
2. **A box is only ticked when the behaviour has been observed working**, not when the code was written. Several items in this plan were previously ticked while the feature was non-functional (see *Corrections* below); that is the failure mode this rule exists to prevent.
3. **Work phases in order** where later ones build on earlier ones. See *Where the work stands* immediately below for the current priority.
4. When switching models or sessions, paste the current plan state so the next session knows where to pick up.

---

## Where the work stands — read this first

### Latest session — 1 August 2026 (seventh sitting): the ladder, and a permission nobody asked for

Straight down the *What to do next* list, on the tablet AVD, no bike.

Closed: **11.1a.6** (the ride notification that was never posted on Android
13+) and **11.6.2a** (the zone ladder, which replaces the `CurrentZoneBar` the
sixth sitting shipped and closes the overlay gap 11.6.2 left behind). 274 JVM
tests green.

**11.6.2a is the substantial one, and building it answered its own three open
questions.** A free ride draws the same ladder with nothing outlined; the watt
labels genuinely do not survive being shrunk to the overlay, so `compact` keeps
the segments and the digit and drops the rest; and the segments are equal
widths rather than proportional to watts, because Z7 is unbounded and Z1 spans
56% of FTP alone — a true scale would draw six zones as slivers beside two
slabs.

It also took a rule off a screen and put it somewhere both screens read.
`ZoneScale.currentZone` is now the app's single answer to "is there a zone at
all" — no FTP, no power, or a stalled board means none. 11.6.2 had left that
rule on `RideUiState` alone, which is the shape of defect this plan keeps
finding: one surface with the check and another free to disagree.

**11.1b.10 is still the owner's call** and untouched — it is a design decision
about an alert, and the item lists the three candidate fixes.

### The session before it — 1 August 2026 (sixth sitting): the ride screen, read

A UI and UX pass, driven end to end on the tablet AVD. **Everything the owner
raised in the "snags from using the app" table below about the ride screen is
now built and seen working**, plus one dead end found while verifying and one
snag reported mid-session.

Closed: **11.6.1** (the next effort under the current one), **11.6.2** (the
zone the rider is actually in), **11.6.3** (icons on the live numbers),
**11.6.4** (the target gauge finally says what the target *is*), **11.6.5**
(the overlay's name), **22.2.1** (the dashboard capped and centred), and
**8.3c**, new. 263 JVM tests green.

**The find of the sitting is 8.3c, and it was found the way 2.4.6 was — by
using the fix for something else.** Driving 8.3b's own repro one screen
further: force-stop mid-class, relaunch, *Keep it*, and the summary comes up
with **both** buttons inert. The cause is one unread Boolean —
`popBackStack` returns false when the destination was never on the stack, and
the crash-recovery door navigates to the summary straight from "Who's riding?".
**11.1a.5 hit precisely this trap on the other door and its comment names it**;
this door was missed. The rider's only way out was to kill the app, which
leaves another unfinished ride behind it.

**The rider-facing name for the floating display is now "overlay".** Not "HUD"
— jargon — and not "strip", which was this session's first answer and the
owner rejected it. The button says **"View in Overlay Mode"**. Six places moved
together; nothing user-visible may say HUD or strip again. The source, this
plan and `ARCHITECTURE.md` still say HUD internally, which is deliberate: one
name in the code, one name on screen.

Two things raised by the owner and **written down rather than actioned**:

- **11.1b.10** — the grey line across the overlay. Diagnosed on the AVD: it is
  the zone-colour edge glow, and Zone 1's colour is grey, so during every
  warm-up it reads as a divider somebody left behind. Fix candidates are in
  the item; which one is right is a design call about an alert.
- **11.6.2a** — the zones drawn as a **scale** rather than as the sentence
  11.6.2 just shipped, after Peloton's own seven-segment indicator. The reason
  it is better is worth reading: the whole ladder is on screen at once, the
  boundaries are in watts (so "you are in 2" becomes "215 W gets you into 3"),
  and the prescribed band can be marked on the same scale. It **replaces**
  `CurrentZoneBar`; do not build both. The overlay never got 11.6.2's compact
  form, so that gap and this item are the same piece of work.
  *Built in the seventh sitting; the bar is gone.*

### The session before it — 31 July 2026 (fifth sitting): an MVP readiness pass

The question asked was "are we near MVP, and where are the genuine gaps?" The
answer is that **the happy path is finished and the unhappy paths are not**.
A ride records, the HUD runs over a film, the class ends, the app comes forward,
the summary is real, the charts are real, the ride exports. Nothing on that
path is faked any more. What is missing is almost entirely what happens when
something does not go to plan — the tablet sleeping, the board dropping out, the
Activity being destroyed mid-class, a thumb landing on *End ride*.

Six new items came out of tracing the journey against the code, and **four of
them are of the family this plan's *Corrections* table exists to catch**:
something fails, the failure is caught and returned as a value nothing reads,
and every screen goes on looking correct.

| Found | Item | Why it is a blocker | State |
|-------|------|---------------------|-------|
| Nothing keeps the screen on during a ride | **19.1.1** *(already on the plan, untouched)* | The tablet sleeps mid-class. Hidden on the bike only because Netflix holds its own wake lock | ✅ |
| A stalled board's last reading is recorded once a second as measured | **2.4.4** | Corrupts `workout_metrics`, `avg_power`, `avg_cadence` and the calibration grid | ✅ |
| `SensorStatus.Reconnecting` is rendered nowhere | **2.4.5** | The rider sees frozen numbers and no reason for them | ✅ |
| The telemetry source the rider chose is forgotten at the next launch | **2.4.6** *(found while verifying 2.4.5)* | **Hardware** exists so a ride never records fabricated numbers, and it silently reverted to Auto | ✅ |
| Crash recovery cannot tell a crashed ride from a live one, and *Discard* deletes the live one | **8.3b** | Data loss, mid-ride, from a dialog the rider did not ask for | ✅ |
| No route back into a ride already running once the Activity is gone | **11.1a.5** | The ride notification does not open the ride | ✅ |
| *End ride* is one tap with no confirmation and no resume | **11.6.6** | A mis-tap ends the class | ✅ |
| The ride notification is never posted on Android 13+ | **11.1a.6** *(found while verifying 11.1a.5)* | `POST_NOTIFICATIONS` is declared and requested by nothing. The bike is Android 11, so it does not bite there — yet | ✅ |

**All six were then built and observed on the tablet AVD in the same sitting,
along with one more that the verifying turned up.** Closed: **19.1.1**,
**2.4.4**, **2.4.5**, **2.4.6**, **8.3b**, **11.1a.5**, **11.6.6** and the
greeting in **22.3.1**. 260 JVM tests and 23 instrumented tests green.

**2.4.6 is the find of the sitting, and it was found by checking the fix for
something else.** While confirming that a dead board says so on screen, the
ride recorded a plausible simulated 68 rpm — with Settings showing **Hardware**
selected two taps away. `SensorRepository.setMode` had exactly one caller, so
the rider's choice was applied to the pipeline **only in the session it was
tapped in**; every launch after that silently reverted to `Auto`, which falls
back to simulated telemetry. The mode that exists so that *a ride never records
fabricated numbers* could not survive the app being closed. It is in
*Corrections* against 2.6.

Two more things worth carrying forward. **8.3b was reproduced before it was
fixed**, and the screenshot is the clearest statement of the problem anyone
will write: a modal saying the app "was closed part-way through a ride" with
the HUD strip two inches above it showing 02:11 and 66 rpm, live. And the
verification work turned up **11.1a.6** — `POST_NOTIFICATIONS` is declared and
requested by nothing, so on Android 13+ the ride notification never appears at
all. The bike's tablet is Android 11, which is exactly why nobody had seen it.

Also raised, not blockers: **19.1.6** (the first run explains nothing — FTP
prefilled at 200 with no way to find a real one, the overlay permission first
mentioned at ride start). And two on the plan already that belong in the same
conversation: **19.1.2** auto-pause (every bottle stop drags the averages down,
and it is the same "the rider is not pedalling" signal as 2.4.4) and
**19.1.3 / 12.4.4** local backup (until accounts exist a wipe costs the rider
everything; per-ride export does not cover it).

**A deliberate scoping call for MVP, written down so it is a decision and not a
drift: ship with cloud sync off and labelled off.** 14 is one sighting from
proven (14.1.6) but several from useful — an uploaded ride still carries no
`user_id` (14.2.1), no surface anywhere says whether sync worked (14.2.3), and
the existing local history has never been uploaded (14.2.6). Half-attributed
rides in a shared pool is a worse first release than an honestly offline one.

Bookkeeping fixed in passing: **12.2.3** was open while the work behind it was
done and observed, and `CLAUDE.md` said 230 JVM tests where there are **253**.

### The session before it — 31 July 2026 (fourth sitting), tablet AVD only

Closed: **16.1.5** (prescribed intervals under the actual trace), **11.1b.1 /
11.1b.2 / 11.1b.7** (the HUD redesign below), **12.4.3** (ride export), and
**19.1.5** (README and CONTRIBUTING, which turned out to mean writing the first
and correcting `ARCHITECTURE.md` instead).

**The HUD stopped being a panel.** 11.1b.1 asks for adjustable opacity, and a
single alpha over a full-width slab cannot deliver what that item is for: a
rider asking for more of their film back only ever got a lighter wash over all
of it, so the numbers got harder to read and the picture never came back. The
strip is now a transparent band with chips floating in it — backing only where a
number or a control sits — and the class timeline moved to the opposite screen
edge in a `FLAG_NOT_TOUCHABLE` window of its own, so taps in that band reach the
film. Two contrast bugs fell out of building the floor, both recorded in
11.1b.2; the second one (the worst backdrop is not white) is the more
interesting.

**Open and deliberately so**: 11.1b.8 (the gaps between chips still eat taps),
11.1b.9 (correct, not yet beautiful), 11.1b.4/11.1b.4a (the owner wants left and
right docks and drag-anywhere, and corners once collapsed).

### Newly raised, nothing actioned — snags from using the app, 31 July 2026

A batch of ten observations from the owner riding the app, written up and
**deliberately not implemented**. None of them is prioritised against the *What
to do next* table below yet; that is a decision for whoever picks them up.
Where they landed:

| Snag | Item | State |
|------|------|-------|
| "Up next" is on the far side of the screen from the current interval | **11.6.1** | ✅ |
| No sign of which power zone the rider is in *right now* | **11.6.2** | ✅ — drawn as the ladder **11.6.2a** asked for, on both surfaces |
| No icons on the live numbers | **11.6.3** | ✅ |
| The target gauge never says what the target *is* | **11.6.4** | ✅ |
| "Back to the HUD" is geeky and factually wrong | **11.6.5** | ✅ — it is "View in Overlay Mode" |
| No gesture to dismiss the HUD's volume panel | **11.5.9** | ❌ |
| Heart-rate zones — shown, logged and tracked, and the age they need | **Phase 21** | ❌ |
| Classes built on heart-rate zones — is it advisable? | **21.5** (verdict: yes, with limits) | ❌ |
| "Your Progress" on the dashboard is meaningless | **22.1** | ❌ |
| The dashboard stretches too wide on a 1280 dp screen | **22.2** | 🔶 **22.2.1** done; the rails (22.2.2–22.2.5) are open |

**The calibration question is closed.** "Do we calibrate or leave the curve
hardcoded?" now has a written answer with its reasoning, at the head of
**2.2a**: *yes, calibrate*. 2.2.4 and 2.2.5 are closed out as answered and
superseded, 2.2a gains a test that fences the model to its two legitimate
consumers (2.2a.8), and the alarming-sounding caveat that had been hung over
phases 7 and 16–18 turned out to be misapplied — it is withdrawn in 7.10.6.
The genuine risk it was masking is **7.10.7**: a simulated ride can currently
propose a real FTP change off invented watts.

Followed by one more, found while writing 21.2.3 and confirmed in the code:
**FTP is the app's best measure of progress and it is not kept.** A ride does
not record the FTP it was ridden at, so an auto-FTP change silently redraws
every past ride's zone bands (**7.8**); and only the latest FTP is stored, so
the change history that 16.3.1 and 22.1.4 both assume exists has never been
recorded (**7.9**, shown by **7.10**). Also unactioned.

Two of these contradict something already in the plan, and both contradictions
are written into the items rather than papered over: **11.6.5** changes copy
that 11.1a.2 ticked as done, and **22.2** sits next to **11.3.1**, which says
the dashboard is fine in landscape. 11.3.1 is still right about what it
measured — there is no dead space — and 22.2 is about the opposite failure.

---

**Latest session: 31 July 2026 (third sitting), on the tablet AVD only — no
bike, by request.** Everything below was driven on a 1920 × 1080 / 240 dpi
emulator matching `HARDWARE.md`, and each tick says what was observed.

Closed this session, none of it needing hardware: **11.1a** (all four doors
between the HUD and the app), **11.1.3 / 11.1.4** (collapse and re-dock, with
persistence), **11.5.1–11.5.7** (volume), **2.2a.2–2.2a.7** (per-bike
auto-calibration), **20.1** (the profile selector), and **14.2.2a** — a new
item for a defect found in passing.

**That last one is the find of the session.** The class library had been
showing **5 classes when the cloud held 72**, for the app's whole history.
`ClassTemplateDto` typed `intervals_json` as `String`; the cloud column is
`JSONB` holding an array, so every fetch threw a decode error, which became
`SyncOutcome.Failed`, which the seeder reads as "no cloud" and answers by
falling back to the five bundled assets. Nothing was wrong on screen and
nothing was wrong in the log. Same shape as everything in *Corrections*: a
swallowed failure with a plausible-looking fallback.

### Still needing a rider on the bike

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

**The session before it, same day, was on the bike with a rider pedalling and
wearing a heart-rate strap.** Everything that needed a human on the pedals is
done except the endurance ride (10.6).

Closed then: **10.4** (HUD over Netflix), **10.5 / 2.3.5** (real BLE
strap), **11.1.5** (pause/resume/stop from the strip with the app in the
background), **11.1.6** (coach audible over a playing film), **2.1a.5**
(resistance is a true 0–100).

Three defects were found in the process, all of them invisible from the UI and
none findable without the hardware — the strap could never have been paired on
this tablet at all, the coach had never once ducked the rider's video, and
`avg_hr` had been recording the lowest reading of the warm-up instead of the
average. All three are fixed, tested and in the *Corrections* table; the rule
they sharpen is at the end of it and is worth reading.

**2.2.5 was attempted and deliberately not shipped.** The sweep is checked in
under `calibration/`; the fit failed cross-validation, and the reasoning is in
2.2.5 and `calibration/README.md`.

---

**The session before it, same day, established how telemetry gets in at all.**
It changed the shape of the project, so read this too.

**The headline: bike telemetry works on real hardware, and every assumption
about how it would work was wrong.** The app had spent its whole history
preparing to read a serial port that either does not exist or belongs to
Bluetooth, on a bike it assumed was jailbroken and is not. The route that
works is binding Peloton's own `SensorService`. Full detail in **2.1a** —
read that section before touching anything under `data/sensor/`.

Observed on the bike: cadence 0→58 rpm, resistance 16→59% tracking the knob,
power 0→176 W, 246 per-second rows persisted, ride saved at 245 s / 6.7 kJ.

Three consequences worth carrying forward:

1. **Watts are measured on hardware, not modelled.** `PowerModel` does not run
   during a bike ride. `SensorReading.powerIsMeasured` marks which is which.
   Much of the uncalibrated-power caveat that hangs over 16–18 evaporates for
   real rides, and 2.2.5 now has a way to fix it for simulated ones.
2. **`SerialSensorSource` and `SerialProtocolParser` are dead on this
   hardware.** Correct code, wrong target. Kept only for a rooted tablet.
3. **The next unknown was the HUD, not the sensors** — and the second sitting
   answered it. Phase 11's premise, glancing at a strip while watching
   something else, has now been seen on the bike over Netflix (10.4) and the
   strip's controls work from the background (11.1.5). What remains in Phase 11
   is code, not verification.

### What to do next, in order

**The six MVP blockers above are done, and so are 11.1a.6 and 11.6.2a.** What
is left of the readiness pass is **19.1.2** auto-pause and **19.1.3 / 12.4.4**
local backup, which were on the plan already and belong in the same
conversation: one is the other half of "the rider is not pedalling", and the
other is the only safety net that exists before accounts.

One of the owner's own is still open, and it carries more weight than anything
below because he is the one riding this:

| Next | Why now |
|------|---------|
| **11.1b.10** The grey line on the overlay | Diagnosed, not decided. One of three candidate fixes, and picking is the owner's call — read the item |

Then the table below, which was written before the fifth sitting and is kept
because its reasoning is still good:

| Next | Why now |
|------|---------|
| **14.1.6** Finish the cloud round trip | **One query away.** The app drove it end to end on the emulator: a profile ride, `WorkoutSyncWorker` ran, and it logged `Synced workout … (135 samples)` — and postgrest-kt throws on a non-2xx, so that is a real HTTP success. But `workouts` has **no `SELECT` grant by design** (14.1.1), so nothing in the app or the anon key can read the row back, and the house rule for this box is *see the row appear*. It needs one `select count(*) from workouts` against the live project through the Management API, which this session was not able to run |
| **11.1b.3 / 11.1b.4** Resizing and side docking | The half of 11.1b still outstanding. Opacity and the two-band layout landed; a vertical dock down one side is probably the better default on a 16:9 tablet and needs a genuine re-flow, not a rotation |
| **11.2.2 / 11.2.3** Time in zone, and "ahead of your usual" | The two things still missing from the strip that are about the next sixty seconds |
| **19.1.3** Local backup, and **12.4.4** restore | Ride export landed (12.4.3); this is the other half — the whole database as one file. Until accounts exist (15) it is the only backup a rider has, and the destructive-downgrade path is still there |
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
| 1 | Local database (Room) + Supabase | 🔶 Room complete — the app now both reads (72 class templates) and writes to the cloud; the written row has not been *seen* (14.1.6) |
| 2 | Telemetry engine (sensor service, BLE, simulated) | 🔶 **Verified end to end on real hardware** — bike board (2.1a), resistance scale (2.1a.5) and a real BLE strap (2.3.5). Per-bike auto-calibration built and gated (2.2a), and **the question of whether to calibrate at all is now settled in writing at the head of 2.2a — yes**; it has yet to see a hardware ride (2.2a.1). **Newly opened: a stalled board's last reading is recorded as a fresh measured sample (2.4.4), and the rider is never told the sensor stopped (2.4.5)** |
| 3 | Foreground service & workout lifecycle | ✅ Complete |
| 4 | Floating HUD overlay | ✅ Complete — raised and driven by the ride |
| 5 | HUD Compose UI & power zones | ✅ Complete |
| 6 | Main app UI | ✅ Complete |
| 7 | Auto-FTP, workload JSON, cloud sync | 🔶 Detection and the update flow complete — `WorkoutSyncWorker` observed running and reporting success; the row itself unseen (14.1.6). **Newly opened: the FTP a ride was ridden at is discarded (7.8) and no history of FTP changes is kept (7.9)** |
| 8 | Polish, testing, edge cases | 🔶 Functional items done; cosmetic backlog remains — **plus 8.3b, newly opened: the recovery prompt cannot tell a crashed ride from a live one** |
| 9 | Ride integration | ✅ Complete — a class runs |
| 10 | Hardware validation | 🔶 Sensor path, protocol, a real ride, HUD-over-video and the BLE strap all done — only the full-length ride (10.6) remains |
| 11 | **HUD-first experience — the current priority** | 🔶 11.1 and 11.1a complete; volume (11.5) done. The HUD is now chips on a transparent band with the timeline on the opposite edge (11.1b.1, 11.1b.2, 11.1b.7); resizing and side docking (11.1b.3–11.1b.5) and the rest of 11.2 remain |
| 12 | Ride history & the rider's own record | 🔶 History, detail, delete and migrations done; export and housekeeping remain |
| 13 | Units and display preferences | ✅ Complete — miles, and the locale default that goes with them |
| 14 | Cloud sync that actually reaches the cloud | 🔶 **One query from done** — schema fixed, the seeder reads 72 templates live, the worker posts and reports success. Only the sighting is missing (14.1.6) |
| 15 | Accounts, login and multi-device sync | ❌ Not started — *fundamental once 14 works* |
| 16 | Data visualisation | 🔶 Post-ride charts done (16.1.1–16.1.5, 16.2), prescribed-vs-actual included — the fundamental half. Trends (16.3) remain, blocked on 7.9 |
| 17 | Companion web application | ❌ Not started — *nice to have* |
| 18 | Social features in the Android app | ❌ Not started — *nice to have* |
| 19 | Ideas worth having, ranked | ❌ Not started — mixed |
| 20 | Who's riding — profile selector & avatars | 🔶 Selector rebuilt for the tablet (20.1, incl. rename/remove); avatars (20.2) not started |
| 21 | Heart-rate zones | ❌ Not started — *the one metric that is measured for every rider whatever the power model does* |
| 22 | The dashboard | 🔶 Barely started — *"Your Progress" shows no progress, and the layout is stretched across a screen it should be using. The greeting no longer says "Good morning" at midnight (22.3.1)* |

---

## What is fundamental and what is not

Phases 12–19 were requested together, but they are not the same kind of work,
and building them in the order they were listed would be a mistake. The
ordering below is the one to work in.

**Fundamental — the app is incomplete without these:**

| # | Why it is not optional |
|---|------------------------|
| 12 Ride history + delete | The app records rides and offers the rider no way to see them or get rid of a bad one. Everything downstream — charts, sync, social — is a view onto a history screen that does not exist. |
| 13 Units | A UK rider is shown kilometres with no way to change it. It is an afternoon's work and it is currently wrong for a large fraction of the audience. |
| 14 Working sync | Cloud sync was ticked as complete having **never written a single row** — see 14.0. The schema is fixed and writes now land; the app driving it is still unproven. Every feature in 15, 17 and 18 sits on top of this. |
| 15 Accounts | Sync without an identity puts every rider's data in one anonymous pool. This is also where the current RLS policies stop being a placeholder and start being a security problem. |
| 12.7 Room migrations | `fallbackToDestructiveMigration()` deletes the rider's entire training history on any schema change. Phases 12–19 all change the schema. This has to go first. |

Two more that belong in the fundamental list, added after riding the app on the
tablet:

| # | Why it is not optional |
|---|------------------------|
| 11.1a Getting between the HUD and the app | There is no door between the HUD and the full app in either direction, and the app does not come forward when the class ends. This is the journey a rider makes most often during a ride and it currently routes through the launcher. |
| 20.1 The profile selector | It is the first screen anyone sees and the thing that makes a shared household bike work, and it is a cluster of small cards in the corner of a 1920×1080 screen. |

**Nice to have — real value, none of it load-bearing:**

16 (beyond the post-ride charts), 17, 18, most of 19, and the avatar work in
20.2. A companion web app
and a friends feed are good ideas for an app people already use daily; they
are not what makes people use it daily. The bike, the HUD and an honest record
of the ride are.

> One caution that applies to all of 16–18, **now much narrower than it was**:
> a ride on the bike records the board's own measured watts (2.1a), so those
> numbers are as comparable between riders as the hardware is. It is
> `PowerModel` that stays uncalibrated until a bike fits its own curve (2.2a),
> and it only ever governs simulated rides and the 11.2.1 resistance band —
> a suggestion and a fiction, never a record. Charts, leaderboards and
> friend comparisons should read `SensorReading.powerIsMeasured` and say which
> they are showing, rather than captioning everything "estimated" — or, worse,
> presenting a modelled figure as fact.

---

## Corrections — items previously ticked that were not working

Recorded so the same mistakes are not repeated, and because several of these
were the *reason* a downstream feature looked broken.

| Item | Was ticked | Reality |
|------|-----------|---------|
| 1.11 Supabase repository | ✅ | Passed `Map<String, Any?>` to kotlinx.serialization, which has no serializer for `Any`. Every call threw and was swallowed by `runCatching`. |
| 2.3 BLE heart rate | ✅ | `connectGatt(null, …)`; CCCD descriptor never written, so the strap was never asked to notify. No reading could ever arrive. |
| 2.5 Metrics calculator | ✅ | Integrated power against sample *count*, not elapsed time — a 45-minute ride over-reported energy by ~1000×. |
| 3.4 Per-second metric recording | ✅ | `workout_metrics` has a foreign key onto `workouts`, but the workout row was only written at ride *end*. Every insert violated the constraint. No ride ever stored a time series. |
| 5.7 Power zone calculator | ✅ | Zone ranges left gaps (0.55–0.56 etc.); a lookup that matched nothing fell through to Z7, so an easy warmup was reported as Neuromuscular Power. |
| 6.6 Class detail screen | ✅ | Interval model used camelCase field names against snake_case assets. Every decode threw and was caught into `emptyList()`. No class ever displayed an interval. |
| 8.3 Crash recovery | ✅ | `getIncompleteWorkout()` returned the most recent workout with no completion filter, so the app offered to resume the ride you had just finished, on every launch. |
| 8.7 Unit tests | ✅ | `PostWorkoutAnalyzerTest.kt` was committed empty. |
| 8.8 Instrumented tests | ✅ | `WorkoutDaoTest` referenced `localUserId` / `classTemplateId`, fields that never existed on the entity. It had never compiled. |
| 8.11.17 Dashboard redesign | ✅ | Shipped with `"12.5"` and `"8.3"` kJ hardcoded, and a constant "FTP Stable" badge, shown as the rider's own statistics. |
| 8.5 Haptic feedback | ✅ | The app never declared `android.permission.VIBRATE`. Every call threw `SecurityException` into a `runCatching`, so the buzz simply never happened. Found by reading logcat during a real ride — it is invisible from the UI. |
| 8.8 Instrumented DAO tests | ✅ | Rewritten so they compiled, and ticked — but `@Before fun setup() = runBlocking { … }` infers its return type from the last expression, and `insertUser` returns a row id. JUnit rejects a `@Before` that is not void, so the class failed to initialise and **all ten tests silently never ran**. They pass now. |
| 8.3a Crash recovery prompt | (untickable) | The service exposed `recoverableWorkout`, but nothing binds to the service on a cold start — which is exactly the situation a crash leaves behind — so the prompt could never have appeared wherever it was rendered. |
| 2.1 Serial telemetry | (never ticked) | Not a false tick, but the same failure shape and the most expensive one yet: **the entire premise was wrong and no test could have said so.** The app spent its whole history preparing to read a character device that either does not exist (`ttyS1`) or belongs to Bluetooth (`ttyS2`), on a bike it assumed was jailbroken and is not. `SerialSensorSource`, `SerialProtocolParser`, `CadenceTracker` and `PowerModel` are all correct code aimed at the wrong target. Fifteen minutes on the actual hardware settled it. See 2.1a. |
| 2.3 BLE heart rate (again) | ✅ | **The manifest never declared `ACCESS_FINE_LOCATION`**, which below API 31 *is* the BLE scan permission. A runtime request for an undeclared permission is denied instantly — no dialog, nothing in the log — so on the bike's own Android 11 tablet no strap could ever be found. The rewrite in 2.3 was correct code behind a door that was nailed shut. Behind it, the Scan button reported `PermissionRequired` and never requested anything, so there was no way to grant from inside the app either. Found the first time a strap was put on. |
| 8.6 TTS audio cues | ✅ | Two defects, neither in the coaching logic. `RideCoach` set the language and audio attributes immediately after the `TextToSpeech` constructor, but the engine binds asynchronously, so both were discarded — the device said `setLanguage failed: not bound to TTS engine` and nothing read it. And audio attributes only *describe* a sound; they request nothing. Ducking needs `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, which nothing ever asked for, so the cue was spoken at full volume underneath a film at full volume. The tick claimed "the rider's video ducks under them" and the video had never ducked once. |
| 3.4 / 9.5.2 Average heart rate | (untickable) | `workouts.avg_hr` was written as **79.0 against a true mean of 105.4** over the ride's own 314 metric rows — 79 being the lowest reading of the warm-up. The running mean was rounded to an `Int` every tick, so once one sample moved it by less than 1 bpm the whole increment was discarded and the average froze. `avg_power` and `avg_cadence` beside it, both `Double`, were exact. A second bug sat behind it: heart rate divided by the *tick* count rather than the heart-rate sample count, which would have buried the readings of any strap connecting mid-ride. Invisible from every screen; found by comparing the stored aggregate against the samples it claimed to summarise. |
| 2.6 Telemetry source toggle | ✅ | The chip persisted and was **applied to the running pipeline only in the session it was tapped in**. `SensorRepository.setMode` had one caller, `SettingsViewModel`; nothing applied the stored preference at startup, so every launch silently reverted to `Auto`. Settings kept drawing the rider's choice because Settings reads DataStore and the pipeline reads its own field. **Hardware** — the mode that exists so a ride never records fabricated numbers — therefore could not survive the app being closed. See 2.4.6. |
| 1.11 / 7.4 Cloud sync (again) | ✅ | **No row had ever reached the cloud** — `profiles` and `workouts` were both empty, by count. The cause was not the DTOs: `migration.sql` never granted `anon` a single table privilege, so every request failed `42501` before RLS was consulted. Behind that sat four more defects, one of which (epoch millis into a `TIMESTAMPTZ`) was invisible to code reading and only appeared on a real insert. Failures returned `SyncOutcome.Failed`, which nothing displays. Full detail in **14.0**. |

All of the above are now fixed and covered by tests, **except the last** —
the wire path is proven and `WorkoutSyncWorker` driving it end to end is not,
which is 14.1.6.

Note the shape of that one, because it is the most expensive kind here: the
first diagnosis, made by reading the code against the migration, was confidently
wrong about the cause and wrong about a security claim. Only a request to the
live project produced the real answer. **For anything involving the cloud, read
the code to form a hypothesis and then go and hit the endpoint.**

The pattern is worth naming, because it has now happened eleven times: a
failure path that is caught, logged and returned as a value nothing reads is
indistinguishable from success from every surface anyone looks at. Where a new
phase below adds a feature that can fail quietly — sync, login, export,
deletion — **it also has to add somewhere the rider can see that it failed.**

The three added on 31 July 2026 sharpen it into a second rule. All three were
found within an hour of a rider sitting on the bike with a strap on, and none
of them could have been found any other way:

- **A permission absent from the manifest is denied with no dialog and no log
  line.** Two of the project's defects are now this exact shape (VIBRATE, then
  ACCESS_FINE_LOCATION). Before trusting any runtime permission path, check the
  manifest declares what the code asks for.
- **Configuring an Android service object before it has bound silently does
  nothing.** `TextToSpeech` warns and continues.
- **An aggregate is only trustworthy when checked against the rows it
  summarises.** `avg_hr` was wrong for the whole project's history beside two
  neighbours that were right.

> **Verify against the source data, not the surface.** Every one of these
> looked correct from the UI. The database, `dumpsys` and logcat are where they
> were visible — and for the ducking, only the rider's ears.

---

## Phase 0: Project Scaffolding & Build System ✅

- [x] **0.1** Android project structure
- [x] **0.2** `minSdk 24`, `targetSdk 34`, `compileSdk 34`
- [x] **0.3** Dependencies wired (Compose, Material3, Room + KSP, Coroutines, Supabase, WorkManager, Navigation, Lifecycle, DataStore)
- [x] **0.4** `AndroidManifest.xml` with permissions
- [x] **0.5** `PelonotApp` application class
- [x] **0.6** Builds and runs
- [x] **0.7** Dependencies centralised in `gradle/libs.versions.toml`
- [x] **0.8** Supabase credentials moved out of source into `local.properties` → `BuildConfig`
- [x] **0.9** R8 enabled for release with kotlinx-serialization / Ktor / Room keep rules

---

## Phase 1: Local Database (Room) ✅

- [x] **1.1**–**1.4** `User`, `ClassTemplate`, `Workout`, `WorkoutMetric` entities
- [x] **1.5** DAOs with leaderboard, PB, average and FTP queries
- [x] **1.6** `AppDatabase` singleton
- [x] **1.7** KSP processes Room annotations
- [x] **1.12** `workouts.is_complete` so a ride is recorded from the moment it starts (fixes the foreign key ordering that prevented all metric recording)
- [x] **1.13** Repository layer over the DAOs (`UserRepository`, `ClassRepository`, `WorkoutRepository`, `SettingsRepository`)

> **Note:** the database uses `fallbackToDestructiveMigration()` while pre-release. **Replace this with explicit migrations before the first real user installs a build** — after that, a schema change silently deletes their entire training history.

### Supabase (Cloud) — ⚠️ wired, never connected

- [x] **1.8** Supabase project created
- [x] **1.9** SQL migration in `supabase/migration.sql`
- [x] **1.10** Client provider, now null when unconfigured so the app is fully offline-capable
- [x] **1.11** Sync repository with **typed DTOs** (was `Map<String, Any?>`, which could not serialize)
- [x] **1.14** `SyncOutcome` distinguishes *disabled* from *failed*, so the worker knows what is worth retrying
- [ ] **1.15** A row actually arriving in the cloud. The DTOs serialize now, and they serialize into columns the schema does not have — see **14.0**. Everything above is real; none of it has ever completed a request

---

## Phase 2: Telemetry Engine

**Goal:** Read real-time sensor data from the Peloton sensor board (serial) and BLE HR monitors.

### 2.1 Serial port
- [x] `SerialSensorSource` as a cold flow — collection opens the port, cancellation closes it
- [x] Removed the `close()` → `reconnect()` → `close()` cycle that made ending a workout start an endless retry loop
- [x] Device path injectable (docs said `/dev/ttyS1`, code opened `/dev/ttyS2`)
- [x] **2.1.3** Verified against real hardware, 31 July 2026 — **and the answer is that this whole approach cannot work on a stock bike.** See 2.1a. `SerialSensorSource` is kept as the fallback for a genuinely rooted tablet and is no longer the default

### 2.1a How telemetry actually gets in — findings on the bike, 31 July 2026

Established on a real Gen 1 (`PLTN-RB1VQ`, Android 11) over wireless adb.
**Every assumption this project held about reading the sensor board was wrong**,
and it was wrong in a way no amount of code reading would have exposed.

- `/dev/ttyS1` **does not exist**. `/dev/ttyS2` exists but is the **Bluetooth
  HCI UART** (`bluetooth:bluetooth`) — opening it would never have produced
  bike telemetry, only a fight with the Bluetooth stack.
- The sensor board is `/dev/ttyO0` (OMAP naming; same char device as the absent
  `ttyS1`, major 4 minor 65), owned `system:system` mode `0660`. Confirmed
  independently by the constant `UART_DEFAULT_DEVICE = "/dev/ttyO0"` inside
  Peloton's own service.
- An ordinary app uid cannot open it, and **the bike is not jailbroken**:
  `ro.build.type=user`, release-keys, `ro.secure=1`, no `su`, no Magisk. The
  premise in the README — that this app targets *jailbroken* bikes — is not the
  situation the app is actually installed into, and does not need to be.
- The route that works is **binding Peloton's own `SensorService`**, which
  already owns the port and hands out decoded values. Its `<service>` tag is
  `exported="true"` with **no `android:permission` attribute**, so the
  `onepeloton.permission.ACCESS_SENSOR_SERVICE` it declares at `signature`
  level is never enforced and any app may bind. That is why third-party bike
  apps work on an unmodified tablet.

- [x] **2.1a.1** `PelotonSensorServiceSource` — binds
      `android.intent.action.peloton.SensorData` with category
      `com.peloton.sensor.category.BIKE`, registers `REGISTER_RPM` (1),
      `REGISTER_WATT` (2) and `REGISTER_RESISTANCE` (3) with a `replyTo`
      [Messenger], and receives repeating `EVENT_*` (7/8/9) replies carrying a
      `data` float. Written against the decompiled contract rather than copied
      from the obvious reference client, which is GPL-3.0 against this
      project's Apache-2.0
- [x] **2.1a.2** `<queries>` for `com.peloton.service.SensorData`. Package
      visibility filtering (targetSdk 30+) otherwise hides it and `bindService`
      fails with nothing useful in the log
- [x] **2.1a.3** A `TIME_OUT` reply carries a `0.0` payload and means *the
      board did not answer*, not *the rider produced nothing*. It is dropped
      rather than recorded, and a run of them fails the flow so
      `SensorRepository` backs off — the same argument as nullable
      `heartRateBpm`
- [x] **2.1a.4** **Observed on the bike**: cadence 0→58 rpm, resistance 16→59%
      tracking the knob, power 0→176 W, 246 per-second rows written to
      `workout_metrics`, `avg_hr` NULL with no strap, ride persisted
      `is_complete = 1` at 245 s / 6.7 kJ
- [x] **2.1a.5** **Resistance is a true 0–100.** Both ends driven to the stop
      on the bike, 31 July 2026: full anticlockwise reads `0.0` and full
      clockwise reads `100.0`, each held flat for twenty seconds, and nothing
      on the path from the board to `workout_metrics` clamps either value. The
      rider could not turn the pedals at 100, which is the physical
      corroboration
- [ ] **2.1a.5a** **The sensor saturates at 100 before the knob does.** The
      rider reports the display reaching 100 and the knob then continuing to
      turn some way further before clicking against its stop. So there is dead
      travel at the top where resistance is still rising and the app cannot
      see it. Consequences: a prescribed band (11.2.1) can say "100" but cannot
      distinguish 100 from well past it, and a rider at the top of the range
      gets no feedback for the last part of the turn. Worth checking whether
      the same dead band exists at the bottom

### 2.2 Protocol parsing
- [x] `SerialProtocolParser` as a pure, testable state machine
- [x] Carries partial commands across read boundaries (a trailing `R` used to lose its value byte)
- [x] `CadenceTracker` smooths jitter and **decays to zero when pedalling stops** (cadence used to freeze at its last value forever)
- [x] **2.2.4** ~~Validate `PowerModel` coefficients against a known curve or a real power meter.~~ **Answered, and the answer was "they are badly wrong"** — RMSE 137 W, median absolute error 66%, R² 0.21 against 310 measured samples (2.2.5). The validation this box asked for is done; what it *implied* — that the app then goes and fixes the constants — is settled the other way in **2.2a**, which calibrates per bike instead. The standing caveat is now scoped and no longer open-ended: modelled watts govern simulated rides and the resistance band, nothing else, and 2.2a.8 makes that a test rather than a promise
- [x] **2.2.5** ~~Fit the coefficients from a measured sweep.~~ **Done once, deliberately not shipped, and deliberately not to be repeated** — superseded by 2.2a. The capture method and the data are worth keeping; the approach is not. Kept in full below because it is the evidence 2.2a's decision rests on.

      **Attempted on the bike, 31 July 2026. The capture method works; one
      sweep was not enough, and the coefficients are unchanged.** A 494-second
      Just Ride sweeping resistance 0–75 against cadence 30–101 is checked in
      at `calibration/2026-07-31-sweep-PLTN-RB1VQ.csv`, with the method and
      full reasoning in `calibration/README.md`.

      What it settled: **the shipped coefficients are badly wrong** — RMSE
      137 W, median absolute error 66%, R² 0.21 over 310 steady-state samples.
      2.2.4's caveat was, if anything, understated.

      Why nothing was shipped: a refit of `P = (a + b·R^k)·rpm + c·rpm³`
      (monotone in resistance, so the `resistanceForWatts` inverse stays
      well-behaved) reaches median 10.7% *in sample*, but holding out one
      resistance level and predicting it gives 13–25% above 75 W — and **at
      R=40 the existing coefficients beat the refit**, 11.4% against 22.8%.
      That is a fit interpolating between the six levels that happened to get
      ridden, not a description of the machine. The unconstrained exponent
      (k ≈ 2.86) also puts R=100 at 80 rpm near 1 kW, because nothing above
      R=75 was sampled.

      A sufficient sweep needs more resistance levels — especially **5–20 and
      80–100**, barely touched here — each held at three or more cadences,
      including high resistance at high cadence, and ideally a second rider to
      separate the machine's curve from one person's pedalling.

      **But prefer 2.2a to doing this again.** A manual sweep does not scale
      past the one person willing to perform it, and per-unit variation and
      wear mean a constant in the source is the wrong shape of answer anyway.
      Every hardware ride already carries the data; 2.2a is the version of this
      that works on a stranger's bike

### 2.2a Auto-calibration — the answer to "what about everyone else's bike?"

> ## Decided: yes, calibrate. The whole argument, in one place
>
> This has been spread across 2.2.4, 2.2.5, this section and a caveat hanging
> over 16–18, and it has read as far more alarming than it is. Settled here so
> nobody re-opens it.
>
> **What the power curve can and cannot touch.** `PowerModel` has exactly two
> live consumers in the app: `SimulatedSensorSource`, which fabricates a ride,
> and `RideSnapshot.resistanceForWatts`, which turns an interval's power target
> into "put the knob about here" (11.2.1). On the bike,
> `PelotonSensorServiceSource` reports the board's own measured watts and the
> curve is never consulted. **No recorded number from a real ride comes from
> this model.** A wrong curve gives a rider a bad suggestion; it cannot corrupt
> a record, move an FTP, or make one ride incomparable with the next.
>
> **So the only question is whether the suggestion is any good — and today it
> is not.** The shipped coefficients score RMSE 137 W, median absolute error
> 66% and R² 0.21 against 310 measured samples off this bike (2.2.5). A
> resistance band built on that is not advice, it is a guess with a confident
> border drawn round it.
>
> **Leaving it hardcoded is not the cautious option, it is the current
> defect.** And a *better* hardcoded constant is not available: the one manual
> sweep failed cross-validation (2.2.5), no stranger will ever perform a sweep
> on their own bike, and per-unit sensor variation (2.1a.5a) plus mechanism
> wear mean a single constant is the wrong **shape** of answer however
> carefully it is measured.
>
> **The drift worry, answered — it was misapplied.** Drift is dangerous when a
> stored record is reinterpreted later against a moved yardstick. That is a
> real problem in this app and it is why a ride must snapshot the FTP it was
> judged against (7.8). Calibration has no such exposure, because nothing it
> produces is ever stored: the band is computed for the interval the rider is
> in and is gone a minute later. Recency weighting (2.2a.5) is therefore a
> feature rather than a hazard — a mechanism that has worn, or been serviced,
> *should* stop being described by how it behaved when it was new.
>
> **The downside is bounded at the status quo.** A fit is adopted only if it
> beats the shipped curve, *and* lands within an absolute 25% out of sample,
> *and* has seen enough of the grid (2.2a.3, 2.2a.4); a simulated ride never
> contributes (2.2a.7). Every failure path ends in "keep using the shipped
> curve", which is exactly where the app is today. The worst case is that this
> does nothing — visibly, in Settings.
>
> **What would make us abandon it**, stated in advance so it is a measurement
> and not a mood: if 2.2a.1 shows that ordinary riding does not cover enough of
> the resistance × cadence grid within a few weeks, this never fires, and it
> should then be **deleted** rather than left as machinery that looks like it
> works. Settings already reports the coverage needed to judge that.

**This supersedes the manual sweep as the way `PowerModel` gets calibrated.**
2.2.5 asked one rider to sweep the operating range for five minutes; that does
not scale to other people's bikes, and the cross-validation failure showed it
barely worked for one. The realisation that makes it unnecessary:

> **On real hardware every ride is already a calibration dataset.** The board
> reports measured watts alongside the cadence and resistance that
> `PowerModel` takes as inputs (2.1a). The app does not need a special ride —
> it needs to notice what it is already recording.

Three reasons this has to be per-bike and continuous rather than a constant
shipped in the source:

1. **Per-unit variation.** The resistance figure comes from a position sensor
   whose mapping is unit-specific, and this bike already proves it: the sensor
   pins at 100 while the knob keeps turning (2.1a.5a). A curve keyed on
   "resistance percent" inherits that bike's own quirk.
2. **Drift over time.** Whatever the Gen 1's braking mechanism turns out to be
   — the repo has never established it, and it is worth writing down when
   someone does — a resistance system that is worn, warm or dirty does not
   behave like a new one. If the mechanism is friction-based, pad wear alone
   makes a fixed constant wrong within months.
3. **It costs the rider nothing.** Normal riding covers the operating range
   over a few weeks without anyone being asked to perform a calibration
   ritual, which is the only version of this that a stranger will ever do.

**Built and tested; one half of it still needs a bike.** Everything below is in
`domain/calibration/` (pure, JVM-tested) plus a `CalibrationRepository` and a
Settings section. The gates are exercised against the real 31 July sweep in
`RealSweepCalibrationTest`, which is the only measured data this project has.

- [ ] **2.2a.1** Accumulate steady-state `(cadence, resistance, measured
      watts)` from rides where `powerIsMeasured` is true. Reuse the filter that
      worked on the 31 July sweep: drop samples where the knob is mid-turn or
      cadence is lurching, since those are transitions rather than operating
      points. Store a compact summary, not every sample — a grid of binned
      means is enough to fit against and does not grow without bound.
      **Written and unit-tested — 3,000 samples collapse to ≤4 cells, and the
      steady-state filter drops mid-knob-turn and lurching-cadence samples —
      but the hardware half is unobserved.** Nothing has yet watched a real
      Hardware-mode ride land in the grid, and per the house rule that is what
      this box is for. Ride the bike and check Settings says more than
      "0 measured seconds"
- [x] **2.2a.2** **Calibration belongs to the bike, not the rider.** A
      household bike has several profiles and one resistance mechanism, so this
      is device-level state that every profile shares. It must not live on
      `profiles` and must not sync as if it were personal (15). *Its own
      `pelonot_bike_calibration` DataStore: no profile column, no Room table,
      not in any DTO — so there is nothing for 15's sync to pick up by
      accident.* Deliberately not a Room migration either: it is derived state,
      and losing it costs a few weeks of passive accumulation rather than a
      rider's record
- [x] **2.2a.3** **Do not adopt a fit that cannot beat the shipped curve.**
      This is the whole lesson of 2.2.5: hold out a resistance level, predict
      it, and keep the generic coefficients unless the fit genuinely wins. An
      auto-calibration that silently makes the numbers worse is the same
      failure as everything in the *Corrections* table.
      **And a second gate the first test of this found the hard way**: "better
      than shipped" is a bar a fit to *pure noise* clears, because the shipped
      curve's median error on real data is 66%. So a candidate must also be
      within an absolute 25% out of sample before it is used at all — chosen
      against what the number is for, since a resistance band 25% out still
      puts a rider in the right part of the knob
- [x] **2.2a.4** Require coverage before fitting at all. The 31 July sweep had
      six distinct resistance levels and that was not enough to determine the
      exponent. Track which cells of the resistance × cadence grid have been
      ridden and stay on the shipped curve until enough of them have.
      *Seven levels, each at three or more cadences, spanning at least 40
      percentage points. The regression test that matters: **the 31 July sweep
      itself does not clear this gate**, and is refused for want of coverage
      rather than for want of accuracy — exactly the conclusion
      `calibration/README.md` reached by hand. If that test ever goes green on
      an `Adopted`, the thresholds have drifted below what was already known to
      be insufficient*
- [x] **2.2a.5** Weight recent rides more heavily so the fit tracks drift
      rather than averaging a worn mechanism together with how it behaved when
      it was new. *Each cell's weight decays 3% per ride and a cell that falls
      below 0.5 is forgotten, so a mechanism that has since been serviced stops
      voting*
- [x] **2.2a.6** Say so in Settings: whether this bike is running the shipped
      curve or its own, how much of the range it has seen, and when it last
      re-fitted. A calibration that silently does nothing is indistinguishable
      from one that works (see the *Corrections* rule). *Observed: "Using the
      built-in curve", the coverage bar, "0 of 7 levels, from 0 measured
      seconds", and a "Start again" that appears only once there is something
      to throw away.* It also says out loud that the built-in curve is known to
      be well out, and that recorded watts are unaffected either way
- [x] **2.2a.7** Simulated rides keep the shipped curve. There is no bike to
      calibrate against and the numbers are fiction by construction — learning
      from one would be the model teaching itself its own answer. *Observed: a
      full simulated ride left Settings reading "0 of 7 levels, from 0 measured
      seconds", and the calibration DataStore file was never even created*

- [ ] **2.2a.8** **Fence the model with a test rather than a comment.** The
      scope argument above is the entire safety case for calibrating at all, and
      right now it holds only because two call sites happen not to have grown. A
      test that asserts `PowerModel` is reached from exactly the simulated source
      and the resistance band — and fails the build when a third consumer
      appears — is what stops some future feature quietly deriving a *recorded*
      number from an uncalibrated curve. It is cheap, and it is the difference
      between a rule and a hope
- [ ] **2.2a.9** **Say what the resistance band is worth while the shipped curve
      is still in use.** Until a bike has calibrated itself, 11.2.1's band comes
      from coefficients known to be 66% out at the median, and it is drawn with
      exactly the same authority as the cadence band beside it — which is
      prescribed by the class directly and is simply *true*. Either mark it as
      approximate until the bike has its own curve, or do not draw it at all.
      The most-wrong number on the ride screen should not be the most
      confident-looking one. Settings already knows which curve is in use
      (2.2a.6), so the screen can too
- [ ] **2.2a.10** Once a bike is on its own curve, revisit **11.2.1a** — the
      Zone 1 band that vanishes for a low-FTP rider because the unloaded curve
      at 85 rpm already exceeds the whole zone. That item is currently blocked
      on "we cannot tell a modelling artefact from a real contradiction", and a
      calibrated curve is precisely what resolves it

> **Scope worth keeping in view before anyone builds this.** On real hardware
> `PowerModel` does not run: a ride records the board's measured watts, and
> another rider's recorded history is unaffected by any of this. Calibration
> only governs **simulated rides** and the **prescribed resistance band**
> (11.2.1) — that is, the quality of a *suggestion*, never the integrity of a
> record. Worth doing, and not worth blocking anything else on.

### 2.3 BLE heart rate
- [x] Rewritten against the Bluetooth SIG spec: real Context, CCCD descriptor write, service-UUID scan filter, cancellable scanning, bounds-checked parsing
- [x] Runtime permission handling across API 24–34 — **and the manifest entry
      it needed.** `ACCESS_FINE_LOCATION` was never declared, and below API 31
      that *is* the BLE scan permission; a runtime request for a permission
      absent from the manifest is denied instantly with no dialog and nothing
      in the log. The bike's tablet is Android 11, so heart rate could never
      have worked on the one device this app exists for. Same shape as the
      VIBRATE bug in 8.5
- [x] `HeartRateStatus` surfaced in Settings
- [x] **2.3.4a** Scan asks for the permission it needs. It previously reported
      `HeartRateStatus.PermissionRequired` and stopped there —
      `SettingsViewModel.heartRatePermissions()` existed and nothing called it
      — so the message named the blocker and offered no way past it
- [x] **2.3.5** **Verified with a real strap on the bike, 31 July 2026.**
      Wahoo TICKR FIT found and connected on the first scan; a 314-second ride
      wrote a heart rate on **every one of its 314 metric rows**, 79–125 bpm,
      no nulls and no fabricated zeros

### 2.4 Sensor repository
- [x] Unified `StateFlow<SensorReading>` merging bike telemetry and heart rate
- [x] **One** reconnect policy with exponential backoff (there were previously three competing ones)
- [x] Hardware mode retries rather than falling back to simulation, so a ride never records fabricated numbers
- [x] **2.4.4** **A stale reading is an absence, not a sample.** When the board
      stops answering, `PelotonSensorServiceSource` closes the flow and
      `SensorRepository` backs off and retries — but `_sensorReading` is a
      `StateFlow`, so it goes on holding **the last value that arrived**. The
      ticker in `WorkoutService.recordMetric` reads `.value` unconditionally
      once a second, so a dropout writes that frozen reading into
      `workout_metrics` every second for as long as it lasts, folds it into
      `avg_power` and `avg_cadence`, and feeds it to the calibration grid with
      `powerIsMeasured` still true. Total output is the one figure that escapes,
      by accident: `WorkoutMetricsCalculator` integrates against
      `reading.timestampMs`, and a repeated reading carries a repeated
      timestamp, so `dt` is zero. That accident is the shape of the fix —
      **`timestampMs` already says how old a reading is and nothing else asks.**
      Same family as nullable `heartRateBpm` and the `TIME_OUT` payload in
      2.1a.3: a zero or a repeat that means *we do not know* must not enter the
      rider's permanent record as though it were measured.
      *Fixed by the one thing that already knew: `timestampMs` says how old a
      reading is and nothing asked. A reading older than three seconds ends the
      tick — no `workout_metrics` row, no contribution to the averages, no
      calibration sample — so the second is a **gap**, which is what actually
      happened. Seven JVM tests on the rule, including the two traps: `EMPTY`
      carries `timestampMs = 0` and must never read as live, and a heart-rate
      packet `copy()`s the bike's reading and must not refresh its timestamp.
      **Observed on the tablet AVD**: a 47-second ride in Hardware mode with no
      board wrote **0 rows** to `workout_metrics` and finalised at 0.0 kJ,
      where before it would have written 47 fabricated ones. The frozen-mid-ride
      case — the board dying at 200 W and holding it — is covered by the tests
      and has not been staged on a device; there is no way to make the
      simulator stall*
- [x] **2.4.5** **The rider is never told the sensor stopped.**
      `SensorStatus.Reconnecting` is constructed, logged and rendered nowhere:
      the only consumer of `SensorStatus` in the whole UI is `isSimulated` on
      `RideUiState`. In Hardware mode with a dead board the ride screen shows
      frozen numbers with no explanation and the HUD shows nothing at all — and
      the strip has no simulated-telemetry marker either, though the ride screen
      does. This is the *Corrections* rule applied to telemetry: a failure path
      that is caught, logged and returned as a value nothing reads is
      indistinguishable from success from every surface anyone looks at.
      *Both surfaces now show the two dashes an absent heart-rate strap has
      always had, rather than a frozen number: `telemetryLive` rides on
      `RideSnapshot` because it is a fact about the **absence** of telemetry,
      and a flow that has stopped emitting cannot announce that it stopped —
      the service's tick is the only thing in the app that notices time passing
      without a reading. **Observed on the tablet AVD** in Hardware mode with no
      board: the ride screen says "No signal from the bike — not recording" and
      all four tiles read `--`; the strip's status line says NO SIGNAL in the
      same amber the pause state uses. One thing found by looking: the full
      sentence clipped to "NO SIGNAL · NOT" on the strip, because that chip is
      only as wide as the clock above it. The strip gets two words and the ride
      screen keeps the sentence. Two dashes at 104 sp read as two coloured bars
      — legible as "no value", but worth a look when 11.6.3 does the metric
      tiles properly*
- [x] **2.4.6** **The telemetry source the rider picked was forgotten at the
      next launch.** Found while verifying 2.4.5, and worse than what it was
      found in aid of. `SensorRepository.setMode` had exactly one caller —
      `SettingsViewModel`, on tap — so the choice was applied to the *object*
      only in the session it was made in. On every launch after that the
      repository was back to its default of `Auto`, while Settings went on
      drawing the chip the rider had chosen, because **Settings reads DataStore
      and the pipeline reads its own field** and nothing reconciled the two.
      The consequence is not cosmetic: `SensorMode.Hardware` exists so that a
      ride never records fabricated numbers, and after a restart it was
      silently `Auto`, which falls back to simulated telemetry when the board
      does not answer. Same shape as everything in *Corrections* — a setting
      that reads back correctly everywhere a human looks and does nothing.
      *Observed on the tablet AVD, and it is the clearest evidence of the day:
      Settings showing **Hardware** selected while the ride beside it recorded
      a plausible simulated 68 rpm / 71 W. `PelonotApp` now collects the
      preference for the life of the process; after the fix the same cold start
      rides "No signal from the bike — not recording"*

### 2.5 Metrics calculator
- [x] Total output by integrating power over **elapsed time**
- [x] Rolling averages over time windows
- [x] Cumulative distance estimation
- [x] Long sample gaps clamped so a backgrounded app cannot integrate idle time

### 2.6 Simulated source ✅
- [x] `SimulatedSensorSource` producing a plausible effort profile with lagging heart rate
- [x] Settings toggle: Auto / Hardware / Simulated
- [x] Ride screen states plainly when telemetry is simulated

---

## Phase 3: Foreground Service & Workout Lifecycle ✅

- [x] **3.1** `WorkoutService` with persistent notification and bound state
- [x] **3.2** Immutable `WorkoutSession` (in-place mutation plus `copy()` was being conflated away by `StateFlow`)
- [x] **3.3** `startWorkout` / `pauseWorkout` / `resumeWorkout` / `stopWorkout`
- [x] **3.4** Per-second metric recording, batched — **now actually works** (see Corrections)
- [x] **3.5** Notification channel and live metrics, with a real app icon
- [x] **3.6** Elapsed time from `SystemClock.elapsedRealtime()` rather than a drifting counter
- [x] **3.7** Pause time excluded from elapsed

---

## Phase 4: Floating HUD Overlay (WindowManager)

- [x] **4.1** `OverlayPermissionHelper`, with status shown in Settings
- [x] **4.2** `HudOverlayManager` using `TYPE_APPLICATION_OVERLAY` / `TYPE_PHONE`
- [x] **4.3** Lifecycle, saved-state and ViewModelStore owners for the ComposeView
- [x] **4.4** Drag-to-move, scoped to the handle so it cannot swallow button presses
- [x] **4.6** Composition disposed on hide (each ride used to leak one)
- [x] **4.7** Fixed the `view.context as Activity` cast that threw `ClassCastException` from the overlay's Service context — the HUD crashed every time it was shown
- [x] **4.5** The overlay is raised when a ride starts and dropped when it ends
- [x] **4.8** Docked full-width to one screen edge rather than floating: the rider is watching something else, and the middle of the screen has to stay clear
- [x] **4.9** `WorkoutService` owns the overlay, not the ride screen's ViewModel — a ride outlives the screen, which is the entire point of the HUD

---

## Phase 5: HUD Compose UI & Power Zone Engine ✅

- [x] **5.1** HUD theme
- [x] **5.2** `MetricCard` with animated transitions
- [x] **5.3** Target zone indicator with out-of-range alerting
- [x] **5.4** ~~Collapsible leaderboard panel~~ — **removed in the HUD redesign.** It was built for a 300dp floating card and has no home on a full-width edge strip whose whole purpose is to stay out of the way. `WorkoutRepository.leaderboardFor` still exists and is still correct; nothing renders it. Tracked as 11.6 rather than left as a silently-broken tick.
- [x] **5.5** HUD controls — a single pause/resume toggle rather than two buttons, one always a no-op
- [x] **5.6** Assembled HUD content, using theme colours rather than hardcoded constants
- [x] **5.7** `PowerZone` with contiguous zone bands
- [x] **5.8** `RideIntent` as a typed enum (was a bare display string a typo could silently defeat)
- [x] **5.9** Zone alerting, suppressed when the rider is stationary

---

## Phase 6: Main App UI ✅

- [x] **6.1** `ProfileSelectorScreen` with adaptive grid and distinct guest treatment
- [x] **6.2** `ProfileCreationDialog`
- [x] **6.3** `MainDashboardScreen` — now showing **real** statistics
- [x] **6.4** `PreRideIntentPrompt` describing each option's actual effect
- [x] **6.5** `ClassLibraryScreen` with working category filters
- [x] **6.6** `ClassDetailScreen` — interval breakdown **now renders** (see Corrections)
- [x] **6.7** `PostRideSummaryScreen` reading real figures from the database
- [x] **6.8** `SettingsScreen` — FTP, weight, theme, telemetry source, BLE, overlay permission, cloud sync, all persisted
- [x] **6.9** Navigation via `NavHost` with typed destinations; ride screens are real destinations rather than booleans checked before the graph
- [x] **6.10** ViewModels with `StateFlow`; no database access from composables

---

## Phase 7: Auto-FTP Engine, Workload JSON & Cloud Sync

- [x] **7.1** `PostWorkoutAnalyzer` — 20-min peak (O(n) sliding window over full-length windows only), biometric decoupling, RPE survey
- [x] **7.2** `FtpBreakthroughDialog`
- [x] **7.3** FTP update flow writing through to the profile
- [x] **7.4** `WorkoutSyncWorker` with a network constraint, retry ceiling, unique work, and a result it actually reads
- [x] **7.5** `ClassTemplateSeeder` listing the assets directory rather than a hardcoded category list
- [x] **7.6** Class template JSON in `assets/classes/`
- [x] **7.7** Seeding moved to application scope (a `LaunchedEffect` was cancelled by navigation mid-seed)

### 7.8 The FTP a ride was ridden at — the bug underneath everything else

**`profiles` holds one `ftp_watts` and `workouts` holds none.** The ride start
passes an FTP into `WorkoutService` (9.1.1), the ride is judged against it live,
and then it is thrown away. Every screen that needs a past ride's FTP therefore
reads the rider's *current* one — `RideDetailViewModel` fetches
`getUser(...).ftpWatts` and hands it to the power chart, which draws the zone
bands and the dashed FTP rule from it.

So an FTP change silently rewrites history: a ride ridden in Zone 5 in January
is redrawn as Zone 4 the moment the rider's FTP goes up in March, and the chart
gives no hint that anything changed. **Auto-FTP (7.1–7.3) makes this fire by
itself**, off a ride the rider only agreed to a dialog about — which is the
difference between a stale reading and a record that edits itself. It is the
same family as the `avg_*` defect in CLAUDE.md: a number derived on read, from
a source that has moved since.

- [ ] **7.8.1** `workouts.ftp_watts`, written when the ride is created, with the
      value the ride was actually judged against. A `Migration`, an exported
      schema in `app/schemas/` and a `MigrationTestHelper` test (12.5), and it
      belongs in the same migration as the other columns 12.5.4 is waiting on
- [ ] **7.8.2** **Nullable, and null means unknown** — do not backfill existing
      rows with the profile's current FTP, which would bake today's guess into
      the record permanently and look exactly like real data afterwards
- [ ] **7.8.3** Every read site uses the ride's own value and falls back to the
      profile's only when it is null: the power chart's zone bands and FTP rule
      (16.1.1), any time-in-zone summary (16.1.4, 11.3.3), and `leaderboardFor`
      if it ever compares zones rather than raw watts. The fallback is today's
      behaviour, so old rides are no worse than they are now
- [ ] **7.8.4** Where the fallback is in use, the screen says so rather than
      drawing bands that look as authoritative as the real ones
- [ ] **7.8.5** A guest ride has no profile and so no FTP at all. It gets no zone
      bands rather than the last-selected rider's

### 7.9 FTP history — the progress measure the app already has and discards

FTP is the one number in this app that is genuinely a fitness measure rather
than a volume measure, and the app already recomputes it for free after every
ride. It keeps only the latest value. **16.3.1** ("FTP over time, marked with
the rides that triggered each change") and **22.1.4** both assume a history
exists; nothing creates one. The previous value is overwritten and gone.

- [ ] **7.9.1** An `ftp_history` table: profile, watts, when, **how it changed**
      and, where there was one, the workout that caused it. A derived history is
      not available — the old value is destroyed on update — so this has to be
      recorded at the moment of the change or it does not exist
- [ ] **7.9.2** The *how* is a typed enum, not a string: profile creation,
      manual edit in Settings, accepted auto-breakthrough, guided FTP test
      (19.2.3), pulled from another device (15.3). `RideIntent` is the
      precedent — 5.8 made exactly this a typed enum after a bare display string
      let a typo silently defeat it. The distinction matters on the chart: an
      FTP the rider typed is a claim, one the app measured is evidence
- [ ] **7.9.3** The workout reference is nullable and `ON DELETE SET NULL`.
      Deleting a ride (12.3) must not delete the fact that the rider's FTP
      changed — the training history is not the ride's to take with it
- [ ] **7.9.4** **One funnel.** Every path that changes FTP goes through a single
      repository method that writes the profile and the history row in one
      transaction. There are already four call sites and 15 and 19.2.3 add more;
      a history that depends on each new path remembering to append to it is a
      history that will be wrong within two features
- [ ] **7.9.5** A change to the same value is not a change. Do not record a row
      when the number has not moved, or a re-save in Settings or an idempotent
      cloud pull will fill the trend chart with vertical noise
- [ ] **7.9.6** Seed the first row from the existing profile at migration time,
      dated to the profile's `created_at` and marked as unknown-origin. Without
      it every existing rider's chart starts at their second FTP change
- [ ] **7.9.7** Room migration, exported schema and a `MigrationTestHelper` test
      (12.5), with the seeding in 7.9.6 covered by it — a data-moving migration
      is exactly the kind that passes a schema check and loses rows

### 7.10 Showing it, and being honest about it

- [ ] **7.10.1** 16.3.1 is now buildable: FTP over time, stepped rather than
      interpolated — FTP does not drift smoothly between two rides, it changes
      on a day — with each change marked by what caused it (7.9.2) and tappable
      through to the ride that triggered it
- [ ] **7.10.2** On the dashboard (22.1.4): current FTP, when it last changed,
      and the direction. This is the progress line the section is missing
- [ ] **7.10.3** In Settings, beside the editable field: what it is now and when
      it last moved, so a rider who does not remember agreeing to a change can
      see the app made it
- [ ] **7.10.4** **An auto-FTP change is the app editing the rider's own
      record**, so it stays visible and reversible: the history says the app did
      it, off which ride, and reverting to the previous value is one action and
      appends a row rather than erasing one
- [ ] **7.10.5** A declined breakthrough should not be re-offered for the same
      ride. `PostRideViewModel` runs the analyser on load, so a rider who
      declines and re-opens the summary is asked again about a ride they have
      already answered for
- [ ] **7.10.6** **The honesty caveat — narrower than it first looked, and
      calibration is not part of it.** An FTP trend is only a fitness trend if
      the watts behind it are comparable over time. On the bike they are
      measured off the board (2.1a) and nothing in the app can move them:
      `PowerModel` does not run during a hardware ride, so per-bike calibration
      (2.2a) cannot shift an FTP by a single watt. That concern was raised here
      and is withdrawn — see the decision block at the head of 2.2a. What
      remains is real but simple: a **simulated** ride's watts are fiction, so
      mark on the chart which values came from measured rides. Partly blocked on
      16.1.6, since `powerIsMeasured` is still discarded at the database
      boundary
- [ ] **7.10.7** **A simulated ride must not propose an FTP at all**, and today
      it can. `PostRideViewModel.load` runs `PostWorkoutAnalyzer` over any
      workout with no regard for where the watts came from, so a demo ride on
      the emulator can offer a rider a breakthrough computed from numbers the
      app invented — and 7.9 would then write that into their permanent history
      as evidence. This is the actual integrity risk in the FTP path; the
      calibration one was a phantom. Needs 16.1.6's column to know, which makes
      that column load-bearing rather than cosmetic. Until it exists, the
      conservative reading is the ride's telemetry-source setting at the time
- [ ] **7.10.8** Decide whether `ftp_history` syncs (14, 15). It is small,
      per-profile and the thing a rider would most miss on a new device, but it
      is also a fitness record about a person and 17.7's private-by-default rule
      applies to it before any of it leaves the tablet

---

## Phase 8: Polish, Testing & Edge Cases

- [x] **8.1** Serial disconnection handled with a single backoff policy
- [x] **8.2** BLE disconnection handled without self-triggered reconnect loops
- [x] **8.3** Crash recovery via `is_complete`, surfaced through `WorkoutService.recoverableWorkout`
- [x] **8.3a** Recovery prompt shown at launch, driven from `AppViewModel` rather than the service. It offers to **keep** the ride, not resume it: the rider stopped pedalling when the app went away, and restarting the clock would splice a gap of unknown length into the record. `WorkoutAggregates` rebuilds the totals from the samples that did land.
- [x] **8.3b** **Crash recovery cannot tell a crashed ride from one that is
      running right now.** `getIncompleteWorkout()` is
      `SELECT * FROM workouts WHERE is_complete = 0 ORDER BY timestamp DESC
      LIMIT 1` and `clearRecoverableWorkouts()` is
      `DELETE FROM workouts WHERE is_complete = 0`. Neither excludes the ride
      in flight — and a ride in flight is `is_complete = 0` by design (1.12).
      `AppViewModel` runs the query in `init`, so **any** creation of
      `MainActivity` while a ride is recording raises the non-dismissible "You
      have an unfinished ride" dialog over the profile picker, mid-class, and
      *Discard* deletes the live row out from under the service — taking its
      metric series with it by cascade and leaving the next per-second insert to
      violate the foreign key that historically killed all recording (3.4). The
      trigger is not exotic: the rider starts a class, minimises to the strip,
      and forty minutes of Netflix on a tablet this size is ample reason for
      Android to destroy a backgrounded Activity. Same shape as the 8.3
      correction one layer down — that one returned the ride you had just
      finished, this one returns the ride you are still on.
      *Reproduced first, and it reads worse than it describes: the dialog says
      the app "was closed part-way through a ride" while the HUD strip two
      inches above it shows 02:11 and 66 rpm, live. The fix is `RideInProgress`,
      deliberately process-scoped rather than a column — "is a ride being
      recorded?" is a question about **this process**, and if it died then none
      is, whatever the table says, which is exactly the case this prompt exists
      for. Two instrumented tests, one of them for the trap in the SQL: written
      the obvious way as `id != :excludingId`, excluding nothing excludes
      everything, because `id != NULL` is never true and no ride could ever be
      recovered again. **Observed on the tablet AVD**: same repro — ride
      started, task swiped away, app reopened — now cold-starts with the ride
      still running and no dialog, and a genuine orphan (left behind by
      reinstalling over a live ride) is still offered*
- [x] **8.3c** **The ride summary was a dead end after a crash recovery.**
      Found by driving 8.3b's own repro one screen further. Force-stop
      mid-class, relaunch, answer *Keep it* — and the summary arrives with
      **both** buttons inert. *Discard* did nothing, *Keep as a guest ride* did
      nothing, and the only way off the screen was to kill the app, which
      leaves another unfinished ride behind it and starts the loop again.
      *One unread Boolean, which is the whole family this plan's Corrections
      table exists for. `popBackStack(Dashboard, inclusive = false)` returns
      **false** when Dashboard was never on the stack, and the recovery dialog
      navigates to the summary straight from "Who's riding?", where it never
      has been. **11.1a.5 hit this exact trap on the other door into a live
      ride** and closed it by pushing Dashboard underneath — its comment names
      the failure in so many words — and this door was simply missed. Answered
      by reading the Boolean rather than by faking a stack: at that point
      nobody has said who is riding, so the honest destination is the profile
      selector. **Observed on the tablet AVD**: recover, keep, and the summary
      returns to "Who's riding?"*
- [x] **8.4** Guest post-ride: file against an existing profile, create one on the spot, keep as a household guest ride, or discard
- [x] **8.5** Haptic feedback for interval alerts — **and the `VIBRATE` permission it needs**
- [x] **8.6** TTS audio cues, with navigation-guidance audio attributes so the rider's video ducks under them
- [x] **8.6a** `RideCoach` wired into the ride, driven by the pure `RideCoachPolicy`. Replaces `ZoneAlertManager`, which had no caller and no decision logic to call it with.
- [x] **8.7** Unit tests: `PowerZone`, `PostWorkoutAnalyzer`, `WorkoutMetricsCalculator`, `RideIntent`, `SerialProtocolParser`, `CadenceTracker`, `PowerModel`, BLE parsing, `IntervalParser`, `ClassIntervalEngine`, `TargetBand`, `RideCoachPolicy`, `WorkoutAggregates`, `UnitSystem`, `Formatters`, `RideDayGrouping`, `WorkoutSession` — **192 tests**
- [x] **8.8** Instrumented tests for Room DAOs (foreign key ordering, `is_complete` filtering, cascade delete)
- [x] **8.8a** Instrumented test for `WorkoutService` lifecycle — start/pause/resume/stop, the workout row existing before its first metric, the batched tail being flushed, and a finished ride no longer being offered for recovery
- [x] **8.9** Manual testing on Gen 1 Peloton hardware — profile selector → dashboard → settings → Hardware telemetry → Just Ride → live board data → post-ride summary → persisted ride and 246 metric rows, 31 July 2026. Imperial units picked up from the device locale with no prompting (13.2), on the actual tablet this time
- [x] **8.12** Verified end-to-end on an emulator: profile creation → class library → intervals → simulated ride → post-ride summary → persisted metrics
- [x] **8.13** Verified on a 1920×1080 landscape tablet emulator, which is the shape of the device this actually runs on

### 8.11 Material Expressive design

- [x] **8.11.0** Inter variable font (SIL OFL)
- [x] **8.11.1–8.11.3** Colour tokens, light/dark schemes, fitness metric palette
- [x] **8.11.4–8.11.6** Elevation, shape and motion tokens — **deduplicated**; there were two conflicting elevation scales and a shape scale that shadowed itself with literal-named tokens
- [x] **8.11.7–8.11.11** Theme wired to `MaterialTheme.shapes` so stock components use it
- [x] **8.11.11a** Dynamic colour made opt-in — it previously overrode the entire palette on API 31+, making the brand theme dead code on any modern device
- [x] **8.11.13** Fade-through screen transitions
- [x] **8.11.17** Dashboard redesign
- [x] **8.11.27–8.11.29** Class library with filter chips and duration badges
- [x] **8.11.32/34** Class detail with zone-coloured interval cards
- [x] **8.11.37–8.11.38** Intent prompt with descriptions
- [x] **8.11.48/50** Post-ride summary cards and RPE selector (now a `FlowRow` — ten 48dp buttons in a `Row` overflowed every phone screen, making the higher ratings untappable)
- [x] **8.11.52** FTP breakthrough dialog
- [x] **8.11.65–8.11.67** Content descriptions, 48dp touch targets, semantic headings
- [x] **8.11.81** Shape as a semantic channel: the zone badge is a circle at Zone 1 and a twelve-point star at Zone 7, morphing between them on a change. Built on `androidx.graphics.shapes`, which is the mechanism behind Material 3 Expressive's shape language and works without moving to the material3 alpha.
- [x] **8.11.82** Off-target is amber, never red — power's own accent is coral, and a coral number turning red is not a signal anyone can read at a glance. The direction is also spelled out beside the label, so colour is never the only channel.
- [x] **8.11.83** Springy, physical motion: interval changes wash the HUD with the new zone's colour, cards overshoot and settle, the countdown re-bounces on every tick
- [ ] **8.11.12** Shared element transitions between profile selector and dashboard
- [ ] **8.11.14** Container transform for library → detail
- [ ] **8.11.15** Predictive back for Android 14+
- [ ] **8.11.16** Navigation rail or bottom bar
- [ ] **8.11.18** Large expressive FAB for Just Ride
- [ ] **8.11.21** Dashboard skeleton loading states
- [ ] **8.11.30–8.11.31** Class search bar, empty-state illustrations
- [ ] **8.11.33/35/36** Sticky class header, start-button loading state, difficulty indicator
- [ ] **8.11.39–8.11.41** Preparation checklist, sensor status, countdown
- [ ] **8.11.42–8.11.47** HUD redesign: progress arcs, blur pause overlay, expressive alerts
- [ ] **8.11.49/51** Achievement badges, share button
- [ ] **8.11.53–8.11.57** Charts: power with zone overlay, heart rate, cadence distribution, PB comparison
- [ ] **8.11.58–8.11.64** Extract the remaining shared components (`ZoneBadge`, `ProgressArc`, `SkeletonLoader`)
- [ ] **8.11.68–8.11.69** High contrast mode, font scaling
- [ ] **8.11.70–8.11.80** Micro-interactions, shimmer, pull-to-refresh, scroll edge effects

> The unticked items above are cosmetic and none of them are on the HUD. Phase
> 11 is worth more than all of them: the app runs a class now, and the question
> is whether the surface the rider actually spends forty minutes glancing at is
> good enough. Charts and shimmer are for the two minutes either side of that.

---

## Phase 9: Ride Integration ✅

**Goal:** Make a class actually run. Today a ride records telemetry and totals, but the class's own intervals do nothing and the floating HUD never appears.

### 9.1 Service lifecycle ✅
- [x] **9.1.1** Ride start launches `WorkoutService` as a foreground service with user, class, intent and FTP
- [x] **9.1.2** `RideViewModel` binds to the service and observes `workoutState` and `currentSession`
- [x] **9.1.3** End Ride stops the service and passes the real workout id to the summary

### 9.2 HUD overlay activation ✅
- [x] **9.2.1** `HudOverlayManager.show()` when a ride starts, `hide()` when it ends
- [x] **9.2.2** The service publishes one `RideSnapshot` the overlay renders from; telemetry stays on its own higher-rate flow so a sensor packet does not recompose the whole strip
- [x] **9.2.3** The overlay's pause/resume/stop drive the service directly
- [x] **9.2.4** Overlay permission asked for at ride start, with "not now" and "don't use the HUD" both honoured
- [x] **9.2.5** The overlay stands down while the app's own ride screen is on top, so the rider never sees two of everything

### 9.3 Class interval engine ✅
- [x] **9.3.1** `ClassIntervalEngine` over `List<Interval>` — a pure function of elapsed time, evaluated on the service's existing ticker rather than running a second clock that would drift away from it
- [x] **9.3.2** `IntervalState`: current and next interval, index, elapsed and remaining, class progress, cue
- [x] **9.3.3** Drives the target gauges on both the HUD and the ride screen
- [x] **9.3.4** The ride finishes itself when the class timer runs out

### 9.3a Interval preview & countdown ✅
- [x] **9.3a.1** `next` on `IntervalState`, null on the final interval by design
- [x] **9.3a.2** The interval clock is a draining progress ring around the zone badge, prominent on every interval rather than only recovery ones
- [x] **9.3a.3** Preview card with the next interval's zone colour, shape, cadence target and length
- [x] **9.3a.4** Five-second warning: the edge hairline thickens and pulses in the next zone's colour, the preview card scales up into a countdown, the strip washes with the new zone's colour, and a haptic fires on each tick
- [x] **9.3a.5** "Give it everything" on the last *hard* interval, "Cool down — ride easy" on a recovery finish, no next-interval preview on the final interval

### 9.4 Sensor data off-hardware ✅
- [x] **9.4.1** `SimulatedSensorProvider`
- [x] **9.4.2** Settings toggle between real and simulated input
- [x] **9.4.3** Graceful fallback when `/dev/ttyS2` is unavailable

### 9.5 Post-ride data flow ✅
- [x] **9.5.1** Final session collected from the service
- [x] **9.5.2** Real duration, average power, output and distance shown in the summary
- [x] **9.5.3** `PostWorkoutAnalyzer` run for FTP breakthrough detection
- [x] **9.5.4** `WorkoutSyncWorker` enqueued when a ride with a profile is saved, and when a guest ride is later filed against one

---

## Phase 11: The HUD-first experience — the current priority

**Premise:** this app is used almost entirely from the corner of the rider's
eye. They start a class, switch to Netflix, and look at Pelonot in glances for
the next forty minutes. The HUD is not an accessory to the ride screen; it *is*
the product, and everything in this phase is judged by whether it survives
being read in half a second from two metres away while out of breath.

### 11.1 Verify the HUD's own interactions on a device
- [x] **11.1.1** Docked strip renders over another app without covering the middle of the screen
- [x] **11.1.2** Sits below the status bar rather than under the clock
- [x] **11.1.3** Tap-to-collapse and the slim strip it collapses to. *Observed
      on the tablet AVD mid-class: the handle takes the strip from 170 dp to
      115 dp — a third of its height handed back to the film — and what
      survives is the clock, the zone number, the four live numbers and the
      controls. On a free ride the saving is much smaller, because the expanded
      strip has no timeline, zone ring or next-up block to shed in the first
      place*
- [x] **11.1.4** Drag to re-dock between top and bottom, and that the choice
      persists. *Observed in both directions: dragged down, the strip moved to
      the bottom edge and `hud_dock=Bottom` was in the DataStore; dragged back
      up, `Top`. A ride started afterwards raised the strip at the edge the
      last one was left at.* The collapse state deliberately does **not**
      persist — `hide()` resets it, so every ride opens showing everything
- [x] **11.1.5** **Pause, resume and stop from the HUD with the app in the
      background** — driven from the strip on the bike with Netflix in the
      foreground, 31 July 2026. Pause froze the ride at 03:00 and it was still
      03:00 twelve seconds later, so the pause genuinely leaves elapsed alone
      (3.7); resume advanced it again; stop tore down the notification and the
      overlay window and left Netflix undisturbed. The overlay never took focus
      from the video app at any point
- [x] **11.1.6** **Spoken coach audible over a playing video** — but only
      after two defects, and neither was in the coaching logic. `RideCoach`
      configured the engine straight after the `TextToSpeech` constructor,
      before the service had bound, so both the language *and the audio
      attributes* were discarded ("setLanguage failed: not bound to TTS
      engine", in logcat on the bike). And attributes alone ask the system for
      nothing: ducking requires an explicit
      `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` request, and nothing ever made one.
      `dumpsys audio` showed Netflix holding `GAIN` with `loss: none` and an
      empty ducked-players list throughout, and the rider reported the cue
      inaudible under the film. With focus requested per cue and released when
      the last utterance finishes: **observed ducking, and the coach clearly
      audible over Netflix**

### 11.1a Getting between the HUD and the app

Today the HUD and the ride screen are two places with no door between them. The
rider raises the HUD when the ride starts, switches to Netflix, and there is no
way back into the app except through the launcher — and no way back out to the
HUD except by leaving the app again. That is a gap in the product, not a
polish item: it is the single journey a rider makes most often during a class.

- [x] **11.1a.1** **Double-tap the HUD brings the full app forward.** Double
      rather than single, because single tap already collapses and expands the
      strip (11.1.3) and that is the gesture a rider fires by accident while
      reaching past the tablet. A single tap that yanked Netflix off the screen
      mid-scene would be the worst possible mis-fire on this surface.
      *Observed on the tablet AVD: double-tapped the strip with the launcher in
      front, Pelonot came forward on the ride screen with the ride still
      running and the overlay stood down.* One trap for anyone verifying this
      by `adb`: Compose ignores a second tap that lands inside
      `doubleTapMinTimeMillis` (**40 ms**), and two back-to-back
      `input tap`s are about 26 ms apart, so the gesture reads as a single tap
      and nothing happens. Put a `sleep 0.12` between them
- [x] **11.1a.2** A **"back to the HUD" control on the ride screen**, so the
      journey is symmetric and does not route through the launcher or the
      recents switcher. `moveTaskToBack` rather than `finish()` — the ride
      screen has to survive, because the rider is coming back to it. Hidden
      when the HUD is off or ungranted, since it would then only hide the app.
      *Observed: tapped it, the launcher returned and the strip came up with
      live telemetry on it*
- [x] **11.1a.3** **The full app comes forward when the ride ends.** The class
      finishing is the one moment the rider definitely wants the whole screen —
      the summary, the RPE question and any FTP proposal are all there and none
      of them fit on a strip. **A foreground service is *not* exempt from
      Android 10's background-activity-start rules** — the note here previously
      assumed it was. `SYSTEM_ALERT_WINDOW` is on the exemption list, which is
      the same grant the HUD is already drawn under, so the app that can show a
      strip can always open itself from one. *Observed: stopped the ride from
      the strip with the app in the background and the summary came up by
      itself, showing the ride's real figures*
- [x] **11.1a.4** **Discard the ride from the post-ride summary**, for the
      session that was a warm-up, a mistake, or somebody else pedalling for
      thirty seconds. Guests get this today (8.4) and riders with a profile do
      not — they have to finish, leave, open history and delete. It has to name
      what is going and be as hard to hit by accident as the delete in 12.3.2.
      *Observed: the dialog names the duration, and confirming took the ride
      and all 135 of its `workout_metrics` rows with it — checked by count
      against the database, not by the screen returning.* Local only: an
      already-uploaded ride stays in the cloud until tombstones exist (15.3.4)
- [x] **11.1a.5** **The cold-start door — there is no way back into a ride that
      is already running.** 11.1a.1–11.1a.3 all assume the app's task still
      exists: `AppForeground.bringForward` sends `ACTION_MAIN` +
      `CATEGORY_LAUNCHER` precisely so an existing task resumes where it was
      left, which is the right behaviour and covers the common case. When the
      Activity is gone it resumes nothing — the graph's start destination is the
      profile selector, and **nothing outside `WorkoutService` knows a ride
      exists**. So the strip's double-tap, the ride notification and the
      launcher icon all land the rider on "who's riding?" with a class still
      recording behind it and no route to it. The notification is the worse of
      the three, because a ride notification that does not open the ride is the
      one thing a notification is for. Needs the running ride to be knowable
      from outside the service — the incomplete workout row already says so, and
      8.3b has to be fixed first or asking the question raises the recovery
      dialog instead.
      *Done with the same `RideInProgress` 8.3b introduced, and only from the
      start destination — that is what makes it a cold-start door rather than a
      trap, since a ride begun the ordinary way sets it too and the rider is
      already on the ride screen by then. Dashboard is pushed underneath on the
      way in, because otherwise the summary's own `popUpTo(Dashboard)` has
      nothing to pop to and the rider finishes the ride into a dead end.
      **Observed on the tablet AVD**: ride started, task swiped away (0 activity
      records, service still `isForeground=true`), reopened from the launcher →
      the ride screen at 00:26 and counting; ended it there and Done on the
      summary returned to the dashboard*
- [x] **11.1a.6** **The ride notification is missing entirely on Android 13+.**
      `POST_NOTIFICATIONS` is declared in the manifest and **requested by
      nothing** — the only runtime request the app makes is for Bluetooth. On
      API 33+ that means the ongoing ride notification is never posted, so the
      route 11.1a.5 just built has no doorbell: `HARDWARE.md` calls that
      notification "the most reliable read on an in-flight ride" and on a
      modern device it does not exist. The bike's own tablet is Android 11 and
      unaffected, which is exactly why this could sit here unnoticed —
      `targetSdk 34` means any other device the app is installed on is not.
      Same family as the `VIBRATE` and `ACCESS_FINE_LOCATION` corrections, one
      step earlier: not a permission absent from the manifest, but one present
      in it that nobody ever asks for. *Seen on the API 36 tablet AVD:
      `importance=NONE` for `com.pelonot` and no notification during a ride,
      until it was granted by hand with `pm grant`*
      *Done. `NotificationPermission` + `RequestRideNotificationPermission`
      (`ui/permission/`), called from the ride screen. Asked at the first ride
      rather than at launch — the only notification this app posts is the
      ongoing ride, so a rider who has never started one has nothing to say yes
      to — and held back by a `deferred` flag while the overlay prompt is up,
      because two system dialogs on the first ten seconds of a class is how
      both get dismissed unread. A denial is not retried and not surfaced: the
      ride is unaffected either way, and the platform stops offering after two
      refusals. **Observed on the API 36 tablet AVD** with the permission
      revoked: free ride started → the dialog appeared over the ride screen →
      Allow → `POST_NOTIFICATIONS: granted=true` and notification id=101 on
      `workout_channel` in `dumpsys notification`, where before there was
      none*

### 11.1b The HUD getting out of the way

The premise of this whole phase is that the rider is watching something else.
The strip currently sits on top of that film as a solid block, in a fixed size,
pinned to the top or bottom edge. Every item here is about the HUD taking up
less of the screen and less of the attention.

- [x] **11.1b.1** **Adjustable opacity**, from solid down to nearly invisible,
      with the film readable through it. Set once in Settings rather than
      fiddled with mid-ride.
      *Done, and it turned into a redesign rather than a slider. A single alpha
      over a full-width panel is the wrong instrument: a rider asking for more
      of their picture back only ever got a lighter wash over all of it — the
      numbers got harder to read and the picture never came back. **The panel is
      gone.** The strip is a transparent band with a handful of chips floating
      in it, and backing is painted only where a number or a control sits;
      everything between them is untouched film at any setting. The slider now
      moves the chips, defaulting to 0.82. Observed on the tablet AVD over a
      full-white page: expanded, collapsed, and the class timeline*
- [x] **11.1b.2** A floor on how transparent it can go, and a check that the
      text still passes contrast against **moving** video rather than against
      one paused frame.
      *The floor is **calculated, not chosen** — `HudOpacity` composites the
      chip over a backdrop and finds the least opaque it can be while every
      colour the strip draws text in still passes WCAG. Two things fell out of
      building it, both of which had been quietly wrong. A floor derived from
      the brightest colour on the strip says nothing about the rest of it: at
      0.59 the white clock passed at 4.5 and the coral power figure sat at 1.55.
      And **the worst backdrop is not white** — the backdrop is a film, so it is
      every colour there is, and a partly-transparent panel can land the
      composited background right on the text's own luminance, where contrast is
      1.0 and the number is invisible. Bisecting on the over-white contrast
      made coral look fine at zero opacity. The floor is about 0.76 with all
      seven colours counted, and the grey labels are lifted towards the primary
      text colour rather than dragging it to 0.81 for everybody. 9 JVM tests.*
      **The moving-video half of this item is still open and always was**: a
      still frame is kinder than a film, screenshots over DRM video come back
      black (10.4), so this needs the rider's eyes on the bike
- [ ] **11.1b.3** **Resizable**, so a rider who wants three big numbers and a
      rider who wants the whole timeline can both have it. Persisted like the
      dock
- [ ] **11.1b.4** **Dock to the left and right edges too**, not only top and
      bottom — **asked for directly by the owner, 31 July 2026**: "I would like
      a version of the HUD on the right and left too, should be able to drag it
      where you want." So this is four edges and one gesture, not two edges and
      a preference. A vertical strip down one side leaves subtitles *and* faces
      clear and is probably the better default on a 16:9 tablet in landscape —
      which is the shape of the device this actually runs on (8.13).
      `HudDock` is a two-value enum with an `opposite()` and a `gravityFor()`
      either side of it; both extend to four cleanly, and the drag detector on
      the handle is currently `detectVerticalDragGestures`, which does not
- [ ] **11.1b.4a** **Corners, once collapsed** — the owner's observation on
      seeing the compact strip: "in compact mode there are more options, bottom
      left, bottom middle, right bottom, right top." He is right, and it falls
      out of the redesign rather than being extra work. Collapsed, the HUD is
      one pill and a row of buttons, not a band — so it no longer *needs* a
      whole screen edge, and a corner is the least of anyone's film. The
      expanded strip still wants a full edge, so the two states may well want
      different position sets, which is a thing `HudDock` cannot currently
      express: it is one enum shared by both. Settle that before writing the
      drag handling for 11.1b.4
- [ ] **11.1b.5** The layout has to genuinely re-flow for a vertical dock, not
      rotate: the timeline, the zone badge and the live numbers each need a tall
      arrangement. Extends 11.1.4, which only ever considered top/bottom. The
      chip redesign in 11.1b.1 makes this materially easier than it was — a
      column of chips is the same components in a `Column` — but the metrics
      chip holds four readouts in a `Row` and a 200 dp-wide dock will not take
      them side by side
- [ ] **11.1b.6** Every one of these choices persists, and the HUD comes back
      where and how the rider left it. *Opacity and dock do; the rest of this
      waits on 11.1b.3 and 11.1b.4 existing*
- [x] **11.1b.7** **The class timeline moved to the opposite screen edge**, in
      an overlay window of its own. Splits the furniture into two thin bands
      instead of one tall block, and because nothing on it is interactive that
      window is `FLAG_NOT_TOUCHABLE` — every tap in that band goes straight
      through to the film. The strip itself can never make that promise; it has
      a stop button on it. *Observed on the tablet AVD: timeline top, numbers
      bottom, re-docking swaps both.* **The owner is not sold on the split** and
      left it to judgement — so treat it as provisional. If it turns out to read
      as two unrelated things rather than one instrument, the fix is small: the
      bar is a standalone composable in a window of its own, and putting it back
      above the chips is a layout change, not a rewrite. Worth settling on the
      bike rather than by argument, and worth considering alongside 11.1b.4 —
      the answer may well differ for a vertical dock, where the opposite edge
      is a *column* and the timeline would have to run down it
- [ ] **11.1b.8** **The strip still eats touches between the chips.** The window
      is full-width and the gaps are now invisible, so a rider tapping their
      film in the space between two chips gets nothing and cannot see why. It
      was the same before the redesign — the difference is that the slab at
      least *looked* like something. A window cannot have holes punched in it,
      so this is either a row of narrow windows or nothing; 11.1b.7 shows the
      shape of the fix for anything non-interactive
- [ ] **11.1b.9** **Revisit the chips as a piece of visual design.** They are
      correct and they are not yet beautiful. Open questions: whether the metric
      accents should hold their colour at low opacity or take a treatment that
      survives any backdrop; whether the chip hairline is doing enough over
      bright scenes; whether the timeline deserves the same silhouette as the
      chips or a deliberately different one; and whether the zone-change flash
      still reads now that it washes chips rather than a whole band
- [ ] **11.1b.10** **The grey line across the overlay.** Reported by the owner
      as "a weird grey line on the HUD", and reproduced on the tablet AVD: a
      full-width hairline running edge to edge just below the chips, reading as
      a stray divider rather than as part of anything.
      *It is not a divider — it is the `edge` glow in `HudOverlayMain`, the
      hairline of the current zone's colour that thickens and pulses as an
      interval change approaches. Two things make it read as chrome instead of
      as an alert. **Zone 1's colour is grey**, so during every warm-up and
      recovery block the "accent" is indistinguishable from a rule someone
      drew by accident; and at rest it is `alpha = 0.45` of that, which is
      exactly the weight of a divider. Its comment also says it sits "along the
      very screen edge", which is true only when the overlay is docked Bottom —
      docked Top it is drawn **last**, so it lands on the inside edge, between
      the chips and the film. Candidates, in order: drop the resting alpha to
      nothing so the line exists only when it is saying something (it still
      thickens and pulses on approach, which is the part that earns its place);
      or give a grey zone a non-grey alert colour; or move it to the true screen
      edge for both docks. **This is a design call about an alert, so it is the
      owner's** — it is diagnosed, not decided*

### 11.2 What the strip is still missing
- [x] **11.2.1** Resistance, with a prescribed range derived by inverting `PowerModel` at the middle of the cadence target. Shown next to cadence — the two inputs together, then the two outputs. Reports *no* band rather than a clamped percentage when the target is out of the knob's reach at that cadence, because the honest instruction there is "spin faster".
- [ ] **11.2.1a** The resistance band disappears on some Zone 1 intervals for a low-FTP rider: the unloaded curve at 85 rpm already produces more watts than the whole zone allows. That is arguably *true* and worth saying out loud ("you cannot ride this easy at this cadence") rather than saying nothing. Blocked behind **2.2a** (see 2.2a.10) — until this bike is on its own curve it is as likely to be a modelling artefact as a real contradiction, and 2.2.4 has now answered that the shipped curve is 66% out at the median, which makes the artefact reading the likelier of the two.
- [ ] **11.2.2** Time in zone: a thin stacked bar of how the ride has been spent, for the collapsed strip where the timeline does not fit
- [ ] **11.2.3** A "you are ahead of / behind your usual" line against `leaderboardFor`, which is the one comparison a rider actually acts on mid-ride
- [ ] **11.2.4** Handle a HUD raised while a call or another overlay is on top

### 11.3 Beyond the strip
- [x] **11.3.1** ~~**Landscape layout for the dashboard.**~~ **Stale — there is nothing wrong with it.** Re-checked twice on the real tablet and again on the matching AVD (1920×1080 @ 240 dpi): the FTP card, the Just Ride button and the three action cards fill the width, and the empty right-hand side this item describes does not exist. The original screenshot was almost certainly taken on a wrongly-configured AVD, which is exactly the trap `HARDWARE.md` was written to close. The profile selector *did* have the problem and is fixed in 20.1
- [ ] **11.3.2** Post-ride charts: power with zone bands, heart rate, cadence distribution (8.11.53–8.11.57)
- [ ] **11.3.3** Time-in-zone summary on the post-ride screen
- [ ] **11.3.4** Skip or extend the current interval mid-ride, for a rider who needs to take a call
- [ ] **11.3.5** Screen-on lock during a ride, so the tablet does not sleep mid-class

### 11.4 Re-home the leaderboard
- [ ] **11.4.1** Leaderboard on the post-ride summary, where there is room for it
- [ ] **11.4.2** A single-line "vs your best" on the ride screen (not the HUD)

### 11.5 Volume control — the tablet has nowhere else to change it

**The reason this is not a nicety.** On the bike's tablet there is no status
bar to pull down, so there is **no system volume UI at all**. A rider watching
Netflix with the coach speaking over it has no way to change either level
without leaving what they are doing. The app is the only surface that can
offer it, which makes this closer to fundamental than to polish.

- [x] **11.5.1** **Media volume**, controlling `STREAM_MUSIC` — which is what
      Netflix and everything else plays on. Needs `MODIFY_AUDIO_SETTINGS` in
      the manifest (a normal, install-time permission, no prompt). Declare it
      *before* wiring the slider: an undeclared permission fails silently and
      this project has shipped that bug twice already (8.5, 2.3). *Observed:
      dragged to 73% and `dumpsys audio` moved `STREAM_MUSIC` from 5 to 11 of
      15 — checked against the system's own value rather than the slider's
      position, which is the whole point of 11.5.7*
- [x] **11.5.2** **Coach volume, independent of the media volume.** Do this
      with `TextToSpeech.Engine.KEY_PARAM_VOLUME` in the `Bundle` passed to
      `speak()` — a per-utterance 0..1 scalar — rather than by moving a stream
      volume. A stream-level control would fight the ducking in 11.1.6 and
      could not make the coach quieter *than* the film, which is exactly what
      a rider who finds it shouty will want. A level of 0 returns *before*
      taking audio focus, so a silenced coach cannot duck the film for the
      length of an utterance nobody can hear. **Wired and persisted, not yet
      heard**: the emulator has no TTS engine worth trusting and the tablet's
      `com.onepeloton.tts` is the only one this ever runs against, so whether
      50% actually sounds like half needs the bike
- [x] **11.5.3** Both in **Settings**, as the place they are set deliberately
- [x] **11.5.4** Both reachable from the **HUD**, since mid-ride is when a
      rider actually discovers the film is too loud, and going to Settings
      means abandoning the ride screen and the film together. *Observed: the
      button opens both sliders inside the strip, already showing the levels
      set in Settings*
- [x] **11.5.5** **This is the deliberate exception to 18.6 / 19.4** — "nothing
      on the strip that is not about the next sixty seconds of pedalling". It
      earns its place only because the tablet offers no alternative. Keep it
      out of the resting strip: put it behind the collapse/expand (11.1.3) or
      a single small control that opens the sliders, so the default HUD is
      still three big numbers and a countdown. *One tonal button among the ride
      controls; the resting strip is unchanged*
- [x] **11.5.6** Volume changes persist, and the coach level survives a
      restart. A rider who turned the coach down did not mean "until the next
      ride". *`coach_volume` in the DataStore; the media level is the system's
      own and the system already remembers it.* The HUD and Settings write the
      same preference, so they are one setting rather than two that drift
- [x] **11.5.7** Setting a stream volume can throw `SecurityException` when a
      Do Not Disturb policy is active on API 23+. Catch it and say so rather
      than letting the slider move and nothing happen — a control that lies
      about having worked is worse than one that is absent. `VolumeController`
      also **reads the level back after every write** instead of trusting it:
      the system clamps and rounds to its own step count, and the slider must
      show where the volume actually is
- [ ] **11.5.8** Volume keys: the owner reports **no physical rocker**, and the
      driver picture in `HARDWARE.md` is consistent with that — the only devices
      declaring `KEY_VOLUMEUP` are the headphone jack (`ACCDET`, inline remote
      only) and the MediaTek keypad driver, which declares the capability
      whether or not buttons are populated. **Settle it with `adb shell getevent
      -l` and a press of every physical button**; ten seconds with someone at
      the bike. Honour the keys if they arrive, but nothing may depend on them
- [ ] **11.5.9** **A gesture to dismiss the expanded volume panel.** It opens
      and closes from one small button today (11.5.4), so a rider who opened it
      mid-ride has to find that same control again with the sliders now in the
      way. A swipe on the panel towards the strip's own edge should close it —
      *towards the edge*, so the direction follows the dock rather than being
      hardcoded down (11.1.4). Two traps: the strip already carries drag-to-move
      and drag-to-re-dock on the same surface (4.4, 11.1.4), so an ambiguous
      swipe must not both close the panel and move the strip; and a slider is a
      horizontal drag consumer sitting inside whatever gesture this adds, so the
      dismiss has to be on the panel's own chrome or clearly vertical. A timeout
      that closes it after a few idle seconds is worth considering alongside,
      since the panel is the one part of the strip that is not about the next
      sixty seconds of pedalling (11.5.5)

### 11.6 The full-screen ride screen — what a rider cannot read on it

The strip gets the attention in this phase because that is where the ride is
watched from. But the ride screen is where a rider looks when they want to
actually *read* something — before the class starts, on a recovery block, when
the film is paused — and everything below came from riding with it in front of
them. All of it is emulator-checkable at 1920 × 1080 / 240 dpi.

*(Note 5.4 says the removed leaderboard panel is "tracked as 11.6"; that work
is **11.4**, and the cross-reference in 5.4 is stale.)*

- [x] **11.6.1** **"Up next" belongs directly under the current interval, not
      across the screen from it.** In landscape the ride screen is three
      columns: `EffortColumn` on the left holds the current interval, and
      `UpNextColumn` on the right holds what is coming, with the whole metric
      grid between them. The two things a rider reads *together* — what I am
      doing, and what I have to be ready for — are at opposite ends of a
      1280 dp-wide screen, and nothing on screen says they are related. Put the
      next interval immediately beneath the current one. This is a re-layout of
      both columns rather than a move of one composable: the right column also
      carries pause, end and back-to-HUD, and those stay where a thumb expects
      them. `UpcomingIntervals` (the rest of the class beyond the next block)
      is a separate question — it can stay on the right, or fold into the
      timeline at the top, which already draws the same information.
      *Done as written. `NextUpBlock` — the preview and the five-second
      countdown it swaps to — hangs off the bottom of `EffortColumn`, directly
      under the current interval card. `UpcomingIntervals` ("THEN") stayed on
      the right with pause, end and the overlay button, which stay put because
      a thumb has learned where End ride is. **Observed on the tablet AVD**:
      "NEXT in 03:53 · ENDURANCE · 85–95 rpm" sitting under "INTERVAL 1 OF 7"*
- [x] **11.6.2** **Which power zone is the rider in *right now*.** The screen
      shows the *prescribed* zone large and unmissable — that is what the
      `ProgressArc` and `ZoneGlyph` in the interval card are — and never says
      which zone the current power actually falls in. The rider learns they are
      off target from an amber number and an arrow, which says "wrong" without
      saying "you are in 3 and you were asked for 4". `PowerZone.forPower`
      already computes it. Three things to decide rather than assume: a free
      ride has no target but a current zone is still meaningful and should
      probably show; the current and target zone must be tellable apart at a
      glance and not two identical badges side by side; and the HUD has exactly
      the same gap, so whatever is designed here should be shrinkable to the
      strip.
      *`CurrentZoneBar` — "NOW  Z2  ENDURANCE … ASKED FOR Z1", amber when the
      two disagree, "ON TARGET" when they do not. Three decisions worth
      recording. It sits **over the metric grid**, not beside the prescribed
      glyph: the zone a rider is in is a reading of their live power, and the
      "asked for" clause travels with it, so the comparison does not need the
      two badges to be adjacent. It is a strip of words against a glyph with a
      shape per zone, so they cannot be confused. And it renders on a free
      ride, where nothing is prescribed.*
      *The find while building it: `RideUiState.currentZone` had to become
      **nullable**. `PowerZone.forPower` answers Z1 for zero watts and for an
      unknown FTP — true, and useless — so a bike nobody is pedalling, or a
      board that has gone quiet, was about to be labelled "Active Recovery".
      Same family as 2.4.4: the absence is the answer. **Observed on the
      tablet AVD** mid-class, both agreeing and disagreeing.*
      *Superseded by **11.6.2a**: the bar has been replaced by the ladder, on
      both surfaces, and the overlay gap this item left is closed with it.*
- [x] **11.6.2a** **Draw the zones as a scale, not as a sentence.** Raised by
      the owner against the 11.6.2 bar above, with a photo of Peloton's own
      indicator: **seven segments in a row, one per zone**, the rider's current
      zone lit, the boundaries labelled in watts underneath (`0 · 123 · 167 ·
      200 · 233 · 266 · 333`), the zone number set large beside it and FTP %
      at the other end. Not a request to copy it — a request for what it does
      better, which is worth naming precisely:
      - **The whole range is on screen at once.** "Z2" tells a rider where they
        are only if they already hold the ladder in their head. A scale shows
        it, and shows how far along the zone they are — Z3-and-just-in is a
        different ride from Z3-nearly-out, and the current bar cannot tell them
        apart.
      - **The boundaries are in watts.** That turns "you are in 2" into "215 W
        gets you into 3", which is an instruction rather than a label. The app
        already has these numbers: `PowerZone.powerRange(ftp)`.
      - **It absorbs the prescribed zone too.** The band the class is asking
        for can be marked on the same scale, which is exactly the comparison
        11.6.2 exists to make — and would let the prescribed glyph go back to
        being decoration rather than the only statement of the target.
      Things to settle rather than assume: what the scale does on a free ride
      (probably the same, minus the prescribed band); whether the watt labels
      survive being shrunk to the overlay (11.6.2 asked the same question and
      the honest answer may be "numbers on the ride screen, segments only on
      the overlay"); and that every watt figure here is FTP-derived, so it
      moves under the rider when auto-FTP accepts a breakthrough (7.8). It
      replaces `CurrentZoneBar` rather than sitting beside it
      *Done, and `CurrentZoneBar` is gone rather than kept beside it.
      `ZoneScale` (`domain/model/`) is pure and tested — boundaries, the
      fraction through the current zone, FTP %, and the watts that reach the
      next rung — and `PowerZoneScale` (`ui/components/`) draws it: zone digit
      large on the left, seven segments, the watts under each one, FTP % on the
      right. The prescribed zone is an outline on its own segment, so "where I
      am" and "where I was asked to be" are one comparison across one object.*
      *The three questions it said to settle, answered by building it. **A free
      ride draws the same ladder** with nothing outlined — the boundaries do not
      depend on a class. **The watt labels do not survive the overlay**, as
      suspected: `compact` drops them and the FTP %, leaving segments and the
      digit, which is what a rider glancing past a film is asking for anyway.
      And **the segments are equal widths, not proportional to watts** — Z7 is
      unbounded and Z1 spans 56% of FTP alone, so a true scale would draw six
      zones as slivers beside two slabs; the watts underneath carry the real
      proportions.*
      *The one structural gain beyond the drawing: `ZoneScale.currentZone` is
      now the app's **single** rule for "is there a zone at all" — no FTP, no
      power, or a stalled board means none — where 11.6.2 had left that rule
      living on `RideUiState` alone, with the overlay free to answer
      differently. 274 JVM tests. **Observed on the tablet AVD**: mid-class in
      Z2 against a prescribed Z1, both marked on the ladder at once, and the
      compact form on the overlay over another app*
- [x] **11.6.3** **Iconography on the live numbers** — a heart for bpm, and the
      same for cadence, resistance and power. The label is `labelSmall` under a
      104 sp number, which makes the only thing identifying the number the
      smallest text on the tile, read from a metre away mid-effort. An icon is
      recognised faster than a word is read. Keep the text label *beside* it
      rather than replacing it — a bare glyph for "resistance" is not something
      anyone recognises unaided — and give the icon `contentDescription = null`,
      because `MetricReadout` already sets a `clearAndSetSemantics` description
      for the whole tile and a labelled icon inside it would be announced twice.
      Same treatment for `SmallStat` (output, distance, avg power) and for the
      HUD's compact readouts.
      *Done, and defined once in `MetricIcons` so the ride screen and the
      overlay cannot drift apart: revolutions for cadence, the knob for
      resistance, a bolt for power, a heart for bpm, a flame for output and a
      rule for distance. `contentDescription = null` on every one, as the item
      asks. **Observed on the tablet AVD** on all four tiles and all three
      totals*
- [x] **11.6.4** **The target gauge does not say what the target is.** This is
      the biggest of these. `TargetGauge` draws a track, a highlighted band and
      the rider's position on it, with **no numbers anywhere** — a rider can see
      they are below the band without ever learning that the interval asks for
      85–95 rpm. `TargetBand` already carries `min` and `max`; show them.
      Prominently on the ride screen, where there is room for "85–95" set large
      next to or under the live value. The HUD strip is a different problem with
      a different amount of space and should be decided separately rather than
      by shrinking one design until it fits both. Two details that will bite:
      the band needs its unit stated once or "85–95" beside a resistance tile is
      ambiguous, and a *missing* band (11.2.1 deliberately reports none when the
      target is out of the knob's reach) must not render as "0–0".
      *`TargetBand.label` rounds to whole units and returns **null**, never
      "0–0", when nothing is prescribed — and null is also what an unreachable
      resistance target gives, so the app never invents an instruction it has
      just decided it cannot give. The ride screen prints "TARGET 80–90 rpm"
      under the gauge with the unit repeated; the overlay does not, and that is
      the item's own instruction not to shrink one design until it fits both.
      The screen reader gets the band on **both**, since the reason for hiding
      it is width and a reader has none. **Observed on the tablet AVD**:
      "TARGET 80–90 rpm" under cadence, "TARGET 0–80 watts" under power, and
      the resistance tile correctly showing no target line at all*
- [x] **11.6.5** **"Back to the HUD" is the wrong label, twice over.** It is
      jargon — "HUD" is a word this project's authors use and a rider does not
      — and it is factually wrong: "back" implies the rider has been there, and
      most of the time they have not been anywhere yet. What the button actually
      does is `moveTaskToBack`: it puts the app away and leaves the strip on top
      of whatever they were watching. Candidates, best first: **"Minimise to the
      strip"**, "Hide the app, keep riding", "Back to my film". The string is
      `R.string.ride_back_to_hud`. Note the same jargon is in the ride screen's
      HUD prompt ("Don't use the HUD") and in Settings, so pick the rider-facing
      word for this thing **once** and change it everywhere, or the app will
      have two names for one feature. This revises copy that 11.1a.2 ticked; the
      behaviour it describes is right and only the label is wrong.
      *Done, and the name is the owner's: **"overlay"**, not "strip", which was
      this session's first answer and was rejected. The button reads **"View in
      Overlay Mode"**. The word was in six rider-facing places and all six
      moved together — the button, the permission prompt's "Don't use the
      overlay", the Settings section, the opacity slider's spoken label, the
      drag handle's, and the Silent coach style's description. **"Overlay" is
      now the rider-facing name for this thing and nothing user-visible may say
      "HUD" or "strip".** The code, this plan and `ARCHITECTURE.md` still say
      HUD internally, which is fine — it is one name in the source and one name
      on screen. **Observed on the tablet AVD***
- [x] **11.6.6** **Ending a ride takes one tap and cannot be undone.** The end
      button is a 72 dp pill at the bottom of the right-hand column, directly
      under pause, pressed with sweaty hands while moving; the HUD's stop is the
      same. There is no resume — `stopWorkout` finalises the row, tears down the
      overlay and stops the service — so a mis-tap at minute 20 of a 45-minute
      class ends the class. The ride itself survives, which is why this is not
      a data-loss item; what it destroys is the remaining twenty-five minutes.
      Confirm it, in the same weight as 12.3.2 and 11.1a.4. Two things to get
      right: the confirmation must be **dismissible by a tap anywhere**, because
      it is raised mid-effort and the common case is "I did not mean that", and
      it must not appear when the class timer ends the ride by itself.
      *Both surfaces, and they had to be answered differently. The ride screen
      gets a dialog naming the elapsed time and what is left of the class —
      that second number is the one the rider does not have in their head
      mid-effort — dismissible by tapping anywhere. **The strip cannot raise a
      dialog at all**: it is `FLAG_NOT_FOCUSABLE` by design, which is the whole
      reason the film keeps focus, so the button asks for itself — first tap
      turns it into "END?", second answers it, four seconds of silence is also
      an answer. Asked in the UI and not the service, so a class that runs out
      of intervals still ends by itself with nobody there to answer. One thing
      found only by looking: an `IconButton` sizes to a 52 dp circle whatever
      is inside it, so the word wrapped to "EN / D?" — it swaps to a pill
      button rather than restyling the icon one. **Observed on the tablet AVD**:
      Keep riding returns to a still-running ride; on the strip, one tap leaves
      the service up, the button reverts on its own, and two taps end it*

---

## Phase 12: Ride history & the rider's own record — fundamental

**The gap:** every ride since the foreign-key fix has been writing a full
per-second time series to `workout_metrics`, and there is no screen in the app
that shows a rider a ride they finished yesterday. `WorkoutRepository` already
has `observeWorkouts(userId)`, `getRecentWorkouts` and `getMetrics`; the data
layer is done and nothing renders it.

### 12.1 History screen
- [x] **12.1.1** `HistoryScreen` + `HistoryViewModel`, as a real `NavHost` destination, reached from a History card on the dashboard
- [x] **12.1.2** Rides grouped by day, newest first, with headers. `RideDayGrouping` is pure with the clock and timezone injected, and tested across midnight, both DST transitions and a non-UTC day boundary — an off-by-one here is only visible around midnight, in a timezone the author does not live in
- [x] **12.1.3** Row shows class title (or "Just Ride"), time, duration, output, avg power, distance, and says so when a ride was rebuilt after a crash — which is what `was_recovered` (12.5.5) exists for
- [x] **12.1.4** Only complete rides, asserted in `WorkoutDaoTest`
- [x] **12.1.5** Empty state that says what to do. Guests get their own, since a guest ride belongs to nobody by design
- [x] **12.1.6** Windowed query. `observeHistory` is a projection joining only `workouts` and `class_templates` and **never touches `workout_metrics`**; `observeCompletedCount` tells the screen whether to offer "Show older rides"

### 12.2 Ride detail
- [x] **12.2.1** `RideDetailScreen`, a separate destination from `PostRide`. Not `PostRideViewModel` with a flag: that one runs the FTP analyser over the whole series on load and offers to rewrite the rider's FTP, which is right ninety seconds after a ride and bizarre on a ride from March
- [x] **12.2.2** `RideSummaryCard` extracted out of `PostRideSummaryScreen` and shared by both
- [x] **12.2.3** Charts (phase 16) land here first. *Bookkeeping: this box was
      left open while the work behind it was done and observed. `RideChartsSection`
      is on this screen and 16.1.1–16.1.5 were each ticked against it on the
      tablet AVD; there was never a separate piece of work here*
- [x] **12.2.4** Edit RPE after the fact, saving on each tap. **Observed**: rated 7 from the detail screen, `rpe_rating = 7` in the row

### 12.3 Delete
- [x] **12.3.1** Delete from the detail screen and from a button on the row. **Not** a swipe: a swipe is right for a mis-tap you can take back instantly, and this list is scrolled far more often than it is edited
- [x] **12.3.2** Confirm before deleting, naming the ride and its date, and saying that the per-second record goes with it
- [x] **12.3.3** Undo snackbar. **The delete is deferred, not reversed** — `workout_metrics` cascades, so an "undo" that re-inserted the aggregates would hand the rider a ride with its time series missing. The row is hidden, the snackbar offers to put it back, and the delete only reaches the database when the snackbar goes away or the screen closes. If the process dies mid-window the ride survives, which is the safe direction. **Observed**: undo, then 49 metric rows and `rpe_rating = 7` still in the database
- [x] **12.3.4** `PRAGMA foreign_keys` asserted directly on the real connection in `WorkoutDaoTest`, alongside a 20-sample cascade. An orphaned metric series is invisible from every screen and grows forever
- [ ] **12.3.5** **Deleting a synced ride must delete it in the cloud too**, or the next pull resurrects it. Needs a tombstone (`deleted_at`) rather than a hard delete, since the device may be offline when the rider deletes. Blocked on 14 — the confirm dialog says "this only deletes the ride on this device" in the meantime
- [ ] **12.3.6** Bulk delete / select mode — after 12.3.5, not before

### 12.4 Housekeeping the record
- [ ] **12.4.1** Re-file a household guest ride against a profile from history (the post-ride flow in 8.4 is the only chance to do it today, and the rider is usually still breathing hard)
- [ ] **12.4.2** Filter by class category and by date range
- [x] **12.4.3** Export a ride — CSV of the metric series, and `.tcx` for Strava and everything else. This is an open-source app: not being able to get your own data out is the thing the subscription product does.
      *Both written from the **samples**, never the aggregates: an export that
      disagreed with the database would be worse than none, and `avg_hr` has
      already been wrong here once. A null heart rate stays null all the way to
      the file — blank in CSV, element absent in TCX — because a zero there is a
      fabricated reading in a file the rider may well average later. Numbers are
      formatted `Locale.US` on purpose: a French device writes 91,5, which in a
      comma-separated file is two columns and in XML is a parse error at the far
      end. Saved through the system file picker rather than a share sheet — the
      rider says where it goes, no `FileProvider` is involved, and the bike's
      tablet has almost nothing installed to share **to**. Observed on the
      tablet AVD: both files written to Downloads, pulled back, and the TCX
      parses as XML with **632 trackpoints against 632 rows in
      `workout_metrics`**. Success and failure are both said out loud in a
      snackbar; a silent export is indistinguishable from a successful one*
- [ ] **12.4.3a** `.fit` as well as `.tcx` — a binary format needing a real
      encoder, where TCX is text. Nothing reads `.fit` that will not read
      `.tcx`, so this is for completeness rather than reach
- [ ] **12.4.3b** **The TCX has no per-second distance, and cannot have one.**
      `workout_metrics` stores cadence, resistance, power and heart rate — there
      is no speed or distance column — so a per-trackpoint `DistanceMeters`
      could only be invented by spreading the ride's total evenly across its
      seconds, which describes a ride nobody did. The lap carries the real
      total. Whether Strava is content with that needs an actual upload to find
      out. Same family as 16.1.6: a missing column, not a missing calculation
- [ ] **12.4.4** Export/import the whole local database as a file. Until 15 exists this is the *only* backup a rider has

### 12.5 Room migrations — do this before anything in 12–19 ships
- [x] **12.5.1** Replace `fallbackToDestructiveMigration()` with explicit `Migration` objects. `AppMigrations.ALL` is the list; a downgrade still falls back destructively, since that only happens when an older APK is installed over a newer one on a development device
- [x] **12.5.2** Export the Room schema to `app/schemas/` and check it in. The stale `2.json` left over from an abandoned `theme_preference` column has been deleted; `1.json` and `2.json` are now the real history
- [x] **12.5.3** `MigrationTestHelper` instrumented test for each migration. 1→2 runs against a real SQLite file created from the exported v1 schema, with rows written beforehand, and asserts they — and the cascade onto `workout_metrics` — survive. **Observed: 18 instrumented tests pass on the tablet emulator**
- [x] **12.5.5** The first migration is one the app needed anyway rather than a placeholder: `workouts.was_recovered`, so history can distinguish a ride rebuilt from its samples after a crash from one that finished normally (12.1.3)
- [ ] **12.5.4** Only then, the schema changes the rest of the plan needs — `deleted_at`, `synced_at`, `auth_user_id` for 14–15, `workouts.ftp_watts` (7.8) and the `ftp_history` table (7.9), a `powerIsMeasured` column (16.1.6), and date of birth on `profiles` (21.1.1). Units (13) turned out to need none — the preference is a display concern and lives in DataStore

> Deliberate consequence: a development device still holding a pre-migration
> database now fails to open rather than silently emptying itself. No shipped
> build has ever existed, so no rider is affected; uninstall and reinstall.

> This is listed last in the phase and is first in the work. Every remaining
> phase adds a column. The moment a build with a real training history is
> installed on the bike's tablet, a destructive fallback stops being a
> pre-release convenience and becomes a data-loss bug that has already happened
> by the time anyone notices.

---

## Phase 13: Units and display preferences — fundamental, small

Distance is hardcoded to kilometres (`Formatters.kilometres`), which is the
wrong default for a UK or US rider looking at a Peloton bike whose own display
is in miles.

- [x] **13.1** `UnitSystem` (`METRIC` / `IMPERIAL`) in `SettingsRepository`, defaulting from the device locale. Absent-means-never-chosen, so the locale is consulted on every read rather than metric being pinned on first launch
- [x] **13.2** Settings toggle, next to the existing FTP and weight fields. **Observed on the emulator**: an `en-US` device opens on Imperial with no prompting
- [x] **13.3** `Formatters` takes the unit system: distance km/mi, speed km/h/mph, body weight kg/lb. No no-argument overload survives — a caller that forgot the preference is a compile error rather than a silent kilometre
- [x] **13.4** **Store SI, convert at the edge.** **Observed**: 160 lb typed into profile creation is `72.5747792016057` in `profiles.weight_kg`, read back as `160.0 lb`, and switching to Metric mid-session redraws it as `72.6 kg` without touching the row
- [x] **13.5** Every surface reads the same setting, delivered through `LocalUnitSystem` from `PelonotTheme` — which is what lets the HUD read it, since the overlay is composed from the service and has no ViewModel to thread it through. Ride screen, post-ride summary, HUD strip, settings and profile creation all consume it
- [x] **13.6** Watts, RPM, BPM and kJ are unit-agnostic and stay as they are. No calories, and the Settings copy says why
- [x] **13.7** JVM tests for the conversions, the settings-field round trip, and locale-derived defaults — **plus** `FormattersTest`, which pins every number under `fr-FR` and `hi-IN-u-nu-deva`. A missing `Locale.US` is the same defect class that put epoch millis into a `TIMESTAMPTZ` (14.0)

---

## Phase 14: Cloud sync that actually reaches the cloud — fundamental

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

- [ ] **14.2.1** Carry the rider through: local `user_id` (Int) → cloud `profiles.id` (UUID). Requires the profile to sync first and its cloud id to be stored locally. Until this lands, every uploaded ride is anonymous
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
- [ ] **14.2.3** **Surface sync state in Settings**: configured or not, last successful sync, count pending, and the actual error text of the last failure. `SyncOutcome.Failed` dies in `Log.w` today, which is precisely why this went unnoticed for the project's whole history
- [ ] **14.2.4** `synced_at` on `workouts` locally, so a ride uploads once and a backlog is knowable
- [ ] **14.2.5** Retry the backlog when connectivity returns, not only at ride end
- [ ] **14.2.6** Upload the rides already sitting in the local database — there is a real history on the tablet that predates sync working
- [ ] **14.2.7** Decide the metrics payload ceiling. A 45-minute ride is ~2,700 samples in one JSONB column; a 90-minute ride is double that. Find the point where the insert starts failing before a rider does
- [ ] **14.2.8** `supabase/003_*.sql` for whatever 14.2.1 and 14.2.2 need, keeping migrations incremental and non-destructive — `002` deliberately did not drop or recreate anything, and the 72 class templates are still the originals

### 14.3 Keeping it working

- [ ] **14.3.1** A round-trip check that can be re-run against a throwaway project, scripted and documented in `supabase/README.md`. Three of the five defects above were invisible to `assembleDebug` and to all 158 JVM tests
- [ ] **14.3.2** Keep `supabase/*.sql` and the DTOs verifiably in step — the column-name test in `WorkoutDtoTest` is a start, but it hardcodes the column list and nothing forces it to match the live schema
- [ ] **14.3.3** Fold the schema into CI (19.1.4) once there is a CI to fold it into

### 14.10 Configuring the endpoint — open-source hygiene

The endpoint must be configurable **in code, not in the app's UI**: a rider
should never be asked to type a URL, and a self-hoster should not need to fork
a screen.

- [ ] **14.10.1** A checked-in `cloud.properties` (or `CloudConfig.kt`) holding the default endpoint and publishable key, overridable by `local.properties` and then by env vars. Today the only source is `local.properties`, which is **gitignored** — so a fresh clone of an open-source project has no cloud at all and no in-repo record of what the community endpoint even is
- [ ] **14.10.2** Precedence documented in the README: env → `local.properties` → checked-in default → offline
- [ ] **14.10.3** Keep `SupabaseModule.client == null` and `SyncOutcome.Disabled` as the behaviour when nothing is configured. **Offline-first is not negotiable**; the cloud stays a mirror
- [ ] **14.10.4** Only publish a default key **after 15.5**. A publishable key is safe to check in exactly when RLS is correct, and right now it is `USING (true)` — publishing it today would publish everyone's data with it
- [ ] **14.10.5** `supabase/README.md`: how to stand up your own project, run the migrations in order, and point a build at it

### 14.11 Credential hygiene

`local.properties` currently holds three values, and one of them is far more
dangerous than its name suggests.

- [x] **14.11.1** `local.properties`' third Supabase value (was `supabase.serviceKey`) is **not** a service-role key — it is an `sbp_` **personal access token**, which is account-wide and can create, modify and delete *every project on the account*, not just this one. It is correctly gitignored and, verified, is read by nothing in `app/build.gradle.kts` and referenced nowhere in the source, so it cannot reach `BuildConfig` or an APK
- [x] **14.11.2** Renamed to `supabase.accessToken` so nobody wires it into `BuildConfig` on the assumption that it belongs there. A service-role key in a client app would be bad; **this one is worse**
- [ ] **14.11.3** Never add a `secret()` call for it. The two that exist (`supabase.url`, `supabase.anonKey`) are the only two that may ever become `buildConfigField`s
- [ ] **14.11.4** Rotate it when the schema work is done — it has been used from a shell and lives in a plaintext file
- [x] **14.11.5** Said in `supabase/README.md`, since a contributor following the setup will otherwise put whatever key they find into the same file

---

## Phase 15: Accounts, login and multi-device sync — fundamental once 14 works

**The rule this phase must not break:** the app works with no account, no
network, and no cloud, exactly as it does today. Login is something a rider
opts into to get their history onto a second device and to use anything
social. A signed-out app is not a degraded app.

### 15.1 Auth
- [ ] **15.1.1** Add the Supabase `auth-kt` module — only `Postgrest` is installed today
- [ ] **15.1.2** Email magic link and/or OAuth. Prefer flows with no password field: the app should not be in the business of handling credentials
- [ ] **15.1.3** Session persisted and refreshed; expiry never interrupts a ride or blocks a screen
- [ ] **15.1.4** Sign in from Settings, never as a gate on launch or on starting a class

### 15.2 Identity model
- [ ] **15.2.1** Local Room profiles stay the source of truth. An account **attaches to** one local profile rather than replacing the profile system — the bike is a shared household device and that is the whole reason profiles exist
- [ ] **15.2.2** `profiles.auth_user_id UUID REFERENCES auth.users` in the cloud schema; `cloud_id` on the local `UserEntity`
- [ ] **15.2.3** Household guests never sync. A guest ride has no owner by definition
- [ ] **15.2.4** Two local profiles on one tablet may be two different accounts — nothing may assume a single signed-in user per device

### 15.3 Sync in both directions
- [ ] **15.3.1** On first sign-in, backfill the whole local history, batched and in the background
- [ ] **15.3.2** Pull on a new device: restore rides and profile
- [ ] **15.3.3** Idempotent by the local workout UUID, so a retry or a re-install cannot double a ride
- [ ] **15.3.4** Conflict rule, written down and one line long: **local wins for a ride in progress, last-write-wins for RPE and profile fields, tombstones win over everything** (12.3.5)
- [ ] **15.3.5** Metric series are large — a 45-minute ride is ~2,700 samples. Decide deliberately whether the full series goes up or only the aggregates plus a downsampled trace, and record the reasoning
- [ ] **15.3.6** Sync never runs on the ride's critical path and never blocks the HUD

### 15.4 Leaving
- [ ] **15.4.1** Sign out keeps every local ride. A rider signing out has not asked to lose their training history
- [ ] **15.4.2** "Delete my cloud data" as a separate, explicit action, with the local record untouched
- [ ] **15.4.3** Account deletion end to end, since GDPR applies to a hobby project too

### 15.5 RLS, properly
- [ ] **15.5.1** Rewrite every policy against `auth.uid()` — currently all six are `USING (true)`
- [ ] **15.5.2** A rider can read and write only their own profile and their own workouts
- [ ] **15.5.3** `class_templates` stays world-readable; it is public data
- [ ] **15.5.4** Verify each policy from a second account, not by reading the SQL. This is the one place where being wrong is a breach rather than a bug

---

## Phase 16: Data visualisation

The post-ride charts (16.1) are close to fundamental — they are what makes a
recorded time series worth recording. The trend work in 16.3 is genuinely
nice-to-have and only becomes interesting after a few dozen rides exist.

### 16.1 The ride itself

All of these are on the **ride detail** screen (12.2), which is where a ride
lives once it is finished. Two columns on the tablet, one anywhere narrower.

- [x] **16.1.1** Power over time with zone bands behind it (was 8.11.53).
      *Drawn as a min/max envelope with the mean through it, over the rider's
      own zone bands and a dashed rule at FTP. Observed on the tablet AVD*
- [x] **16.1.2** Heart rate over time, drawn **only where samples exist** —
      null is unknown, and a line dropping to the axis says the rider's heart
      stopped. *Each contiguous run of samples is its own path; a strap that
      pairs mid-ride charts only the part it saw, and one that drops out gets a
      break rather than a straight line across the gap. Covered by tests both
      ways*
- [x] **16.1.3** Cadence distribution. *Banded at 10 rpm, with coasting
      excluded — without that filter every ride has a large spike in the 0–9
      band that says nothing about how it was ridden*
- [x] **16.1.4** Time in zone as a stacked bar, shared with the HUD's collapsed
      strip (11.2.2). *With a legend: seven colours in a bar is a code nobody
      has been given the key to*
- [x] **16.1.5** The class's prescribed intervals drawn under the actual trace — "what you were asked for" against "what you did" is the single most useful post-ride view.
      *Each interval is an outlined block at its target power band, behind the
      trace so the record is always on top of the prescription. Two things the
      building of it turned up. The band is scaled by `workouts.intent_modifier`
      — the multiplier the ride was **given**, not one re-derived from today's
      preferences — so this half is a record; the FTP half is not, and is 7.8.
      And the plan is **clipped to what was ridden**: a class abandoned part way
      would otherwise hang 18 minutes of prescription off the end of a
      12-minute axis, so the segments stop where the ride does and the sentence
      says the class was longer. Observed on the tablet AVD against a real
      `Torque Repeats 20` ride that stopped at 10:31 of 20:00 — five blocks
      (Z1, Z2, Z5, Z2, and a sliver of the sixth), 28% of the prescribed time
      inside the band, and a free ride beside it with no blocks, no legend and
      no compliance sentence*
- [ ] **16.1.5a** The prescribed **cadence** has nowhere to be drawn. An
      interval prescribes a cadence range as well as a zone, and 16.1.3 is a
      *distribution*, not a trace — there is no time axis to lay a target on.
      Either a cadence-over-time chart or nothing; the data is already parsed
      and thrown away by 16.1.5
- [ ] **16.1.6** Axis label reads from `SensorReading.powerIsMeasured` rather
      than saying **estimated** unconditionally. On the bike it *is* a meter
      (2.1a); on a simulated ride it is a model (2.2.4). A ride can in principle
      contain both, so decide what a mixed series is labelled.
      **Blocked, and on something smaller than it looks: nothing records it.**
      `powerIsMeasured` exists on each `SensorReading` and is thrown away at the
      database boundary — `workout_metrics` has no column for it — so a ride off
      the real board is indistinguishable on disk from a simulated one. The
      chart therefore errs towards "estimated", which is the safe direction
      given the rule that a modelled watt is never presented as measured. It
      needs one column, a `Migration`, an exported schema and a
      `MigrationTestHelper` test (12.5). Do that and this becomes a one-line
      change. **Promoted from cosmetic to load-bearing**: 7.10.7 needs this
      column to stop a simulated ride proposing a real FTP change, so it is no
      longer only about what an axis is captioned

### 16.2 Building them
- [x] **16.2.1** Compose `Canvas`, no charting dependency — these are four
      fixed chart types, and a library is a large surface for a small need.
      *Also a library would have to be taught that a null heart rate is
      unknown rather than zero, which is the defect this project has already
      fixed twice*
- [x] **16.2.2** Downsample before drawing: 2,700 points into ~300 buckets
      keeps peaks (min/max per bucket, not mean — averaging erases exactly the
      sprint the rider wants to see). *Bucketed by **elapsed time**, not sample
      index: a recovered ride has gaps, and bucketing by index would compress
      them out and draw a ride that looks continuous. A test rides 2,700
      seconds with a one-second 600 W spike in the middle and asserts the spike
      survives while its bucket's mean does not reach it*
- [x] **16.2.3** Off the main thread, cached on the ride, computed once.
      *Built on `Dispatchers.Default` after the summary is already on screen —
      the totals are what the rider opened the screen for*
- [x] **16.2.4** Accessible: every chart has a text summary, since a chart is
      unreadable to a screen reader and a fair amount of this data is a
      sentence. *The canvas itself carries the summary as its content
      description and the visible copy of it is cleared from the tree, so it is
      announced once rather than twice*

### 16.3 Trends — nice to have
- [ ] **16.3.1** FTP over time, marked with the rides that triggered each change. **Blocked on 7.9** — this was written as a charting task, and the data it charts does not exist: a profile holds one FTP and the previous value is overwritten. See 7.10.1 for what to draw and 7.10.6 for what the line is allowed to claim
- [ ] **16.3.2** Weekly volume and output
- [ ] **16.3.3** Personal bests by duration
- [ ] **16.3.4** This ride against your previous best at the same class (`leaderboardFor` already computes it — see 11.4)
- [ ] **16.3.5** A calendar heatmap of ride days. Cheap, and the streak is the thing that gets people on the bike

---

## Phase 17: Companion web application — nice to have

Only worth starting once 14 and 15 work; it is a view onto the same Supabase
project and has nothing to show before rides are reaching it.

- [ ] **17.1** Stack and repo layout. A separate top-level `web/` directory or a separate repo — **the Android build must never depend on it**
- [ ] **17.2** Auth shared with the app via the same Supabase project; a rider signs in once conceptually
- [ ] **17.3** Ride history and ride detail, reusing the chart definitions from 16 conceptually if not literally
- [ ] **17.4** Profile customisation: display name, avatar, bio, FTP, units
- [ ] **17.5** Friends — request, accept, block. New `friendships` table with its own RLS; this is the first schema where a rider can see another rider's data and it deserves more care than the rest
- [ ] **17.6** A light activity feed: friends' recent rides, kudos, a comment. Deliberately not a full social network
- [ ] **17.7** **Private by default.** Nothing is visible to anyone until the rider opts in, with per-ride visibility (private / friends / public). Defaulting to visible would publish training history people did not know they were publishing
- [ ] **17.8** Self-hosters get the same deal as 14.10 — the endpoint is configured at build time, not typed in
- [ ] **17.9** Decide what "public" means before shipping it: a public profile URL is an outward-facing surface with moderation and abuse implications a hobby project has to actually think about

---

## Phase 18: Social in the Android app — nice to have

Everything here is behind a signed-in account and must vanish cleanly when
signed out — not grey out, not prompt, not appear at all.

- [ ] **18.1** Friends list and requests, mirroring 17.5
- [ ] **18.2** A feed of friends' recent rides on the dashboard, below the rider's own stats and never above them
- [ ] **18.3** Kudos, and nothing that requires typing during or just after a ride
- [ ] **18.4** Compare a class you both rode — same class, both traces, one chart. This is the version of a leaderboard that is actually motivating
- [ ] **18.5** Friend leaderboard on the post-ride summary, alongside the rider's own history (11.4.1)
- [ ] **18.6** **The HUD stays social-free.** Nothing on the strip during a ride. It has half a second of attention and it belongs to the interval
- [ ] **18.7** A comparison across riders is honest when both sides are measured watts off their own boards (2.1a), and misleading when either side is modelled. Carry the caveat on the modelled ones specifically rather than on all of them — a blanket disclaimer nobody reads is the same as none
- [ ] **18.8** Mute, block and report exist from the first version that has a feed, not the version after someone needs them

---

## Phase 19: Ideas worth having, ranked

Sorted by value per unit of work. The first group is arguably fundamental and
has simply never been written down.

### 19.1 High value, small
- [x] **19.1.1** **Screen-on lock during a ride** (also 11.3.5) — the tablet sleeping mid-class is a bug the rider experiences as the app being broken.
      *Nothing held the screen awake: no window flag, no wake lock, not even the
      permission. Invisible on the bike because Netflix holds its own, so it
      only bites a rider on Pelonot's own ride screen — or on a podcast, or on
      anything else that does not. Asserted in the two places that are up for
      exactly as long as a ride is: the ride screen's window and the strip's
      overlay window, of which exactly one exists at a time, because the overlay
      stands down while the ride screen is on top. Held through a pause too — a
      rider refilling a bottle has not left. **Observed on the tablet AVD**:
      `SCREEN_BRIGHT_WAKE_LOCK 'WindowManager'` with
      `ws=WorkSource{com.pelonot}` on the ride screen, and still held after
      minimising to the strip with another app in the foreground*
- [ ] **19.1.2** **Auto-pause** when cadence has been zero for ~20 s, and auto-resume on the first tick. Every ride has a bottle stop, and it currently drags the averages down
- [ ] **19.1.3** **Local backup/restore of the database to a file** — the only safety net that exists before 15, and it survives the destructive-migration problem too
- [ ] **19.1.4** **CI**: GitHub Actions running `assembleDebug` and `testDebugUnitTest` on every PR. An open-source project taking contributions without this is asking maintainers to be the build server
- [ ] **19.1.6** **The first run explains nothing.** A new rider is dropped
      straight onto the profile picker; profile creation asks for an FTP with
      **200 prefilled** and no way to find a real one (19.2.3 is the guided test
      and is unbuilt); the overlay permission — the thing the entire product is
      built on — is first mentioned at ride start; and a heart-rate strap is
      discoverable only by opening Settings. None of it is broken, and all of it
      assumes the rider already knows what this app is. The smallest honest
      version: say what FTP is and that a guess is fine and the app will correct
      it (7.1 already does), offer the overlay permission before the first ride
      rather than during it, and mention the strap once
- [x] **19.1.5** **README and CONTRIBUTING** covering the build, the fact that simulated telemetry makes the whole app usable with no bike, and — corrected — that **no jailbreak is needed**. Worth saying plainly that it installs on a stock bike, since that is the difference between a project people can try and one they assume they cannot.
      *Written. Note the item's own premise was wrong: there was no README at
      all to correct — the root prerequisite was being advertised by
      **`ARCHITECTURE.md`**, which opened "bytes arrive from the bike's sensor
      board over a serial character device" and drew `/dev/ttyS2` in its first
      diagram. The correction had been added in §6, 380 lines below the claim,
      where nobody reading top-down would reach it first — and `CLAUDE.md` sends
      every newcomer and every new session to that file before any other. §1a
      now leads with the `SensorService` bind and the serial path is demoted to
      §1a-bis, for a rooted tablet, which is what it is*

### 19.2 High value, medium
- [ ] **19.2.1** **Custom class builder** — build your own intervals in the app. The class library is the subscription's core product and the interval model is already a plain list; this is the feature that makes the app stop needing Peloton at all
- [ ] **19.2.2** **Community class library** — share and import classes. `class_templates` is already a cloud table and already world-readable
- [ ] **19.2.3** **Guided FTP test** — a proper 20-minute protocol with pacing cues, rather than inferring FTP from whatever the rider happened to ride. `PostWorkoutAnalyzer` already does the maths
- [ ] **19.2.4** **Strava upload**, following the `.tcx` export in 12.4.3
- [ ] **19.2.5** **Training load and freshness** over weeks. Flag it hard: built on estimated watts, this is a *relative* trend for one rider and nothing more

### 19.3 Worth doing eventually
- [ ] **19.3.1** Multi-week training programmes
- [ ] **19.3.2** Achievements and streaks (pairs with 16.3.5)
- [ ] **19.3.3** ~~Heart-rate zones and HR-based targets~~ — **moved to Phase 21**, which is what this one line actually is: a profile schema change, a zone model, live display, per-ride tracking and HR-targeted classes
- [ ] **19.3.4** Localisation, once the string catalogue is stable
- [ ] **19.3.5** Wear OS or a phone companion as a second HR source
- [ ] **19.3.6** Opt-in, off-by-default crash reporting. For this audience the default matters more than the feature

### 19.4 Explicitly not doing
- Calorie estimates (13.6) — a nutrition claim the power model cannot support
- Anything that requires a network to start a ride
- Anything on the HUD that is not about the next sixty seconds of pedalling

---

## Phase 20: Who's riding — the profile selector and avatars

The first screen anyone sees, and the one that has had the least thought. It is
also the screen that makes the shared-household story work: a bike in a living
room has three or four riders and picking the right one has to take one glance
and one tap, from two metres away, by someone who has already got their shoes
on.

The obvious reference is a TV streaming app's profile picker, and it is the
right one — same device shape, same distance, same job.

### 20.1 The profile selector

- [x] **20.1.1** **Centre the profiles and make them big.** Today they are
      small cards in a grid pinned to the top-left of a 1920×1080 screen, with
      the rest of it empty. Confirmed by screenshot on the tablet emulator, 31
      July 2026. *Rebuilt as a TV-picker: one centred row of square tiles with
      the heading above it, observed at 1920×1080/240 dpi*
- [x] **20.1.2** Landscape-first, centred both ways, sized off the screen rather
      than a fixed dp — this app runs on a tablet bolted to a bike, not a phone.
      *Tile size is derived from the available width and the number of tiles,
      bounded at both ends: a floor so a household of six stays tappable with
      sweaty hands, and a ceiling so one lone rider does not get a comic 500 dp
      square. The avatar and its initial scale with the tile — a fixed type
      style left a small letter marooned in a large circle*
- [x] **20.1.3** Guest keeps its distinct treatment (6.1) but stops competing
      with the real riders for the eye. It is the exception, not a peer.
      *A peer in layout, deliberately not in weight: riders are filled cards,
      Guest and New rider are outlined, so the eye lands on a real rider
      without having to read anything*
- [x] **20.1.4** "Create a new profile" belongs alongside the riders as one more
      tile, not as a full-width bar at the bottom of an otherwise empty screen
- [x] **20.1.5** Edit and delete a profile from here. Deleting one has to say
      what happens to their rides — `workouts.user_id` is `ON DELETE SET NULL`,
      so the rides survive as unattributed rather than being destroyed, and the
      dialog should say so rather than letting the rider guess. *Press and hold
      a rider. Rename is here because it is the one field Settings cannot
      change; FTP and weight stay there and the dialog says so. Removal reads
      "Their rides are kept — they stop being filed against anyone and stay in
      the history as unattributed."* Deleting the selected profile also clears
      `lastProfileId`, or the dashboard would go on greeting a rider who has
      been removed.
      **One trap worth carrying forward:** `Card(onClick = …)` has no
      long-press, and the first version put `onLongClick` in `semantics` only.
      That is an accessibility action, not a gesture — a real press-and-hold
      fell straight through to the click and opened the dashboard. It needs
      `Modifier.combinedClickable`

### 20.2 Avatars

- [ ] **20.2.1** A checked-in set of avatars to choose from. Licence first:
      whatever is used has to be genuinely open (SIL OFL, CC0 or MIT), credited
      in the repo, and vendored rather than fetched at runtime — the app starts
      a ride with no network and that is not negotiable (19.4). Generated
      identicon-style avatars derived from the profile name are the other
      candidate and have no licence question at all
- [ ] **20.2.2** `profiles.avatar` in Room, behind a real migration (12.5).
      Store a **reference** — a pack id or a relative file path — never image
      bytes in the row: a database that carries photos is a database that
      cannot be exported, synced or backed up cheaply
- [ ] **20.2.3** Pick from the built-in set at profile creation, with a sensible
      default so nobody is forced through a choice to start riding
- [ ] **20.2.4** **Set an avatar from the camera or the gallery on Android.**
      `PhotoPicker` on API 33+ and `ACTION_OPEN_DOCUMENT` below it, so the
      common path needs no storage permission at all. Downscale and re-encode
      on import — a 12 MP phone photo has no business being loaded to draw a
      64dp circle — and write it into app-private storage
- [ ] **20.2.5** Strip EXIF on import, and honour the orientation tag before
      discarding it. A gallery photo carries GPS coordinates, and this one will
      end up synced (15) and possibly visible to friends (17.5)
- [ ] **20.2.6** Avatars appear wherever a rider is named: the selector, the
      dashboard greeting, history, and any leaderboard. Not on the HUD (18.6)
- [ ] **20.2.7** Avatar changes sync with the profile, once 14 and 15 work. A
      custom image is a blob and needs Supabase Storage rather than a column;
      decide deliberately whether it goes up at all before building it
- [ ] **20.2.8** Change your avatar from the companion web app — **much later**,
      and strictly after 17 exists. Listed here so it is not re-invented as a
      separate feature when it is the same field

---

## Phase 21: Heart-rate zones — the metric that is measured for everyone

The app writes a heart rate on every sample it can (2.3.5: 314 rows, 314
readings, no nulls) and does almost nothing with it — a live number, an
average, and a line on the ride detail chart. **Zones are what make a heart
rate mean anything.** They are also the one framing on this bike that does not
depend on the power model: on hardware the watts are measured (2.1a) but on a
simulated ride they are modelled and wrong (2.2.4), whereas a strap on the
rider's chest is measuring the rider either way.

This supersedes **19.3.3**, which was one line in a backlog and is really this
whole phase.

### 21.1 What the zones are computed from

**Ask for the max HR first and the age second.** The app does not want anybody's
age; it wants their maximum heart rate, and age is only a proxy for it — a poor
one, with a 10–12 bpm spread between individuals at the same age, which is wider
than a zone. So 21.1.3 is the primary path and 21.1.2 is the fallback for a
rider who does not know their number. That ordering is both more accurate and
asks less about the person, which is a rare combination and worth taking.

- [ ] **21.1.1** **Date of birth on the profile.** `profiles` today is name,
      weight, FTP and a created-at, so this is a schema change and gets the full
      treatment: a `Migration`, an exported schema in `app/schemas/` and a
      `MigrationTestHelper` test (12.5). A **full date**, and stored as one:
      a date picker is a control everyone already knows, where "what year were
      you born" is an odd field people have to stop and think about. Not an age
      integer, or every rider's zones go quietly stale on their birthday.
      **Nullable** — a rider who does not want to give it gets *no* HR zones
      rather than wrong ones, and with 21.1.3 in front of it many riders will
      never be asked at all
- [ ] **21.1.1a** **Sync the year, not the date** (14, 15). On the tablet a date
      of birth is a fitness input; in a cloud row beside a display name it is an
      identity field, and that boundary — not the collecting of it — is where
      this datum changes character. Only the year has any effect on the maths
      (0.7 bpm per year of age, against a formula whose own error is 10–12), so
      deriving it at the sync edge costs nothing and means the useful part is
      the only part that travels. Decide this **when the DTO is written**, not
      after: "we sync every column in the row" is a default, not a decision
- [ ] **21.1.2** *The fallback.* Estimated maximum heart rate from age, using
      **Tanaka (208 − 0.7 × age)** rather than the folk formula 220 − age, which
      overestimates for younger riders and underestimates for older ones. Say on
      screen, once and plainly, that it is an estimate — and show it updating as
      the rider fills the field in, so it is visibly a fitness calculation and
      not a profile form harvesting a birthday
- [ ] **21.1.3** *The primary path.* **A measured max HR, asked for first.** Any
      age-based formula has a between-individual spread of roughly 10–12 bpm,
      which is wider than a zone — so for a meaningful fraction of riders the
      estimated zones are simply the wrong zones. Let a rider who knows their
      own number type it, and offer the highest heart rate the app has ever
      recorded for them as a starting point (it already has every sample). It
      overrides the estimate wherever both exist
- [ ] **21.1.4** Resting heart rate, if and only if the model chosen in 21.2
      needs it. Do not collect a field nothing reads
- [ ] **21.1.5** Threshold heart rate (LTHR) as the best-quality basis, optional
      and much later. The guided FTP test in 19.2.3 is the same twenty minutes
      of riding, so if that is built, this comes almost free from it

### 21.2 The zone model

- [ ] **21.2.1** A `HeartRateZone` in `domain/`, pure and JVM-tested at every
      boundary, mirroring `PowerZone` in shape but **not sharing its bands or
      its colours**. Five zones is the usual HR convention against seven for
      power, and reusing the power palette would tell a rider that HR zone 4 and
      power zone 4 are the same thing, which they are not
- [ ] **21.2.2** Pick a basis and name it in the UI: %HRmax is simplest, %HRR
      (Karvonen) is better and needs 21.1.4, %LTHR is best and needs 21.1.5.
      One of them, chosen on purpose, stated where the zones are shown
- [ ] **21.2.3** **The boundaries used for a ride are stored with the ride, not
      recomputed on read.** A rider who corrects their max HR in March must not
      silently rewrite what every ride in January said they did. This is the
      same shape as the `avg_*` trap in CLAUDE.md — and the same bug the power
      charts already have, now written up properly as **7.8**. Do the two in one
      migration: they are the same column added to the same table for the same
      reason
- [ ] **21.2.4** Nothing anywhere displays a zone when the heart rate is null.
      Unknown is unknown; this project has already corrupted a rider's record
      twice by treating a missing heart rate as a number

### 21.3 Seeing it during the ride

- [ ] **21.3.1** Current HR zone on the ride screen beside the live bpm — the
      same job 11.6.2 does for power, and worth designing as one thing so the
      screen does not end up with two unrelated zone treatments
- [ ] **21.3.2** On the HUD only if it earns its half-second (11.5.5, 18.6). A
      zone number is arguably a better use of strip space than raw bpm, since
      the rider cannot act on "148" without doing arithmetic first
- [ ] **21.3.3** Honest states for the two riders who have no zones: no strap
      connected, and no date of birth recorded. Neither gets a blank tile, and
      the second gets a way to fix it

### 21.4 Recording and tracking it

- [ ] **21.4.1** Time in each HR zone for a ride, computed from the samples
      exactly as 16.1.4 does for power. With 21.2.3 in place this needs no new
      table — the samples and the boundaries are both already there
- [ ] **21.4.2** Post-ride: an HR-zone distribution beside the power one, and
      the HR trace (16.1.2) banded by zone. Note 16.1.2 deliberately breaks the
      line across gaps; the banding must not paper over them
- [ ] **21.4.3** Weekly time-in-zone as a trend (16.3). This is the number that
      actually drives a training decision — "how much easy riding did I do this
      month" — and it is the honest answer to what the dashboard's progress
      section is reaching for (22.1)
- [ ] **21.4.4** Be careful what a zone summary is allowed to imply. Heart rate
      **lags effort by 30–60 seconds**, drifts upward across a long ride at
      constant power (cardiac drift), and moves with heat, sleep, caffeine and
      illness. Time-in-zone across a 45-minute class is meaningful; the "zone"
      of a 30-second interval is mostly the previous interval's

### 21.5 Classes built on heart-rate zones — worth doing, within limits

**The verdict, since the question was asked: yes — but only for long blocks,
and never as a replacement for the power and cadence targets on short ones.**

For it: this is exactly what current polarised / "80-20" training practice
asks for — most of the work genuinely easy, a little of it hard — and the
entire difficulty of riding easy is that riders overshoot when they are chasing
watts. A *ceiling* is what a zone-2 ride needs, and a heart rate measured off
the rider's own chest is a more trustworthy ceiling than an uncalibrated power
model (2.2.4).

Against it: the 30–60 second lag in 21.4.4 makes a short interval untargetable
by heart rate — the rider is out the other side of a 40-second surge before the
number arrives. And it depends on a strap, which is optional hardware that can
drop out mid-class.

- [ ] **21.5.1** An interval may carry an HR-zone target **as well as**, not
      instead of, its power and cadence targets. `Interval` is a serialised
      model and `intervals_json` is snake_case with `@SerialName` matching the
      assets exactly — a new optional field has to land on both sides (the
      bundled assets *and* the cloud `class_templates`). This is precisely the
      shape of the 14.2.2a defect: a decode mismatch throws, the sync reports
      failure, and the app quietly falls back to five bundled classes with
      nothing wrong on screen and nothing in the log
- [ ] **21.5.2** A minimum interval length before an HR target is even allowed —
      on the order of three minutes. Below that the target is power and cadence,
      enforced in the model rather than left to whoever writes a class
- [ ] **21.5.3** An HR-targeted class **requires a connected strap**: say so
      before the ride starts rather than thirty seconds in, and if the strap
      drops mid-ride fall back to the interval's power/cadence target rather
      than showing a rider nothing to aim at
- [ ] **21.5.4** "Zone 2 base" is the obvious first class: one long block, one
      target, and the app's whole job is to keep saying *ease off* — the cue
      riders most need and least want to hear
- [ ] **21.5.5** Wherever a zone came from an age estimate rather than a
      measured maximum (21.1.2 vs 21.1.3), the class says so. Prescribing effort
      from a formula with a 12 bpm spread is fine; doing it silently is not

---

## Phase 22: The dashboard — the first screen, and the least considered

Two separate complaints, both raised from riding the app: the progress section
does not show progress, and the layout is stretched across a screen it should
be *using*.

### 22.1 "Your Progress" shows no progress

As it stands the section is a heading, the subtitle "Track your performance over
time", and two cards: today's output in kJ and the last ride's output in kJ.
Nothing there is a trend, nothing is compared to anything, one of the two is
usually 0.0, and both are the same quantity on the same axis. The honest empty
state (see *Corrections*) fixed an outright lie — it used to show hardcoded
figures on a device that had never recorded a ride — without making what
replaced it mean anything.

- [ ] **22.1.1** **Decide in one sentence what the dashboard is for**, and let
      the section follow from that rather than from what fits. The candidate
      answer: *"should I ride today, and what should I ride?"* — anything that
      is really "what have I done" belongs to history (12) and trends (16.3)
- [ ] **22.1.2** Replace the two kJ cards with **consistency**: rides this week
      against the rider's own recent norm, and the calendar heatmap (16.3.5).
      What gets somebody onto a bike is a streak they do not want to break, not
      a kilojoule total they cannot interpret
- [ ] **22.1.3** **A trend that is genuinely a trend** — output or minutes per
      week over the last six to eight weeks, sparkline-sized. The history query
      already returns what this needs
- [ ] **22.1.4** FTP, with the date it last changed and what changed it
      (7.10.2, 16.3.1). The app already computes this (7.1) and it is the
      closest thing to a real progress number it owns — but it currently keeps
      only the latest value, so the history this card wants has to be recorded
      first (7.9)
- [ ] **22.1.5** A **last ride** card that opens the ride detail (12.2) —
      class name, RPE, and whether it beat the rider's own previous ride of the
      same class, which `leaderboardFor` already computes and nothing renders
- [ ] **22.1.6** Personal bests (16.3.3), suppressed entirely until there are
      enough rides for them to be true. A "best" computed from one ride is noise
      wearing a trophy
- [ ] **22.1.7** Every figure here has to be honest about whose watts it is
      (16.1.6). A rider who moved from simulated to hardware telemetry gets a
      step change in their own history, and an unexplained cliff in a progress
      chart reads as the app being broken
- [ ] **22.1.8** Rebuild `DashboardStats` and the dashboard ViewModel around
      whatever 22.1.1 decides, rather than bolting cards onto the current two
      totals. Keep every query windowed the way 12.1.6 does — this is the first
      screen after profile selection and it must never touch `workout_metrics`

### 22.2 A tablet-shaped dashboard

**Read 11.3.1 first, and note it is not wrong.** It says the dashboard fills
the width with no dead right-hand side, re-checked twice on the bike and once
on a matching AVD, and that is true. The complaint here is the opposite
failure: a single column of full-width cards *stretched* across 1280 dp. A card
1200 dp wide with a two-word label in it is harder to read than the same card
at 600 dp, and the screen is big enough to be showing more than one thing at a
time.

- [x] **22.2.1** Cap the main column and centre it — on the order of 700–800 dp
      — so a card reads as a card rather than a band across the room. Measured
      on the 1280 × 720 dp AVD from `HARDWARE.md`, never on a phone.
      *Capped at 760 dp. A **maximum**, not a width, so nothing changes below
      the breakpoint. The rails it opens up are deliberately left empty —
      22.2.2 and 22.2.3 are the decision about what goes in them, and filling
      them card by card is exactly what 22.2.3 says produces three columns of
      unrelated things. **Observed on the tablet AVD**; 22.2.5's check on the
      bike itself is still owed*
- [ ] **22.2.2** Then use the two rails that opens up **deliberately**, rather
      than leaving symmetrical dead space: for instance who is riding and
      today's context on one side, the last ride and the streak on the other.
      The rails exist only in landscape and must fold back into the column below
      a breakpoint
- [ ] **22.2.3** Decide the three regions as one layout — what the middle is
      for, what a rail is for, and what a rail does when it has nothing to say
      (it disappears; it does not show an empty card). Doing this card by card
      produces three columns of unrelated things, which is worse than one
- [ ] **22.2.4** The same question applies to Settings and History, which are
      also full-width cards on a wide screen. Do the dashboard first and find
      out whether the answer generalises before rolling it out
- [ ] **22.2.5** Verify against the real system furniture — a 48 dp bottom
      navigation bar and no top status bar (`HARDWARE.md`) — and on the tablet
      itself before ticking anything here

### 22.3 Small things on the dashboard

- [x] **22.3.1** **"Good morning" was a string literal**, shown at every hour
      of the day — cheerfully wrong for two thirds of it, on a bike that mostly
      gets ridden in the evening. It reads the clock now, with a fourth case
      for the small hours because somebody riding at 2 a.m. is not having a
      morning. Read once per composition rather than from a flow: nobody's
      evening turns into night while they look at this screen. *Observed on the
      tablet AVD at 23:42: "Good evening,"*

---

## Phase 10: Hardware Validation — partly done, 31 July 2026

Done on a real Gen 1 (`PLTN-RB1VQ`) over wireless adb, with a rider pedalling.
Note that the bike is **stock, not jailbroken**, which turned out to be the
finding rather than an obstacle — see 2.1a.

- [x] **10.1** Sensor board device path confirmed: `/dev/ttyO0`, `system:system`
      `0660`. **Not readable by this app, and not made readable** — there is no
      root on a stock bike. `/dev/ttyS1` does not exist and `/dev/ttyS2` is the
      Bluetooth UART
- [x] **10.2** Moot as written, and answered: the raw byte protocol never
      reaches us. Peloton's service owns the port, decodes the packets
      (`F5,41,36,F6` for RPM, `F5,44,39,F6` watts, `F5,49,3E,F6` resistance,
      19200 baud) and hands over a float. `SerialProtocolParser` is unexercised
      on this hardware and stays only for the rooted-tablet path
- [x] **10.3** Superseded for real rides: the board reports watts directly, so
      there is nothing to calibrate on the bike itself. The remaining
      calibration question is about simulated rides — see 2.2.5
- [x] **10.4** **HUD renders over a video app** — verified over Netflix on the
      bike, 31 July 2026. Full-width strip docked to the top edge, middle of
      the screen clear, every figure live (cadence 84, resistance 32, power
      71 W, heart rate 98, interval countdown and next-interval preview). The
      overlay window is present as an `appop=SYSTEM_ALERT_WINDOW` window and
      never takes focus from the video app. Overlay permission turned out to be
      granted already
- [x] **10.5** **BLE strap connects and streams** — Wahoo TICKR FIT, found and
      connected on the first scan, a heart rate on all 314 rows of a ride. See
      2.3.5 for the two manifest and UI defects that had to be fixed first
- [ ] **10.6** Full-length ride: battery, thermals, memory, no dropped samples.
      The longest run so far is 8 minutes

> **Screenshots do not work over a playing film.** Netflix's player sets
> `FLAG_SECURE`, so `adb exec-out screencap` returns an empty image and the HUD
> cannot be captured over DRM video — it captured fine over Netflix's own
> non-secure PIN dialog. Anything about readability over moving video (11.1b.2)
> has to be judged by the rider's eyes, not from a screenshot. Likewise a
> spoken cue lasts a second or two, so polling `dumpsys audio` every five
> seconds slides straight past the duck; the rider hearing it is the
> measurement.

---

## Quick Reference: Coggan 7-Zone Power Model

Bounds are **contiguous and half-open** (`lower ≤ pct < upper`). The published table quotes whole percentages, which leaves unassigned gaps between zones; modelling those literally means a rider at 55.5% of FTP matches no zone at all.

| Zone | Name | % of FTP | Training purpose |
|------|------|----------|------------------|
| Z1 | Active Recovery | < 56% | Warmup, cooldown |
| Z2 | Endurance | 56–76% | Aerobic base |
| Z3 | Tempo | 76–91% | Aerobic efficiency |
| Z4 | Lactate Threshold | 91–106% | Sustainable hard effort |
| Z5 | VO2 Max | 106–121% | Max oxygen uptake |
| Z6 | Anaerobic Capacity | 121–151% | Short power bursts |
| Z7 | Neuromuscular Power | ≥ 151% | Explosive sprints |

## Quick Reference: Ride Intent

`P_target = FTP × zone% × k`

| Intent | `k` | Effect |
|--------|-----|--------|
| Reach New Milestones | 1.05 | Targets 5% higher |
| Just Stay Fit | 0.95 | Targets 5% lower |
