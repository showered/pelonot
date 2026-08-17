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
- [x] **21.1.1a** ~~**Sync the year, not the date**~~ (14, 15). On the tablet a date
      of birth is a fitness input; in a cloud row beside a display name it is an
      identity field, and that boundary — not the collecting of it — is where
      this datum changes character. Only the year has any effect on the maths
      (0.7 bpm per year of age, against a formula whose own error is 10–12), so
      deriving it at the sync edge costs nothing and means the useful part is
      the only part that travels. Decide this **when the DTO is written**, not
      after: "we sync every column in the row" is a default, not a decision

      ***Largely settled from the other end, 4 August 2026 — the app now only
      ever asks for a year (21.1.1b), so there is no full date to leak.*** What
      is left of this item is the DTO decision itself, which is now trivial
      rather than delicate: `birth_date` holds 1 January of a year, and either
      the column becomes a year (21.1.1b) or the sync edge takes
      `millisToBirthYear`. Do not close it until the DTO is written

      ***Closed in the fifty-first sitting, by reading the DTO the item was
      waiting for.*** `ProfileDto` *is written — id, name, `ftp_watts`,
      `weight_kg` — and it carries **neither the date nor the year**. Nor does
      the cloud table: `supabase/migration.sql`'s `profiles` has `name`,
      `weight_kg`, `ftp_watts`, `theme_preference` and nothing else. So the
      answer to* "the year, not the date" *turned out to be* neither*, by
      default rather than by decision, and this item's own warning —* "we sync
      every column in the row" is a default, not a decision *— has landed the
      other way up: **nothing was decided and nothing travels.** That is the
      right outcome for this datum and it is now written down rather than
      merely true.*

      *What the reading did turn up is the cost on the way back, and it is not
      this item's: a rider restoring onto a new tablet gets their name, FTP and
      weight and **silently loses their heart-rate zones**, because the basis
      those zones are drawn from was never up there. That is **15.3.7**
- [ ] **21.1.1b** **Should the column be a year? — the owner's question,
      4 August 2026.** Verbatim: *"Ask for year of birth. Why not? Does it
      really matter about the exact month/day? It can be resolved to 1st
      January in the db… perhaps implement just a year now (with a dropdown,
      not datepicker) and add a PLAN item to come back to this."* And, when the
      caption defect below made them wonder if the day mattered after all:
      *"Happy to bow to whatever you think is the best UX on this one. I like
      to remove barriers to entry. All people want to do is ride."*

      **The UI half is done and the answer is that the year is right.** Both
      screens that ask — profile creation (20.3.3) and Settings' heart-rate
      zones — now use one `BirthYearPicker`, one tap. The arithmetic is why,
      and it is checkable rather than a matter of taste: **this app has
      exactly two consumers for the date and both reduce it to age in whole
      years.** Tanaka moves **0.7 bpm** per year against a formula whose own
      between-individual error 21.1 puts at 10–12 bpm; `FtpEstimator`'s age
      term moves **0.6%** on a number deliberately pitched low and expected to
      be corrected by the first hard ride. Storing 1 January makes a rider at
      most one year older than they are, and neither consumer can tell.

      **The layout underneath it changed on the owner's word, same day.**
      *"It looks a bit ridiculous to be honest... it should be a single list
      that you can scroll. Not a grid layout."* The grid's own justification —
      22.4's "tile what is looked at" — turned out to be the wrong rule
      applied to a control answered once and never referred back to; a single
      `LazyColumn`, opened already scrolled near the rider's likely answer,
      replaced the `LazyVerticalGrid` in the one shared component both screens
      already used. No duplication to fix, only the layout.

      **The thing worth recording is why the owner's doubt was not evidence
      against it.** They wondered whether the "your age" caption bug meant a
      full date was needed. It did not: that caption named age because the birth
      year had been **skipped**, and a full date picker would have produced the
      identical sentence. The two questions — *how precise* and *what did we
      actually use* — look alike and are unrelated, and only the second was a
      defect.

      Against that, a full date costs at least three interactions to a year's
      one, and Material's picker opened on **August 2026**, which is ~500
      presses of the month arrow for a rider born in 1985. On the owner's own
      principle — *remove barriers to entry, all people want to do is ride* —
      the more precise control is strictly more barrier for strictly no gain.

      **What is left is the schema, and it is the part with a real question in
      it.** `profiles.birth_date` is still epoch milliseconds holding 1 January,
      which is a column whose type promises more than its contents mean. Two
      routes, and neither is urgent:

      - **Leave it.** Zero risk, and `millisToBirthYear` / `birthYearToMillis`
        are the only two places that know. The cost is that the column keeps
        inviting a future writer to put a real date in it, and 21.1.1a's sync
        decision has to remember to narrow it.
      - **Migrate to `birth_year INTEGER`.** Honest, makes 21.1.1a's leak
        impossible by construction rather than by discipline, and the migration
        is one `CAST` away since every existing value is either null or a date
        whose year is all anyone reads. It touches `MaxHeartRate`,
        `FtpEstimator`, the DTO and two screens.

      **Recommended: migrate, and do it with 21.1.1a's DTO rather than before
      it** — the two are the same decision seen from the schema and from the
      wire, and doing them together means one migration instead of a column
      that changes shape twice.

      One thing this must not do: **a rider who entered a real date before
      today keeps it.** Nothing in this change rewrote anybody's stored value,
      and a migration to a year must take the year out of what is there rather
      than clearing it
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
- [x] **21.1.6** ~~**Ask the answerable question first — the owner's note, 3
      August 2026.**~~ Verbatim: *"It's better UX to ask people their age and
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

      ***Done in the fifty-first sitting, and it was one screen rather than
      two.*** *Profile creation had already come out on the right side of this
      by another route — 21.1.1b made the year of birth the only thing either
      screen asks, and onboarding has never had a maximum-heart-rate box at all,
      so a new rider already gets zones without ever meeting the question.
      **Settings was the whole of the defect**, and it was the exact inversion
      the note describes: the first control in the section was an empty box
      labelled* `Maximum heart rate (bpm)`*, and* "Born in 1986" *was the fourth
      thing down, under a small* "Don't know it?"*. The KDoc above the composable
      called that ordering "the design" and gave the accuracy argument for it,
      which is why it is rewritten rather than deleted: the accuracy argument is
      still true and still decides which number wins.*

      ***The reveal is one-way on purpose, and that is the one design decision
      not in the item's own text.*** *A disclosure that folds away again is a
      field that can hold a number governing the save with nothing on screen
      showing it — the same family as the read-modify-write defect (7.9): state
      that acts and cannot be seen. So it is shown from the start for a rider
      who already gave a number, and thereafter only ever revealed.*

      ***And 21.1.3's offer stays where it was, which is a decision rather than
      inertia.*** "Use 190 — the highest you've recorded" *moved behind the
      disclosure with the field it fills in, and the cost is real: a rider with
      strap data who does not know their maximum will not see the app's better
      answer. Promoting it beside the year was considered and refused, because
      **the highest a rider has recorded is a floor, not a maximum** — 21.1.3's
      own words — and a floor offered as the first answer would give every rider
      with a strap zones that are systematically too low. That is the accuracy
      half of 21.1 broken in the other direction, and it would be invisible.*

      ***Watched on the tablet AVD in three states with each other as
      controls, and the profile row read after the save rather than the
      screenshot alone.** A profile with a year and no number: the estimate
      leads, the ladder reads* "Zones from 180 bpm — estimated from your year of
      birth"*, and the whole section now fits above the fold where the ladder
      used to be pushed off it. Then the reveal tapped — the field appears with
      the offer under it and the link is gone —* `Use 190` *tapped, and the
      ladder switches live to* "Zones from 190 bpm — your own number" *with all
      five bands recomputed. Saved: `max_hr_bpm = 190` with `birth_date`,
      `ftp_watts` and `weight_kg` untouched and the other profile untouched
      (7.10.3's one-tap-one-write rule holding). Relaunched: the number is on
      screen from the first frame and the reveal link is absent. Then a second
      profile with neither, by hand: one* Set year of birth *button, one link,
      and* "No heart-rate zones yet. Pelonot won't guess a maximum" *— which is
      21.3.3 unchanged, with its sentence reordered to name the year first.*
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
- [x] **21.2.3** **The boundaries used for a ride are stored with the ride, not
      recomputed on read.** A rider who corrects their max HR in March must not
      silently rewrite what every ride in January said they did. This is the
      same shape as the `avg_*` trap in CLAUDE.md — and the same bug the power
      charts already have, now written up properly as **7.8**. Do the two in one
      migration: they are the same column added to the same table for the same
      reason

      **This box was stale, and the audit is the interesting part.** Every word
      of it was built by 21.4.2a and watched on the tablet AVD in the same
      sitting — `workouts.max_hr_bpm`, migration 12 → 13, nullable and not
      backfilled, carried on `WorkoutSession` so the finalise cannot revert it
      (8.3d.4), read off the row on a resume, and travelling in `RideFacts` to
      the cloud. Nobody came back to cross it off, and it stayed unticked while
      **21.4.1 read it as a denominator** and found it already there. Note the
      one thing the item asked for that did *not* happen and did not need to:
      it is **not** one migration with 7.8. The FTP column landed at 11 → 12 and
      this one at 12 → 13, months apart, and each was exercised against a real
      database on its own — which is better than the item's own instruction, not
      worse. Same family as 19.1.6's three false clauses and the two
      `classlibrary` rules before it: **on this project a written claim nobody
      re-reads goes stale in both directions**, and this is the first one found
      stale in the direction of *already done*
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

- [x] **21.4.1** Time in each HR zone for a ride, computed from the samples
      exactly as 16.1.4 does for power. With 21.2.3 in place this needs no new
      table — the samples and the boundaries are both already there

      ***Done in the forty-seventh sitting, and "exactly as 16.1.4 does" is the
      one instruction in it that had to be disobeyed.*** *Power is recorded for
      every second of a ride and a heart rate is not, so the copy of
      `TimeInZone` with a different enum in it would have counted a strapless
      second as **H1 Recovery** — a rider who wore nothing filed as having spent
      forty minutes in Recovery, which is this project's oldest defect
      (`heartRateBpm` nullable, 21.2.4) arriving as a percentage instead of a
      number.* `TimeInHeartRateZone` *therefore carries* `secondsUnrecorded`
      *beside the zones, divides by the time a heart rate was actually reported,
      and the card says what that was out of whenever it is not the whole ride.
      The two bars are one implementation (`ZoneBar`); only the palette, the*
      `H` *prefix and that caption differ.*

      ***Three things it inherits from elsewhere in the plan.*** *No maximum
      means **no zones at all**, never a default (21.2.4) — so the card is drawn
      only for a rider who wore a strap **and** has given the app a maximum, and
      `Time in zone` keeps its `loneCard()` width (22.6) on every other ride.
      The counts are a **count of seconds**, so a trimmed ride reads what it
      wrote down rather than recounting the fifth of its rows that survived
      (23.4.3): `RideDistributions` gained the zones, the unrecorded seconds and
      **the maximum they were counted against** — `zoneMaxHrBpm`, `zoneFtpWatts`'
      twin. And a ride trimmed by an **earlier** build has no stored heart-rate
      counts, which is read as *never counted* rather than as zero.*

      ***The trap the change contained is worth keeping.*** `RetentionRepository
      .distributionsFor` *built its charts without a maximum heart rate, and was
      right to until this item: the number drew bands and that method draws
      nothing. The moment it became a denominator, a trim run without it would
      have **frozen an empty heart-rate distribution onto the row** and lost the
      answer permanently — the exact failure the whole file exists to prevent,
      introduced by the file that depends on it. Same shape as 8.3d.4: a second
      writer carrying a stale idea of what a row needs.*

      ***Watched on the tablet AVD in three cases with each other as controls.***
      *A fresh 8:12 simulated ride under a rider with no measured maximum
      (Tanaka 179 from a 1985 birth year, stamped on the row as*
      `max_hr_bpm = 179`*): both cards side by side, H1 2:01 · 25% through H5
      1:21 · 16%, adding to the ride. **A strap that dropped out for three
      minutes** (`heart_rate` nulled for seconds 120–299 by hand, since the
      simulator never stops reporting): the caption reads* "%HRmax · a heart rate
      for 05:13 of 08:13"*, the five counts match the SQL row for row
      (69/87/59/49/49 seconds), and the percentages are of **313 seconds, not
      493**. **And no strap at all**: no card, and `Time in zone` back at a
      column's width. Power's time in zone was 8:13 in all three, which is what
      makes them controls.*

      *One thing worth reading off the first screenshot rather than the code:
      power spent **37% in Z1** and the heart **25% in H1** on the same ride.
      That gap is 21.4.4's lag made visible, and it is the argument for drawing
      the two beside each other.*
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

      **Half of this box was true when it was ticked and half of it was not.**
      The banded trace landed with 21.4.2a and was watched; *"an HR-zone
      distribution beside the power one"* did not exist until **21.4.1**, three
      sittings later, and nothing on any screen looked wrong in between — the
      heart rate simply had a trace and no distribution while power had both.
      Ticking a box with two clauses in it is how that happens, and it is the
      third instance of the pattern in `STATUS.md`'s item 7
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
- [x] **21.4.2c** ~~**The ride records the maximum and not where it came
      from.**~~
      Found while building 21.6.3, which wanted to say *"and your maximum is an
      estimate"* and could not. `MaxHeartRate` carries a `source` precisely
      because a zone drawn off Tanaka is a different claim from one drawn off a
      measurement (21.5.5) — and `workouts.max_hr_bpm` stores the **number
      alone**, so once the ride is over the claim is unrecoverable. Every past
      ride's heart-rate zones are therefore presented with one authority, which
      is the thing 7.8, 21.2.3, 23.4.12 and `power_is_measured` each exist to
      refuse.

      It is a column and a migration, and it was **deliberately not done in the
      sitting that found it**: one nullable `max_hr_source`, written at the same
      moment `max_hr_bpm` is (so 8.3d.4 applies — it has to live on
      `WorkoutSession` too), and read by the two cards that already say what
      they are drawn from. The reason for leaving it was that the wrong fix is
      available and tempting: resolving the source from the rider's *current*
      profile would answer for the fallback case and silently guess for every
      ride that carries its own number, which is a claim about provenance
      derived from a row that has since moved — exactly 7.8's shape.

      ***Done in the forty-ninth sitting, and built as the item specified.***
      *One nullable `workouts.max_hr_source` (migration 20 → 21), carried on
      `WorkoutSession` so 8.3d.4 cannot write its default back over it at the
      finalise, read off the row on a resume so a maximum typed in between a
      crash and the pick-up cannot relabel the first half of a ride, and on the
      wire in `RideFacts` as the enum's own name — the same shape
      `power_provenance` travels in, so a word a build does not know reads back
      as null instead of dropping a whole ride over its provenance. The read
      side is one rule and `RideChartsLoaderTest` is four cases of it:* **the
      source follows the number.** *The ride's own source for the ride's own
      maximum, null included; the rider's current source only where the number
      beside it is today's too, which is not a guess because the card already
      says whose number it is.*

      ***Both directions are said and the third is silent, which is a decision
      rather than a default.*** *21.5.5 asks only for the estimate to be
      labelled. Saying* "your own number" *on the other branch is what buys the
      silence its meaning: with one branch labelled, a card that says nothing
      could be either — and for a ride recorded before this column it genuinely
      is unrecoverable. Two claims and one honest gap. The words are Settings'
      own, because it is the same sentence about the same number and two
      vocabularies for one fact is how a rider comes to think they are two.*

      ***Watched on the tablet AVD in three cases with each other as
      controls***, *and the row read at each step rather than the screenshots
      alone. The upgrade first: `user_version` 20 → 21 on launch with 2 rides
      and 1,261 metric rows intact and `max_hr_source` null on both — the honest
      gap, and their Heart rate card still reads* "zones from %HRmax" *and
      nothing more. Then a ride under a profile carrying only a year of birth:
      the finalised row says `179 · Estimated`, not merely the row at insert,
      which is the 8.3d.4 assertion; and both cards read* "· estimated from your
      year of birth". *Then a measured maximum of 185 set by hand and another
      ride: `185 · Measured`, and* "· your own number" *on both.*

      *One thing deliberately not built: 21.6.3's verdict sentence — whose
      wanting to say* "and your maximum is an estimate" *opened this item — is
      **left alone**. The caption now says exactly that, one line above it on
      the same card, and a screen reader announces both. Repeating it inside the
      sentence is Phase 26's* less is more *broken for a claim already on the
      screen.*
- [x] **21.4.3** Weekly time-in-zone as a trend (16.3). This is the number that
      actually drives a training decision — "how much easy riding did I do this
      month" — and it is the honest answer to what the dashboard's progress
      section is reaching for (22.1)

      ***Done in the sixty-third sitting, as one card on *Your riding* —* Easy
      and hard *— and the item's own first word is the thing that had gone
      stale.* **"Weekly" was written before 22.5**, *the owner's note that a week
      is the wrong window for somebody who rides once of them; at that cadence a
      weekly intensity mix is one ride's shape drawn as a trend, which is the
      defect 22.5.1 exists to have removed. It is the same rolling 30 days the
      card at the top of the screen already reports, and*
      `RidingHistoryBuilder.isInWindow` *is one predicate now rather than two, so
      the two cards cannot disagree about which rides are in it.*

      ***It is power's zones and not the heart's, which is worth stating because
      this item sits in a heart-rate phase.*** *Every ride has power and a
      minority have a strap, so a 30-day heart summary would be 21.4.1's coverage
      trap at thirty times the scale — a shape drawn from the two rides somebody
      happened to wear a strap for, presented as a month. **21.4.6** is the
      heart's version and the condition it has to meet.*

      ***Three rules decide where a ride's seconds come from, and none of them is
      new.*** *A condensed ride is read from* `distributions_json` *and never
      recounted (23.4.2) — its rows are still there, so a scan returns a wrong
      number rather than nothing. A ride is counted against the FTP it was
      **ridden at** (7.8), and a ride with no FTP on the row is **not counted**
      rather than divided by today's number: unlike ride detail there is no room
      to caveat one ride inside a month. And a ride nobody can count stays in the
      denominator and is said out loud —* "from 6 of 13 rides" *— which is
      21.4.1's coverage caption a level up.*

      ***The card observes and does not prescribe***, *which is 21.4.4 obeyed
      rather than quoted. There is a famous target here — polarised 80-20 — and
      putting it on the card would turn every sentence into a mark out of ten for
      a rider who never asked to be marked.* **Easy** *is Z1–Z2 and* **hard** *is
      Z4 and above, the second of those borrowed from* `EffortAgainstPlan` *so
      the two features cannot come to mean different things by the same word. Z3
      is in neither number, is visible in the bar, and is not named.*

      ***Watched on the tablet AVD on two profiles as each other's controls, and
      checked against the database rather than against the screenshot.*** *Alex:*
      "Time in zone across the last 30 days · from 6 of 13 rides" *and* "1 hour 8
      minutes ridden: 4% easy (Z1–Z2) and 95% hard (Z4 and above)". *The same
      window counted in* `sqlite3` *by hand gives Z1 107 · Z2 74 · Z3 28 · Z4
      3809 · Z5 26 · Z6 28 · Z7 21, against the card's 106 / 74 / 29 / 3809 / 26
      / 28 / 21 — **identical totals, with two seconds sitting one band over**,
      which is the whole-watt rounding the query documents rather than a
      disagreement. Then a real 5:52 ride was finished and the card moved to* "7
      of 14" *without leaving the screen, which is the flow's own design: it
      observes* `workouts` *and not* `workout_metrics`, *so it redraws when a
      ride finishes rather than a few times a minute while one is ridden. Robin,
      the control: 3 of 12 rides, 12 minutes, 46% easy — a completely different
      shape off the same code.*
- [ ] **21.4.5** **The 30-day mix on the dashboard is deliberately not built.**
      *Your riding* is one tap from the card that opens it, and the dashboard
      spent two whole items (22.8, 22.9) getting its height back down from 993 dp
      of content in a 664 dp viewport. A fourth card there would spend that
      again on the least urgent of the three questions — *how hard* is something
      a rider asks about a month, not about tonight. If it ever goes there it is
      **one line**, not the bar and the legend
- [ ] **21.4.6** **The heart's own version of 21.4.3, and the condition it has
      to meet first.** Time in HR zone across the window is the more honest
      answer to *how much easy riding did I do* — a heart rate is a better
      ceiling than an uncalibrated power model (21.5) — and it is unbuildable
      until enough rides carry a strap. The rule it needs is 21.4.1's, applied to
      a set of rides rather than to one: the counts divide the time a heart rate
      was **reported**, and the card says how many of the window's rides that
      was. A month drawn from the two rides somebody wore a strap for, captioned
      as a month, is the defect; the same month captioned *from 2 of 9 rides* is
      not. Note the denominator moves too — `workouts.max_hr_bpm` is the ride's
      own (21.2.3), so the same three-rule shape 21.4.3 uses for the FTP applies
      here for the maximum
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

### 21.6 Do not ask what the heart rate already knows — the owner's note, 4 August 2026

***Renumbered from 21.5 in the forty-eighth sitting, and it had to be.** This
section and the one above it were both numbered 21.5, with items 21.5.1–21.5.4
appearing twice — so "21.5.3" named two different things, one of them a class
that requires a strap and the other a sentence on the ride detail screen. Item
numbers are how this plan refers to itself, which makes a collision a real
fault rather than untidiness. The **classes** section keeps 21.5, because
`MaxHeartRate.kt` cites 21.5.5 from the source; this one moved.*

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

- [ ] **21.6.1** **Prefill the effort answer rather than replace it** (26.3).
      An endurance class ridden mostly in HR zones 4–5 was hard; a threshold
      class ridden in zone 2 was easy. Prefilling costs nothing if it is wrong
      and saves a tap when it is right. **It must stay a prefill**: the rider's
      own answer is a fact about them and the app may not write one on their
      behalf, which is the same rule as 7.10.4/7.10.5 — *the app must not edit
      the rider's record behind them*. A prefilled answer must also be
      distinguishable from one the rider gave, or the column stops meaning what
      it says
- [ ] **21.6.2** **Feed the FTP proposal** (7.x). This is the one with teeth,
      because an FTP is written into the rider's permanent record and every
      zone in the app is derived from it. Two hard prerequisites before any of
      it: the ride's power must be **measured** rather than modelled
      (`PowerProvenance.isTrustworthyAsMeasured`, 7.10.7 — the same gate that
      already stops a simulated ride proposing anything), and the maximum heart
      rate must be **measured** rather than the Tanaka estimate (21.1), because
      an inferred effort built on an estimated maximum is two guesses wearing
      one number
- [x] **21.6.3** **Say it on the ride detail screen, which is free and safe.**
      "You spent 18 minutes in HR zone 4 on an endurance ride" is an
      observation about a ride, not a claim about the rider, and it needs
      neither 21.6.1's prefill nor 21.6.2's gates. **Read 21.2.3 first** — the
      thing that blocks it is that nothing yet draws an HR zone for a *past*
      ride, and 7.8's trap is why: the zone bands would be drawn from whatever
      maximum the rider has *today*, not the one the ride was ridden at

      ***Done in the forty-eighth sitting, and what made it buildable was that
      its own blocker had been cleared two sittings earlier without anybody
      noticing.*** The sentence above says plainly what stands in the way —
      nothing draws an HR zone for a past ride — and 21.2.3 (the maximum
      recorded onto the row) and 21.4.1 (the seconds counted into
      `distributions_json`) between them made that false. This is the same seam
      as the two items before it, read from the other end: the last three
      sittings looked for *claims that had gone stale*, and a **blocker** that
      has gone stale is one of those, in the direction of *now possible*.

      **`EffortAgainstPlan`, one sentence, and null on most rides.** The class
      writes down what it prescribed and the strap writes down what was made, so
      the join is a comparison of two counts of seconds. Every reason it says
      nothing is a rule from somewhere else in this plan: a free ride was asked
      for nothing, a rider with no maximum has no zones (21.2.4), a ride with no
      strap has nothing to compare, and — the one that is this item's own —
      **a strap that heard eleven minutes of forty describes eleven minutes**,
      not the ride. That last is 21.4.1's coverage caption arriving as a verdict
      instead of a percentage, which is worse, because a percentage can be
      checked against the caption beside it and a verdict cannot.

      **The two scales are never equated, and the wording carries that.**
      `HeartRateZone` says outright that it is not `PowerZone` with five
      entries: different conventions, different boundaries, and H4 is not Z4. So
      each side is asked the *same coarse question in its own terms* — how much
      of this was hard, where hard is each convention's own threshold — and the
      sentence names the heart's side as *"your top two heart-rate zones"* and
      the class's as what it prescribed. Neither is described in the other's
      language, and the test asserts the word *Threshold* appears in neither.

      **The tolerance is twenty percentage points and is deliberately blunt**,
      which is 21.6.4 built rather than quoted. Heart rate lags 30–60 seconds,
      drifts up across a long ride and moves with heat, sleep and caffeine, so
      the failure that costs something is telling a rider they overcooked an
      easy ride when they did not. A gap that size is a different ride, not a
      different day. Ten minutes of reported heart rate is the floor, and it has
      to cover at least half of what the ride recorded.

      **It survives a trim, and that is a design choice rather than luck.** It
      reads the *prescription* — the blocks the class asked for, which are as
      true as they ever were — and never the compliance, which 23.4.3 withdraws
      from a condensed ride. The heart's own counts come from
      `distributions_json`, written before the seconds went.

      **And it reaches the post-ride summary too, out of the same component
      (12.6), which is safe for a reason worth stating rather than assuming.**
      The effort question sits *above* the charts on that screen, deliberately
      (12.6.1), so the rider has already answered it by the time the observation
      is on screen. Beside the question it would have been 21.6.1's prefill
      without 21.6.1's rule that the rider's own answer stays theirs — the same
      change, in the wrong order, would have broken a rule the item below it
      exists to protect.

      ***Watched on the tablet AVD in four cases, on one real 12:48 ride of
      `THR-01 Threshold 2×4` with the power's own time in zone as the control.***
      The class prescribes 4:00 of Z4 in the 12:48 that was ridden — 31% — and
      every case below is the same 768 seconds with only the heart changed, so
      `Z1 4:27 · Z2 3:14 · Z3 1:58 · Z4 1:03 · Z5 0:51 · Z6 1:15` is identical
      in all four screenshots.

      - **As ridden, straight off the simulator:** H4 1:42 + H5 2:07 = 3:49,
        which is 30% against the prescription's 31%. *"About what the class
        asked — 3 minutes 49 seconds in your top two heart-rate zones, against
        the 4 minutes it prescribed."* A real case out of the box, and a
        one-point gap is exactly what the middle verdict is for.
      - **Every second at 160 bpm** (H4 for a Tanaka maximum of 179): *"Harder
        than the class asked — 12 minutes 48 seconds …, against the 4 minutes it
        prescribed."*
      - **Every second at 110** (H2): *"Easier than the class asked — no time in
        your top two heart-rate zones, against the 4 minutes it prescribed."*
      - **A strap that heard the last 3:13 of the 12:48, all of it H4** — and
        this is the case worth having watched, because the card draws, the
        coverage caption says *"a heart rate for 03:13 of 12:48"*, and **there
        is no verdict at all**. Without the gate it would have said *harder than
        the class asked* off 100% of a quarter of the ride.

      **Two defects came out of looking at the card rather than the diff**, and
      both had been on screen for four sittings. The zone list did not end in a
      full stop, so the new sentence ran straight on from it as one sentence —
      the postfix is load-bearing now and is tested. And `formatDuration` had
      the plural hard-coded, so the card had been saying **"1 minutes 42
      seconds"** since 16.2.4, in a string that is read aloud by a screen reader
      as well as printed. **767 JVM tests, 0 failures**, up from 751
- [ ] **21.6.4** **The honest limit, stated once so it is not rediscovered.**
      Heart rate lags effort by a minute or two, drifts upward across a long
      ride at constant power, and moves with heat, caffeine, sleep and
      illness. It is a good signal about *a ride* and a poor one about *a
      sixty-second interval*. Anything built here should compare whole blocks
      or whole rides, never single samples — and it must degrade to silence
      when no strap is worn, which on this bike is most of the time


---

### 21.7 An Apple Watch as the strap — the owner's note, 16 August 2026

**The owner's words, verbatim:** *"Apple Watch as heart rate monitor. I don't
use Apple Watch but my friend does. Let's do what we realistically can to
support this as HRM. Peloton support it."*

**The likely answer is that it already works and nobody has tried it.**
`BleHeartRateManager` does not look for straps by name — it filters the scan on
the **standard Heart Rate service UUID (0x180D)**, which is why a Polar H10 is
found and why the previous name-matching implementation found nothing. Anything
that advertises that service is an ordinary strap to this app. **So the work
here is a measurement and a sentence, not a feature** — unless the measurement
says otherwise, in which case 21.7.3 is the fallback.

**Why Peloton supporting it is not evidence that we can.** Peloton's Apple Watch
support is their own watchOS app talking to their own iPhone app talking to
their own account — a platform, not a Bluetooth trick. Copying that shape needs
an iOS app, a watchOS app, a paid Apple Developer account and a transport to
this tablet that does not exist (29.2, route 3). **That is not the realistic
thing the note asks for.**

**And the fact that decides the shape needs confirming before anything is
promised.** watchOS supports CoreBluetooth as a *central* — a watch can talk to
a strap — and, as far as this plan knows, **not as a peripheral**: a watch
cannot advertise itself as a heart-rate monitor. If that is right, then every
App Store app that claims to "broadcast your Apple Watch heart rate" is really
watch → iPhone → **iPhone** advertising as the BLE peripheral, and the rider
needs their phone near the bike as well as the watch. **Both routes look
identical to this app** — a standard HRM appears in the strap list — but they
are different instructions to the rider, and telling somebody the wrong one is
how a working feature reads as broken.

- [ ] **21.7.1 Try it before building anything**, with the friend and their
      watch, and it needs no bike and no pedalling: install a broadcaster app
      on the watch or phone, open Settings → heart-rate strap, and see whether
      it appears in the scan and delivers BPM. **The whole item may close
      here.** Note the trap this tablet has already produced twice: on
      Android 11 the BLE scan permission is `ACCESS_FINE_LOCATION`, and a
      permission the manifest does not declare is denied instantly with no
      dialog and nothing in logcat — that is declared and granted today, and it
      is the first thing to re-check if the scan finds nothing
- [ ] **21.7.2 Write down which route worked**, in `HARDWARE.md` beside the
      strap that has been verified, naming the app and whether the phone had to
      be present. A rider being told *"pair your Apple Watch"* when the answer
      is *"and keep your phone on the bike"* is a support problem this project
      can avoid for the cost of one sentence
- [ ] **21.7.3 If it does not work, the fallback is a list rather than a
      build.** Straps this app has been *seen* to work with, in Settings, where
      a rider chooses one — the Polar H10 is verified (2.3.5) and an Apple Watch
      would join it or not. **Do not build a special Apple Watch path**: there
      is no Android API that reaches a watch except the standard one already
      here, and a second code path for a device nobody in the project owns is a
      path nobody can test
