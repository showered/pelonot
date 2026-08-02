> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

# Session log — where the work stood, sitting by sitting

The latest sitting lives in [PLAN.md](../PLAN.md). When it stops being the
latest it comes here, to the top, unedited. Below that are the 31 July snag
list and the three narratives that changed the shape of the project.

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
