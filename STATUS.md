# Where Pelonot is

**Written 4 August 2026, updated the same evening (twenty-eighth sitting).**
Measured, not estimated: `assembleDebug` passes, **606 JVM tests and 62
instrumented tests, 0 failures**, and **465 of 690 plan boxes** are ticked
across 27 phases. It is a summary — every claim below belongs to a phase file and
names the item, so the reasoning is one hop away in [PLAN.md](PLAN.md) and
[plan/](plan/). Nothing is decided here.

> This page exists because the plan answers *why* extremely well and *where are
> we* not at all (19.1.7). It is rewritten by whichever sitting changes the
> picture rather than patched, so treat the date above as its shelf life.

---

## The one-paragraph answer

**The bike half is finished and the cloud half is a week old.** A rider gets on
a stock Peloton, picks a profile, starts one of 72 designed classes, watches
Netflix with a translucent overlay of their own numbers on top, and ends up with
a per-second record that is theirs — measured watts off the board, not estimated
— with charts, an FTP that corrects itself, heart-rate zones, a household
leaderboard and an export. All of that is built and has been observed working on
the real hardware. What arrived in the last two sittings is everything *off* the
bike: accounts, a cloud backup that has been seen making the round trip, a
companion web app that is now hosted, sign-in by scanning a QR code with a
phone, and one leaderboard with everybody on it. That half works and is thin —
it has been ridden by one household for two days, and the first thing the owner
tried on it was broken. **The gap between here and finished is not features. It
is a handful of first-run and honesty problems that any new rider would meet in
their first ten minutes**, plus one setting on the Supabase dashboard that is
currently open and should not be, and one deploy that has not been run.

---

## The five tiers, and where each one stands

| Tier | What it is | State |
|------|-----------|-------|
| **1. The ride** | Telemetry, the service, classes, the overlay, the ride screen | ✅ **Done and ridden.** The one open defect family is the sensor board's serial port (2.7d), which is Peloton's leak and not ours |
| **2. The record** | History, charts, FTP, heart-rate zones, export, migrations | ✅ **Done**, bar the cosmetic backlog and one deferred retention decision |
| **3. The household** | Profiles, the household leaderboard, ghosts, streaks | ✅ **Done**, and the live ghost landed with it — you can now race a housemate's ride, or your own best, *while* you ride. Two corners owed: the *they finished* state and the gap watched moving under a real rider |
| **4. The cloud** | Accounts, backup, the web app, the everyone-leaderboard | 🔶 **Working end to end, two days old, and already caught out once.** Round trip observed, RLS verified from a second account, web app hosted — and the first flow the owner tried on it was broken, because a fix had never been deployed (17.16.6). **That is deployed and verified now (17.16.8)**, and the shape of the lesson stayed: what caught it was a command that diffs the internet against the repo, not the fix itself. Sign-out, account deletion and pull-to-a-new-device are not built |
| **5. Ready for someone else** | First run, onboarding, CI, the polish backlog | 🔶 **The onboarding gap is closed.** The first thing a new rider meets is a designed screen rather than three text boxes: 20.3 asks a name, a weight, a birth year and one sentence about your riding, estimates an FTP rather than demanding one, and now offers to back it up by account (15.8) rather than leaving that for Settings to mention to nobody. What is left is the overlay permission still being explained only at ride start (19.1.6) and a green CI run (19.1.4). See *How close to done*, below |

---

## What is built

**The bike, and it is the part nobody should have to think about again.**
Telemetry comes from Peloton's own sensor service on a **stock, un-jailbroken
tablet** (2.1a) — no root, no serial port, no hardware mod. The board's own
self-identifying frame decides which metric is which, which is the fix for
**2.7**, the worst defect this project has had: the service labels its replies
by position in its request cycle, so 55 of 204 messages arrived mislabelled and
a stationary rider was recorded at 544 rpm. 1609 + 464 messages have since been
captured with zero mislabels. Underneath it sits a fence that **rejects rather
than clamps** — an impossible value becomes a gap, and it takes its neighbours
with it, because a power of 37 W filed as 37% resistance breaks no bound anyone
can write.

**A ride.** A foreground service, per-second recording into Room, auto-pause
when the pedals stop, a screen-on lock, and a ride that can be **resumed** —
after a crash (8.3d), and now also after being **ended by accident**, which is
the same machinery meeting a finished ride for the first time (12.6.2). It opens
on a ten-second countdown, and the overlay permission is asked for *inside* that
countdown with the count held while the rider is away answering it (11.6.14) —
before that, the first ride anybody took landed on a modal the moment the clock
started. On the bike the watts are *measured by
the board*; the modelled curve only ever drives the simulator and a suggested
resistance band, and the app never presents a modelled watt as a measured one.

**The overlay.** Translucent chips docked to a screen edge over Netflix or
anything else, collapsing to a single pill, with a spoken coach that ducks under
the film instead of shouting over it. This is the product's whole reason to
exist and it works on the real tablet.

**Classes.** 72 of them, **generated from a catalogue by a build that refuses to
emit a session breaking a design rule** (`classlibrary/`), bundled in the APK,
and reconciled onto an already-seeded tablet by retire-rather-than-delete so a
ride never loses the class it points at. **A class is now drawn as a shape
before it is ridden** (22.7.2) — height for zone, width for time, so a long
threshold block and a set of four repeats look like the different workouts they
are, with a sentence over it that agrees with the class's own title.

**The record.** History, ride detail, delete, CSV and TCX export, explicit Room
migrations with an exported schema and a test each, and a local backup/restore
through the system file picker. Charts: power against the rider's zones, heart
rate drawn only where a strap was reporting **and banded by the rider's own
heart-rate zones** (21.4.2), cadence against the class's prescribed rpm, the
ride against your own previous best at the same class, and mean-maximal power by
duration. **The same charts appear on the post-ride summary and on a ride from
March**, out of one component — they are the same ride, and the only differences
left are the three things that are true only tonight (12.6).

**FTP that corrects itself** — detected from a ride, proposed rather than
applied, declinable in a way that stays declined, reversible in one action that
appends rather than erases, never proposed from simulated watts, and recorded
onto the ride so a later change cannot silently redraw history.

**Heart-rate zones** built on the rider's own maximum, with Tanaka as a labelled
estimate and every column nullable, because a default maximum is a guess about
somebody's body. **The maximum a ride was ridden at is recorded onto the ride**,
for the same reason its FTP is: a rider who measures a real 186 in September
must not silently redraw every ride they did in August (21.2.3).

**The household.** A profile selector built for the tablet, a per-class
leaderboard, the household's **last 30 days** with streaks and an opt-out — a
week was the wrong window, and it did not merely look wrong: a housemate riding
once a week was absent from the board rather than shown with a zero (22.5.4) —
and a housemate's
trace drawn behind your own — **all of it a Room query, none of it touching the
network**, which is rule 3 of the connectivity model.

**And racing one of them live (24.3.3–24.3.9).** Pick a finished ride of the
class before you start it — a housemate's best, or your own — and one number on
the ride screen says how far ahead of or behind it you are *at that point in the
class*. Cumulative rather than second-by-second, off the clock that already
excludes paused time, so stopping for a bottle does not lose you the race; and
never on the overlay, which belongs to the next sixty seconds of pedalling.
Both sides have to be watts the bike actually measured, so it does not appear on
a simulated ride at all — which is the honest answer rather than a missing
feature. **`RIVALS.md` is the plain-English description.**

**The cloud, as of the last two sittings.** Accounts with a screen that says
what an account is *for*; row-level security applied and then **verified from a
second real account — 21 probes, 0 failures** — rather than read; a backlog that
drains oldest-first so a ride is never lost to exhausted retries; a payload that
is columnar, versioned inside itself and 228 KB → 54 KB per ride; sign-in by
**scanning a QR code with a phone**, where the code is not the credential and
the bike ends up with a session of its own; a hosted companion web app; one
leaderboard carrying every registered rider **plus** everyone on your own bike,
with no friend graph to maintain; and, as of this sitting, **an offer to back
up rather than a destination to go looking for** — at profile creation and
again from the dashboard for a profile that has been riding offline, each
dismissable on its own and neither shown on a build with no cloud (15.8).

**The floor under all of it**: offline is the mode, not a fallback. A rider with
no account makes **no request to Supabase at all**, and a fence test fails the
build if a new cloud entry point appears that does not name the rider it acts
for.

---

## What is outstanding

### Blocking a stranger being able to use this

| # | What | Why it blocks |
|---|------|--------------|
| **19.1.6** | The first run explains nothing | A new rider is dropped on the profile picker; the overlay permission — the thing the product is built on — is first mentioned at ride start; a heart-rate strap is discoverable only in Settings |
| **19.1.4** | CI is written and never green | The workflow exists. One green run on GitHub ticks it, and until then contributions have no build server but a maintainer |
| **15.4.1–15.4.3** | Sign out, delete cloud data, delete the account | GDPR applies to a hobby project, and sign-out must keep every local ride |
| **17.16.2** | How the web app is deployed | The fix shipped and the check confirms it (17.16.8), but the command itself lives only in the owner's shell history. `./web/check-deployed.sh` is what catches the next drift, and only if somebody runs it |

### Deliberately deferred, with the reason written down

- **23.4 retention** — condensing old rides to aggregates. Blocked *on purpose*
  by **16.3.3a**: personal bests are re-scanned from every measured ride's
  samples on every load, so trimming would silently make a rider's bests worse.
- **17.5 / 18.1 friends** — dropped in favour of "everyone registered"
  (18.11) while the population is four people. The item stays open for the day
  the answer changes.
- **12.3.5 deleting a synced ride** — needs a tombstone, not a delete, or the
  next pull resurrects it. Pull itself (15.3.2) is not built.
- **2.2a calibration** — settled as *yes*, and gated on capturing a sweep with
  the coverage `calibration/README.md` specifies. The first fit failed
  cross-validation.
- **11.7.2** — a decision that is the owner's rather than a session's, written
  up with the measurements behind it. (11.1b.10 was the other one and it is
  answered: the owner reported the same hairline twice, once grey and once
  orange, which settled it — a rule drawn across somebody's film is a rule
  whatever colour it is.)
- **Phase 27 alerts** — records, streaks, and being beaten. Written out at full
  length and deferred on the owner's own weighting: *"definitely nice-to-have
  and low priority for now"*.

### Nice to have, and honestly labelled as such

Most of Phase 17 beyond what is hosted, most of Phase 18 beyond the leaderboard,
avatars (20.2), the Material Expressive cosmetic backlog (~30 items in 8.11), a
custom class builder (19.2.1), a guided FTP test (19.2.3), Strava upload
(19.2.4), and localisation. None of it is load-bearing: `plan/fundamentals.md`
is the standing argument for why, and it has been right so far.

**The live ghost's household half is now built** (24.3.3–24.3.9) and is
described above; what is still on this list is **18.12**, the same thing across
bikes, whose endpoint (`class_ghost`) already exists and has never been called.

**What the owner would rather have is 24.3.10**, and it is the next thing to
build in this area: Peloton's own shape — a live leaderboard in watts, several
rows, your personal best and a friend's ranked as you ride, rather than the
single gap that shipped. It deliberately reopens two decisions the single-gap
version made, and the plan records the disagreement rather than settling it
quietly.

---

## What is wrong today, ranked

1. **Public sign-up is open, and that is now a decision rather than an
   oversight (18.11.1).** The owner settled it on 4 August — *"Leave on public
   signup. It doesn't matter — it requires email validation anyway"* — and the
   measurement supports them: `mailer_autoconfirm` is `false`, so an account is
   not usable until somebody opens a link in the inbox they claimed. What
   actually decided it was that **18.11.1 and 15.8.2 could not both be built**:
   a rider creating their first profile has no account by definition, so
   invite-only would have made signing up from their own phone impossible.

   The exposure is unchanged and still worth stating, because it is now an
   accepted risk rather than an open door: `workouts` and `profiles` hold "your
   own rows and nobody else's", so a stranger who registered would see
   **leaderboard entries and ghost traces** — display names, class ids,
   durations, output. Not ride dates, not RPE, not heart rate, not anyone's
   rows. The item to care about instead is **17.16.3**: which publishable key is
   on the internet matters more once the door is deliberately open. And this is
   the paragraph to re-read the day the project has more than four riders.
2. **The deploy is written down nowhere (17.16.2)**, which is what is left of a
   defect that has now been paid for twice. The pairing-page fix was verified
   against the live endpoint *from a local copy*, never shipped, and the owner
   scanned a QR the next day into the unfixed page. **That is fixed now** — the
   owner redeployed and `./web/check-deployed.sh` reports seven files the same,
   exit 0 (17.16.8). But the gap was open for exactly one drift because the
   *check* exists, not because the deploy became reliable: it still lives only
   in the owner's shell history, and the next drift will be found only by
   somebody choosing to run the script.
3. **The cloud tier has been alive for two days.** Everything in it has been
   observed once, by one household, mostly on an emulator. This project's
   history is three cloud defects that all returned success codes, plus the one
   above, so the right posture is that the round trip works and nothing about it
   is weathered.
4. **The sensor board's serial port leaks (2.7d)**, and it is Peloton's, not
   ours. One `/dev/ttyO0`, one open, so two bike apps can never both work — and
   after the other app is gone the port can stay unopenable **until the tablet
   is rebooted**. What we owe it is 2.7.7 and 2.7.8: say what actually happened,
   and stop rebinding so eagerly.
5. **The power curve is measurably wrong** — RMSE 137 W against the board's own
   watts. It is fenced to two consumers (the simulator and a suggested
   resistance band) and can never reach a recorded number, which is the only
   reason this is a caveat rather than a defect. Adding a third consumer breaks
   that.
6. **`WorkoutServiceTest` is flaky about one run in three (8.8b)**, and the
   instrumented suite is order-dependent, which is why CI runs only the JVM
   tests. A red run you are trained to re-run is a suite nobody reads.
7. **Nothing keeps the two design systems in step (17.15.2)**, and nothing keeps
   this page in step with the plan (19.1.7a). Both are stated rather than
   hidden, and both have the same cheap fix that should not be built until the
   drift actually happens. **The third member of that list has been struck off**:
   "nothing keeps the deployed web app in step with the repo" stopped being
   hypothetical within a day of being written, so it got its cheap fix
   (`web/check-deployed.sh`) and is now item 2 above. Read that as evidence
   about the other two rather than as a reason they are different.
8. **10.6 is still unanswered**: battery, thermals and memory over a full-length
   ride. The one 20-minute ride on real hardware was spent finding 2.7.

---

## How close to done

**Done for this household: weeks, not months — and mostly not code.** The bike
works, the record is honest, the backup runs. **Two of the four things on this
list a week ago are now closed** — the sign-up setting was decided (kept open,
deliberately) and the web app was redeployed and verified. What is left is
sign-out doing the right thing, and a full-length ride that measures battery and
heat.

**Done for a stranger with a Peloton: the six rows in the table above.** In
order of what a new rider meets first: a first-run flow that offers an account
and gets to a usable FTP without a text box. **Both halves of that are now
built** — 20.3 is a three-step screen that estimates an FTP from questions a
person can answer, and 15.8 fills the slot it was left with, offering to back
the profile up right there and again from the dashboard if it was skipped.
What is left is a first run that explains the overlay permission before the
ride needs it (19.1.6), and a green CI run so the project can take a patch
(19.1.4). That is a genuinely short list, and it is short because the hard
parts — a stock bike, honest telemetry, migrations, an overlay that survives
Netflix — are behind us.

**Done as the plan is written: 67%, and it will never be 100.** 459 of 689
boxes, and the remaining 230 are not a queue. They are a place ideas are kept
with their reasoning attached, which is what has stopped this project rebuilding
things it had already decided against. **It went *down* the sitting before this one while
three things were finished**, which is the clearest possible demonstration of
why not to read it as progress: three of the owner's notes added forty boxes
between them, most of Phase 27's. A closed box and an open one are not the same
unit of work either: Phase 25 is 12 boxes and one afternoon; 20.3 was six boxes
and a screen that had to be designed. **Read the percentage as an inventory count,
never as a completion estimate.**

**The thing most likely to move that date is not on any list**, and it is worth
naming: this app has been ridden by one person on one bike. Every defect that
mattered — the mislabelled frames, the FTP save that put the old value back, the
`avg_hr` that was wrong for the project's whole history, the payload version
that never travelled — was found by *using it and then looking at the database*,
not by reading code or writing tests. The next twenty hours of riding will find
things this list does not have on it.

---

## Where to read more

| Question | File |
|----------|------|
| What is done, what is next, and this sitting's story | [PLAN.md](PLAN.md) |
| How data actually flows | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Racing a housemate's ride, in plain English | [RIVALS.md](RIVALS.md) |
| The bike's measured display, system and input facts | [HARDWARE.md](HARDWARE.md) |
| The traps that have already bitten, and the house rules | [CLAUDE.md](CLAUDE.md) |
| Why the phases are ordered the way they are | [plan/fundamentals.md](plan/fundamentals.md) |
| Things once ticked that were not working | [plan/corrections.md](plan/corrections.md) |
| The offline/cloud rules in full | [plan/connectivity.md](plan/connectivity.md) |
| One phase in detail | `plan/phase-NN-*.md` — PLAN.md's table says which |
