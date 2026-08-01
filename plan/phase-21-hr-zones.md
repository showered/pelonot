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
