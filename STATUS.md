# Where Pelonot is

**Written 4 August 2026, updated 16 August (fifty-seventh sitting).**
Measured, not estimated: `assembleDebug` passes, **795 JVM tests, 0
failures**, the 21 migration tests green including 20 → 21, and the
whole 113-test instrumented suite last run green in the forty-fourth sitting.
**618 of 837 plan boxes** are ticked — counted as
`grep -c '^- \[x\]' PLAN.md plan/*.md`, which is worth writing down because the
figure this page carried before (*588 of 805*) was counted some other way and
had drifted, across 28 phases. It is a summary —
every claim below belongs to a phase file and
names the item, so the reasoning is one hop away in [PLAN.md](PLAN.md) and
[plan/](plan/). Nothing is decided here.

> **There is a demo recording as of 5 August**, driven on the tablet AVD at the
> bike's own 1280 × 720 dp: the profile picker, the dashboard and its
> household, the class library, Start Class with the leaderboard beside it, the
> countdown, the ride screen with the board running live, **the overlay over
> YouTube**, the post-ride summary and charts, and history. It is the shortest
> answer to *what is this* that exists.
>
> **The dashboard in it is out of date twice over, as of 10 and 16 August.**
> The owner's first note — *"seems very stretched"* — was measured at 993 dp of
> content in a 664 dp viewport and answered: History and Settings are doors in
> the greeting row rather than cards, and the household panel is beside the
> rider's own numbers instead of below the fold. **And the primary action is no
> longer a door at all** — it names a class to ride, chosen from the rider's own
> history and saying why (22.8.6, 22.8.11). Their second note was about what
> that left behind — *"lots of unused space… we shouldn't have empty space"* —
> and the screen now fills its own fold without stretching anything: the offer
> card **draws the shape of the class it is offering**, and the right-hand rail
> carries **the rider's ride-days grid** when there is nobody else on the bike
> (22.9). Everything else in the recording still stands.

> This page exists because the plan answers *why* extremely well and *where are
> we* not at all (19.1.7). It is rewritten by whichever sitting changes the
> picture rather than patched, so treat the date above as its shelf life.

---

## The one-paragraph answer

**The bike half is finished and the cloud half is a week old.** A rider gets on
a stock Peloton, picks a profile, is offered a class to ride by name — chosen
from their own history and saying why — starts it or picks another of the 72,
watches Netflix with a translucent overlay of their own numbers on top, and ends
up with a per-second record that is theirs — measured watts off the board, not
estimated — with charts, an FTP that corrects itself, heart-rate zones, a household
leaderboard and an export. All of that is built and has been observed working on
the real hardware. What arrived in the last two sittings is everything *off* the
bike: accounts, a cloud backup that has been seen making the round trip, a
companion web app that is now hosted and has grown up — your rides with
totals and twelve weeks of output, one ride with its zone bands and time in
zone, the leaderboard, a feed with kudos and comments, and a profile you can
actually edit from something with a keyboard — sign-in by scanning a QR code
with a phone, and one leaderboard with everybody on it. That half works and is thin —
it has been ridden by one household for two days, and the first thing the owner
tried on it was broken. **The gap between here and finished is not features. It
is a handful of first-run and honesty problems that any new rider would meet in
their first ten minutes**, plus one setting on the Supabase dashboard that is
currently open and should not be, and one deploy that has not been run.
**One of those first-run problems turned out to have been fixed weeks ago and
was still being listed here** — see *What is wrong today*, item 7.

**And three more of them were found in the fifty-sixth sitting by walking that
first ten minutes end to end from a wiped app** (20.4.3): the account offer at
the end of setup showed a spinner where a QR belongs and **never asked the
server for a code at all** (20.4.7); Android's notification permission was put
in front of the rider **21 seconds into their first class**, while they pedalled
(20.4.8); and *Not now* on the overlay prompt was **un-answered by the very next
resume** (20.4.9). All three are fixed and watched. All three were invisible to
anybody with a profile already made and the permissions already granted, which
is everybody who had checked this app before.

---

## The five tiers, and where each one stands

| Tier | What it is | State |
|------|-----------|-------|
| **1. The ride** | Telemetry, the service, classes, the overlay, the ride screen | ✅ **Done and ridden.** The one open defect family is the sensor board's serial port (2.7d), which is Peloton's leak and not ours — and the app's own half of it is now built (2.7.7, 2.7.8): it stops rebinding a port that has never answered, and says *"restarting the tablet usually frees it"* instead of promising to reconnect for ever |
| **2. The record** | History, charts, FTP, heart-rate zones, export, migrations | ✅ **Done**, bar the cosmetic backlog. **Retention (23.4) is built**: a rider can ask for rides older than six months or a year to be condensed to an outline — the lowest and highest watt of each ten seconds, kept as real rows so nothing is an average the bike never measured. It is **off by default**, offers the backup first, and a condensed ride says so on its charts and in its exports (`metrics_detail_sec`). It is survivable because the three things that used to be re-derived from samples are now written down when the ride is recorded: its efforts (16.3.3a), where its watts came from (23.4.12) and what its seconds counted (`distributions_json`) — measured as an identical time-in-zone table before and after a trim. **And restore has been fixed after refusing every backup this build made** — a version number kept equal to another version number by a comment, which drifted (19.1.3a). **A ride ridden as a guest is no longer lost to the record either (12.4.1)**: every query on `workouts` is filtered to a profile and a guest ride has none, so once the post-ride summary closed the ride appeared on no screen in the app. Unclaimed rides now sit at the top of every rider's history under *Not filed against anyone*, and opening one asks whose it was |
| **3. The household** | Profiles, the household leaderboard, ghosts, streaks | ✅ **Done**, and it now has a **live leaderboard** — start a class anybody on the bike has ridden and you are racing all of them at once, ranked as you ride, against your own bests as well as theirs. Seen on the real bike as well as the emulator; what is owed is watching it move under somebody actually pedalling |
| **4. The cloud** | Accounts, backup, the web app, the everyone-leaderboard | 🔶 **The web app is a real app now, and it is not the one on the internet.** The fifty-third sitting built the whole of Phase 17 past the pairing page: your rides with totals and twelve weeks of output, one ride with power-zone and heart-rate-zone bands and time in zone, a name and a hide switch for it, the cross-bike leaderboard, a feed with kudos and one comment, and a profile with a bio, an FTP, a weight, a maximum heart rate and units. **Sharing is off by default** — the feed shows nobody until a rider turns their own on, which is how a social feature was added without widening what a stranger who signs up can see. Two defects came out of it that had been live for a fortnight, both a reader that had never met the real bytes: every *measured* ride's caption claimed its watts were unrecorded, and a condensed ride never said it was one. **`./web/check-deployed.sh` reports six files drifted**, so none of it has reached anybody yet. Previously: **Working end to end, two days old, and already caught out once.** Round trip observed, RLS verified from a second account, web app hosted — and the first flow the owner tried on it was broken, because a fix had never been deployed (17.16.6). **That is deployed and verified now (17.16.8)**, and the shape of the lesson stayed: what caught it was a command that diffs the internet against the repo, not the fix itself. **Sign-out and deleting your cloud copy are built and watched now (15.4)** — the second one signs the rider out with it, so a delete cannot undo itself at the next ride. **And the cloud has stopped being write-only (15.3.2)**: a rider's history can come back down onto a new bike, under a restore that only ever *adds* rides and never overwrites one already here. Building it found that the wire had never carried the ride's own facts — the FTP it was ridden at, what its seconds counted before a trim — so a restored ride would have had its zones redrawn from today's numbers; they travel inside the versioned payload now, with no cloud migration. It is built and tested and **has not yet been watched against the real endpoint**, which is the one thing left on it. Account deletion is still not built |
| **5. Ready for someone else** | First run, onboarding, CI, the polish backlog | 🔶 **The onboarding gap is closed.** The first thing a new rider meets is a designed screen rather than three text boxes: 20.3 asks a name, a weight, a birth year and one sentence about your riding, estimates an FTP rather than demanding one, and now offers to back it up by account (15.8) rather than leaving that for Settings to mention to nobody. **The whole path was then walked end to end in the thirty-fourth sitting** and four more faults on it were fixed: two controls that did not line up (20.4.5), Android back throwing away every answer (20.4.6), a pairing code that outlived the screen showing it (15.6.13), and the confirmation email pointing at `localhost` (15.7.6). **The thirty-fifth sitting then fixed the one the owner could not get past**: signing in by QR from a phone that was *already* signed in sat on three dots for ever, because the pairing page called Supabase from inside an auth callback and deadlocked the session lock it holds (15.6.14) — reproduced, measured with `navigator.locks.query()`, and fixed. The code is also shown by default rather than behind a button and replaces itself while the screen is up (15.6.15). What is left is **not the app**: the project's confirmation emails go through Supabase's built-in test sender, capped at **two an hour** and documented as refusing addresses outside the project team, so a real sign-up cannot be completed or repeated until custom SMTP exists (15.7.7 — the owner's). **And two first-run faults are closed.** *(11.6.15)* The overlay prompt's three buttons included one — *Don't use the overlay* — that turned the app's primary surface off for good, with nothing saying so or where it came back; Settings already offered it back under the same name, so it is one sentence at the moment of the decision. **The whole path was then walked end to end again in the fifty-sixth sitting, from a wiped app, and three more first-run defects came off it (20.4.3)**: the account offer's QR **never arrived** — a spinner where a code belongs, because the trigger that asks for one never mentioned the profile it needs (20.4.7); the **notification permission was asked 21 seconds into the first class** rather than in the countdown where 11.6.14 already put the other one (20.4.8); and ***Not now* was un-answered by the next resume**, because the check knew only that the permission was still ungranted (20.4.9). **And the app now says what it is (19.1.6).** A rider who had just side-loaded this onto their bike met *"Who's riding?"* over two grey tiles, and that was every word the app said about itself; an empty profile list is the only first run there is, so the picker's empty state is the first run — the app's name, one sentence about the overlay, and *Set up · Four questions, then ride* filled and first. **Three of that item's four claims turned out to have been false for weeks** and it was the top row of the blockers table the whole time: the FTP has not been prefilled with 200 since 20.3, the overlay permission is asked during the countdown because the owner asked for it there (11.6.14), and a strap is one tap off the ride screen (11.6.9). Beside that: a green CI run (19.1.4), which needs a pull request rather than a push and has never once fired. **The web deploy has now happened and the command is written down at last** — it is `git push` (17.16.2). See *How close to done*, below |

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
must not silently redraw every ride they did in August (21.2.3). And **a ride
says how long it spent in each of them** (21.4.1), beside the power one it has
always had — with the one rule that stops it being the power chart in a
different colour: a second with no strap on it is *unknown*, not Recovery, so
the zones divide the time a heart rate was actually reported and the card says
*"a heart rate for 5:13 of 8:13"* when the strap missed some of the ride.
**And a ride is held against the class it was ridden to** (21.6.3): the class
recorded the effort it prescribed and the strap recorded the effort that was
made, so one sentence says *"Harder than the class asked — 12 minutes 48
seconds in your top two heart-rate zones, against the 4 minutes it
prescribed."* It says nothing at all on most rides, and that is the design: a
free ride was asked for nothing, and a strap that heard three minutes of
thirteen describes three minutes rather than the ride. **And every one of those
zones says what it was drawn from** (21.4.2c): a maximum heart rate is either
the rider's own number or an estimate off their year of birth, the two are 10
to 12 bpm apart — wider than a zone — and the ride now records which it had.
A ride from before that column says *nothing* rather than guessing, because the
answer really is gone.

**A level, and it is the only number here that cannot go down (26.4).** Lifetime
rides, minutes and kilojoules through a square-law curve, drawn as `LVL 7`
beside a rider's name on the dashboard, on the household panel and on the
profile selector. It says *has ridden more* and never *is fitter* — which is why
it is not the FTP with a nicer badge: the FTP falls when a rider is ill and
Phase 7 moves it by itself, so a "score" built on it would demote somebody in
their sleep. A guest gets no badge at all, because a guest's rides are filed
against nobody and they could never leave level 1.

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
feature.

**And there is always something ahead of you** (24.3.18). The board no longer
needs somebody to have ridden the class: it generates targets — **the plan**
(the class ridden at the middle of every band it prescribes, which is the only
non-arbitrary one and needs no history at all), your own best plus five per
cent, your usual, and a round number that rises as you do. A generated row
carries a `○` and can never be mistaken for a person. Six rows are visible and
the rest scrolls, and every non-human row is a sentence about the rider —
*Your best this year*, *Your recent best* — rather than the durations that sat
among real names until 24.3.12a was settled.

**And that single gap is now a leaderboard** (24.3.10–24.3.13b), which is what
the owner wanted and is Peloton's own shape. Start a class anybody on this bike
has ridden and a board appears with no choosing: your best ever, your best of
the last twelve months, your best of the last thirty days, and every
housemate's best, ranked live on the class total in kilojoules. It shows
**three rows — the one you are chasing, you, and the one chasing you** — which
is what keeps a leaderboard readable by somebody at 90 rpm. Your row carries
your total; every other row carries the gap to you. Somebody whose ride ends
before yours says `FINISHED` and their number stops. **`LEADERBOARD.md` is the
plain-English description**, and `RIVALS.md` describes the single-rival version
it replaced, which is switched off rather than deleted.

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
| **19.1.4** | CI is written and never green | The workflow exists, and the fifty-eighth sitting found why it has never run: `ci.yml` triggers on `push` to **main** and on `pull_request`, and every commit in this project is on `setup`. **One pull request ticks it.** Until then contributions have no build server but a maintainer |
| **15.4.3** | Delete the account itself | Sign-out and *Delete my cloud copy* are built and watched against the real endpoint (15.4.1, 15.4.2, 15.4.4) — every ride stays on the bike either way. Deleting the **account** needs an Edge Function, because Supabase has no user-initiated self-delete, so it is one deploy away rather than one decision |
| ~~**17.16.2**~~ | ~~How the web app is deployed~~ **— closed, and the deploy has happened.** | The owner answered it: **`git push`**. Cloudflare watches the repository, so there is no command to run and `web/wrangler.jsonc` is a fallback nobody uses. `./web/check-deployed.sh` reports all seven files matching, so 15.6.14's pairing fix and 17.16.9's voice are live. **What is left is 17.16.3, and it is no longer a deploy**: `web/config.js` is untracked, the host serves it anyway on the legacy key, so the hosting side supplies it and only the hosting side can change it |

### Deliberately deferred, with the reason written down

- **23.4's remaining corners** — the trimmer itself is built (see tier 2), and
  what is left is deliberately not in front of anybody. **23.4.1** is the one
  measurement on the real bike that the whole feature was sized off, and it now
  takes a glance at Settings → Storage rather than three adb queries. **23.4.7**
  is the same policy applied in the cloud and **23.4.11** is the question that
  forces about other people's rides on a shared endpoint. **23.4.13** is
  bringing a condensed ride's seconds back down for a rider with an account: its
  own item on purpose, because it is a download rather than a retention policy,
  and until it exists the dialog says so in as many words. Two readers still
  degrade under a trim and both are honest losses rather than wrong numbers: a
  condensed rival draws no live ghost, and Settings' maximum-heart-rate
  suggestion drops.
- **Phase 29, Health Connect and Apple Health** — the owner's note of 16 August,
  marked *"REALLY HIGH importance"*, and it is written up rather than built
  because it is blocked on things a session cannot do. **Health Connect asks
  nothing of anybody**: no account, no registration, no API key, no fee, and
  the Play Store's health-data declaration binds an app distributed on Play,
  which this is not. What it needs is the **bike** — Health Connect is part of
  the platform only from Android 14, the tablet is Android 11, and three adb
  commands (29.1.1) decide whether the rest of the phase is a feature or an
  essay — plus one decision that is genuinely the owner's, since the client
  library floors at minSdk 26 and this app claims 24. **Apple Health is a *no*
  on the facts**: HealthKit has no Android SDK and no server API at all, because
  the data lives on the rider's iPhone, so the honest route is the `.tcx` this
  app already exports, carried across once (29.2.2). An iOS app built *for*
  HealthKit would be the largest thing in the plan for a feature a file already
  delivers.
- **21.7, an Apple Watch as the heart-rate strap** — the owner's other note of
  16 August, and the likely answer is that **it already works and nobody has
  tried it**: the strap scan filters on the standard heart-rate service rather
  than on a device name, so anything advertising it is an ordinary strap. The
  item is one measurement with a friend's watch, and the thing to write down
  afterwards is whether their phone had to be in the room.
- **17.5 / 18.1 friends** — dropped in favour of "everyone registered"
  (18.11) while the population is four people. The item stays open for the day
  the answer changes.
- **12.3.5 deleting a synced ride** — needs a tombstone, not a delete, or the
  next pull resurrects it. **Pull is built now (15.3.2)**, so this is no longer
  hypothetical: a ride deleted on this bike is still in the account, and a
  restore on a new one would bring it back. Nothing can produce that today —
  a rider has one tablet — and it is the sentence to be honest about before a
  second one exists (15.3.4).
- **2.2a calibration** — settled as *yes*, and gated on capturing a sweep with
  the coverage `calibration/README.md` specifies. The first fit failed
  cross-validation.
- **Nothing, for the first time in a while.** All three of the decisions that
  were the owner's rather than a session's are answered: 11.7.2 (name the
  governing metric in the catalogue — built), 11.1b.10 (a rule across
  somebody's film is a rule whatever colour it is), and 24.3.14 (the
  leaderboard's score is the class total in kilojoules). **24.3.15**, the
  toggle between racing by output and racing by distance, is still queued —
  the data is agnostic already, and now that the leaderboard exists the
  control has a surface to live on. **24.3.12a is a decision waiting on the
  owner** at their own request: what the rows on the leaderboard should be
  called, since `12 months` and `30 days` are placeholders and the owner has
  said the first is *"no good at all"*.
- **Phase 27 alerts** — records, streaks, and being beaten. Written out at full
  length and deferred on the owner's own weighting: *"definitely nice-to-have
  and low priority for now"*.

### Nice to have, and honestly labelled as such

Most of Phase 17 beyond what is hosted, most of Phase 18 beyond the leaderboard,
the rest of avatars (20.2.4–20.2.8 — the built-in set is done and drawn on three
screens), the Material Expressive cosmetic backlog (~30 items in 8.11), a
custom class builder (19.2.1), a guided FTP test (19.2.3), Strava upload
(19.2.4), and localisation. None of it is load-bearing: `plan/fundamentals.md`
is the standing argument for why, and it has been right so far.

**The live leaderboard is built** (24.3.10–24.3.13b) and is described above;
what is still on this list is **18.12**, the same thing across bikes, whose
endpoint (`class_ghost`) already exists and has never been called — a friend's
best is the one kind of row the board cannot draw yet, and it is absent rather
than broken.

**What the ghost was, before the board replaced it** (24.3.3–24.3.9): the
single gap that
shipped. The owner settled the shape through the inbox: **the leaderboard wins
and the rival goes behind a flag rather than into the bin**, because a rival's
ceiling is one person and everything under the ghost is a leaderboard with a
`LIMIT 1` on it. The ride screen shows three rows — you, the one you are
chasing, the one chasing you — which is what makes it legible at 90 rpm.
The score is settled — **total kilojoules for the class** — and the underlying
race is metric-agnostic, so racing by distance is a presentation decision
rather than a data one. Worth knowing before it is built: **a distance race
needs no measured power**, so it is populated on the many rides an output
race has to exclude.

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
2. ~~**The deploy is written down nowhere (17.16.2).**~~ **Closed, by the owner,
   and the answer is one word: `git push`.** Cloudflare watches the repository,
   so the branch reaching GitHub republishes the site. There is no build step
   and nothing to install; `wrangler.jsonc` in `web/` is a fallback nobody has
   ever run. It is in CLAUDE.md and `web/README.md` now, because this had held
   three fixes back rather than merely being untidy — the last of them being the
   fault the owner themselves reported, a phone already signed in meeting three
   dots for ever (15.6.14). **That fix is live**: `./web/check-deployed.sh`
   reported `link.js` and `link.html` drifted before the push and all seven
   files matching after.

   **What the answer does not explain is now the interesting part.**
   `web/config.js` is git-ignored and untracked, so a push cannot be carrying
   it — and the host serves it anyway, `200`, still on the legacy JWT key form.
   Something on the hosting side supplies it. So **17.16.3 is not closable from
   this repository at all**: the key on the internet is the Cloudflare side's to
   change, not a commit's, and that is worth knowing before somebody rotates the
   one they remember (14.11.4).

3. **The cloud tier has been alive for two days.** Everything in it has been
   observed once, by one household, mostly on an emulator. This project's
   history is three cloud defects that all returned success codes, plus the one
   above, so the right posture is that the round trip works and nothing about it
   is weathered.
4. **The sensor board's serial port leaks (2.7d)**, and it is Peloton's, not
   ours. One `/dev/ttyO0`, one open, so two bike apps can never both work — and
   after the other app is gone the port can stay unopenable **until the tablet
   is rebooted**. **What we owed it is now paid** (2.7.7, 2.7.8): the app stops
   promising to reconnect to a port that is not coming back, and says the
   remedy instead — *"The bike's sensor isn't answering — not recording.
   Restarting the tablet usually frees it."* It tells that condition apart from
   a board that merely dropped out, because a service that cannot open the port
   still binds and then answers every poll with `TIME_OUT`, and it stops
   rebinding after four tries rather than reaching attempt 141. **The ride is
   still lost** — nothing in userspace can reopen that port — so this is
   honesty about the failure rather than a fix for it.
5. **The power curve is measurably wrong** — RMSE 137 W against the board's own
   watts, 66% median absolute error. It is fenced to two consumers (the
   simulator and `RideSnapshot.resistanceTarget`) and can never reach a
   recorded number, which is the only reason this is a caveat rather than a
   defect. Adding a third consumer breaks that. **As of the twenty-ninth
   sitting the second consumer is not drawn anywhere**: 11.7.3 took the
   resistance band off both the ride screen and the overlay, precisely because
   a 66%-wrong guess was being shown with the same authority as two measured
   numbers. The code is kept because 11.7.3 says when it comes back — a bike
   riding its own calibrated curve (2.2a).
6. **`WorkoutServiceTest` is flaky about one run in three (8.8b)**, and the
   instrumented suite is order-dependent, which is why CI runs only the JVM
   tests. A red run you are trained to re-run is a suite nobody reads.
7. **A written rule that nothing checks describes the library nobody built.**
   `classlibrary/` R10 has always said a title names the shape and the demand,
   *"not the category and the length"*, and ended with the words **not
   tested** — and all 72 titles ended in their own duration. "The Long Climb
   30" was drawn beside a chip already reading `30 min`, on four screens.
   Stripped, and `build.py` refuses to emit them now. Read it as evidence
   about the other rules marked *not tested* rather than as a closed item.

   **It was read that way, and it paid out again (23.2.8).** The rule the
   owner's own rename produced — *a position word in a title is a promise that
   the blocks say it too* — sat in the README for two months and was enforced on
   **neither** surface: not on titles at all, and on descriptions in the
   standing direction only. Two classes were breaking it. `CLB-04` "Rolling
   Climbs" said *"repeated seated rises"* with **no position on any of its
   seventeen blocks**, and `SPR-05` "Sprints, Three Ways" promised a seated set
   in a class whose only positioned blocks ask the rider to stand up. Both are
   fixed and the rule is checked on both surfaces now. **One rule in that file
   still says *not tested* — R8** — and 23.2.8f says what checking it would
   cost. The pattern is the item, not the classes: on this project, a claim
   written down and checked by nobody has twice turned out to describe something
   that was never built.

   **It paid out a third time, and this page was the thing that was wrong
   (19.1.6).** The top row of the blockers table above described four faults a
   stranger meets on first run. **Three of them had been fixed by other items
   and nobody came back to cross them off**: the FTP has not been prefilled with
   `200` since 20.3 replaced the whole screen, the overlay permission is asked
   during the countdown because the owner asked for it there (11.6.14), and a
   heart-rate strap is one tap off the ride screen (11.6.9). The fourth was
   true, was the only one nobody had built, and was the smallest — so a
   fixed-weeks-ago list was standing in front of a twenty-minute change. The
   escalation is what makes it worth reading: the first two instances were
   rules in `classlibrary/README.md`, and this one is **the summary page a
   person reads instead of the plan**. See item 8.

   **It paid out a fourth time, and this one went the other way (21.4.1).** Two
   boxes in Phase 21 were wrong in opposite directions and neither made any
   screen look broken. **21.2.3 — the maximum heart rate a ride was ridden at,
   stored on the ride — had been built, migrated and watched three sittings
   earlier and never crossed off**, so the plan carried it as outstanding while
   the column was on the row and two readers were using it. And **21.4.2 was
   ticked with two clauses in it**: the banded heart-rate trace, which was
   built, and *"an HR-zone distribution beside the power one"*, which was not —
   so for three sittings the heart rate had a trace and no distribution while
   power had both, and nothing was wrong on the screen, there was simply
   nothing on it. The lesson is narrower than the three above and worth keeping
   separately: **a box with two clauses in it gets ticked for whichever one was
   done**, and a stale claim goes stale in the direction of *already finished*
   as readily as the other way.
8. **Nothing keeps the two design systems in step (17.15.2)**, and nothing keeps
   this page in step with the plan (19.1.7a). Both are stated rather than
   hidden, and both have the same cheap fix that should not be built until the
   drift actually happens. **The third member of that list has been struck off**:
   "nothing keeps the deployed web app in step with the repo" stopped being
   hypothetical within a day of being written, so it got its cheap fix
   (`web/check-deployed.sh`) and is now item 2 above. Read that as evidence
   about the other two rather than as a reason they are different.

   **Both were then measured on 13 August 2026, and they came out opposite
   ways.** The design tokens have **not** drifted — all 28 colour declarations,
   the six spacings and the 760 dp cap still match `Color.kt` and `Theme.kt` —
   so 17.15.2 stays unbuilt on its own terms rather than on inertia. (The grep
   found two values that named no Kotlin original, and one, `--radius-control`,
   that was never a copy at all despite a comment saying it matched the app;
   both are fixed in the file.) **This page had drifted**, which is item 7's
   third instance above — and the thing to notice is that **19.1.7a's proposed
   script would not have caught it**. That script counts boxes and tests; what
   went stale here was four sentences of prose about what a rider meets. So
   19.1.7a stays unbuilt for a better reason than "not yet": the drift this page
   actually suffers is the kind only a person re-reading it against the code can
   find, and doing that is what the forty-fifth sitting spent its morning on.
9. **10.6 is still unanswered**: battery, thermals and memory over a full-length
   ride. The one 20-minute ride on real hardware was spent finding 2.7.

---

## How close to done

**Done for this household: weeks, not months — and mostly not code.** The bike
works, the record is honest, the backup runs. **Three of the four things on this
list a week ago are now closed** — the sign-up setting was decided (kept open,
deliberately), the web app was redeployed and verified, and the exits are built
and watched (15.4: sign-out keeps every ride, deleting the cloud copy signs the
rider out with it). What is left is one throwaway sign-in to watch the restore
end to end (15.3.2), and a full-length ride that measures battery and heat.

**Done for a stranger with a Peloton: the three rows in the table above.** In
order of what a new rider meets first: a first-run flow that offers an account
and gets to a usable FTP without a text box. **Both halves of that are now
built** — 20.3 is a three-step screen that estimates an FTP from questions a
person can answer, and 15.8 fills the slot it was left with, offering to back
the profile up right there and again from the dashboard if it was skipped.
**And the app now introduces itself (19.1.6)**: an empty bike is the only first
run there is, so the profile picker's empty state says the app's name, one
sentence about the overlay, and *Set up · Four questions, then ride*. What is
left is a green CI run so the project can take a patch (19.1.4) — one pull
request, since the workflow has always been waiting for one rather than for a
push. That is a
genuinely short list, and it is short because the hard parts — a stock bike,
honest telemetry, migrations, an overlay that survives Netflix — are behind us.
**It is also shorter than it looked**: three of the four faults 19.1.6 listed
had already been fixed elsewhere, and this page had been repeating them for
weeks (item 7).

**Done as the plan is written: 72%, and it will never be 100.** 577 of 801
boxes, and the remaining 224 are not a queue. They are a place ideas are kept
with their reasoning attached, which is what has stopped this project rebuilding
things it had already decided against. **It has gone *down* in a sitting where
three things were finished**, and moved by one box in a sitting whose whole
subject was six leaderboards — which is the clearest possible demonstration of
why not to read it as progress: three of the owner's notes once added forty
boxes between them, most of Phase 27's, and the sitting that built the feature
the owner asked for on 3 August moved it by six boxes while opening a seventh
(23.4.13) that nobody had thought of until the dialog had to be worded. A closed box and an open one are not the same
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

*One honest exception, from this sitting: **restore had been refusing every
backup this build made** and was found by reading the schema version on the way
past to something else (19.1.3a). It is the counter-example that proves the
shape of the rule rather than breaking it — nobody had restored a backup since
the version drifted, so there was no use to find it by.*

---

## Where to read more

| Question | File |
|----------|------|
| What is done, what is next, and this sitting's story | [PLAN.md](PLAN.md) |
| How data actually flows | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Racing everybody who has ridden this class, in plain English | [LEADERBOARD.md](LEADERBOARD.md) |
| The single-rival ghost it replaced, now switched off | [RIVALS.md](RIVALS.md) |
| The bike's measured display, system and input facts | [HARDWARE.md](HARDWARE.md) |
| The traps that have already bitten, and the house rules | [CLAUDE.md](CLAUDE.md) |
| Why the phases are ordered the way they are | [plan/fundamentals.md](plan/fundamentals.md) |
| Things once ticked that were not working | [plan/corrections.md](plan/corrections.md) |
| The offline/cloud rules in full | [plan/connectivity.md](plan/connectivity.md) |
| One phase in detail | `plan/phase-NN-*.md` — PLAN.md's table says which |
