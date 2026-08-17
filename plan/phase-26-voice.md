# Phase 26 — the app's voice: less is more

## Phase 26: How much the app says, and in whose language

**From the owner's inbox, 4 August 2026.** Verbatim, because the second half is
the part that is easy to lose in a summary:

> *"When working on tasks that involve improving design and UX, err on the side
> of 'less is more'. Try and avoid verbosity, particularly around geeky terms
> like watts and kilojoules. You can still use these geeky terms but there is a
> time and a place. As an example, the profile selector screen includes terms
> like W and FTP, where perhaps just a number (nicely presented) could be
> better. Take a look at screens (when you're working on them) from the
> perspective of a customer who wants a really sleek modern experience."*

**This is a standing rule, not a backlog of screens.** It says *when working on
a screen*, which makes it the copy-and-density equivalent of what 22.4 is for
layout: a thing every future session applies rather than a thing one session
does. It is therefore also a line in `CLAUDE.md`, and the items below are the
specific debts that already exist.

**What it is not.** It is not "remove the numbers". This app's whole reason to
exist is that it measures a rider properly, and a rider looking at a power chart
wants the axis labelled in watts. The distinction the owner drew is *time and
place*: a unit belongs where somebody is reading a measurement, and does not
belong where somebody is picking their name off a screen. The failure mode this
guards against is the one every enthusiast-built app has — every surface written
as though the reader is the author.

**Three tests to apply to a screen, in order:**

1. **Who is reading this, and what did they come here to do?** The profile
   selector is answered by a *tap*, not by a number. The ride detail chart is
   answered by a number and needs its unit.
2. **Would this sentence survive being read out loud by someone who does not
   ride?** "63 kJ total output" would not. "Ride Summary · 24 min" would.
3. **Does the label earn its line?** A label above a number that repeats what
   the number obviously is (`Duration  24:03`) is one line of two doing work.
   Cards and tiles usually want the number large and the label small, once.

**Where jargon is right and must stay**, so that this item does not get applied
where it does damage: the ride screen's metric tiles and the overlay (a rider at
two metres reading their own effort, 11.6); chart axes and their captions (16.1);
the FTP screens, which are *about* FTP and where the acronym is the subject
(7.10.1); and anywhere the app has to be honest about provenance — "estimated",
"modelled", "measured" are claims, not decoration, and shortening them loses the
claim (14.4.7, 21.1).

---

### 26.1 The debts that already exist

- [x] **26.1.1** **The profile selector, which is the owner's own example.**
      Each tile reads `150 W FTP` under the rider's name. Three problems in six
      characters: `W` and `FTP` are both jargon, the number is the *least*
      useful thing about a rider on a screen whose only question is "which of
      you is it", and it is stale by design — the number moves on its own
      (Phase 7) and nobody chooses their profile by it. Wanted: either nothing
      under the name, or something a person actually recognises themselves by
      — last ride, or rides this month. **Prefer the recognition cue to the
      measurement**; the FTP already has two screens of its own

      *Done and observed on the tablet AVD: a name and a face, and nothing
      else. **Neither of the two options this item offered**, in the end — the
      recognition cue was going to need a per-profile query for a subtitle
      nobody had asked for, and the honest answer to "which of you is it" is
      the face. A profile tile with nothing under the name also puts the two
      secondary tiles' subtitles to work: Guest and New rider now carry the
      only explanatory text on the screen, which is 20.1.3's weighting made
      visible instead of argued.*
- [x] **26.1.2** **The post-ride summary's six labelled rows** are the clearest
      case of test 3: `Average power  188 W` twice over in a column reads like a
      spec sheet at the moment a rider is out of breath and wants to know how
      they did. See 26.2 — it is the same work as the layout item and should be
      done in one pass, not two

      *Done in one pass with 22.4.6, which is where the evidence is. What
      changed in words rather than layout: the value carries the weight and the
      label sits under it small, the unit is a quiet label beside the number
      rather than part of it, and **the largest type on the screen is the class
      the rider chose** — it used to be the words "Ride Summary", which is the
      one thing a rider who has just stopped pedalling already knows.*
- [x] **26.1.3** ~~**Audit for "output" and kilojoules.**~~ `Total output` in kJ is
      on the summary card, the dashboard and the leaderboards. It is the one
      genuinely obscure unit in the app — watts at least appear on every gym
      bike in the world — and it is used in places where *what a rider did* is
      the question rather than *how much energy*. Decide once, in one place,
      whether kJ is shown to a rider at all outside charts and comparisons, and
      what replaces it where it is not. Note it cannot simply go: the household
      leaderboard ranks on it (24.1) and two riders' kJ is the only fair
      comparison the app has

      ***Done in the fifty-second sitting, and the premise needed correcting
      before the audit could be run.*** *This item calls kJ* "the one genuinely
      obscure unit in the app" *and reasons from there —* "watts at least appear
      on every gym bike in the world." *For this app's actual audience it is
      close to the opposite. **Everyone who runs this owns a Peloton bike**, and
      Total Output in kilojoules is the number Peloton's own leaderboard has
      ranked them on since the day they bought it. It is the most familiar unit
      in the app to the only people who will ever see it, not the least. An
      audit run on the original premise would have removed the one figure this
      audience already reads fluently.*

      ***So the rule that decides it is CLAUDE.md's rather than obscurity: a
      unit belongs where a measurement is being* read*, not where a choice is
      being* made.*** *That gives one answer per surface and it is the decision
      this item asked to be made once:*

      - ***Kept, because the number is being read or compared:*** *the ride
        screen's output tile, the overlay, `RideFigures` on the summary and on
        ride detail, chart captions, **every leaderboard**, the rival chips on
        class detail and the race score. The item is right that it cannot simply
        go from the leaderboards — two riders' kJ is the only fair comparison
        this app has (24.1) — and that is not a grudging exception: a
        comparison* is *a measurement being read.*
      - ***Cut, because a choice is being made:*** *the **history list row**,
        which carried five facts — the time, the duration, the kilojoules, the
        average watts and the distance — on a screen whose only question is
        which ride to open. That is precisely the failure case CLAUDE.md spells
        out for a profile tile reading `150 W FTP` under a name, on a screen
        whose only question is* which of you is it. *The class name answers most
        of it and the time and duration answer the rest; the other three are one
        tap away on the ride itself, where they are being read. Observed on the
        tablet AVD.*
      - ***Already done by another item, and nobody crossed it off:*** *the
        **dashboard**, one of this item's three named places, has carried no
        kilojoule total since **22.1.2** replaced two of them with rides and
        minutes —* "Rides rather than kilojoules, because 'have I been riding'
        is answered by rides" *is a comment in `MainDashboardScreen` today. So a
        third of this item was finished weeks ago by a phase that was not
        looking for it. Same family as 19.1.6 and 21.4.1: **a written-down claim
        goes stale in the direction of* already finished *as readily as the
        other way.***
- [x] **26.1.4** **Settings, and the difference between a setting and an
      explanation.** Several rows carry a paragraph of body text under them.
      Some of it is load-bearing (the telemetry source, the consent gate — 23.1)
      and some is the author talking. The audit is per row: keep the sentence
      that changes what a rider would choose, cut the one that explains how it
      works

      ***Done, per row, and read on the tablet AVD rather than in the diff.***
      Nine cuts, and the useful part is *which* nine — every one of them is a
      sentence answering a question nobody standing on that row was asking:
      - **Units** defended *not offering calories* and explained why watts have
        no imperial form. What a rider needs is what the switch moves.
      - **Use wallpaper colours** opened with *"Material You"* — Android's name
        for the mechanism, and nobody's name for anything they want.
      - **Maximum heart rate** printed the formula, *"Tanaka's 208 − 0.7 ×
        age"*. It is the right formula (21.1.4) and naming it is the author
        talking; that it is a **guess**, and how wrong it can be, is the part
        that changes what a rider types.
      - **How solid the overlay is** explained why the slider stops where it
        stops — which the slider demonstrates by stopping. It also said
        *"strip"*, which CLAUDE.md says is never the rider-facing word for the
        overlay, so the cut fixed a naming fault nobody had noticed.
      - **Position** justified its own default (*"Top is the default because
        subtitles live along the bottom"*). Turned round, the same fact is the
        reason to choose an edge: *"Subtitles live along the bottom, so top is
        usually the safer edge."*
      - **Coach volume** explained why there are two sliders, on a screen
        showing two sliders — and it is shared with the overlay's own volume
        panel, so the cut lands twice.
      - **Volume**'s footnote ended *"— which is when you actually find out the
        film is too loud"*, which is the author enjoying themselves.
      - **Backup** repeated *Your rides*' sentence from the card directly above
        it. Said twice it stops being read.
      - **Show me to the others** carried **two** paragraphs on one switch, and
        only the second answers the question a rider actually has, which is
        whether turning it off costs them anything.

      **One row was audited and left alone, which is the other half of doing
      this honestly.** The FTP field keeps *both* its lines: Settings is the one
      screen where that number is **typed** rather than read, so spelling the
      acronym out earns its place here even though 22.8.2 moved the same
      definition off the dashboard — and the provenance line under it (*"up from
      198 W · the app's first guess"*) is 7.10.4's rule that a number which
      moves by itself and cannot be traced is indistinguishable from a bug
- [x] **26.1.5** **The pre-ride goal prompt named its own multiplier.** *"Push
      targets 5% higher than your zones prescribe"* and *"Ease targets 5% below
      your zones for a sustainable effort"* — on the one dialog standing between
      a rider and starting a class, and each line carrying three pieces of
      machinery (a percentage, the word *targets*, and the claim that zones
      *prescribe*). The owner's call, 5 August: *"let's hide away the +5% and
      -5%, it's too geeky, the user doesn't need to 'see behind the curtain' on
      this one."* Now *"A bit harder than your zones ask for"* and *"A bit
      easier, for an effort you can hold"*. **`k` is unchanged** — 1.05 and
      0.95 still scale every target, and `RideIntent.fromMultiplier` still
      depends on them being distinct. What changed is that the number is no
      longer the thing the rider is asked about. **Observed on the tablet AVD**
- [ ] **26.1.6** **There is no way to ride a class at the zones it was
      authored with.** `RideIntent` has exactly two entries and both scale away
      from the prescription; Cancel abandons the start rather than accepting
      it, so **every ride ever recorded by this app is ±5% off the catalogue**.
      That sits oddly beside `classlibrary/`, where a build refuses to emit a
      session that breaks a design rule and then every rider is moved off it by
      default — and `DEFAULT = JustStayFit` means a resumed ride whose modifier
      does not resolve silently becomes 0.95 rather than 1.0.

      **Raised 5 August and deliberately not built**: the owner's answer was
      *"leave it entirely"*, and 26.1.5 above is what they asked for instead.
      Written down because the reasoning is worth keeping and the fix is small
      if the answer ever changes — a third entry at 1.0, made the default.
      `fromMultiplier`'s distinctness rule survives it, since 1.0 collides with
      neither. What it would cost is a third card on a dialog that Phase 26 has
      just finished making quieter, which is the honest argument against

### 26.2 The rule, written where it will be read

- [x] **26.2.1** **In `CLAUDE.md`, beside the width-cap rule.** One line, the
      same shape as 22.2.6's: *less is more, and a unit belongs where a
      measurement is being read rather than where a choice is being made.* A
      rule in a phase file is a rule a session finds after it has already
      written the screen
- [ ] **26.2.2** **Judged on the 1280 × 720 dp AVD**, and by looking at the
      screen rather than at the diff. Same condition as 22.4.5 — density is a
      thing you see and not a thing you count

---

### 26.3 Ten answers where three will do — the owner's note, 4 August 2026

**Verbatim:** *"It's 1-10 which is good but honestly it causes me anxiety,
wondering if I'm selecting the right option. Please change it to three options.
Use better labels than I'm suggesting but basically it should be a) that was
easy b) that was a good workout c) I'm exhausted. Under the hood these can
still map to 1-10 if that makes data migration easier. Whatever you think."*

**This is the same rule as the rest of the phase, applied to a control rather
than to a sentence.** A ten-point scale is not more information, it is more
*decision* — and the decision it asks for is one nobody can make. Borg's RPE
earns its resolution in a lab, where it is calibrated against a rider who has
been taught it and repeated across sessions. On a bike in a living room, asked
of somebody who has just stopped pedalling and is out of breath, the gap
between 6 and 7 is not a measurement; it is a doubt manufactured by the
control. The owner's word for the result is the right one: *anxiety*.

**Nothing downstream needed the ten points.** Exactly one consumer reads the
number rather than displaying it — `PostWorkoutAnalyzer.suggestFtpFromRpe`,
which asks *was this ≤ 4*.

- [x] **26.3.1** **Three answers, and the column stays 1–10** — which is the
      owner's own "whatever you think", answered. `PerceivedEffort` is the
      type: *Comfortable* / *A good workout* / *Everything I had*, each storing
      the middle of its band (3 / 6 / 9) and reading back anything inside it.
      Three reasons not to change the column: **a ride already recorded keeps
      its exact answer** instead of being reinterpreted, the cloud payload's
      field is untouched (14.4), and `EASY_RPE_THRESHOLD` keeps working —
      *Comfortable* stores 3, so a hard class that felt easy still proposes an
      FTP bump. That last one is the trap worth naming: had the easy level
      stored 5, the FTP proposal would have silently stopped firing and **no
      screen would have looked any different**. It is a test.

      *Done and observed on the tablet AVD. A new ride answered "A good
      workout" lands as `rpe_rating = 6` in the database; **a ride rated 7 on
      the old ten-point scale opens in ride detail reading "A good workout"**,
      which is the backward-compatibility claim checked against real data
      rather than argued. Both screens ask it the same way, which matters more
      than either: a rider who answered on the night must not meet a 1–10 row a
      month later.*
- [x] **26.3.2** **Each answer says what it felt like.** Three wide buttons have
      room for a line under the label, and that line is what makes them
      answerable — *Comfortable* against *A good workout* is not a distinction
      anybody can draw from the titles alone. It is also why three is not
      simply "ten with less detail": the detail moved out of a number the rider
      had to interpret and into words that interpret themselves
- [x] **26.3.3** **The wording is the owner's to settle.** These labels are a
      first draft against their brief ("use better labels than I'm
      suggesting"). Worth a look on the bike: *Everything I had* is the most
      likely to be wrong, because a rider who stopped a class early did not
      give everything and may not want to say they did

      *Settled by the owner, 4 August 2026: **"I love what you've done. Keep it.
      If someone ends a ride early then they are unlikely to rate it at all."***
      *The second sentence is the part worth keeping, because it answers the
      objection rather than waving it off: the case the item worried about —
      somebody who quit at eight minutes being asked to claim they gave
      everything — is a case where nobody answers the question at all, so the
      label is never read by the rider it would have misdescribed. Which also
      says something about the RPE answer rate that no code change can: **an
      unanswered RPE is evidence about the ride**, and nothing today
      distinguishes "did not rate it" from "has not rated it yet". Not worth an
      item on its own; worth remembering if 7.10.7's proposal ever starts
      reading absence as an answer.*


---

### 26.4 A score, shown the same way everywhere — the owner's note, 4 August 2026

**Verbatim:** *"We may have thrown the baby out with the bathwater a little. We
removed '200 W FTP' from profile selector screen and now it looks SO much
better. But now we don't see '200' at all. I wonder if there's a clean and
beautiful way we can show people's scores, consistently, a design system
feature, as a way of showing their overall score. A bit like 'lvl' in video
games. See what you think. Happy to leave it."*

**The note contains its own best argument and it is the word *lvl*.** A level in
a game is not a measurement dressed up — it has three properties, and they are
what make it feel good to see:

1. **It only ever goes up.** Nobody is demoted for a bad week.
2. **It is earned by playing**, so it accumulates rather than being measured.
3. **It is comparable between players without a unit**, because it is
   dimensionless by construction.

**FTP has none of the three.** It falls when a rider is ill, off the bike, or
simply having a bad day on the test that proposed it (Phase 7 moves it *by
itself*, which is 7.8's whole trap). It is a measurement, not an accumulation.
And raw watts are unfair between bodies — which the app already knows, because
the household board ranks on kJ (24.1) and kJ/kg exists precisely so two
housemates are not compared by mass. **So putting the FTP number back as a
"score" is 26.1.1's defect with the unit filed off**: the same stale, personal,
uninterpretable number, now also implying a demotion the rider did not earn.

The interesting reading of the note is therefore not "put 200 back". It is that
the app **has no dimensionless number for a rider at all**, and the reason the
profile tile felt empty is that nothing in this app has ever said *how much you
have ridden* in one glyph.

- [x] **26.4.1** ***Done, and the curve is the item.*** `domain/progress/
      RiderLevel.kt` — lifetime rides, minutes and kilojoules, through
      `level = 1 + floor(sqrt(points / 70))`. Seventy points is one typical
      ride (thirty minutes, 200 kJ), so **the first finished ride a rider ever
      does takes them to level 2**, which is the only place on this curve where
      one ride can move the number and is exactly the moment it should. After
      that a level costs the square of how far up it is: level 3 at four rides,
      4 at nine, 11 at a hundred, 30 at 841. A once-a-week rider is level 8
      after a year and 13 after three.

      **Three weights and one of them carries the item's argument.** A ride is
      20 points whatever it was, a minute is 1, and ten kilojoules are 1. The
      per-ride term is the largest for a short ride on purpose — ten twenty-
      minute rides beat one three-hour one at the same output, which is 22.5.2's
      *reward getting on the bike* arriving on a badge. And the kilojoule term
      is deliberately small, because it is the only one that is unfair between
      bodies: doubling the output over identical time is **at most one level**,
      which is a test rather than a claim.

      **`RidingHistory` turned out not to be the source, and that is worth
      writing down.** The item said "derived from figures `RidingHistory`
      already produces"; it produces seventeen *weeks*, and a level over a
      window is a level a rider can be demoted from by the calendar. The source
      is a new grouped query, `WorkoutDao.observeRiderTotals`, which is the only
      figure in this app that is not windowed. **And a trimmed ride still counts
      in full** — 23.4 condenses `workout_metrics`, not `workouts`, and all
      three columns live on the ride row. **Ten JVM tests**, the load-bearing one
      being that the level never falls across 500 rides of accumulation
- [x] **26.4.2** ***Done — the visible wording is `LVL` and a number.*** `LVL`
      is the owner's own word from the note, and it is the whole of what the
      badge is allowed to say: no unit, no adjective, nothing about fitness.
      The rule is written as rule 1 of four in `RiderScore`'s KDoc, where the
      next call site will meet it.

      **The one place it says more is the screen reader**, and that is the
      opposite of a loophole. A sighted rider reads `LVL 7` beside their own
      name on their own dashboard and the context supplies most of the meaning;
      a screen reader has no context to lend it, so the `contentDescription` is
      *"Riding level 7, earned by 46 rides"* — spelled out rather than left for
      the listener to supply. Less is more is a rule about a glance, and a
      screen reader is not glancing
- [x] **26.4.3** ***Done.*** `ui/components/RiderScore.kt`, one shape (the pill),
      one type scale (`labelSmall` + `titleSmall`), one colour
      (`primaryContainer`), and **four rules in the KDoc naming what a future
      call site must not do** — the `readableColumn` / `WideGrid` / `loneCard`
      habit the item asked for. Rule 3 is the one that would have been got
      wrong by taste: **never amber**, because amber is this app's off-target
      signal (11.8.3) and a rider's own identity must not wear the colour that
      means *you are wrong*.

      **The thin track along the bottom is the progress to the next level**, and
      it is unlabelled on purpose — a rider does not need the arithmetic to see
      that the bar is nearly full. It is also what keeps `RiderLevel.progress`
      a drawn number rather than a computed one nobody reads.

      **17.15.2's catch is real and is now 26.4.6**: the badge does not exist on
      the web app
- [x] **26.4.4** ***Done, in all three places, and the third one is the owner's
      to overrule.*** The two uncontested homes went in first — beside the name
      on the dashboard's greeting row, and beside each name on the household
      panel. On the dashboard it **costs no height at all**, which is the whole
      reason it is there rather than in a card: 22.8 found that screen carrying
      993 dp of content into a 664 dp viewport, so a badge that costs a row is a
      badge that pushes something below the fold.

      **The profile selector carries it too, and the note's own words are why.**
      26.1.1 took `150 W FTP` off that screen and the owner's verdict was "SO
      much better", so the item was right to call it the careful one — but the
      note says *"we removed '200 W FTP' from profile selector screen… but now
      we don't see '200' at all"*. That screen **is** the one the note is about,
      so leaving the badge off it answers a different question from the one the
      owner asked. On the tablet it reads as identity: `LVL 7` under *Simon* is
      the same shape as a level under a name in a game, where `150 W FTP` was a
      unit and a piece of jargon on a screen whose only question is *which of
      you is it*. **It is one line to remove and 26.4.5 explicitly allows
      "leave it"** — this is a session's judgement, taken because the note named
      the screen, and it is the owner's to reverse.

      **The household panel's ordering is deliberately untouched.** Alex sits
      above Simon at LVL 3 to his LVL 7, because the rows are ordered by riding
      *in the window* and the badge is who somebody is *over years*. Ordering by
      level would turn a presence card into a lifetime ranking, which is
      24.2's competition-nobody-entered arriving by the back door
- [x] **26.4.5** ***Held, and it decided one thing.*** The FTP keeps its two
      screens, its zone ladder and its chart bands; nothing was moved, replaced
      or captioned. Rule 2 in `RiderScore`'s KDoc is the rule that keeps it that
      way: **never beside the FTP as if they were the same kind of thing**,
      because a row containing both invites the reading that a higher level is a
      fitter rider — the one thing this number must never say. The FTP glance
      card and the level badge are on the same dashboard and are 400 dp apart
- [ ] **26.4.6** **The badge does not exist on the web app, which is 17.15.2
      arriving exactly as it said it would.** `RiderScore` is Compose, its
      colour is `primaryContainer` out of `Color.kt`, and nothing keeps
      `web/tokens.css` in step — so a rider who opens the companion app sees the
      number nowhere. **Deliberately not built in the same sitting**: the web
      app's own profile view is 17.x work, the deploy is the owner's (17.16.2),
      and a badge transcribed into CSS that nobody can see until a redeploy is
      the third fix stacked behind that door. What it needs when it comes: the
      *arithmetic* must not be transcribed with the colour. `RiderLevel` is
      pure Kotlin and a second implementation in JavaScript is two answers to
      one question — the level belongs in the payload or in a shared rule, not
      recomputed on the page
- [ ] **26.4.7** **Nothing writes the level down, and one day something should
      want to.** It is derived on read from three columns, every time, which is
      right today and is the same shape as the trap at 7.8: a figure derived on
      read cannot answer *what was it then*. It matters the moment anything
      wants to say **"you reached level 8"** — Phase 27's alerts are the obvious
      caller and Phase 28's achievements the other — because a level-up is an
      event and this has no memory of one. **Not a defect and not scheduled**:
      the honest note is that the first feature to need it should add the
      column, and should add it as *the level at the moment it changed* rather
      than as a cache of the current one
- [ ] **26.4.8** **Rule 2 is narrowed by the owner, and this is where that is
      written down.** *"Never beside the FTP as if they were the same kind of
      thing"* was decided at 26.4.5 and held; the owner's note of 16 August 2026
      asks for the level on the rider's face and the FTP under it, on the
      profile selector, and says in the same breath that they know it has
      repercussions (20.6, and the build is 20.6.4 and 20.6.5).

      **The rule is not deleted, because the reason for it has not changed.** A
      row containing `LVL 7` and `150 W` side by side still invites the reading
      that a higher level is a fitter rider, and that is still the one thing the
      level must never say. What changes is *where* the app is allowed to draw
      both about the same rider, and the shape it must take when it does:

      1. **Two screens: the profile selector, and the live leaderboard.** Not
         the dashboard greeting, not the household panel, not the static class
         board, not the overlay. The owner's own wording is the scope —
         *"perhaps just change what i said on the profile selection screen"* —
         and a rule that survives on every other surface is a rule; one relaxed
         wherever it is inconvenient is a habit.

         **The second screen arrived a day later and is 24.3.19b**, from the
         owner's own reference picture of Peloton's board, and it is written
         here rather than left as two plan items quietly disagreeing: this
         paragraph said *"not any leaderboard"* on 16 August and 24.3.19b was
         written the same day. **What makes the leaderboard different from the
         household panel is consent, not layout.** An FTP beside a housemate's
         name on a *presence* card publishes a measurement of somebody who was
         never asked; a leaderboard is a surface every row on it has opted into
         being ranked on, and `household_visible` is that opt-in (24.2.3). The
         enforcement is structural rather than remembered — the two columns are
         read in the join that carries the switch.

         **The static class board (`ClassLeaderboardCard`) is deliberately not
         included.** It ranks on kilojoules and kJ/kg and has no face on it at
         all; adding an FTP there would be the rule relaxing by habit, which is
         what this condition exists to prevent.
      2. **Never on one line, and never the same weight.** The level is *on the
         face*, the FTP is a caption under the name — different sizes, different
         colours, different rows, with the name between them. The forbidden
         thing was always the row that presents them as two readings of one
         instrument, and the tile does not do that.
      3. **The badge does not change to accommodate it.** No unit creeps onto
         the level, no "fitness" appears beside it, and it is still not amber.
         `RiderScore`'s rules 1, 3 and 4 are untouched.

      **The honest note is that this is a judgement made under an explicit
      delegation** — *"Happy to go with what you think"* — so it is the owner's
      to reverse on sight, exactly as 26.4.4's placement is. **What would settle
      it is one look at the tablet**, which is also what settled 26.1.1 in the
      other direction

- [ ] **26.4.9** **The compact badge is too big, and the word is what makes it
      that way — the owner's note, 17 August 2026.** *"It's too big, too much
      padding. Should probably just be the number with very little padding
      around it. It's obscuring too much of that avatar and looks bad."*

      **The measurement, taken before the write-up, and it is worse than the
      note claims.** On the tablet with a household of five the profile tile is
      about 158 dp, so the face is 70 dp (`size * 0.44f`), the ring takes
      3.9 dp of stroke and the disc inside it is **58 dp**. The badge's height
      is `70 × 0.26` = 18 dp and its minimum width is `18 × 2.4` = **44 dp**.
      So the pill is **three-quarters of the width of the face it sits on**,
      centred on the bottom of it, which on an Open Peeps figure is the collar
      and the shoulders — and the word it is that wide to hold is set at
      `18 × 0.30` = **5.5 sp**, which is not readable at arm's length let alone
      at two metres. It is paying 44 dp of somebody's face for a word nobody
      can read.

      **The fix is the owner's and it is the right one, and the trade is better
      than a shrink.** Drop `LVL` from the compact form and the number stops
      competing for width: the badge becomes a disc about as wide as it is
      tall — 18 dp instead of 44, a **60% reduction** — *and the number inside
      it can get larger* rather than smaller, because it is no longer the
      smaller half of a pair. That is the part worth naming, because "make it
      smaller" and "make the number bigger" sound like opposite instructions
      and here they are the same one.

      **This is Phase 26's own rule arriving at the one component built to
      state it**, which is why it is written here rather than in Phase 20: the
      badge's KDoc is the place *less is more* was made structural, and it has
      been carrying a word it did not need on the one surface where the word
      costs a face.

- [ ] **26.4.9a** **Rule 1 is narrowed, not lifted, and this says where the
      meaning goes instead.** `RiderScore`'s first rule is *"it says `LVL` and
      a number, and nothing else"*, and the thing that rule was built to
      forbid is a label **richer** than the number's honest claim — no unit, no
      "fitness", no adjective. Dropping the word is the opposite move and the
      rule's reason survives it intact. **But the word was doing a second job
      nobody wrote down**: it was saying *what kind of number this is*, and a
      bare number on a face has to get that from somewhere.

      **It gets it from three places, and they are worth listing because if any
      of them goes the badge is ambiguous again.** The **ring** round the face
      is the progress to the next level (20.6.8), and a number inside a
      progress ring is the most recognised "level" there is. The **screen
      reader is untouched** — `describe` still says *"Riding level 8, earned by
      41 rides"*, and rule 1 never applied to it. And on both surfaces that
      draw it the **other** number about that rider carries its own unit: the
      FTP is captioned `FTP 155 W` under the name on the profile selector and
      on the leaderboard row (26.4.8), so the two cannot be swapped for each
      other.

      **The pill keeps the word**, and that is not inconsistency. The pill
      beside a name on the dashboard greeting and the household panel has no
      ring, no face and nothing else about that rider on the row — it has none
      of the three things above, so it needs the word, and it has 64 dp of its
      own space to spend on one. **The compact form's difference from the pill
      is now a real one rather than a type scale**, which is the honest reading
      of the `compact` parameter's KDoc: it said only the type scale and the
      padding follow the height, and after this the wording does too.

- [ ] **26.4.9b** **What this costs 24.3.19d, and it is the finding rather than
      a caveat.** The rank is deliberately **off** the live leaderboard —
      24.3.17c deleted it on the owner's own argument that most rows are the
      rider's own targets, and 24.3.19d is the open question about putting it
      back after the reference picture turned up with one down its left edge.

      **A bare number on a face is safe on that board today precisely because
      there is no rank on it.** Put a rank column back and every person's row
      has two unlabelled small integers on it, one of them inside the face and
      one beside it, and the reader has to work out which is which — on the one
      surface where a small number in a ranked list *obviously* means position.
      So this item does not decide 24.3.19d and does not want to; what it does
      is **raise its price**, and the answer if the rank ever comes back is
      that the compact badge takes its word back on that screen rather than
      that the rank goes without one. Written down now because it is invisible
      later: the two items are in different phases and nine days apart, and the
      dependency runs from the smaller to the larger
