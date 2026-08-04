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

- [ ] **26.1.1** **The profile selector, which is the owner's own example.**
      Each tile reads `150 W FTP` under the rider's name. Three problems in six
      characters: `W` and `FTP` are both jargon, the number is the *least*
      useful thing about a rider on a screen whose only question is "which of
      you is it", and it is stale by design — the number moves on its own
      (Phase 7) and nobody chooses their profile by it. Wanted: either nothing
      under the name, or something a person actually recognises themselves by
      — last ride, or rides this month. **Prefer the recognition cue to the
      measurement**; the FTP already has two screens of its own
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
- [ ] **26.1.3** **Audit for "output" and kilojoules.** `Total output` in kJ is
      on the summary card, the dashboard and the leaderboards. It is the one
      genuinely obscure unit in the app — watts at least appear on every gym
      bike in the world — and it is used in places where *what a rider did* is
      the question rather than *how much energy*. Decide once, in one place,
      whether kJ is shown to a rider at all outside charts and comparisons, and
      what replaces it where it is not. Note it cannot simply go: the household
      leaderboard ranks on it (24.1) and two riders' kJ is the only fair
      comparison the app has
- [ ] **26.1.4** **Settings, and the difference between a setting and an
      explanation.** Several rows carry a paragraph of body text under them.
      Some of it is load-bearing (the telemetry source, the consent gate — 23.1)
      and some is the author talking. The audit is per row: keep the sentence
      that changes what a rider would choose, cut the one that explains how it
      works

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
easy b) that was a good workout c) I'm exhausted."*

**This is the same rule as the rest of the phase, applied to a control rather
than to a sentence.** A ten-point scale is not more information, it is more
*decision* — and the decision it asks for is one nobody can make. Borg's RPE
earns its resolution in a lab, where it is calibrated against a rider who has
been taught it and repeated over sessions. On a bike in a living room, asked of
somebody who has just stopped pedalling and is out of breath, the gap between 6
and 7 is not a measurement; it is a doubt manufactured by the control. The
owner's word for the result is the right one: *anxiety*.

**Nothing downstream needed the ten points.** One consumer reads the number at
all — `PostWorkoutAnalyzer.suggestFtpFromRpe`, which asks *was this ≤ 4* — and
everything else displays it.

- [x] **26.3.1** **Three answers, and the column stays 1–10.** `PerceivedEffort`
      is the type: `Comfortable` / `A good workout` / `Everything I had`, each
      storing the middle of its band (3 / 6 / 9) and reading back anything
      inside it. Three reasons the column does not change: **a ride already
      recorded keeps its exact answer** rather than being reinterpreted, the
      cloud payload's field is untouched (14.4), and `EASY_RPE_THRESHOLD` still
      works — *Comfortable* stores 3, so a hard class that felt easy still
      proposes an FTP bump. That last one is the trap worth naming: had the easy
      level stored 5, the FTP proposal would have silently stopped firing and
      **no screen would have looked any different**. It is a test.

      *Done and observed on the tablet AVD. A new ride answered "A good workout"
      lands as `rpe_rating = 6` in the database; **a ride rated 7 on the old
      ten-point scale opens in ride detail reading "A good workout"**, which is
      the backward-compatibility claim checked against real data rather than
      argued. Both screens ask it the same way, which matters more than either:
      a rider who answered on the night must not meet a 1–10 row a month later.*
- [x] **26.3.2** **Each answer says what it felt like.** Three wide buttons have
      room for a line under the label, and that line is what makes them
      answerable — *Comfortable* against *A good workout* is not a distinction
      anybody can make from the titles alone. It is also the argument for why
      three is not simply "ten with less detail": the detail moved from a number
      the rider had to interpret into words that interpret themselves
- [ ] **26.3.3** **The wording is the owner's to settle.** The labels here are a
      first draft against their brief ("use better labels than I'm suggesting").
      Worth a look on the bike: *Everything I had* is the one most likely to be
      wrong, because a rider who stopped a class early did not give everything
      and may not want to say they did

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
- [ ] **26.3.3** **The wording is the owner's to settle.** These labels are a
      first draft against their brief ("use better labels than I'm
      suggesting"). Worth a look on the bike: *Everything I had* is the most
      likely to be wrong, because a rider who stopped a class early did not
      give everything and may not want to say they did

