> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

# Session log — where the work stood, sitting by sitting

The latest sitting lives in [PLAN.md](../PLAN.md). When it stops being the
latest it comes here, to the top, unedited. Below that are the 31 July snag
list and the three narratives that changed the shape of the project.

---

### 5 August 2026 (thirty-first sitting): the board says less, and the screens it landed on get their shape back

**The owner left four notes and all four are about the leaderboard arriving on
a screen that was designed without it.** They were written up as eight items
and the inbox is empty; seven of the eight are built and seen.

**The live board says less, and it is smaller than what the thirtieth sitting
built** (24.3.17a–c). Every row carries its own total rather than the signed
gap to yours; no row carries a unit; and there is no ranking at all — no rank
column, no `4TH OF 6`, no `LEADING`. Between them the three notes undo two of
24.3.13's three decisions, and each is right for a reason worth keeping:

- **A gap is arithmetic the rider did not ask for.** `+12` is one subtraction
  away from the two totals it came from and only means anything to somebody
  holding their own number in their head at 90 rpm. Four totals in a column
  are compared by eye.
- **The rank was a category error, not an overstatement.** Four of the board's
  row kinds are the rider's own past rides, so *4th of 6* describes a field
  that is mostly one person. The ranking still orders the board and picks the
  window — it is simply not drawn. What goes with it is the one thing the
  header carried: a rider cannot tell whether there are two more rows below or
  twenty. Accepted rather than solved, on the owner's reading that only the
  rows next to them were ever actionable.
- **No unit is safe only while one metric is reachable**, and the comment at
  the drawing site says so: 24.3.15 would make the board's unit selectable,
  and the header that could have named it is gone.

*Observed on the tablet AVD against five seeded rides of `END-01`:* `KILO 10 /
30 DAYS 9 / YOU 8`, moving to `KILO 17 / YOU 17 / 30 DAYS 16` as the rider drew
level and passed one.

**The ride screen's three notes, and two of them turned out to be one change**
(11.6.17–11.6.19). *"The 'next' section — it could be scrollable tbh. Why
not!"* is the rest of the class, capped at three blocks since 24.3.13b took its
column away — and making it scroll **and giving it the column's slack** is also
11.6.16's fix, because growth above it is then paid for by the list getting
shorter rather than by OUTPUT, DISTANCE and AVG POWER falling off the bottom in
silence. `NextUpBlock` reserves the taller of its two states as well, measured
rather than typed in. *Observed across four interval changes: the totals row
sat on the same pixel row throughout, and the fourth upcoming block —
previously unreachable — scrolls into view.*

**And the totals shrink rather than clip.** The owner feared overflow and the
tile was already the one 11.6.12 caught rendering `63.`: a fixed 34 sp with
`maxLines = 1` and no overflow, in a third of a 360 dp column. `ShrinkToFitText`
measures with a `TextMeasurer` and decides the size once, so a number changing
twice a second does not pulse. *Seen at four digits by resuming a seeded
interrupted ride: `OUTPUT 1083 kJ`, `AVG POWER 1195 W`, whole, with their unit
labels intact.* And tapping the distance reads it the other way for this ride
only — `0.10 mi` → `0.31 km`, nothing written, so Settings stays the single
writer of the preference.

**The static board had no ceiling anywhere between the query and the screen**
(24.1.8), which the owner noticed at three rows beside *how did that feel*. The
row count is not "how many people live here" — 18.11 removed the friend graph,
so it is *how many people use this app*. `ClassLeaderboard.visible` keeps the
podium and the rider's own neighbourhood, marks the skip with a `⋮` and counts
what is hidden. The two boards now differ in **which** window and they should:
mid-ride only the rows next to you can be acted on, and afterwards the top of
the board is genuinely interesting.

**The Start Class screen was 22.7.2's own admission coming true** (22.7.3).
That item shipped saying in as many words that the household card *"does not
draw at all"* on the AVD it was judged on — so the screen was designed without
the thing the owner is now complaining about. **A card gated on data the test
device cannot produce is a card that has not been designed**, which is the same
blind spot as everything gated on measured power. The fix is that the board is
not a fact about the class: it is a column of its own, beside it, absent
entirely when there is nobody on it. *Observed against a seeded household of
twelve: `1 Ava / 2 Ben / 3 Cleo / ⋮ / 8 Hana / 9 Simon / 10 Ivy / and 6 more`,
with the class's chart and all six blocks on screen at once — 22.7.2's own
success criterion, restored. A 16-block class with no board fills the panel 4
across and still does not scroll.*

**On the summary the two cards are equal heights with the question still at the
top**, so *"How did that feel?"* and *"On this bike"* land on the same line.
Centring the question in the stretched card was tried first and reads worse:
the two headings then disagree by a hundred dp.

635 JVM tests, 0 failures. Everything above was driven on the tablet AVD by
serial, with the bike attached the whole session and never touched.

**24.3.16 was built and then taken back out, which is the sitting's one
negative result and worth as much as the rest.** One row and a *gap* rather
than a total is the right shape for a strip — the ride screen's own argument
against the gap does not carry, because there is no column and the rider's own
kilojoules are not on the overlay at all. What killed it was width, measured
rather than felt: `HudExpanded` is one row of chips across a 1280 dp band, and
a 132 dp race chip starved the weighted metrics chip until the four live
readouts rendered as `CADEI`, `RESIS`, `POWE` with the digits cut off. The
owner saw exactly that — *"it shouldn't feel so crowded. Particularly the power
numbers it's all crammed in and clipping"* — and the strip is back to what it
was. `LiveStandings.nearest` and its tests are kept; nothing draws them.

**What is owed.** Where a race could live on the overlay that is not the chip
row. And the board has still never been watched moving under somebody actually
pedalling.

---

### 5 August 2026 (thirtieth sitting): the leaderboard, and the column the eye goes to

**The live leaderboard is built** — 24.3.10 through 24.3.13b — and it is the
feature the owner asked for in the last sitting, in Peloton's own shape.

**What a rider gets.** Start a class anybody on the bike has ridden before and
a board appears, ranked live on the class total in kilojoules, with **three
rows: the one you are chasing, you, and the one chasing you.** Nobody is
picked; the race is simply there. Four kinds of row (24.3.12) — your best
ever, your best of the last twelve months, your best of the last thirty days,
and every housemate's best — so a rider who is improving has an unreachable
ghost and a very reachable one at the same time. That is the owner's own
reasoning: *"Just something to always be reaching for, you know?"*

**The single rival is hidden rather than deleted** (24.3.11), on the owner's
instruction, and the interesting part is **how little was behind the flag**.
Two call sites. `RivalTrace`, the elapsed-second alignment, the measured-power
gate and `active_ride_rival` are all still on the live path, because the
leaderboard is built on top of every one of them. 24.3.11's claim that the
ghost was *"a foundation rather than a detour"* was cashed in this sitting and
it held.

**Three decisions the drawing forced, and each has a rule behind it.**
- **The window slides; it never shrinks.** Leading gives you the three below,
  last gives you the three above. A card that lost a row at the top would
  change height at the exact moment a rider was doing well — 11.6.8 by a new
  door. And last is not a corner case: it is the first ten seconds of every
  race, since the field starts level and anybody who moved first is ahead of a
  rider who has not turned a pedal.
- **The header carries what the window hides** — `4TH OF 6`, and `LEADING`
  rather than `1ST OF 6`, because that is what a rider was trying to do.
- **Two number spaces, and the sign tells them apart.** Your row is your total
  with its unit; every other row is the gap to you, signed, without one. Safe
  only because the ranking agrees with it: above always reads `+`, below always
  reads `−`.

**The owner moved the furniture mid-sitting, and was right** (24.3.13b).
*"'Then' section should go under 'next'. This then frees up space for
leaderboard which is where your eyes are naturally drawn to anyway."* The first
half is 11.6.1's own argument carried one step further — *now*, *next* and
*then* are one thought, and the rest of the class was the only part of it still
in another column. The second half fixed a defect the first draft had already
caused: squeezed above the totals, the board had pushed `OUTPUT`, `DISTANCE`
and `AVG POWER` clean off the bottom of the screen, silently, because a Column
clips rather than complaining. Found by looking at the tablet.

**And a lever, because otherwise this could only be looked at on a bike with a
rider on it** (24.3.13a). The measured-power gate means every AVD ride drops
its race one second in. `com.pelonot.debug.RACE` lets the *live* board draw on
a simulated ride and **changes nothing that is written down** — the samples
still record honestly that the watts were modelled, so the ride is still
excluded from every board afterwards. That distinction is the whole safety
argument: a lever that made a simulated ride *claim* to be measured would
poison the record permanently.

**Observed on the tablet AVD**, against six hand-seeded measured rides of
`END-01` and across a whole 20-minute class: `Racing 5 on END-01: Your best
200, 12 months 160, 30 days 139, Kilo 180, Grace 120` — five rows from six
rides, with the ride that is never the best of anything absent and nothing
appearing twice. Then the board moving under a rider through three states:
`6TH OF 6` with two rows above at the start, `5TH OF 6` after passing *30
days*, and `4TH OF 6` with a neighbour each side. The class detail screen
draws the household board and **no** *Ride against* card, which is the flag.
And the board rebuilt itself after a mid-ride resume with nothing having been
written down for it — the class and the rider are already on the row.

**The inbox had three entries and is empty.** The countdown pushing the totals
off the bottom of the screen (**11.6.16** — a real defect, and the note names
the fix as well as the fault); what the opponents should be called
(**24.3.12a**, *"12 months is no good at all"* — **an open item with the
owner's name on it at their own request**, so a session that finds it still
open should say so rather than invent an answer); and the leaderboard on the
overlay (**24.3.16**, which **overrules 24.1.5 and 18.6** — nothing social on
the strip — and the write-up says so plainly rather than letting two rules
quietly disagree).

**And 24.3.6 was finally seen**, two sittings after it was written. The
*"they finished"* state had tests and no observation, because seeing it needs a
ride that outlasts its rival's. What made it cheap was seeding a rival with a
**90-second** ride — the state arrives at minute two instead of minute
eighteen — and the technique generalises to anything in this feature that ends.
`1 GRACE / FINISHED / +6` with her number frozen and the rider's climbing, then
`LEADING` a minute later with Grace at `−3`. **The board turns out to be a
better home for it than the single gap was**, for a reason the item did not
anticipate: a frozen number beside two moving ones is obviously frozen, where a
single frozen number is indistinguishable from a comparison that has quietly
broken.

629 JVM tests and 64 instrumented tests, 0 failures. The instrumented suite was
run with `ANDROID_SERIAL=emulator-5554` because the bike was attached the whole
session.

**And it ran on the real bike**, which is what the AVD could not vouch for.
`Racing 1 on END-03: Your best 238` with real measured watts and no lever, and
the board on screen as `2ND OF 2 / 1 YOUR BEST +2 / 2 YOU 0 kJ`. Two things
came out of that which no fixture had produced: **the two-row case**, a field
smaller than the window shown whole; and the dedupe firing for real, because
the owner's best-ever ride of that class is two days old and is therefore also
their best of the last twelve months and of the last thirty days — three
questions, one ride, one row. It is the case `oneRowPerRide` exists for and it
arrived unprompted on the first real ride it was tried on. The test ride was
discarded through the app's own recovery prompt, and the bike is back to
exactly the seven rides it had.

**What is owed.** The board watched moving under somebody actually pedalling —
the bike observation above was made with nobody on it, so the numbers were
right and still. That needs a rider, and CLAUDE.md is right that it is a
perishable resource.

---

### 4 August 2026 (twenty-ninth sitting): one instruction at a time

**Two things landed: 11.7, and the answer to the leaderboard's score.**

**11.7 is built, on the owner's own priority, and it is the item that had come
up three times.** *"What do i do? do i focus on zone, cadence, or
resistance?"* — the answer is that it was never three instructions. Power is
not something a rider *does*; it is what happens when you turn the pedals at
some cadence against some resistance. One outcome, two controls, and the
screen was giving all three the same size tile, the same gauge and the same
amber.

**The block now says which axis it is asking for.** 11.7.2 had already chosen
route (b) — name it in the catalogue — over route (a), derive it from the
cadence band. The implementation puts governance on the **cadence intent**:
`GRIND`, `CLIMB`, `SPIN` and `SURGE` govern by cadence and the middle of the
range does not, with `POWER(x)` and `CADENCE(x)` overriding at the call site.
That is not route (a) in disguise, and the distinction is the whole reason (b)
was chosen: an author who writes `GRIND` **has said what they mean**, and the
50–60 band is a consequence of that intent rather than its source. It seeded
**231 blocks of 1071** — exactly the 231 the tails measurement found before
the field existed — and 840 write nothing, because absent means power.

**What a rider sees.** Resistance loses its band outright, on both surfaces
and always: no class prescribes it, the band was `PowerModel` inverted, and
that curve is 66% out at the median against the board's own watts. Of the
three numbers competing for attention mid-effort it was **the derived guess,
drawn with the same authority as the two that are measured**. The governing
metric keeps the gauge, the amber, the arrow and the `TARGET` line; the other
keeps its shaded band and loses every signal that says the rider is wrong.
The consequence is the thing to judge it by: **exactly one tile carries a
`TARGET` line at any moment.**

**Observed on the tablet AVD across both governed states of one class**,
`CLB-01`, which has both. On the Z2 endurance block a rider at 94 rpm against
an 80–90 band is quiet cyan — no arrow, no target line — while power carries
`TARGET 80–108 watts` and the amber; on the Z4 grind the two swap completely.
The strip's line under the timer said `75–85 RPM · 30–40%` and now says
`0–80 W` or `50–60 RPM` depending on the block. The next-up preview shows the
rpm only for a block that is asking for it, seen both ways round in one
screenshot.

**Four surfaces changed, not the two the items named**, and the two extra were
found by driving the flow rather than reading the diff: the next-up preview
was naming a cadence directly under the zone name that was the real
instruction, and the class detail list — the one screen where both halves
belong, because it is where a class is *studied* — now bolds the governing
half and dims the other.

**And the voice had 11.7.1a's exact twin, which no item named.** `adviceFor`
checked the cadence first and *returned* on it, so on a threshold block a
rider spinning a perfectly good 92 rpm against the library's neutral default
was told to ease back — and the power drift the class actually cared about
**could never be reached at all**. Same defect as the amber, one channel
louder.

**On the bike itself**, which is where the second defect came from. The class
library re-seeded against the owner's own seven rides with nothing lost — 73
templates, still one retired, all four ride links intact, 32 of the 72 now
carrying `governed_by`. And the position chip was drawing **"SIT" as a
vertical S/I/T**: three letters is not too long for a chip, it is too long for
the room the chip was left, which is the note `MetricReadout` already carries
about "BPM". Invisible on the AVD, obvious on the tablet.

**And the leaderboard's score is settled** (24.3.14). Asked directly and
answered directly: **the class total in kilojoules**, *"the score that the real
peloton gives you"* — so cumulative, and 24.3.5 and 11.6.7 both stand rather
than being reopened. The owner asked for the data to be **metric-agnostic** in
the same breath, and left the judgement to me: *"if it's really that trivial"*.
It was, and the measurement is the reason — `WorkoutAggregates.from` was
already integrating kilojoules **and** kilometres in one pass over the same
samples with the same gap clamp, and `RivalTrace.from` was duplicating half of
that loop. Sharing the constants closed a drift rather than adding one: a
second copy of metres-per-revolution would have given the ghost a distance that
disagreed with the distance the ride recorded. **The toggle is 24.3.15**, and
it is deferred for a stated reason rather than a vague one — it is a control on
the leaderboard's own surface, and the only place it could live today is the
picker 24.3.11 is about to hide.

One finding to carry into the build: **a distance race needs no measured
power.** 24.4.2 excludes any ride with a single non-measured sample, which is
why most classes have no ghost at all today — but distance is integrated
*cadence*, measured on every ride this app has ever recorded.

**The inbox is empty.** *Rivals vs Leaderboard* became **24.3.11–24.3.14**:
the leaderboard wins on the owner's own reasoning (a rival's ceiling is one
person), the ghost goes behind a flag rather than into the bin, and
**24.3.14 is a question back** — *watts* has now been used twice for the
score, and the two readings of *"as of this point in the class"* build
different features.

616 JVM tests and 62 instrumented tests, 0 failures. The instrumented suite was
run with `ANDROID_SERIAL=emulator-5554` because the bike was attached the whole
session.

**What is owed on 11.7:** the spoken half was not heard, only tested. A cue
lasts a second or two and CLAUDE.md's own rule is that audio is the rider's to
confirm, not something to poll `dumpsys` for.

---

### 4 August 2026 (twenty-eighth sitting): riding against somebody, and the shape the owner wants instead

**The live ghost is built — 24.3.3, 24.3.4, 24.3.5, 24.3.7, 24.3.8 and 24.3.9
— and the owner has already said the better idea is the one that is not
built.** Both halves of that matter, and the second is the more useful.

**What it does.** A rival is chosen on the class detail screen before the
class starts — a housemate's best ride of it, or your own — and one number on
the ride screen says how far ahead of or behind that ride you are *at this
point in the class*: `+18 kJ`, `−4 kJ`. Not a position, not a percentage, not
a list. Cumulative rather than instantaneous, on 11.6.7's argument that the
ride screen's numbers already changed too fast to read. Keyed off the clock
that excludes paused time, so a bottle stop does not lose a race. Nothing
about it is written to `workouts`, because 8.3d.4 means the finalise would
wipe it — the choice lives in `active_ride_rival` (migration 15 → 16), which
exists purely so a crash mid-ride does not lose it, and is deleted when the
ride ends. **`RIVALS.md` is the plain-English description**, written because
the owner asked for one mid-sitting: *"I don't really know what you're
doing!"*

**The measured-power gate is the part worth knowing.** The rival's side is
excluded by the query, as 24.3.1 already had it. This side cannot be known
until the watts arrive, so one modelled sample drops the ghost for the rest of
the ride and it never comes back — `Mixed` fails `isTrustworthyAsMeasured` on
purpose, and a race that is honest for ten minutes and fiction for the next
ten is worse than none. The practical consequence is that **the feature does
not exist on the emulator**, which is exactly what was observed there.

**Observed on the bike, not reasoned about.** Migration 15 → 16 ran against
the owner's own seven rides with nothing lost; the picker offered *Your best ·
238 kJ*, their real 30-minute END-03 ride; logcat said `Racing Your best: 238
kJ over 1800s` with no *Dropping the ghost* after it; the card rendered; the
`active_ride_rival` row was there in `sqlite3` mid-ride and gone afterwards.
On the AVD the same gate did the opposite and correctly refused to race at
all. **Two things were owed and are still owed**: the *"they finished"* state
(24.3.6, tested but never seen), and the number watched moving under a rider,
which needs somebody pedalling. Neither box is ticked.

**Two defects found by looking at the tablet rather than at the diff**, which
is the technique as much as the result. The picker card was capped but not
filled, so it sat at half the width of the board above it; filled but not
capped, it spanned the whole 1280 dp panel — 22.6 broken in both directions by
modifier order alone, invisible in the source. And the gap was drawn in
`MetricPowerCoral`, which against the dark ride screen reads as **red**: a
rider one kilojoule down was being told in the colour of a fault that they
were losing, which is precisely what 24.3.4 rules out.

**Then the owner's own idea, and it is better.** Verbatim, paraphrased for
the numbers: *"let's do what Peloton does and show a live leaderboard (in
watts) which includes a live 'as it stands' leaderboard of where YOUR personal
best is (on this class) and also your FRIEND's personal best... The ghost
score should be the score that that user had at that exact moment in the
class."* That is **24.3.10**, and it deliberately reopens two decisions this
sitting shipped: 24.3.4's *not a list* (a leaderboard of two is a number — but
a leaderboard of several is a leaderboard) and 24.3.5's *cumulative, not
instantaneous* (the owner's own 56-vs-65 W example is instantaneous, and
11.6.7 is why that was avoided). The item records the tension rather than
quietly resolving it. The owner's instruction was to finish this first and
then move: *"I think my live leaderboard idea works better and I'd rather you
get cracking on that."*

**And a priority arrived with it.** The owner asked for the *resistance target
vs cadence target vs power zone target* problem next, "as a priority" — that
is **11.7**, and it is the third time it has come up. 11.7.2 was decided last
sitting, so 11.7.1a, 11.7.3 and 11.7.4 are unblocked and waiting on nothing.

606 JVM tests and 62 instrumented tests, 0 failures. The instrumented suite
was run against `emulator-5554` by serial because the bike was attached the
whole session and `connectedDebugAndroidTest` would have reached it.

---

### 4 August 2026 (twenty-seventh sitting): the account fills the slot 20.3 left for it

**Two owner notes arrived and both were already in the plan.** Auto-FTP going
down as well as up restates 7.11's own asymmetry argument, arrived at
independently rather than by reading it; the three-target confusion restates
11.7 word for word, and the owner flagged their own uncertainty about it
("might already be in the plan") rather than insisting it was new. Both are
recorded as confirmation in their items rather than as new instruction, because
neither supplies what its item is actually blocked on — 7.11.1 needs ride data
before it can put a number on the evidence window, not a guess dressed as one.

**11.7.2 did get an answer, because it was the one open question actually
theirs to make.** Asked directly: name the governing metric in the catalogue
(`governed_by`, optional, absent means power) rather than infer it from the
cadence band. Decided, not built — it stays in queue order behind what
follows, but 11.7.1a, 11.7.3 and 11.7.4 no longer wait on a design question.

**A third note landed mid-sitting, live in PLAN.md rather than through
chat, and it was fixed on the spot.** *"It looks a bit ridiculous... a
single list that you can scroll. Not a grid."* `BirthYearPicker` was already
one shared component on both screens that ask (20.3.3, Settings) — nothing to
deduplicate, only the layout: `LazyVerticalGrid` replaced by a `LazyColumn` of
full-width rows, opened already scrolled near the rider's likely answer.
Observed on the tablet AVD — opens centred on 1986, scrolls smoothly, a tap
selects and closes with no second confirm step (21.1.1b).

**Then the queue's own next item: 15.8, the account as the front door.**
20.3 built `Step.Account` and an `accountOffer` slot with nothing in it,
null on a build with no cloud; this sitting filled the slot rather than
building a second flow beside it, which is exactly what reading 15.8 and
20.3 together in the twenty-sixth sitting was for.

**The one rule the whole item hangs off — the profile exists before the
offer, never after it — needed the screen restructured rather than just
filled in.** The hook as built deferred `onProfileCreated` until the account
step's own `onDone` fired, which is backwards: a rider who force-stops
mid-offer would have no profile at all. `ProfileCreationScreen.finish()` now
runs the moment the rider leaves the result step, and a separate
`onAccountOfferFinished` callback closes the screen afterward — checked in
`sqlite3` while still sitting on the offer screen, not taken on trust: the
row existed, `ftp_watts` matched the estimate shown, `auth_user_id` empty.

**Linking is automatic and it needed no new plumbing.** `AccountViewModel`
already scopes itself to `SettingsRepository.settings.lastProfileId`, and
`createProfile` sets that id before `Step.Account` ever composes — the same
mechanism 15.6's pairing already used from Settings. So `ProfileAccountOfferStep`
is the existing `ScanToSignIn` / `PairingSection` / `SignInForm` composables
(promoted from `private` to shared) wired to that ViewModel unchanged, plus
15.8.6's cost line and a "Not now" the same width and weight as the controls
above it rather than a grey link underneath them.

**The dashboard got the other half — a card for a profile that has been
riding offline, dismissable per profile.** `profiles.account_offer_dismissed`
(migration 14 → 15) rather than a device-wide flag, on the same argument as
`household_visible`: a household bike has several riders and one of them
dismissing this must not silence it for the others. Observed twice —
`sqlite3` after a force-stop showed one profile's dismissal `1` and another's
still `0`, and the card stayed gone for the dismissed profile after a fresh
launch. It never doubles up with the existing backup reminder (23.3.1): the
dashboard checks the account offer first and only falls back to
`BackupReminderCard` when it does not apply.

**15.8.5's reconciliation is half done, and the honest half at that.** The
plan asked for 23.3.1's own ten-ride count to trigger both cards. It doesn't
yet — that count lives in `SettingsRepository`, per tablet, and this offer is
per profile, so wiring one to the other now would let a housemate's rides
decide whether *this* profile gets asked. The card triggers on "has ridden at
all" instead, which is simpler and correct, and 23.3.1a is the item that has
to move before the two counts can honestly become one.

**Migration 14 → 15 ran against the emulator's own multi-profile database**,
not only against `MigrationTestHelper` — the instrumented suite (15 tests,
including the new one) run directly against `emulator-5554` by serial, because
the real bike was also attached this session and `connectedDebugAndroidTest`
would have reached it. 599 JVM tests, 0 failures — no new ones needed, since
nothing in 15.8 is pure logic (`PostRideSummaryScreen`, `ProfileCreationScreen`,
`AccountViewModel` and the dashboard are all Compose/Room, and the migration
and the flow it enables are what the instrumented suite is for).

**What is not built is 15.8.2's web half**, and it did not need to be —
17.16.6 already closed it in the twenty-fifth sitting. Reading an item fully
before starting it found free work twice this sitting: once in 15.8.2, once
in 15.8.3's linking.

---

### 4 August 2026 (twenty-sixth sitting): the first question the app asks

**The screen the owner said "can't go into production" is gone.** 599 JVM
tests, 0 failures, and the migration ran against the bike's own 7-ride
database rather than only against a test.

**One inbox entry, and it closed the loop the last sitting opened.** *"The live
URL is now up to date"* — confirmed rather than taken on trust, which is the
entire point of having built `check-deployed.sh` the sitting before: seven files
the same, exit 0, against two drifted twenty-four hours earlier (**17.16.8**).
So 17.16.6's fix is on the internet and the state that produced the bug report
no longer exists. **17.16.2 is what it hands forward**: the deploy is still
written down nowhere, and the gap was open for exactly one drift because the
*check* exists, not because the deploy got reliable.

**18.11.1 is closed as *not* to be done, and the way it closed is the thing to
remember.** The owner asked the right question — *"If we switch it off then how
do legit users sign up?"* — and checking it turned up a collision neither item
had spotted: **18.11.1 and 15.8.2 are direct contradictions.** 15.8.2 says a
rider creating their first profile has no account *by definition* and must be
able to sign up from their phone; invite-only makes that impossible. Not a
security item that lost to convenience — two items that could not both be built,
and the one that serves the rider won. The owner's reason stands on measurement
too (`mailer_autoconfirm` is `false`, so an account needs a real inbox). **15.8.2
is unblocked and 17.16.3 matters more now**: if the door is deliberately open,
which key is on the internet is the question worth being careful about.

**Then the work: 20.3, which is Route B and a screen.** The decision was
genuinely close on the item's own three-way balance, and what made it one-sided
was a fact about `PostWorkoutAnalyzer` that nobody had brought to the question —
**auto-FTP can only ever propose a rider's FTP *upward*** (`proposal >=
currentFtp × 1.02`). So the two errors are not alike: an estimate that starts
**low** is deleted by the first hard ride; one that starts **high** is
permanent, because no breakthrough ever clears the threshold and every ride sits
in Zone 2 for ever. Route A cannot express that asymmetry and Route B can — so
every coefficient is pitched *below* the published mid-range, and
`FtpEstimatorTest.estimateIsBelowPublishedMidRange` is where a future change has
to argue against it. The same bias covers the field this app deliberately does
not have: published W/kg tables differ ~15% on sex and nothing here collects it.

**There is no Skip, and that is the sub-decision 20.3.2 asked to be made by
looking.** A skip in front of the questions makes them optional and hands Route
A to everybody in a hurry. The escape sits on the *answer* instead — *"I know my
FTP — set it myself"* — so it is reached only by a rider who has seen the
estimate and disagrees, which is exactly the rider who should be typing.

**The owner's own call landed mid-sitting and it was right: ask for the year,
not the date.** Checkable rather than a matter of taste — the app has exactly
two consumers for this datum and both reduce it to whole years, so 1 January
costs **0.7 bpm** on Tanaka (against its own admitted 10–12 bpm spread) and
**0.6%** on the FTP term. Against that, Material's `DatePicker` opened on
*August 2026*: roughly five hundred presses of the month arrow for a rider born
in 1985. `BirthYearPicker` is a grid of 48 years on the panel, one tap, opened
forty years back. **And when the owner wondered whether the caption bug meant a
date was needed after all, it did not** — that caption said "your age" because
the year had been *skipped*, and a date picker would have produced the identical
sentence. *How precise* and *what did we actually use* look alike and are
unrelated; only the second was a defect (**21.1.1b**).

**Three defects found by looking rather than by building, and all three are
sentences.** The layout was right first time. `displayName.lowercase()` rendered
*"i ride now and then"* back at the rider — text a person wrote does not survive
being case-folded by a machine. The caption named an input the estimate had not
used. And **Settings offered a full date picker over the column onboarding fills
with 1 January**, so a rider who answered "1986" was shown "1 January 1986" as
though they had said it. One control in both places now.

**One defect was in the funnel and would have been invisible.**
`UserRepository.save` filed every new profile's first FTP as `ProfileCreated`
*"whatever the caller said about it"* — correct for as long as typing was the
only way to get a number, and wrong the moment the app estimates one. Verified
in `sqlite3` rather than on screen: `Estimated` for the estimate,
`ProfileCreated` for a typed 265.

**What is not built is 15.8**, and the screen was built with its hook in place —
`accountOffer` is a fourth step the screen already knows how to show, null on a
build with no cloud (15.8.7). That was the whole reason to read 15.8 and 20.3
together: one screen, built once.

---

### The twenty-fifth sitting — three notes, and the fix that never reached the internet

**The owner left three more notes and one of them was a bug report on a live
flow.** 585 JVM tests, 0 failures. All three are written up and the inbox is
empty, which is the rule; two of them were also built.

**"The 'link my account' doesn't work … there was no way of actually signing
in."** Checked, and it was two faults stacked. **The first is that yesterday's
fix was never deployed.** `link.js` on the host is the pre-17.16.5 version —
`route()` still reading its own DOM, and the error path returning without
reopening the retry box — so an unrecognised code drew the expiry card and
*nothing else*. 17.16.5 was observed working against the live endpoint from a
**local copy**. The repo was fixed; the internet was not. **17.16.2 predicted
this in the same sitting** — *"nothing checks that the deployed copy is the
committed one. Today they match, and today is the only day that has been
checked"* — and it drifted inside a day, straight into the owner's hands.

**The second fault is in the fix itself, and it is the one worth carrying
forward.** 17.16.5 gated the sign-in form on the pairing code being recognised,
and that collapsed two different questions. *May a session leave this phone for
that bike?* has to know which bike — that is 15.6.5, and it is what the confirm
step is for. *May the rider sign in to Pelonot on their own phone?* is the same
sign-in `index.html` offers, to the same project, and a five-minute pairing code
makes it no safer. So an expired code was a dead end **even after the fix**. The
page inverts now: sign in whenever, confirm separately, the confirm step still
names the device. All four session/code states measured against the live
project, including typing `ymmh d7za` — lower case, with the space a person
reading eight characters off a screen would put in — from the expired card.

**And the thing that let the first fault reach the owner is now one command.**
`./web/check-deployed.sh` — curl and diff, no credentials, non-zero on drift.
Its first run *is* the evidence above: five files the same, `link.html` and
`link.js` not. Same argument as `CloudConfigFenceTest`: the shipped artefact is
what a rider meets.

**The Start Class screen shows the class now (22.7.2).** The owner's other
standing note, and the plan's own next item. It was six full-width rows each
spanning 1872 dp to carry four facts down their left edge, with the seventh
block of a 30-minute class **below the fold on the one screen whose job is to
show the whole class**. The visualisation is the class itself — height for zone,
width for time — and it reads as two different workouts from across the room:
`The Long Climb 30` is a ramp into one long orange block, `Torque Repeats 4×2
20` is four spikes with recoveries between them. **No value axis, deliberately**:
the vertical is a zone *ordering*, and the gap between Z1 and Z2 is not the gap
between Z6 and Z7 in watts.

**Two facts fell out of drawing it that neither the item nor the plan had.**
Zone 1 needs a height floor, or a warm-up reads as an empty left-hand edge
rather than as riding. And **adjacent blocks at the hardest zone are one
effort** — the library splits a fifteen-minute threshold block in two to change
the cadence, and calling that two efforts describes a workout with a rest in it
that nobody gets. The sentence over the chart now agrees with the class's own
title: *"4 × 2 min at Lactate Threshold"* over `Torque Repeats 4×2 20`.

**The width rules got their first mixed screen**, which is 22.4.3's "capped
column inside a wider frame" case that no screen had used: the profile and the
interval grid take the panel, the summary is `readableText`, the leaderboard is
`loneCard`, and **Start is a 420 dp control rather than a 1872 dp band**.

**The two things found by looking rather than by planning** are the ones to
remember. The content needed **centring when it does not fill the panel** —
22.7.1's rule arriving on a third screen, because most classes are seven or
eight blocks and top-aligning them hangs the screen off the app bar with a hole
above the button. And `WideGrid` grew an opt-in `equalHeightRows`, because one
tile carrying a position chip is 20 dp taller than its neighbours and a ragged
row reads as a mistake. **Opt-in and it has to be**: equal heights need
`IntrinsicSize.Min` and a `Canvas` throws rather than answering an intrinsic
query, so every caller with a chart in its cells keeps the layout that works for
anything.

**The third note is the biggest and none of it was built: the account is a thing
a rider has to go and find (15.8).** The owner is right about the symptom, and
the diagnosis worth stating is that this is **omission rather than principle** —
rule 1 says a rider makes no request to Supabase and 15.2.6 says they see no
prompt on a screen they did not open, and neither says the account has to be
hidden. Creating a profile is a bare `AlertDialog` with three text fields and no
mention that a cloud exists. **It conflicts with 20.3 over one screen** and that
is said out loud in the item: both notes want profile creation rebuilt, and
building 15.8 on top of the current dialog then rebuilding it for 20.3 is two
designs for one screen.

**The other two notes are written up and take their place in the queue.** The
live ghost (24.3.3–24.3.9, 18.12): half of it was already 24.3.2, and the half
that is missing is *during* a ride, which is where a ghost lives — everything
social this app has happens before or after one. `class_ghost` already exists in
`007` and nothing calls it. And alerts (**Phase 27**), promoted out of 19.3.2's
one line the way Phase 21 was promoted out of 19.3.3's, with the owner's own
weighting kept: low priority.

**The thing worth taking from this sitting** is that the pairing defect was not
in code anybody wrote wrong. The code was right, in the repo, with a note in the
plan saying nothing checked whether it had shipped. **A fix that has not been
deployed is indistinguishable, from the rider's side, from one that was never
written** — and this project now has one command that can tell them apart.

---

### 4 August 2026 (twenty-fourth sitting): nine notes, and the first ride nobody has to survive

**The owner left nine notes between sittings and the sitting was mostly about
them.** 576 JVM tests, 0 failures. Eight were in the inbox at the start and a
ninth arrived while the work ran; all nine are written up and the section is
empty, which is the rule.

**Two of the nine were answers to questions this plan had asked *them*, and both
are worth keeping as decisions rather than ticks.** 22.6.3 wanted a build-time
fence around "no single card takes the panel", and the answer was no —
*"don't enforce it deterministically, just bear it in mind"* — which is right,
and the reasoning generalises: this project's three fences all guard things that
are **invisible when broken**, and a card banded across 1232 dp is visible from
the other side of the room. 26.3.3 asked whether *Everything I had* is the wrong
label for somebody who quit early, and the answer answered the objection rather
than waving it off: a rider who stops early does not rate the ride at all.

**The first ride anybody takes was the worst ride the app gives, and it is the
one nobody had watched.** The countdown lands on zero, `startRide` runs, and
*then* Android's overlay permission is asked for — so the ten seconds a rider
spends clipping in buy them a modal, a trip to the system settings app, and a
class whose clock has been running the whole time. It is 11.6.13's own argument
one layer up. The prompt is inside the countdown now and **the count stops while
the question is outstanding** — through the dialog *and* through the trip out of
the app. Measured: the prompt over *Get set* with the count at 10, ninety
seconds away granting it, and the count still at 10 on the way back (11.6.14).

**The defect inside that fix is the one to carry forward.**
`requestOverlayPermission` called `dismissOverlayPrompt`, which is to say it
marked the question **answered at the moment the rider left to answer it**. The
countdown would have restarted the instant they were sent away — the exact thing
the owner asked to fix, reintroduced by the obvious implementation. The dialog
closing and the question being answered are different events, and the state has
to say so.

**The line across the film is gone.** The owner reported the same hairline twice
— once grey, once orange — and that is the answer rather than two bug reports: a
rule drawn edge to edge across somebody's film **is** a rule, whatever colour it
is. One alpha, `0.45` → `0`. It still thickens and pulses before an interval
change, which is the only part that was earning its place (11.1b.10).

**The summary is the record now.** *"This should be pretty much the same as when
you view it from history, right?"* — nearly, and the difference was not
principled: charts were private to `RideDetailScreen` because 16.1 landed there
first, so a rider who had just stopped pedalling got six figures and half a
screen of black. Both screens share the section now, and **the extraction that
mattered was the second one**: `buildRideCharts` came out with it, because the
rule deciding which FTP draws the zone bands (7.8) was inside a ViewModel, and a
second copy of that is a second answer to the one question this app has already
got wrong once (12.6.1).

**And a ride ended by accident can be carried on.** 8.3d built all of this for a
crash, and none of it had met a ride that was *finished*: the reopen now clears
`is_complete` — left set, a ride still being ridden sits in history and in the
leaderboards — and `synced_at`, or the cloud keeps the short version of a ride
that got longer. Checked in the database rather than on the screen, because a
resumed series comes back contiguous and cannot show any of it: 153 samples, one
per second, `resume_count = 1` surviving the second finalise (12.6.2).

**The last two are the same discovery arriving twice, and it is 22.5's.** The
owner's *"panels need to be centrally aligned — look in particular at the
bottom"* is not really about the bottom of a list: at one ride a week, **most
days hold exactly one ride**, so the half-empty row that looks like an edge case
against dense fixture data is the ordinary reading of the screen. A day that
does not fill the row centres what it has (22.7.1). Then the household panel,
which counted a *week* — and there the same assumption did something worse than
look wrong: a rider with no rides in the window has no row at all, so a housemate
riding once a week was **absent from the household** rather than shown with a
zero (22.5.4).

**The ninth note arrived mid-sitting and was built too.** *"Any chart that shows
heart rate over time should include visual indicator for heart rate zones"* — and
the scope is right, because the power trace has carried its bands since 16.1.1,
so an unbanded heart-rate trace beside a banded one is the app being inconsistent
about its own idea. What made it more than an afternoon's drawing is that the
bands need a denominator, and **nothing recorded the maximum heart rate a ride
was ridden at**: `workouts.max_hr_bpm` is migration 12 → 13, and a ride from
before it draws its bands from the rider's maximum today *and says so*. That is
7.8 exactly, one column along, and it was already written down as 21.2.3 waiting
for the first thing that would trip it (21.4.2, 21.4.2a).

**The thing worth taking from this sitting**: every one of the four defects above
was invisible to `assembleDebug`, to the JVM tests, and to a screenshot of the
screen at rest. Two needed a stopwatch and a trip out of the app, one needed
`sqlite3`, and two needed a database with a *realistic* rider in it rather than a
busy one. 22.5.5 said that last one in advance and it has now been true twice.

---

### 4 August 2026 (twenty-third sitting): the panel, used

**A design sitting, and the owner was in it — five inbox notes arrived while it
ran and every one of them changed the work.** 576 JVM tests, 0 failures.

**The ride summary was the brief and it was the right place to start.** It was a
centred column of six label-value rows — `Average power  188 W` — on a screen
1280 dp wide: *the moment the app has the rider's full attention, saying the
least it ever says with the most room it ever has.* It is now one row of six
figure tiles in the metric colours the ride screen already uses, headed by the
class the rider chose rather than the words "Ride Summary", with Done and
Discard pinned so that getting off the bike never involves finding the button
first (22.4.6, 26.1.2).

**The token that made it possible is the point, not the screen.** 22.4.1 asked
for a companion to `readableColumn` so that *use the width* is as cheap to reach
for as *cap it*. `WideGrid` is it — row-major so the order survives the fold,
and **balanced**, because six figures in a grid that fits five wide came out as
five and a stray, and a lone tile on a second row reads as a mistake rather than
as a layout. Two pure functions and six tests, since the failure they guard is
silent: forget the gaps between cells and every cell lands under its minimum.

**Then the owner said the quiet part: *"Make sure you're using the full width of
the screen... Only constraint should be that no ONE CARD should go full
screen."*** That is the criterion 22.4.3's audit was going to have to invent,
handed over before it had to. Seven screens carried the cap. Dashboard, history,
class library, *Your riding* and *Your FTP* wanted the width; Settings and the
account screen genuinely wanted the cap, which is what it was always for. The
dashboard now fits on one screen without scrolling, and the class library shows
21 classes where it showed 7.

**Two of the notes were corrections to work done an hour earlier, and both were
right.** The owner saw the ride-detail charts "disappear" — they had not, they
had been pushed below the fold by the new figures grid inside a 760 dp cap,
which is 22.4.2 in one observation. And then: *"the 'time in zone' card is
stretching full width... this violates a design rule. If the rule doesn't exist,
please make the rule."* It did not exist. **Uncapping a screen is not uncapping a
card**, and only the first half of that sentence had been written down.
`Modifier.loneCard()` is the third token and CLAUDE.md now carries all three
together (22.6).

**Two notes were about the rider rather than the layout, and both invalidated a
design decision rather than adjusting one.** *This Week* was built on an unstated
assumption of several rides a week; on the owner's stated assumption of one, it
reads "0 rides" six days out of seven — the first thing on the dashboard telling
a rider doing exactly what they meant to do that they have done nothing. It is
**Last 30 days** now, rolling rather than a calendar month, because a month
resets on the 1st and hands a rider a zero the day after they rode. **The streak
was the same defect one level down and worse**: it counted consecutive *days*, so
a rider who has never missed a Sunday scored 1 — and a streak of 1 is
deliberately not shown. The most consistent rider the app can have was invisible
to the feature built to reward consistency (22.5).

**And the RPE scale went from ten answers to three**, because *"it causes me
anxiety, wondering if I'm selecting the right option."* The column stays 1–10 —
the owner's own suggestion, and the right one: a ride already recorded keeps its
exact answer, the cloud payload is untouched, and `EASY_RPE_THRESHOLD` keeps
working because *Comfortable* stores 3. **That last one is the trap worth
carrying forward**: had it stored 5, the FTP proposal would have silently stopped
firing and no screen would have looked any different. It is a test now. Checked
both ways on the AVD — a new ride answered "A good workout" lands as
`rpe_rating = 6`, and a ride rated **7 on the old scale opens reading "A good
workout"** (26.3).

**The thing worth taking from the shape of this sitting**: four of the five notes
were the owner *looking at a screen*, and two of them caught regressions inside
the hour. `assembleDebug` passing proves very little here — it never did — but
neither does a screenshot the author took to confirm what they expected. The
1280 × 720 AVD is where all of this was decided, and it is the only reason any of
it is ticked.

---

### 4 August 2026 (twenty-second sitting): the round trip seen at last, one leaderboard, and a website with the door left open

**Two sittings' worth of work landed on the cloud tier and the narrative never
caught up with it, so this entry covers both halves.** 547 JVM tests, 0
failures.

**14.1.6 has been open since the third sitting and finally closed, and it could
never have closed before Phase 15**: `003` revokes the anon key, so the sighting
needed a real session. A tablet signed itself in by QR, the drain fired, and
three rides arrived — 332, 50 and 1185 samples, attributed, `class_id` CLB-01,
`v=1`, 47,890 bytes for the twenty-minute one. Then read back **in the web app**,
which is the version of that sighting a person can have rather than a query.
**15.5.4 closed with it**: two real accounts, 21 probes, 0 failures. A cannot
create, read, rename or delete B's profile; cannot record, see, edit or delete
B's ride; and cannot hand their own ride to B, which is the
`WITH CHECK`-without-`USING` hole 15.5.1 was written to close.

**Four defects on the way, and not one of them was catchable by a test.** The
cloud's class library and the bundled one were **different libraries** (14.2.9):
23.2.6 renumbered the catalogue, the cloud kept the old series, and
`workouts.class_id` has a foreign key onto it — so no ride against any bundled
class could ever have been backed up, by anyone, ever. One unacceptable row
**blocked every ride behind it for ever** (14.2.7); nothing drained the backlog
on launch (14.2.10); and the payload's version field never travelled (14.4.3a) —
`version: Int = VERSION` is omitted by an encoder with `encodeDefaults` off, and
the tests missed it by encoding with it *on*, so tests and production used
different encoders and only one was ever checked. The one field whose job is to
survive into an unknown future was the one field that never left.

**The owner's leaderboard note replaced a friend graph that had already been
built and applied.** Verbatim: with three or four riders who already know each
other, *"everyone should just have visibility over everyone's scores"*. The
`friendships` table with request / accept / block is dropped — it was the right
answer to a question nobody had asked. What replaces it is two narrow
`SECURITY DEFINER` functions, because **"everyone can see everyone's scores" is
not the same sentence as "everyone can read everyone's rows"**: the policies on
`workouts` and `profiles` are unchanged, and the ghost even strips heart rate.
18.9 was applied rather than quoted — this is the household board with more rows
on it, one type and one renderer, and the failure mode is a shorter board rather
than a missing one. Verified from two accounts, 15 probes, 0 failures.

**Then the owner's two notes for this sitting, and the first one has a door
standing open behind it.** *"Website is now running here."* It is: the deployed
bytes are the repo's, `config.js` is served beside them, and the host's
`.html` → extensionless **307** carries the QR's fragment intact — measured in a
browser, ending at `/link#ABCD2345`, reaching the live project and answering
*"That code has expired"*, which is `device_link_describe` working as `anon`
from the deployed page. **But hosting it publishes the endpoint and the
publishable key, which is exactly the condition 18.11.1 was written against**,
and the project answers **`"disable_signup": false`**. With `007` applied, an
account created by anyone who finds that URL lands on the household's
leaderboard. The blast radius is bounded and worth stating rather than
dramatising — 15.5.4 means it is leaderboard entries and ghost traces, not
anybody's rows — and the fix is two minutes in a dashboard that is **the owner's
and not a session's**. It is now the top line of *What to do next*.

**The second note is the one this file has needed for a while.** *"There is so
much plan documentation (this is good!) but it's difficult to get a true
understanding of where we are."* Correct, and the cause is structural: 7,200
lines across 25 files, every one written for the session that will *do* the
work, with the reasoning kept rather than summarised — which is what has stopped
this project re-litigating settled decisions, and is no help at all to a person
asking whether it is nearly finished. **[STATUS.md](STATUS.md) is that page**
(19.1.7), and the thing worth carrying forward is that it defines *done* three
ways, because the honest answer differs by a lot depending on who asks: done for
this household is weeks and mostly not code; done for a stranger with a Peloton
is six rows; done as the plan is written is 410 of 594 boxes and will never be
100, because the plan is where ideas are kept rather than a queue. **Read the
percentage as an inventory count, never as a completion estimate.**

### Latest session — 3 August 2026 (twenty-first sitting): the app goes online, and the endpoint stops being hypothetical

**The owner asked for accounts, and said they were available to help set it up.**
Three inbox notes were emptied on the way — including one about the inbox
itself, and one asked mid-session about whether this should become a monorepo.
Phase 15 exists now: the app has auth, a screen that says what an account is
*for*, a companion web app, and signing in by scanning a QR code with a phone.
**`003`, `004` and `005` are applied to the real project.** 532 JVM tests, 0
failures.

**The migration that had been written for two sittings finally ran, and the
important part is what happened after it.** `003` returned `201`, which proves
nothing — this project's history is three cloud defects that all returned
success. So the catalogue was read back: nine policies, `WITH CHECK` on every
write and `USING` on every read. Then the endpoint was probed over HTTP with
the real anon key, which is the path an attacker takes rather than the one the
SQL describes: `profiles`, `workouts` and `device_link` all answer **401**, and
`class_templates` answers **200**. Three pre-consent profile rows deleted; the
16 workouts kept, because one of them is 14.4.5's specimen.

**That habit found something reading the SQL again could not.** `anon` still
held **`TRUNCATE`** on `class_templates`, and then on `device_link` the moment
`004` created it — from Supabase's own default privileges, not from any
migration here. TRUNCATE ignores row-level security, being table-level, so on
paper the 72-class library was one statement from empty. It is not reachable
(PostgREST speaks no TRUNCATE and `anon` has no login), which makes it exactly
what 14.0 called the old `USING (true)` policies: **a loaded gun rather than a
fired one**. `005` puts it down. The rule it leaves behind is 15.5.7: *after any
migration that creates a table, read the catalogue back* — what a migration
granted and what a table ends up holding are different questions.

**The owner's QR idea turned out to be both feasible and the flow this app
should have led with.** The bike's tablet is the worst keyboard in the house;
a phone is the opposite of all four reasons why. Supabase has no device
authorization grant, so it is built out of a locked table, four `SECURITY
DEFINER` functions and a one-time OTP. Three things it gets right on purpose:
**the code is not the credential** (the bike sends only the SHA-256 of a secret
it keeps, so a code photographed off the screen collects nothing); the table has
RLS on and **not one policy**, so no role can read it and every access is a
narrow function with `search_path` pinned; and **the bike ends up with a session
of its own** — this project has refresh-token rotation on, so two devices in one
token family revoke each other, which is why the phone's own refresh token is
the thing not to copy.

**`004` had a real flaw that only the endpoint could show.** `device_link_
describe` was authenticated-only, which reads as the safer choice and is the
wrong one: the phone must be told **which device it is signing in** *before* it
asks anybody for a password, and a protection that only fires after the password
is typed is not one. It is granted to `anon` now, with the bound stated — you
cannot ask without the code, and a claimed code answers `expired` exactly like
an unknown one.

**Driving the AVD found the defect that reading the diff would not have, again.**
Settings read `hasAccount` off the profile row, so a tablet holding **no session
at all** said *"Backed up to your account"* — while the sync status line one
card below, which does ask the gate, was silently absent. Two surfaces a card
apart disagreeing, and the wrong one is the sentence a rider reads *instead of*
checking. It now says the honest thing 15.2.8 asked for and nothing was drawing:
*"Signed out on this bike. Your rides are still here and still yours … 3 rides
are waiting to go up."*

**15.2.8 is the design decision worth carrying forward.** 15.2.4 says nothing
may assume one signed-in user per device, and that is right about the data
model — but the client library holds exactly **one** session per process. Both
are true, and the honest place to reconcile them is the gate: *having an account*
and *this tablet carrying that rider's credentials* are different questions, and
only the second can send a request. Without it, Priya's ride goes out under
Simon's JWT with Priya's `user_id`, `003`'s `WITH CHECK` refuses it — correctly —
and she is told forever that the network is at fault.

**What is built and not yet seen working is the sign-in itself**, because it
needs an account and creating one is the owner's to do. Everything up to that
point is observed: the bike asks the live project for a pairing code and gets
one, the server describes it back under the device's own name, `poll` with the
right secret and with a wrong one are indistinguishable, `claim` is 401 to
`anon`, and both web pages render against the real endpoint. **15.5.4 — every
policy checked from a second account — is still open and is still the item in
this phase that matters most.**

---

### Latest session — 3 August 2026 (twentieth sitting): the owner's five snags, and the phase one of them opened

**Five notes in the inbox, and a sixth about the inbox itself.** The owner's
rule, verbatim: *"even though you read my comments as one of the first things
you do, don't necessarily action them first. They should have plan entries
created and then triaged with just the same weighting as any other plan
items."* That is now how CLAUDE.md and the inbox's own heading read — **emptying
it is urgent, building it is not** — and it replaces the older line saying the
inbox outranks *What to do next*. All five are written up; four are built and
observed; the fifth turned out to be a whole phase. 516 JVM tests, 0 failures.

**The zone ladder's bounce was not the animation, it was the quantity being
animated.** `fractionThroughZone` is a position *within* a rung, so crossing a
boundary reset it from ~1.0 to ~0.0 and the spring drove the fill **backwards
across a whole segment** before growing again in the next one — a recoil at the
exact moment the rider looked down to see that something had changed. The
ladder was holding two coordinates where a rider reads one. `ZoneScale` now
carries `ladderPosition`, one number across all seven rungs, and the drawing
animates that alone. The property that says it is right is **monotonic in
power**, swept at every watt from 1 to 400 — a coarse sweep steps straight over
this defect, because it lived only at the boundaries.

**Two of them were confirmed by looking rather than by reasoning, and one of
those was the owner's own.** *"Output in watts … has a decimal place but gets
cut off"* — the ride screen's OUTPUT tile was rendering **`63.`**, two digits, a
decimal point, and the tenth clipped clean off. Whole kilojoules everywhere now,
which is 11.6.12 and the owner's call to make.

**The countdown had to be a gate, not a curtain.** A class used to start its
first interval and its clock on the same tick as the tap, so the rider was
already behind a Z1 target while reaching for the handlebars — and the opening
seconds of every ride on disk are somebody getting onto a bike, filed as riding.
Drawing a countdown *over* a running ride would have moved that defect rather
than fixed it, so `RideScreen` returns early and `startRide` is genuinely not
reached. Ten seconds, skippable, and a resume skips it outright — 8.3d's whole
argument is that the ride never really stopped.

**The heart beats, and the interesting part is what stops it.** The period is
the live bpm, so 180 is three beats a second and the owner's example is the
specification — but it is re-read at the *top of each beat* rather than keyed on
bpm, because keyed, the animation restarts mid-contraction every time the 2 Hz
display reading moves. And it stops dead when the reading does: a heart still
beating over a strap that has dropped out is 2.4.4's frozen cadence in the one
place a rider would be most alarmed to find it afterwards. Screenshots cannot
show motion, so it was **measured**: across a burst of captures the glyph swells
110 → 132 px and its green roughly doubles, resting between beats.

**The fifth note was the one worth arguing with, and the honest answer was
no.** *"Heart rate zones — pretty sure this is already covered."* It was not,
and the reason is the phase: **the app had no maximum heart rate for anybody**,
so it had no boundaries to colour between. Phase 21 opens rather than the colour
being faked. The order of the two inputs is the whole design — the rider's own
number is asked for first and date of birth is the fallback — because every age
formula has a 10–12 bpm spread between individuals, which is **wider than a
zone**, so an estimate gives a meaningful fraction of riders the wrong zones
outright. Asking for the real number is both more accurate and asks less about
the person, which is a rare combination. The estimate is Tanaka, not the folk
220 − age, and every screen showing it says it is one.

**Migration 11 → 12 is the mirror of 10 → 11 and lands on the other side of
it.** `resume_count` took `NOT NULL DEFAULT 0` because zero stated a *fact*.
There is no equivalent fact about a rider's heart: any default maximum is a
**guess about a body**, silently prescribing zones off a number nobody gave. So
both columns are nullable and every profile already on a tablet comes out with
no zones at all — which is correct until they are asked, and is the same rule as
a null heart rate.

**And driving it found a defect reading the diff would not have.** The *"use the
highest you've recorded"* offer read the rider's id from `uiState.value.profile`
— still null during the section's first composition — so a rider with **382
recorded samples was offered nothing**, with nothing on screen looking broken.
The same shape as 8.3d.4 and 7.10.3: the code is right about what it wants and
wrong about when it can have it.

**It was checked on the bike, not only on the AVD.** The owner switched the
tablet on mid-session and gave permission to install on it. Migration 11 → 12
ran against the bike's own database: `user_version` 12, the profile and all six
rides intact, and both new columns null — it invented nothing. And Settings
there offers *"Use 170 — the highest you've recorded"*, off the owner's real
strap data, which is the point at which 21.1.3 stops being a demo.

One stale note corrected while there: **the bike does have a profile now**, so
the line under *What to do next* saying it has none is no longer true.

---

---

### 3 August 2026 (nineteenth sitting): a crash is a pause nobody got to press

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

### 3 August 2026 (eighteenth sitting): what has to be true before the app goes online

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

**Two answers from the owner closed the sitting's open questions.**

*On the endpoint (**14.10.4**, now closed):* this build points, **through
environment variables**, at a Supabase project used by the owner's household
and *"one or two friends"*, and *"data is not an issue"*. So **there is no
community endpoint to fund and there never was going to be one** — the bill
argument was sized for a published default serving strangers, and four riders
is ~6 MB a year against 500 MB. Nothing about the shipped configuration
changes, which is the point of writing it down: `cloud.properties` stays
checked in and **empty**, now for the *stronger* reason rather than the
speculative one — the endpoint is the owner's household's, so checking it in
would hand a private project to every clone of a public repository. The env
layer is exactly what 14.10.2's precedence was built for. **One thing the
answer deliberately does not settle**: volume is not isolation, and *"one or
two friends"* means real people's rides sharing one project, which makes
**15.5.4** more load-bearing rather than less.

*On applying `003` (**14.2.1a**):* authorised — *"I'm happy for you to delete
all data on new installs of the APK. We are still building the app."* Not run,
because the session was asked to update the plan and stop. Two things are still
to decide when it is: **run it before 15.1 rather than after**, accepting a
window where the cloud is unreachable by anything (it already is — no profile
has an `auth_user_id`), because the alternative is a window where a real
session exists and `USING (true)` is still live; and the single `workouts` row
stays, because 14.4.5 wants to `pg_column_size()` the pre-14.4 shape and it is
the only specimen.

*And a third thing that is new work rather than an answer:* **auto-cleanup**,
in the owner's words *"old rides condensed to just basic information rather
than full tick-by-tick record"*. That is **23.4.2** exactly, and it lifts
23.4's *"do not build this yet"*. The design was already right; what this
sitting changed is what has to happen first. **16.3.3a is now a hard
prerequisite** — `personalBests` re-scans every measured ride's samples on
every load, so trimming would **silently make a rider's five-second and
twenty-minute bests worse**, unannounced, on the screen that exists to show
their training is working. Calibration is *not* affected (the grid is
accumulated live and stored serialised — checked, not assumed). And the
model does something interesting to it: for a signed-in rider a local trim is a
**cache eviction** with the cloud holding the original, and for a rider on the
middle rung it is **deletion** — two features wearing one name, and one
confirmation dialog must not mean both.

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

### 2 August 2026 (fifteenth sitting): four items, and a query the bike answered on its own

No rider, and none needed. The tablet AVD for everything with a screen, and
**the bike's own database for the one question that had been waiting on
hardware** — which turned out not to need a rider at all, only 1,661 rows that
were already there. Closed: **25.4.2**, **16.3.1 / 7.10.1**, **14.4** (with
14.4.6, the item it was blocked behind), **23.3.1**, **7.10.4** and **7.10.5** —
which finishes **Phase 7**. 443 JVM tests and 9 migration tests, 0 failures.
Three new items came out of it.

**The owner answered 25.4.2 in the plan file: rename them.** `END-08` was
called "Seated Climbs 45" and not one block in it said *seated* — the same
defect 25.1 opened with, pointed the other way round. R11's half-a-class cap
was never the thing that was wrong and has not been touched. **It was four
classes rather than three**, because auditing the titles turned up `END-12`
doing the same at Z2, and an audit is only worth having if it is finished. The
rule the four leave behind is in `classlibrary/README.md` under R10: **a
position word in a title is a promise that the blocks say it too** — and "big
gear" is a position word, because in cycling usage it means seated torque and a
rider reads it as the instruction. `SWT-09` "Big Gear / Fast Legs 45" keeps its
name, since its big-gear blocks really are marked `SEATED`. The new names come
off the axis the data does carry: *Tempo Climbs 5×5*, *Climb and Spin*, *Low
Cadence Sweet Spot*, *Low Cadence Threshold 4×4* — which is also what the rest
of the industry calls them. A rename is safe where 23.2.6's rebuild had to take
new ids, because the title is not the foreign key.

**The FTP trend got the screen it had been waiting for (16.3.1 / 7.10.1).** The
dashboard card answers *where is it now*; this answers *how did it get here*.
Two decisions are about honesty rather than drawing. **A mark per change says
how the app came to believe it** — filled where it measured the value off a
ride, hollow where the rider typed it, which is the distinction
`FtpChangeSource`'s own documentation opens with, drawn rather than described;
`PulledFromCloud` is hollow too, because another device's arithmetic is not
this bike's measurement. And **the first value is not a change**: it is where
the number began, so it has no mark and no row. The third came out of looking
at it — **the axis runs to *now*, not to the last change**, because stopping on
the day of the last change says the record ends there when the flat run to the
edge is the rider's answer to "how long have I been at this".

**Then 14.4, which had a precondition, and the precondition is the interesting
part.** 14.4.6 said settle the `getFloat().toDouble()` question first: if the
board reports fractional values, the noise digits are in the payload, the
charts, the exports and the calibration grid. It has been sitting there marked
as needing the bike — and it needed the bike only in the sense that the bike
was already holding the answer. One `sqlite3` query over 1,661 recorded rows:

- **The board does report fractional power and the digits are real.** Tenths of
  a watt, off the `0x44` frame. 1,360 of the rows are fractional.
- **The noise the finding feared existed and had already been fixed.**
  `29.2000007629395` is `29.2f` widened, and it appears only in the three rides
  recorded *before* 2.7c — the fix that made the frame decide the metric also
  took the value off `getFloat()`. Nothing is rewritten; those rides are
  already marked suspect by 2.7.5.
- **Cadence and resistance are integral in every row**, which turned out to be
  worth 11 KB a ride.

That last one is why the payload landed where the storage budget said it would.
The first columnar draft measured **64 KB**, not 49 — `80.0` is two characters
more than `80`, across three columns and 2,700 samples. `CompactDouble` writes
a whole number without its decimal, which is not a rounding, and the round-trip
test now reports **49 KB against 228 KB** with both shapes built from the same
samples. The version went **inside** the payload rather than in a column beside
it, against the item's wording: a column and the JSON it describes are written
by different code and can drift, and a version that disagrees with its payload
is worse than none.

**And the backup reminder (23.3.1), which is a design problem disguised as a
feature.** The hard half is not knowing when to speak, it is knowing when not
to. It counts **rides, not days** — a rider off the bike for a fortnight has
lost nothing since their last backup; time passing is not risk. **"Not now"
moves the line rather than silencing it**, one mark serving both a backup and a
dismissal because the reminder only asks one question. And **never having
backed up does not lower the bar**, because a rider three rides in has nothing
to lose yet and an app that opens with a warning is one whose warnings are gone
by the day they matter. The mark is written only on success: recording a failed
backup would tell the rider they are safe on precisely the day they are not.

**And the two items that finish Phase 7, which are the same principle twice:
the app must not edit the rider's record behind them.** Declining a
breakthrough cleared a field in memory and nothing else, so closing the summary
and reopening it asked again about a ride the rider had already answered for —
and asked often enough, "no" stops being a decision and becomes a thing to tap
past, with a permanent change to their own record on the button beside it. It
is written on the ride now (migration 8→9), because it is a fact about a ride:
it travels in the backup and it goes away when the ride does. The other half is
the accepted case — an auto change can be **put back in one action that appends
a row rather than erasing one**, since deleting it would be a second edit
covering the first and leaving a history saying nothing ever happened.
`AutoBreakthroughReverted` earns its own source: "I set this myself" and "the
app moved my FTP and I disagreed" are different events, and only the second says
the app was wrong.

Three items opened. **25.4.3** — the rename put `SWT-05` and `THR-06` in the
same words and made visible that they are nearly the same class, identical work
differing only in the recovery, which is a small version of what 23.2.6 was
complaining about. **14.4.7** — the new payload drops `power_is_measured`, the
one thing it does not carry, and `PowerProvenance` gates real decisions.
**23.3.1a** — cloud backup is per profile and the backup file is per tablet,
which nobody has to answer until Phase 15 exists.

---

### 2 August 2026 (fourteenth sitting): the record stops editing itself

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

---

### 1 August 2026 (thirteenth sitting): a library that was designed, and the instruction it could not give

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

---

### The session before it — 1 August 2026 (twelfth sitting): the record explains itself, and the numbers hold still

No bike, no rider — the whole sitting ran on the bike tablet's *existing*
database and on the tablet AVD. Eight items closed: **2.7.5**, **11.6.7**,
**11.6.8**, **13.8**, **11.6.9**, **11.6.10**, **16.1.7** and **16.1.8**.
380 JVM tests, 0 failures. **That was the whole of the first real ride's snag
list except 22.2.6 and 23.2.6**, the two substantial ones — both of which the
thirteenth sitting then closed.

**With 11.6.9 and 11.6.10 the ride screen no longer traps the rider.** Pairing
a strap, changing the telemetry source and fixing the coach volume are all
things a rider finds out they need mid-class, and every route to them used to
cost the ride. They are now a sheet over it, reachable from a gear on the ride
screen, from the dead-end heart-rate card, and from inside the overlay's volume
panel. Telemetry was switched to Hardware *mid-ride* on the AVD to prove it.

**2.7 is now closed apart from the serial-port leak underneath it.** 2.7.5 asked
what to do about the rides recorded before the frame fix, and the answer is
**mark and say so, and change nothing**. `RideIntegrity` counts a ride's
impossible samples on read, against the same bounds the recorder rejects on and
**whole-row** — one impossible field condemns the other two, because that is
precisely what the labelling defect does to them. Ride detail draws its charts
from the samples that survive and says so, with the stored average and the
corrected one side by side rather than one quietly replacing the other.

It found **three** corrupted rides on the bike, not the two the plan expected —
and, more usefully, a fourth ride recorded *after* the fix with **zero**
impossible samples. That is the fix seen from the record's side.

**Two of the four items had the wrong cause written down, and both were found
by measuring rather than by reading.**

- **11.6.8** blamed a border that adds to layout width. Compose borders draw
  inside the element and add nothing. What actually moved was the column on
  either side of the ladder sizing itself to its own text: "TEMPO" and
  "NEUROMUSCULAR POWER" are the same element one zone apart, and 99% to 100% is
  a whole digit. Both now reserve their widest string.
- **13.8** said the label ignores the rider's preference. It does not — it
  follows it, and the preference on a fresh install is a *guess from the
  locale*, which is not the answer to this question for anyone in the UK. The
  fix is a kg/lb control, not a better guess.

**The technique worth carrying: a 2 Hz display update is hard to photograph, so
make it a 3 s one.** 11.6.7 was verified by temporarily setting the display
interval to 3000 ms and sampling the raw framebuffer: the cadence figure
changed at 3.04, 3.05, 2.75, 2.99, 2.99, 3.01, 3.03 seconds — the pacing
exactly, with no timing argument to make — while the *same ride* wrote 60 rows
across a 60-second span, every one a distinct cadence. Screen slowed, record
untouched, both measured in one run. Then restored to 500 ms.

**And the cost of 13.8 is in the database, not on the screen.** 77 typed into
the old dialog on an `en-US` device stores `34.93` kg. Half a rider — and the
number kJ/kg divides by on the household leaderboard.

### Latest session — 1 August 2026 (eleventh sitting): the frame says what it is

**2.7 is solved.** Not fenced, not contained — the cause is found, the fix is
in, and both halves were verified on the bike. **Read 2.7c, then 2.7d.**

**`msg.what` was never the identifier.** Peloton's service passes the board's
own wire frame in `responseHexString` beside every value, and the frame is
self-identifying: `F1 <id> <len> <digits, least significant first> <checksum>
F6`, with `0x41` cadence, `0x44` power (tenths of a watt), `0x49` resistance —
and **`0x4A`, raw resistance, which is the intruder of 2.7b**. `msg.what` is
assigned by position in the service's request cycle, so anything that disturbs
the cycle slides the labels along while the payloads stay put. Provoked on the
bike with a second sensor app: **55 of 204 messages carried a payload
disagreeing with their own label, and a stationary rider was reported at 544
rpm.**

So `PelotonFrameParser` decodes the frame and the frame decides. The
raw-resistance report is dropped **by identity rather than by plausibility** —
which is the difference between a fence and a fix, because no bound could ever
catch it in the power column, and that was the 636 W spike on the first real
ride's chart.

**Verified on the bike, one app, rider pedalling: 1609 messages on the ride
screen and 464 with the overlay up, zero mislabels and zero dropouts in both.**
The ride it recorded has 200 samples, no impossible values and no gaps.

**And the overlay is exonerated.** It was never the cause; it correlates with
*leaving the app*, and on this tablet that can mean a second bike app binding
the same sensor service.

**Underneath it, a second defect worth more attention than it has had (2.7d):
`SensorService` opens the exclusive UART inside `onBind`.** One port, one open,
so two bike apps can never both work — and the port **leaks**: the app sat dead
on retry attempt 141 after the other app was gone, and the tablet needed a
reboot. That is the *silence* half of 2.7, and it is why pedalling never
revived the first real ride. 2.7.7 and 2.7.8 are what remains of it.

Two techniques worth keeping. **The whole diagnosis cost 90 seconds of
pedalling**, because the decisive captures were taken with the rider stationary
— resistance polls regardless, and a cadence of 544 with nobody on the pedals
needs no interpretation. And **this tablet has `log.tag=W` set globally**, so
every `Log.i`/`Log.d` in the app is dropped device-wide; three trace attempts
produced nothing at all before that was found.

### The session before it — 1 August 2026 (tenth sitting): nothing impossible reaches the record, and a dead board comes back

**Read 2.7 and then 2.7a.** The defect the ninth sitting handed over — the
overlay corrupting telemetry and the corrupt values being *recorded* — is
**contained but not cured**, and the distinction matters more than the tick
marks:

- **2.7.3 and 2.7.4 are done and observed.** A plausibility fence with
  physical bounds, applied where readings are published and again where they
  are written; and `failOnSilence`, which turns a source that has stopped
  delivering into a source that failed, so the app's one retry policy rebuilds
  it. On a 213-second AVD ride carrying 30 seconds of the bike's exact
  corruption signature and 20 seconds of a dead board: **0 impossible values
  recorded, four gaps where the lies used to be, and telemetry alive again at
  122 s without an app restart.**
- **2.7.1 is half done and the half that is done is not the cause.**
  `TelemetryAssembler` replaces three `var`s that started at `0.0` and were all
  emitted whenever any one moved — so every hardware ride's first message
  published two measured-looking zeroes and every reading after it mixed
  instants. Real, and now tested. But the rotation itself is still unexplained,
  and the leading theory has moved: nothing in this app collects `readings()`
  twice, so a second registration is more likely **not ours**. Peloton's own
  app binds the same service, and one UART multiplexed between two clients
  produces exactly this signature. A counter now logs at `E` the moment two
  registrations are live, and the next ride reads it.
- **2.7.2 needs one minute of pedalling.** Every unhandled `msg.what` gets its
  bundle logged. That is the whole instrument.

**The technique worth carrying: the bike is a perishable resource, so the
defect was brought to the emulator.** Two debug broadcasts — `CORRUPT` and
`SILENCE` — sit beside the `COAST` lever from 19.1.2a, and the corruption is
modelled on the measurement rather than invented: 41 samples in 53 carry the
ghost near 602, as they did on the bike.

**The thing to be honest about, and it is in 2.7a as a test:** the recorded
ride's peak cadence is 173 rpm — a power value in the cadence column, entirely
possible as a cadence, invisible to any bound there is. **The record is now
free of the impossible, not free of the wrong.** 346 JVM tests, 0 failures.

### The first real ride — 1 August 2026, on the bike, with a rider

**Read this before picking anything up.** The app was ridden for real for the
first time: a 20-minute class, `HC-01`, 1196 samples, every one of them
`power_is_measured = 1`, and `avg_hr` exact against its own samples. The
recording path works on real hardware.

**And it found a defect that outranks everything else in this plan.** Raising
the overlay corrupts telemetry: cadence, resistance and power start appearing
in each other's columns, and the corrupted values are **recorded**, not merely
displayed. Measured on the bike with the rider pedalling steadily — 82 seconds
full-screen, then the overlay raised and nothing else changed:

| Phase | Samples | Impossible values |
|-------|---------|-------------------|
| Full screen | 82 | **0** |
| Overlay up | 53 | **41 (77%)** |

The rider was averaging 61 rpm and 47 W. **The ride summary reported 109 RPM
and 137 W.** The whole diagnosis, the evidence and the fix direction are in
**2.7**, which is where the next session should start.

Two things about it worth carrying separately from the item:

- **The overlay is the product's headline feature and it is the thing breaking
  the record.** 10.4 verified the overlay *renders* over video, which it does.
  Nobody had checked what it does to the data underneath.
- **The database was the witness, not the screenshots.** Two screenshots 15
  seconds apart cannot show two surfaces disagreeing; `workout_metrics` records
  what the recorder actually saw, once a second, with a timestamp. That is what
  turned "the overlay looks erratic" into a 0-vs-41 measurement in a quarter of
  an hour.

**Eight more snags came off the same ride** and are filed as 11.6.7 (numbers
update too fast to read), 11.6.8 (the zone ladder shifts sideways at every zone
change), 11.6.9 (a blank heart rate is a dead end), 11.6.10 (no way to reach
Settings without ending the ride), 13.8 (profile creation asks for pounds and
never asks which), 16.1.7 / 16.1.8 (the charts have no axes — the heart-rate
one is a line with no numbers on it), 22.2.6 (the width cap is one screen's fix
rather than a rule) and 23.2.6 (**the classes are not good enough; rebuild the
library**).

### Latest session — 1 August 2026 (ninth sitting): the model built, and the column three features were waiting on

The eighth sitting settled what offline and online mean. This one made the
code agree with it. **Phase 23's first two sections are done and observed** —
23.1.1 through 23.1.6, 23.2.1, 23.2.2, 23.2.5 and 23.3.2 — on the tablet AVD.
308 JVM tests green, both migration tests green.

**The class library ships in the APK.** All 72, 104 KB of JSON, 9 KB
compressed — the non-decision 23.2.1 predicted. `ClassTemplateSeeder` no
longer knows Supabase exists, so the first path a fresh install takes makes no
network call at all.

**The gate is `auth_user_id`, per profile, in one place.** `CloudAccess`
replaces `SupabaseModule.isConfigured` as the answer to "may we?", and
`SupabaseSyncRepository` asks it before it even resolves the client. The part
worth keeping is structural rather than clever: **no cloud method can be
called without naming the rider it acts for**. `fetchClassTemplates()` used to
take nobody.

**A fourth violation of rule 1 turned up that the plan had not listed.**
`UserRepository.save` upserted a profile's name, weight and FTP to the cloud on
every create, rename and edit — so a rider who never signed in had their name
in Supabase from the moment they typed it. Found by grepping for the *client*
rather than for the features known to use it, which is the technique to reuse.

**The consequence to be clear about: no build can reach the cloud now.**
Nothing sets `auth_user_id`, because Phase 15 does not exist. That is rule 1
working, not a regression — but **14.1.6 is unreachable from the app** until
sign-in is built, and 23.1.2 had to be observed by setting the column by hand.

One observation was not designed for: a sync queued while the profile had an
account, then run after the account was taken away, **refused itself**. The
worker re-checks the gate rather than trusting the enqueue, and that is what
sign-out will need (15.4.1).

**Then the column.** `workout_metrics.power_is_measured` (migration 3→4)
records per sample where a watt came from — it had always existed on
`SensorReading` and was thrown away at the database boundary. **Three separate
features were blocked on it and all three closed together**: the chart caption
that could not say what it was drawing (16.1.6), the FTP proposal a simulated
ride could make (7.10.7), and the household leaderboard that must not rank
fiction beside fact (24.4.2). It is nullable with no default, because every
sample already on a tablet was recorded when nothing knew the answer, and
`Unknown` is treated exactly as `Modelled` by everything that decides anything
— but they are not the same claim and `PowerProvenance` does not pretend they
are.

**And 24.1, the household leaderboard**, which is rule 3 made real: a Room
query, no account, no network. On class detail and the post-ride summary,
ranked on kJ with kJ/kg beside it. The AVD case is the one the design is for —
Simon 240.0 kJ / 3.11 kJ/kg, Alex 210.0 kJ / 3.56 kJ/kg — the two numbers
disagree and both are shown. 321 JVM tests, 21 DAO tests, 4 migration tests.

**The verification technique worth carrying forward**: the emulator can only
produce simulated rides, and the board excludes those by design, so both this
and the consent gate were driven by editing one column in the tablet's
database by hand (pull, `sqlite3`, push back through `run-as`). Note also that
`connectedDebugAndroidTest` **uninstalls the app**, so it wipes the profiles
and rides a UI session has just set up — run instrumented tests before driving
the UI, not between.

### The session before it — 1 August 2026 (eighth sitting): the connectivity model, settled

No code. The owner settled **what offline and online mean in this app**, which
had been drifting: the app was half offline-first by design and half quietly
online by accident. The answer is written out in full in *The connectivity
model* below and it is now the section that wins over any older item that
disagrees with it.

Three things in the shipped code contradict it today, and they are listed in
that section rather than in *Corrections*, because they are not defects against
the plan as it stood — the plan asked for exactly this. They are defects
against the model as of now. The largest is the smallest to describe: **an
install with no account still talks to Supabase**, both on first launch (the
class seeder) and after every profile ride (the sync worker).

The consequence with real teeth is **23.2**: the class library lives in the
cloud, and only five of its seventy-two classes are bundled in the APK. Under
the old model that was a fallback nobody would hit. Under this one it is what
the default rider gets.

Two new phases came out of it — **23** (making the ungated tier complete) and
**24** (household social, which needs no cloud, no account and no network, and
should be built before anything in 17 or 18).

**The storage question was asked and is answered with measurements, not
estimates**, in *What a workout costs* below. Short version: a 45-minute ride
is **292 KB in the local database** and roughly **25–30 KB stored in Supabase**
after TOAST compression. Four riders at a ride a week is about **6 MB of cloud
a year**. The 500 MB free tier is not the constraint for a household — it is
somewhere around 13,000 rides — so **no purging feature is needed for the
reason it was proposed**. Three other things fall out of the measuring, and
they are the reason the section is worth reading: the *local* database fills
seven to ten times faster than the cloud does, a published community endpoint
(14.10) would fill the free tier inside its first year, and there is a
float-to-double widening in the sensor path that could triple every payload
and is one query away from being settled.

### The session before it — 1 August 2026 (seventh sitting): the ladder, the bottle stop, and a rider who could not stop

Straight down the *What to do next* list, on the tablet AVD, no bike.

Closed: **11.1a.6** (the ride notification that was never posted on Android
13+), **11.6.2a** (the zone ladder, which replaces the `CurrentZoneBar` the
sixth sitting shipped and closes the overlay gap 11.6.2 left behind), **19.1.2**
(auto-pause), **19.1.2a** (new, and the reason 19.1.2 could be ticked at all),
**19.1.3 / 12.4.4** (backup and restore) and **11.5.9** (two ways out of the
overlay's volume panel). 291 JVM tests green.

**With that, the fifth sitting's readiness pass is finished** — every blocker
it found is built and observed, and a rider now has a way to get their whole
history off the tablet and back onto one.

**The backup work turned up a bug of exactly the shape this plan collects.**
The SQLite magic is `SQLite format 3` followed by a **NUL**, and it had been
written with a trailing space, so every genuine backup was refused with "that
file is not a Pelonot backup". The unit tests all passed, because they built
their test header out of the same wrong constant. A test written from the spec
rather than from the code is the only thing that could have caught it, and
there is now one.

**19.1.2a is the find of the sitting, and it is about verification rather than
about the app.** Auto-pause is a feature about a rider *stopping*, and the
simulated rider **never stops** — smooth effort wave, cadence never below about
60. So the whole family of behaviour around standing still (auto-pause, the gap
a stop leaves in `workout_metrics`, what the averages do across one) could only
ever be seen with a person on the pedals, which `CLAUDE.md` rightly calls a
perishable resource. The fix is four lines in the simulator and a receiver in
the **debug source set** — `adb shell am broadcast … --ei seconds 40` makes the
simulated rider stop — and nothing of it exists in a release build. Every
observation ticked under 19.1.2 came from it.

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
| No gesture to dismiss the HUD's volume panel | **11.5.9** | ✅ |
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


### Latest session — 5 August 2026 (thirty-second sitting): polish, and a demo of it

**The ask was polish and a demo video to show people, so the method was to
drive the whole flow on the tablet AVD and fix what the screens actually
showed.** Nine changes, every one of them found by looking rather than by
reading code. 645 JVM tests, 0 failures. The bike was attached the entire
session and never touched.

**There is a recording now** — the profile picker, the dashboard and its
household, the class library, Start Class with the leaderboard beside it, the
countdown, the ride screen with the board running live, **the overlay over
YouTube**, the summary and its charts, and history. It is 2:55 and it is the
shortest answer to *what is this* that exists.

**The live board was the thing that nearly did not make the video**, and the
fix was already in the repo: a simulated ride can never be ranked (24.4.2), so
the app's best feature is invisible on an emulator. `RaceDebug` — built in the
thirtieth sitting for exactly this — lets the *live* comparison run on modelled
watts while `power_is_measured` still records the truth, so the ride is
correctly excluded from every board afterwards. Seeding fake measured rides was
the obvious move and would have been the wrong one.

**The units were being cut off the live readouts, and the race chip was never
why** (24.3.16). `100 RPM / 296 W / 188 BPM` drew as `100 RP`, `296` with the W
gone, `188 BP` — with no race chip on the band at all. The previous sitting had
put the owner's *"the power numbers it's all crammed in and clipping"* down to
a 132 dp chip starving the weighted readouts, taken the chip out, and the
clipping stayed. The cause is older: value and unit were both unweighted in a
`Row`, so the number was measured first and the label got the remainder. The
number is the weighted one now, sized against `"000"` rather than against
itself — sizing on the live value would resize the readout every time it
crossed 99, a number pulsing under the rider. **24.3.16 is corrected rather
than reopened**: the owner's decision stands, but "there is no width that buys
a fifth chip" was measured against a row already over-committed by this bug.

**R10 had said the right thing for months and nothing checked it** (and it is
now item 7 on STATUS.md's *what is wrong* list, as evidence about the other
rules marked *not tested*). It says a title names the shape and the demand,
*"not the category and the length, which the rider can already see"* — and all
72 titles ended in their own duration, so "The Long Climb 30" was drawn beside
a chip reading `30 min`, on the library, the start screen, the ride screen and
every row of history. Both halves fixed: the durations are gone and `build.py`
refuses to put them back. The check had to learn two things by being run —
match the class's *own* length rather than any trailing number (`SWT-01` is
"Sweet Spot 5 + 4"), and check uniqueness, which the duration had been doing
quietly. No id moved, so nothing retired: 72 templates, 0 retired, rides on
`END-01` still resolve.

**The dashboard's household panel had no ceiling** — 24.1.8 capped the class
leaderboard and stopped one card short, and the same argument carries over
exactly. Twelve profiles drew twelve rows. `HouseholdPanel.of` windows it at
six, keeps the top of the list and **always the rider's own row**, marks a lift
from below the cut with `⋮`, and counts what it dropped. Two things found while
in there: `Spacer(Modifier.width(...))` twice inside a `Column`, which is
nothing at all, and the Just Ride card stretched to its neighbour while its
content wrapped, so `CenterVertically` centred nothing and the title sat on top
of 250 dp of empty teal.

**And three lines were saying the machinery out loud.** The owner's call on the
goal prompt: *"let's hide away the +5% and -5%, it's too geeky, the user
doesn't need to 'see behind the curtain' on this one"* (26.1.5). Beside it,
`END-01`'s shape sentence read "one 1 min effort at Tempo", where the word and
the numeral say the count twice; and Settings still promised your rides appear
"on the week summary everyone here can see", describing a screen 22.5.4
replaced with 30 days — in the one place a rider goes to find out what other
people can see. The FTP card's 4 dp teal gradient bar is gone too: on a card
whose whole content is one measured number, a full-width filled bar is read as
a meter, and a meter permanently at 100% is a claim nothing here is making.

**One negative result, and it cost most of an hour.** The summary's leaderboard
looked clipped mid-row, the diagnosis was `IntrinsicSize.Min` under-measuring
wrapping text, and a `SubcomposeLayout` equal-height row was written to replace
it. It was not clipped. It was the scroll viewport, and the original was
correct all along — the board renders whole, `Hana / Simon / Ivy / and 6 more`.
Reverted in full. The lesson is the cheap check that was skipped: **scroll
before believing a clip.**

**Then the owner's inbox arrived mid-session with a high-priority note, and
it is built.** *"Auto-generated leaderboard ghosts to ride against … there
should always be some target … no matter how high you go there should always
be a target ahead of you."* Written up as **24.3.18** with five candidates and
the argument attached rather than as a menu, four of them chosen and built:

- **The plan** — the class ridden at the middle of every band it prescribes,
  and the one worth arguing for. It is the only non-arbitrary target, catching
  it means *I rode what the class asked for*, and it needs no history — so it
  is on the board for the first attempt at a class nobody has touched, which
  with 72 classes and a four-person household is **the ordinary case**. It does
  not touch `PowerModel`: a zone target is watts already.
- **Just past your best**, **Your usual** (the median, and the only row
  deliberately *behind* a rider on form), and **a round number that moves** —
  a pace rather than a trace, because a fixed total cannot satisfy *however
  high you go*.
- And **"Tom's last ride"**, on the owner's follow-up: a best is a monument
  and can be two years old, a last ride is news.

**The board is six rows now, and the owner was right that there was space.**
They said so from memory and it was settled by measuring rather than by
agreeing: `uiautomator` puts the rows 66 px apart and the Overlay button at
y ≈ 672, so six fit and a seventh collides. The rest scrolls, and the list
follows the rider as they pass and are passed.

**24.3.12a is finally shut**, after a fortnight open with the owner's name on
it — and what closed it was ghosts arriving, because a second family of
invented name on a board already reading `12 MONTHS / 30 DAYS / Ava` would
have been two problems instead of one. The fault was nameable: every other row
is a person and those two were durations. Every non-human row is a sentence
about the rider now.

**The one distinction worth carrying forward is two flags rather than one.**
`GhostKind` has `isPerson` and `isGenerated` separately, because the rider's
own best is neither a person nor invented — conflating them would either mark
a real ride of theirs as fictional or let a generated target be counted as a
rival, and the second is precisely what the honesty rule exists to prevent. So
a generated row carries a `○` and colour goes on saying *is this me*.

**Two things written up rather than built**, both because they are the owner's
call and not a session's. **26.1.6**: there is no way to ride a class at the
zones it was authored with — two intents, both ±5%, so every ride this app has
ever recorded is off the catalogue that a build refuses to let anyone break.
Raised, and the owner's answer was *"leave it entirely"*. **22.7.4**: 22.7.1
centred a day that holds one ride and left the day's *heading* hard against the
left edge, so a section header no longer sits over its section — most visible
on the last screen of the demo, and fixable in two opposite directions.
