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

**How a session handles it.** Read this section before picking work. Take an
entry, decide where in the plan it belongs, write it up as numbered items with
the reasoning kept rather than summarised — and then **empty the entry out of
this section**. An idea still sitting here has not been dealt with; an idea that
has moved has.

**Emptying it is urgent; building it is not.** The owner set this rule directly,
in the twentieth sitting: *"don't necessarily action them first. They should
have plan entries created and then triaged with just the same weighting as any
other plan items."* So an inbox entry jumps the queue **into the plan** and then
takes its place in it — what it buys is a written-down item with the reasoning
attached, not a promotion past work that matters more. (This replaces the older
line that said the inbox outranks *What to do next*.)

*Empty. Five entries have passed through it. Standing and seated riding is now
**Phase 25**, built but for one judgement that needs a rider (25.3.4). The two
left in the eighteenth sitting, and the two left in the nineteenth, are written
up with the owner's words kept verbatim at the head of each:*

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

*Both of the nineteenth sitting's entries are written up, with the owner's words
kept verbatim at the head of each:*

- ***In-ride targets** → **11.7**. The owner's question — "do i focus on zone,
  cadence, or resistance?" — is the right one, and the screen does not answer
  it. It is not three targets: it is one **outcome** (power) and the two
  **controls** that produce it, drawn as three tiles of equal weight.
  Measuring the library moved most of the question: all 1071 intervals
  prescribe both a zone and a cadence band, so nothing today can tell a cadence
  **instruction** from a cadence **suggestion** — but the catalogue already
  knows the difference and cannot say it, with 574 blocks in the two neutral
  bands and 231 out in the tails where cadence plainly is the exercise. **11.7.2 is
  the owner's decision** (derive it, or name it in the catalogue — the
  recommendation is to name it, and the argument is 25.4.2's). **11.7.1a is a
  defect found on the way and worth fixing whichever way that goes**: amber
  fires on every metric equally, so a threshold block tells a rider spinning a
  perfectly good 92 rpm that they are wrong.*
- ***Resume session** → **8.3d**. The owner wants an interrupted ride resumed,
  not merely kept — which **contests 8.3a**, so its reasoning had to be
  answered rather than overruled. It does not survive: the gap it calls "of
  unknown length" is arithmetic off the row and the last sample, and
  `elapsedSeconds()` has excluded paused time since Phase 3, so `timestamp_sec`
  was never seconds-since-start but **seconds of riding**. **A crash is a pause
  nobody got to press.** 8.3a's concern is kept rather than discarded, as
  8.3d.2: the break becomes a fact on the row, because a contiguous series
  cannot show it and `was_recovered` means the other thing.*

*The twentieth sitting's five ride-screen snags are written up and the entries
are out. **The owner also set the rule for this section itself** — verbatim:
"even though you read my comments as one of the first things you do, don't
necessarily action them first. They should have plan entries created and then
triaged with just the same weighting as any other plan items." That is now how
CLAUDE.md reads: the inbox is emptied first, the **ordering** is argued
afterwards on the merits. Where the five went, with the owner's words kept
verbatim at the head of each:*

- ***Powerzone indicator** → **11.6.11**. The bounce is not the animation, it is
  the quantity: `fractionThroughZone` is per-zone, so crossing a boundary drives
  the fill backwards across a whole segment before it grows again. One
  continuous coordinate across all seven rungs, monotonic in power, and the
  boundary stops being an event.*
- ***Output in watts** → **11.6.12**. The call is the owner's and stands. The
  live tiles already round; the number with the tenth on it is **OUTPUT**, and
  it is the one that clips its own unit label as the kilojoules reach three
  digits.*
- ***Heart rate pulse** → **21.3.4**, and it needs nothing else in Phase 21, so
  it is the half of the heart-rate note that can land now. Two rules make it
  honest: the period is the live bpm, and it stops when the reading does.*
- ***Heart rate zones** → **21.3.1**, and the honest answer is that it is **not**
  already covered: the app has no maximum heart rate for anybody, so it has no
  boundaries to colour between. Cheap once 21.1 asks the question; inventing the
  number instead of asking is what 21.1 exists to refuse.*
- ***Beginning of ride countdown** → **11.6.13**. Ten seconds, skippable. The
  thing to get right is that it sits **before** `startRide` — a curtain over a
  ride that has already begun moves the defect rather than fixing it, because
  the clock, the first interval and the recorder are all behind it.*

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

### Latest session — 3 August 2026 (nineteenth sitting): a crash is a pause nobody got to press

**The owner left two notes in the inbox and went for a ride**, which set the
session: no rider to ask, and the bike itself off limits because they were on
it. Both notes are written up, the inbox is empty, and one of the two is built
and observed on the tablet AVD. 498 JVM tests, 0 failures.

**The one that got built was the one that argued with the plan.** The owner
wants an interrupted ride *resumed*, not merely kept — and **8.3a had already
decided against exactly that**, in writing: offering resume would "splice a gap
of unknown length into the record". A note from the owner outranks the
ordering, but it does not outrank a reason, so the reason had to be checked
rather than waved past. It did not survive, in two independent ways.

The gap is **not of unknown length**. It is `(now − workouts.timestamp) − the
last recorded second`, three numbers the app already has, and an app that can
measure a thing is not entitled to call it unknown. That is `RideInterruption`,
which is pure and tested so the judgement in it can be argued with in a test
rather than on a bike.

And the deeper one: **`elapsedSeconds()` has subtracted paused time since Phase
3.** `workout_metrics.timestamp_sec` has therefore never meant *seconds since
the ride started* — it means **seconds of riding**. A rider who pauses for five
minutes already leaves no hole in the series and nobody has ever called that
dishonest. **A crash is a pause nobody got to press.** Resuming at the last
recorded second is not a new claim about the record; it is the claim the record
has been making all along.

What 8.3a was *right* about survives rather than being discarded. Because the
series comes back contiguous, nothing in it can show that anything happened, so
the break becomes a fact on the row — `resume_count` and `interrupted_sec`,
migration 10 → 11. Both `NOT NULL DEFAULT 0`, and the contrast with 9 → 10 is
the whole reasoning: `synced_at` was left null because a default would have
**claimed** something untrue, and zero here claims only what is certain — no
ride already on a tablet was ever resumed, because resuming did not exist. **A
default is safe exactly when it states a fact rather than a guess.**

**Three defects came out of driving it that reading the diff had not found, and
the third is the one worth carrying.** A resumed class called itself a free ride
in its own subtitle; the zone ladder drew every boundary at 0 W. Both were
visible in a screenshot. The third was not visible anywhere: **`stopWorkout`
finalises a ride by building a fresh `WorkoutEntity` out of `WorkoutSession`**,
so every column the session does not carry goes back as its default — and the
resume that had been stamped on the row correctly was **overwritten with zero
when the ride ended twenty minutes later**. A ride observed to resume twice sat
on disk claiming it had been ridden straight through, with nothing wrong on any
screen. It is 7.10.3 again: two writers, one row, the later one holding a stale
copy of a field it does not know exists. Found the way that one was — build the
feature that records the data, then **look at the data**. The rule it leaves
behind is now in CLAUDE.md.

The database is what closed it, not the screenshots: **332 samples, 332 distinct
seconds, 1 to 332, no gaps and no duplicates** across two resumes, and
`avg_power` on the row agreeing with `AVG(power)` over that ride's own samples
to two decimal places **across a resume boundary** — which is what proves the
running means were carried forward at the sample counts they were built at.

**The second note is a design question and is deliberately not built** — the
owner asked for a suggestion, and there was no one to give it to. *"What do I
do? Do I focus on zone, cadence, or resistance?"* It is **11.7**, and measuring
the library moved most of it off opinion: all **1071** intervals in the 72
bundled classes prescribe both a zone and a cadence band, so nothing today can
tell a cadence *instruction* from a cadence *suggestion* — while **574** blocks
sit in the neutral 75–85 / 80–90 bands and **231** are out in the tails at
50–70 or 105–125, where cadence plainly *is* the exercise. **The catalogue
already knows and has no field to say it in.** The framing that follows: these
are not three targets but one **outcome** (power) and the two **controls** that
produce it, drawn at equal weight — and the third of them, resistance, is not
prescribed by any class at all. It is inverted out of `PowerModel`, whose
shipped curve is **66% out at the median**. The least trustworthy number on the
screen is presented with the most authority.

**11.7.2 is the owner's to decide** and the recommendation is written down:
name the governing metric in the catalogue rather than infer it from the band,
because deriving intent from a number is the shape that has cost this plan the
most, and because a heuristic cannot express the case the owner explicitly
asked about — a block that genuinely wants both. **11.7.1a is a defect found on
the way and worth fixing whichever way that goes**, and it was seen live during
this session's own test ride: amber fires on every metric equally, so an
endurance block told a rider spinning a perfectly good 92 rpm that they were
wrong about something the class was not asking for.

Nothing was installed on the bike. The owner was riding it.
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
| 2 | **14.2.1a** apply `003`, **15.5.4** verify it from a second account | The only step in this list where being wrong is a **breach** rather than a bug. Nothing else may go online first, and "the SQL looks right" is not the check. **The wipe is authorised** (owner, 3 Aug); run `003` *before* 15.1, not after. And the endpoint now has friends on it, so this is other people's data |
| 3 | **15.1** auth, **15.2** identity, **15.3** sync both ways | The phase that unlocks everything. 15.3.1 is mostly built already — it is 14.2.6's drain with a sign-in trigger on it |
| 4 | ~~**14.2.3** sync state in Settings~~ | **Done in the same sitting**, because it belongs before riders trust the thing rather than after. Two of three states seen on the AVD; the failing one is tested and will be seen for free the first time 14.2.1a's endpoint refuses something |
| 5 | **20.3** the initial FTP, **22.4** use the width | The owner's own two, and 20.3's own words are that the current shape **cannot go into production**. Onboarding is the first thing a new rider meets and the online tier is what brings new riders |
| 6 | **17** the web app, **18** social across bikes | Last, and in that order: the bike's tablet is a bad place to type, so the web app is the natural home for friend requests and display names, and 18 is those features arriving back on the bike. **Read Phase 24 first** — the household half is built, needs no account from anybody, and 18.9 says every screen here is built *on top of* its 24 equivalent rather than beside it |

Two things that are **not** blockers and were checked rather than assumed: the
payload format is settled and versioned inside itself (14.4, incl. 14.4.7's
provenance), so 17.3 has something stable to read; and household social (24) is
complete enough that 18 has a floor to build on.

**14.10.4 was that list's one owner-blocker and it is now answered.** There is
no community endpoint: this build points at the owner's household project
through env vars, with one or two friends on it, and volume is not a concern at
that scale. `cloud.properties` still ships empty and `CloudConfigFenceTest`
still keeps it that way — the reason is now that the endpoint is *private*
rather than that the decision is *pending*. **Retention (23.4) came with the
answer** and is real work now rather than deferred; read 23.4.8 before starting
it, because trimming silently degrades personal bests until 16.3.3a lands.

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

| ~~**8.3d** Resume an interrupted ride~~ | **Done and observed over two resumes of one ride.** The owner's, and it contested 8.3a — whose reasoning did not survive: the gap it called unknown is arithmetic, and `elapsedSeconds()` has excluded paused time since Phase 3, so **a crash is a pause nobody got to press**. 8.3a's concern is kept as 8.3d.2 rather than discarded. It also turned up the rule now in CLAUDE.md: **the finalise writes defaults over any column `WorkoutSession` does not carry** |

**Three of the owner's own are open, and all three want the owner rather than a
session:**

| Next | Why now |
|------|---------|
| **11.7.2** Which metric governs a block | **The nineteenth sitting's write-up of the owner's own question**, with the recommendation made and the measurements behind it (1071 intervals, 574 neutral, 231 in the tails). Derive it or name it in the catalogue — naming it is recommended, and the argument is 25.4.2's. Read 11.7.1 first; it reframes the question |
| **11.7.1a** Amber on a metric nobody asked for | **Not blocked on 11.7.2 and worth doing either way.** `MetricStatus.isOffTarget` fires on every tile equally, so an endurance block tells a rider spinning a good 92 rpm that they are wrong. Seen live in the nineteenth sitting's own test ride. Small, and it removes most of the felt confusion on its own |
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
| 8 | Polish, testing, edge cases | 🔶 Functional items done; cosmetic backlog remains. **8.3d is closed: an interrupted ride can be resumed, not merely kept** — the owner asked for it and it contested 8.3a, whose reasoning did not survive being checked (the gap is arithmetic, and `timestamp_sec` has meant *seconds of riding* since Phase 3). The break is written down rather than smoothed over — `resume_count` / `interrupted_sec`, migration 10 → 11 — because a resumed series comes back contiguous and cannot show it. Observed on the tablet AVD over two resumes of one ride, with the series and the row's own averages cross-checked against the samples. It also found the defect in 8.3d.4 that **the finalise writes defaults over anything `WorkoutSession` does not carry**, which is now a rule in CLAUDE.md |
| 9 | Ride integration | ✅ Complete — a class runs |
| 10 | Hardware validation | 🔶 A **full 20-minute ride is done** — and it is what found 2.7. 10.6's remaining questions (battery, thermals, memory) are unanswered because the ride's telemetry was the story |
| 11 | **HUD-first experience — the current priority** | 🔶 11.1 and 11.1a complete; volume (11.5) done. The HUD is now chips on a transparent band with the timeline on the opposite edge (11.1b.1, 11.1b.2, 11.1b.7); resizing and side docking (11.1b.3–11.1b.5) and the rest of 11.2 remain |
| 12 | Ride history & the rider's own record | 🔶 History, detail, delete and migrations done; export and housekeeping remain |
| 13 | Units and display preferences | ✅ Complete — miles, and the locale default that goes with them |
| 14 | Cloud sync that actually reaches the cloud | 🔶 **A row knows whose it is now (14.2.1)** — every ride the app ever uploaded arrived anonymous, and `profiles` was keyed by a per-device autoincrement, so the second bike to sign in would have overwritten the first rider's profile rather than creating its own. `profiles.id` **is** the auth user id; `CloudAccess.accountIdFor` answers the gate and the identity in one lookup because they are one question. **And the app knows what it has not backed up (14.2.4–14.2.6)**: `synced_at`, not backfilled, with the worker draining a profile's backlog oldest-first so a ride that exhausts its retries is still in the queue rather than lost. What is left is **14.2.1a** — `003_cloud_identity.sql` is written and not applied — — and **Settings now says whether the rides are actually arriving (14.2.3)**, which is the item that would have caught all three of the defects in 14.0 the day they appeared. **14.10.4 is closed by the owner**: there is no community endpoint to fund — this build points at their household project through env vars — so `cloud.properties` stays empty for the stronger reason that the endpoint is *private*. Otherwise: built and **gated shut** — every call goes through `CloudAccess` and no profile has an account, so nothing reaches the cloud until Phase 15 exists. 14.1.6's sighting is still missing and is no longer drivable from the app. **The endpoint is configurable from a clone now (14.10)** — checked-in `cloud.properties`, empty and fenced that way. **The payload format is changed (14.4)** while the cloud still held one row: columnar, versioned inside itself, 228 KB → 49 KB measured — 54 KB since provenance joined it, which is **14.4.7 closed**: `pm` is per sample, because a scalar on the row would have to pick a side in a ride the board dropped out of |
| 15 | Accounts, login and multi-device sync | ❌ Not started, but **no longer starting from nothing** — the eighteenth sitting built the two things underneath it. `profiles.id` **is** the auth user id (14.2.1), so signing in needs no cloud-id round trip; and 15.3.1's first-sign-in backfill is 14.2.6's backlog drain with a trigger on it. **15.5 is written in `supabase/003_cloud_identity.sql` and deliberately unticked**: the policies exist in a file, and 15.5.4 says they are verified from a second account, which reading the SQL is not. `auth_user_id` is still the gate and still nothing sets it |
| 16 | Data visualisation | ✅ **Complete.** Post-ride charts done, the power caption says where the watts came from (16.1.6), and every trace now carries a scale decided once for all four (16.1.7 / 16.1.8). **The first trend is built (16.3.1)** — FTP over time on its own screen, with the ride behind each change one tap away — which also settles where a trend lives. **Three more landed in the seventeenth sitting**: the prescribed cadence finally has a chart (16.1.5a), weekly volume and the ride-day calendar share a second screen — *Your riding* (16.3.2, 16.3.5) — and a ride can be drawn against the rider's own previous best at the same class (16.3.4). **Phase 16 is complete**: 16.3.3 is mean-maximal power on *Your FTP*, measured rides only, with a gap breaking the window. What is left is **16.3.3a**, the scan's ceiling |
| 17 | Companion web application | ❌ Not started, and **now sixth on a written road rather than an undated *nice to have*** — the owner named it as a destination in the eighteenth sitting. Still account-tier only: a household-only profile does not exist in the cloud and never appears there, which is 17.10's copy problem with a data-model cause. It reads `metrics_payload`, which is settled and versioned inside itself since 14.4, so it has something stable to build against |
| 18 | Social **across bikes** — the networked tier | ❌ Not started, and it sits on 15. **Phase 24 is the half that does not, and it is largely built** — which is 18.9's whole point: every screen here goes *on top of* its 24 equivalent rather than beside it, or one of the two leaderboards drifts and it will be the one nobody rides against |
| 19 | Ideas worth having, ranked | 🔶 Mixed, and not untouched: screen-on lock, auto-pause, local backup/restore and the README are done (19.1.1–19.1.3, 19.1.5), and **CI is written and waiting on its first green run** (19.1.4) |
| 20 | Who's riding — profile selector & avatars | 🔶 Selector rebuilt for the tablet (20.1, incl. rename/remove); avatars (20.2) not started. **20.3 is new and is the owner's**: profile creation asks a rider for their FTP in a text box prefilled with `200`, which by their own words **cannot go into production**. The constraint that makes it interesting is that the app cannot simply stop having a number — FTP is the denominator of the whole zone system and is written onto the ride at its start |
| 21 | Heart-rate zones | ❌ Not started — *the one metric that is measured for every rider whatever the power model does* |
| 22 | The dashboard | 🔶 **A *This Week* card now opens the progress section** — rides, minutes and the streak, and the door to *Your riding* (16.3.2/16.3.5). It is the number **22.1.2** has been asking for since the sixth sitting, in the place it asked for it, though that item is still open: the two kJ cards below it are unchanged. **The FTP card is now a progress card (22.1.4)** — the number, a stepped sparkline of every value it has held, and how far it moved and who moved it. That is the first thing in the section that is a trend rather than a total; the two kJ cards below it are still what they were (22.1.2). The width cap is a theme token applied across the app rather than one screen's fix (22.2.6); what goes in the rails it opens up (22.2.2, 22.2.3) is still undecided |
| 23 | Offline by default — making the ungated tier complete | 🔶 **Retention (23.4) is no longer deferred** — the owner asked for old rides condensed to their aggregates rather than kept sample by sample, which is 23.4.2 as written. The design was already right; what is new is **23.4.8**, a hard prerequisite: personal bests are re-scanned from every measured ride's samples on every load, so trimming would silently make a rider's bests worse until 16.3.3a stores them per ride. Calibration is unaffected — checked, not assumed. **The consent gate (23.1), the class library (23.2) and the backup reminder (23.3.1) are done and observed** — rule 1 is true rather than intended, the 72 classes are designed rather than generated (23.2.6) and reach an already-seeded tablet by reconcile-and-retire (23.2.6c), and the offline rider is now told when ten rides have gone by unprotected. The cloud as an update channel (23.2.3/23.2.4) and retention (23.4, deliberately not yet) remain |
| 24 | Household social — the tier that needs no cloud | 🔶 **24.1, 24.2 and 24.3.1 built and observed** — the per-class board, the household's week with streaks and an opt-out, and a housemate's trace drawn behind your own on ride detail. What remains is **24.3.2**, the live pace target during a ride, which is a ride-screen design problem rather than a data one |
| 25 | Out of the saddle | 🔶 **The field, the ride screen, the spoken coach, the overlay's cue and the library's own use of it are done and observed (25.1–25.4.2).** The titles no longer claim a position the intervals do not give. What is left is how the cue reads over a playing film (25.3.4, needs the rider). **25.4.3 is closed**: the two near-twins the rename exposed are separated by their work as well as their titles, as `SWT-13` rather than an edited `SWT-05` — the id is the foreign key |
