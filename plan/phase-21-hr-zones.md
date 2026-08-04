> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

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

- [x] **21.1.1** **Date of birth on the profile.** `profiles` today is name,
      weight, FTP and a created-at, so this is a schema change and gets the full
      treatment: a `Migration`, an exported schema in `app/schemas/` and a
      `MigrationTestHelper` test (12.5). A **full date**, and stored as one:
      a date picker is a control everyone already knows, where "what year were
      you born" is an odd field people have to stop and think about. Not an age
      integer, or every rider's zones go quietly stale on their birthday.
      **Nullable** — a rider who does not want to give it gets *no* HR zones
      rather than wrong ones, and with 21.1.3 in front of it many riders will
      never be asked at all

      *Done — `profiles.birth_date`, epoch ms UTC, migration 11 → 12, exported
      schema and a `MigrationTestHelper` test. **Both new columns are nullable
      and that is the decision**: the contrast with 10 → 11 is the reasoning, and
      it lands on the other side of it. `resume_count` took `NOT NULL DEFAULT 0`
      because zero stated a *fact* — nothing already on a tablet had been
      resumed, since resuming did not exist. There is no equivalent fact here: a
      default maximum heart rate is a **guess about a rider's body**, and it
      would silently prescribe zones off a number nobody gave. So every existing
      profile comes out of the migration with no zones at all, which is the
      correct answer until they are asked. **Observed on the bike's own database
      as well as the AVD's**: `PRAGMA user_version` is 12, the profile and all
      six rides are intact, and `max_hr_bpm` and `birth_date` are both null —
      the migration invented nothing.*
- [ ] **21.1.1a** **Sync the year, not the date** (14, 15). On the tablet a date
      of birth is a fitness input; in a cloud row beside a display name it is an
      identity field, and that boundary — not the collecting of it — is where
      this datum changes character. Only the year has any effect on the maths
      (0.7 bpm per year of age, against a formula whose own error is 10–12), so
      deriving it at the sync edge costs nothing and means the useful part is
      the only part that travels. Decide this **when the DTO is written**, not
      after: "we sync every column in the row" is a default, not a decision
- [x] **21.1.2** *The fallback.* Estimated maximum heart rate from age, using
      **Tanaka (208 − 0.7 × age)** rather than the folk formula 220 − age, which
      overestimates for younger riders and underestimates for older ones. Say on
      screen, once and plainly, that it is an estimate — and show it updating as
      the rider fills the field in, so it is visibly a fitness calculation and
      not a profile form harvesting a birthday
- [x] **21.1.3** *The primary path.* **A measured max HR, asked for first.** Any
      age-based formula has a between-individual spread of roughly 10–12 bpm,
      which is wider than a zone — so for a meaningful fraction of riders the
      estimated zones are simply the wrong zones. Let a rider who knows their
      own number type it, and offer the highest heart rate the app has ever
      recorded for them as a starting point (it already has every sample). It
      overrides the estimate wherever both exist

      *Done, and the offer is real: **"Use 190 — the highest you've recorded"**,
      off a `MAX(heart_rate)` over the rider's own samples. It is offered and
      never written for them, because the hardest thirty seconds they have ridden
      is a **floor, not a maximum**, and only the rider can say which. Driving it
      found a defect reading the diff would not have: the section asks for the
      figure from a `LaunchedEffect(Unit)` on first composition, when `uiState`
      is still the default and the profile is still null, so a rider with 382
      recorded samples was shown no offer at all with nothing looking broken. It
      resolves the id from the settings flow now. **Confirmed on the bike
      itself**, which is where it stops being a demo: the tablet's own Settings
      offers *"Use 170 — the highest you've recorded"*, off the owner's real
      strap data across their real rides.*
- [ ] **21.1.4** Resting heart rate, if and only if the model chosen in 21.2
      needs it. Do not collect a field nothing reads
- [ ] **21.1.6** **Ask the answerable question first — the owner's note, 3
      August 2026.** Verbatim: *"It's better UX to ask people their age and
      weight than to ask max bpm. No normal person knows their max bpm! Let's
      infer it. This whole app needs to be great UX for normal people who just
      want to crack on and ride."*

      **This contests 21.1's ordering, and it should win — but only the
      ordering.** The two arguments are about different things and both are
      right. 21.1 is about *accuracy*: an age formula has a 10–12 bpm spread
      between individuals, which is wider than a zone, so an estimate gives a
      meaningful fraction of riders the wrong zones outright. The owner's note
      is about *answerability*: a field is worthless at any accuracy if the
      rider cannot fill it in, and a text box asking a normal person for a
      number they have never measured does not produce a careful answer — it
      produces a guess typed into a field the app will then treat as measured
      fact. **An unanswerable question is not more accurate than an estimate.
      It is less.**

      So the synthesis, and it is the same one 20.3 reaches for FTP: **ask what
      everyone can answer, offer what the few can.** Date of birth becomes the
      default path with the estimate shown live as it is filled in (21.1.2 is
      already built and already says it is an estimate); the measured number
      moves behind an explicit *"I know my maximum heart rate"*, where the
      riders who have actually done a ramp test will look for it. 21.1.3 stays
      exactly as built and keeps its offer off the rider's own recorded samples
      — what changes is which of the two is the first thing on the screen, not
      which one wins when both exist. **The measured number still overrides the
      estimate**, which is the part of 21.1 that is about accuracy rather than
      about ordering.

      What this must not do is quietly make the estimate look like a fact.
      Every surface showing a derived maximum says so (21.1.2), and 20.3.4's
      rule applies here unchanged: a rider who knows the number is a guess will
      correct it, and one who does not will ride a year of wrong zones without
      ever suspecting the app of anything.

      See **20.3.7** — the same question, asked once, answers both this and the
      FTP estimate, and that is an argument for Route B in 20.3.2 rather than a
      coincidence
- [ ] **21.1.5** Threshold heart rate (LTHR) as the best-quality basis, optional
      and much later. The guided FTP test in 19.2.3 is the same twenty minutes
      of riding, so if that is built, this comes almost free from it

### 21.2 The zone model

- [x] **21.2.1** A `HeartRateZone` in `domain/`, pure and JVM-tested at every
      boundary, mirroring `PowerZone` in shape but **not sharing its bands or
      its colours**. Five zones is the usual HR convention against seven for
      power, and reusing the power palette would tell a rider that HR zone 4 and
      power zone 4 are the same thing, which they are not
- [x] **21.2.2** Pick a basis and name it in the UI: %HRmax is simplest, %HRR
      (Karvonen) is better and needs 21.1.4, %LTHR is best and needs 21.1.5.
      One of them, chosen on purpose, stated where the zones are shown

      ***%HRmax**, chosen because it is the only basis that needs a single number
      from the rider — %HRR wants a resting rate (21.1.4) and %LTHR wants a
      threshold test (21.1.5), and 21.1.4 is explicit that a field nothing reads
      must not be collected. Settings says so under the ladder: *"As a percentage
      of maximum heart rate"*.*
- [ ] **21.2.3** **The boundaries used for a ride are stored with the ride, not
      recomputed on read.** A rider who corrects their max HR in March must not
      silently rewrite what every ride in January said they did. This is the
      same shape as the `avg_*` trap in CLAUDE.md — and the same bug the power
      charts already have, now written up properly as **7.8**. Do the two in one
      migration: they are the same column added to the same table for the same
      reason
- [x] **21.2.4** Nothing anywhere displays a zone when the heart rate is null.
      Unknown is unknown; this project has already corrupted a rider's record
      twice by treating a missing heart rate as a number

### 21.3 Seeing it during the ride

> **The owner asked for both halves of this from the inbox, and one of them
> revealed what the phase costs.** Verbatim: *"Heart rate zones — pretty sure
> this is already covered but let's make this really 'expressive' too with
> colour changes between heart rate zones."* It is **not** covered: the ride
> screen draws bpm in one fixed green whatever the rider's heart is doing, and
> the reason is 21.1 — the app has no maximum heart rate for anybody, so it has
> no boundaries to colour between. That is the honest answer to give back: the
> colour is 21.3.1 and it is cheap, but it is gated on asking the rider one
> question first (21.1.3), and inventing a number rather than asking is exactly
> what 21.1 exists to refuse.

- [x] **21.3.4** **The heart beats.** The owner, verbatim: *"Would be kinda neat
      and 'material expressive' if there was a heart beating on screen, in time
      with actual heart beat (well, simulating it… if it's 180bpm then it should
      pulse 3x per second)."*

      **This one needs nothing from the rest of the phase** — it is driven by
      the bpm the app already has, not by zones — so it is the half of the
      owner's heart-rate note that can land immediately, and it should not wait
      behind 21.1.

      It earns its place rather than being decoration: a pulse is the one
      encoding of heart rate a rider reads **without looking at it**, in
      peripheral vision, mid-effort — the number needs focus and the rhythm does
      not. Two rules make it honest rather than ambient. The period comes from
      the **live bpm** (60/bpm seconds, so 180 bpm is three beats a second and
      the owner's example is the specification); and **it stops when the reading
      does** — a heart still beating on screen over a strap that has dropped out
      is precisely the frozen-88-rpm lie of 2.4.4, in the one place a rider
      would find it most alarming to learn afterwards. Null bpm draws no heart
      at all, the way the tile already draws no number.

      Watch the cost: this animates at up to 3 Hz for 45 minutes on a tablet
      that also has a film playing. Scale one small glyph, do not recompose the
      tile

      *Done — `BeatingHeart`, in the heart-rate tile's empty right-hand half so
      a swelling glyph never pushes the digits around. The period is re-read at
      the **top of each beat** rather than keyed on bpm: keyed, an
      `InfiniteTransition` would restart mid-contraction every time the 2 Hz
      display reading moved (11.6.7), which stalls the beat. **Measured on the
      AVD rather than asserted**, because a screenshot cannot show motion: across
      a burst of captures the glyph swells 110 → 132 px and its green roughly
      doubles, resting between beats — which is a cardiac cycle rather than a
      sine breath. Ride screen only for now; the overlay is deliberately left
      alone until it earns its half-second (21.3.2).*

- [x] **21.3.1** Current HR zone on the ride screen beside the live bpm — the
      same job 11.6.2 does for power, and worth designing as one thing so the
      screen does not end up with two unrelated zone treatments. **The owner's
      ask above lands here**: the bpm itself takes the zone's colour, which is
      the cheapest possible version and the one that needs no extra room on a
      full screen. Note it collides with `MetricReadout`'s amber — a value
      already recolours when it is off target — so the tile cannot carry both
      signals in the same channel; heart rate has no target band today (11.7.1a
      is the same collision seen from the other side), which is what makes the
      colour free here and not free on cadence

      *Done and **observed live on the AVD**: at 112 bpm against a 190 maximum
      the tile reads *HEART RATE · H1 RECOVERY* in the heart-rate green, and at
      121 it is *H2 AEROBIC* in lime — the boundary is 114, which is where the
      model puts it. The zone comes off `RideViewModel`, which observes the
      profile rather than reading it once, so a rider who sets their maximum from
      the ride screen's own settings sheet (11.6.10) sees the zones appear
      without leaving the class. It falls back to the metric's own green rather
      than to grey when there is no maximum: absent zones are absent, not worse.*
- [ ] **21.3.2** On the HUD only if it earns its half-second (11.5.5, 18.6). A
      zone number is arguably a better use of strip space than raw bpm, since
      the rider cannot act on "148" without doing arithmetic first
- [x] **21.3.3** Honest states for the two riders who have no zones: no strap
      connected, and no date of birth recorded. Neither gets a blank tile, and
      the second gets a way to fix it

### 21.4 Recording and tracking it

- [ ] **21.4.1** Time in each HR zone for a ride, computed from the samples
      exactly as 16.1.4 does for power. With 21.2.3 in place this needs no new
      table — the samples and the boundaries are both already there
- [x] **21.4.2** Post-ride: an HR-zone distribution beside the power one, and
      the HR trace (16.1.2) banded by zone. Note 16.1.2 deliberately breaks the
      line across gaps; the banding must not paper over them

      **The owner asked for this from the inbox, 4 August 2026**, and widened
      it: *"Heart rate zones, visualised. Any chart that shows heart rate over
      time, it should include visual indicator for heart rate zones."* **Any
      chart** is the operative phrase and it is right — the power trace has
      carried its zone bands since 16.1.1, so an unbanded heart-rate trace
      beside a banded power one is the app being inconsistent about its own
      idea. The surfaces are ride detail today, the post-ride summary once
      12.6.1 puts the charts there, and the web app's ride view (17), which
      draws its own charts from the same payload and will not inherit this for
      free
- [x] **21.4.2a** **What the bands are drawn *from* is the decision, and it is
      21.2.3 wearing a different hat.** Zone boundaries come from the rider's
      maximum heart rate, `workouts` has no column for it, and the maximum
      moves — a rider who measures a real 186 in September and replaces the
      Tanaka estimate of 177 silently redraws every ride they did in August.
      That is exactly 7.8's trap, and the power chart has it too. Three ways
      out, in the order they should be considered:
      - **Store it with the ride** (21.2.3): `workouts.max_hr_bpm`, nullable,
        written at ride start beside `ftp_watts`. Correct, and it is one
        migration. **Read 8.3d.4 first** — anything written to `workouts`
        during a ride must also live on `WorkoutSession` or the finalise puts
        the default back, silently, twenty minutes later.
      - **Null means no bands.** Every existing ride has no maximum recorded,
        so on this rule the feature shows nothing on any ride the app has
        already — which is honest and is also the whole feature missing for a
        week. Worth considering *with* the next one rather than against it.
      - **Fall back to today's maximum and say so.** A caption in the same
        family as 16.1.6's provenance line: the bands are drawn from the
        rider's current maximum because the ride did not record one. This is
        the reading that gets the feature onto old rides without lying about
        them, and the sentence is the entire cost.
      Recommended: the column *and* the caption — new rides are exact, old ones
      are drawn and labelled. Do not ship the bands with neither

      *Both done, and it is the recommendation as written. **`workouts.max_hr_bpm`,
      migration 12 → 13**, nullable and not backfilled — the twin of 6 → 7 for
      the other denominator, and the same argument 11 → 12 made one table along:
      filling last summer's rides with the number the rider gave this morning
      would look exactly like data and be a guess.*

      ***Observed on the tablet AVD, both ways.** A ride recorded before the
      column existed draws its bands and says* "zones from %HRmax · your maximum
      today — this ride did not record its own"*; a ride recorded after it says*
      "zones from %HRmax" *and nothing else, and its row carries
      `max_hr_bpm = 190` — the rider's own measured maximum, stamped at the
      start of the ride rather than recovered afterwards from a profile that may
      have moved.*

      *Three things worth carrying forward. **The session had to carry it**, or
      the finalise writes the default back twenty minutes later — CLAUDE.md's
      rule, and this is the third column to need it after `ftp_watts` and
      `resume_count`. **The resume reads it off the row**, like the FTP, so a
      maximum changed between a crash and a resume cannot rescore the ride being
      picked up. And **the bands are painted behind the trace, never as a fill
      under it**, because 16.1.2 deliberately breaks the line where the strap
      dropped out and an area fill would paper over exactly that gap.*

      *One thing deliberately not done: only the zones the ride actually reached
      are drawn, so a rider who never left H2 gets one band rather than five
      slivers of which four are off the top of the chart.*

      ***The migration was exercised against a real database rather than only a
      test.** The AVD's own store went from `user_version = 12` to 13 on launch
      with 11 rides and 2,353 metric rows intact and every old ride's
      `max_hr_bpm` null. The `MigrationTestHelper` test for 12 → 13 is written
      and **has not been run**: `connectedDebugAndroidTest` uninstalls the app,
      which would take that database with it, and CLAUDE.md's rule is to run the
      instrumented suite before driving the UI rather than after.*
- [ ] **21.4.2b** **The cloud copy of a ride has neither denominator.**
      `WorkoutDto` carries no `ftp_watts` and now no `max_hr_bpm`, so the web
      app (17) draws every ride's zones from whatever the *reader's* profile
      says — which is 7.8 and 21.2.3 again, on the surface where they were never
      fixed. Not urgent and not free: it is a payload change (14.4) and a column
      on the cloud table, and the honest interim is that the web app draws no
      zone bands at all rather than wrong ones
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

### 21.5 Do not ask what the heart rate already knows — the owner's note, 4 August 2026

**Verbatim:** *"Also with regards to FTP auto calculation, and maybe this 'how
did that ride feel' data ... surely there's something we can infer from heart
rate (if connected)? For example if an endurance ride the rider spent most of
the time in the top 1 or 2 zones, then they clearly found that ride more
difficult than it should have been, you don't even need to ask!"*

**The idea is right and the app is already half-way to it.**
`PostWorkoutAnalyzer.detectBiometricDecoupling` looks for the opposite case —
zone-4 power carried at a *low* heart rate, which is a rider who has improved —
and 21.1/21.2 gave the app a maximum heart rate and five HR zones for the first
time. What is missing is the join: **the class says what effort it prescribed,
and the heart rate says what effort the rider actually made.** A gap between
those two is information nobody has to be asked for.

Three things this could feed, in increasing order of how much they can hurt:

- [ ] **21.5.1** **Prefill the effort answer rather than replace it** (26.3).
      An endurance class ridden mostly in HR zones 4–5 was hard; a threshold
      class ridden in zone 2 was easy. Prefilling costs nothing if it is wrong
      and saves a tap when it is right. **It must stay a prefill**: the rider's
      own answer is a fact about them and the app may not write one on their
      behalf, which is the same rule as 7.10.4/7.10.5 — *the app must not edit
      the rider's record behind them*. A prefilled answer must also be
      distinguishable from one the rider gave, or the column stops meaning what
      it says
- [ ] **21.5.2** **Feed the FTP proposal** (7.x). This is the one with teeth,
      because an FTP is written into the rider's permanent record and every
      zone in the app is derived from it. Two hard prerequisites before any of
      it: the ride's power must be **measured** rather than modelled
      (`PowerProvenance.isTrustworthyAsMeasured`, 7.10.7 — the same gate that
      already stops a simulated ride proposing anything), and the maximum heart
      rate must be **measured** rather than the Tanaka estimate (21.1), because
      an inferred effort built on an estimated maximum is two guesses wearing
      one number
- [ ] **21.5.3** **Say it on the ride detail screen, which is free and safe.**
      "You spent 18 minutes in HR zone 4 on an endurance ride" is an
      observation about a ride, not a claim about the rider, and it needs
      neither 21.5.1's prefill nor 21.5.2's gates. **Read 21.2.3 first** — the
      thing that blocks it is that nothing yet draws an HR zone for a *past*
      ride, and 7.8's trap is why: the zone bands would be drawn from whatever
      maximum the rider has *today*, not the one the ride was ridden at
- [ ] **21.5.4** **The honest limit, stated once so it is not rediscovered.**
      Heart rate lags effort by a minute or two, drifts upward across a long
      ride at constant power, and moves with heat, caffeine, sleep and
      illness. It is a good signal about *a ride* and a poor one about *a
      sixty-second interval*. Anything built here should compare whole blocks
      or whole rides, never single samples — and it must degrade to silence
      when no strap is worn, which on this bike is most of the time

