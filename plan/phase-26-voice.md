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

- [ ] **26.4.1** **If it is a level, it is built on volume, not on fitness.**
      Rides, minutes and kilojoules over the rider's whole history, through a
      curve that grows slowly — monotonic by construction, so it survives an
      injury, a holiday and a bad winter. This is the same argument 22.5.2 made
      about the streak: the quantity the app should reward is *getting on the
      bike*, because that is the one the rider controls. Pure, JVM-testable, and
      derived from figures `RidingHistory` already produces
- [ ] **26.4.2** **It must never be presented as fitness, and the wording is the
      whole risk.** A rider at level 12 beside a rider at level 30 must not read
      as *fitter* — it reads as *has ridden more*, which is true and is also the
      only thing the number can honestly claim. This is where Phase 26's own
      rule cuts both ways: less is more, but a number with no label is a number
      the reader supplies their own meaning for. One word, not a paragraph
- [ ] **26.4.3** **The deliverable is the component, not the number.** The
      owner's phrase is *"consistently, a design system feature"*, and this
      project's habit is the right one — `readableColumn`, `WideGrid`,
      `loneCard`: one token, its KDoc naming the others. A `RiderScore`
      composable with one shape, one type scale and one colour, so the profile
      tile, the dashboard, the household board (24.1) and the web app (17.15)
      cannot each draw it slightly differently. **17.15.2 is the catch**:
      nothing keeps `tokens.css` and `Color.kt` in step, so a badge invented on
      the bike will not exist on the web until somebody transcribes it
- [ ] **26.4.4** **Where it goes is a separate decision from what it is, and the
      profile selector is the one place to be careful.** 26.1.1 just took a
      number off that screen and the result is the owner's own "SO much better",
      so a badge goes back there only if it reads as *identity* rather than as
      *measurement* — "I'm the one on 12" is a recognition cue, "I'm the one on
      150 W" is not. The dashboard and the household board are the uncontested
      homes. Judged on the AVD (26.2.2), and it is the kind of thing to draw
      three ways and look at rather than argue about
- [ ] **26.4.5** **The FTP is not homeless and does not need this.** It has two
      screens of its own (7.10.1, 7.10.2), the ride screen's zone ladder is a
      reading of it, and every chart's bands come from it. Whatever this becomes,
      it is a second quantity beside the FTP and never a replacement for it —
      and if the answer turns out to be "leave it", which the owner explicitly
      allowed for, that is a legitimate close for this section rather than a
      failure
