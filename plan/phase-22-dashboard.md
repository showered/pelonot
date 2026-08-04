> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 22: The dashboard — the first screen, and the least considered

Two separate complaints, both raised from riding the app: the progress section
does not show progress, and the layout is stretched across a screen it should
be *using*.

### 22.1 "Your Progress" shows no progress

As it stands the section is a heading, the subtitle "Track your performance over
time", and two cards: today's output in kJ and the last ride's output in kJ.
Nothing there is a trend, nothing is compared to anything, one of the two is
usually 0.0, and both are the same quantity on the same axis. The honest empty
state (see *Corrections*) fixed an outright lie — it used to show hardcoded
figures on a device that had never recorded a ride — without making what
replaced it mean anything.

- [ ] **22.1.1** **Decide in one sentence what the dashboard is for**, and let
      the section follow from that rather than from what fits. The candidate
      answer: *"should I ride today, and what should I ride?"* — anything that
      is really "what have I done" belongs to history (12) and trends (16.3)
- [ ] **22.1.2** Replace the two kJ cards with **consistency**: rides this week
      against the rider's own recent norm, and the calendar heatmap (16.3.5).
      What gets somebody onto a bike is a streak they do not want to break, not
      a kilojoule total they cannot interpret
- [ ] **22.1.3** **A trend that is genuinely a trend** — output or minutes per
      week over the last six to eight weeks, sparkline-sized. The history query
      already returns what this needs
- [x] **22.1.4** FTP, with the date it last changed and what changed it
      (7.10.2, 16.3.1). The app already computes this (7.1) and it is the
      closest thing to a real progress number it owns — but it currently keeps
      only the latest value, so the history this card wants has to be recorded
      first (7.9). *7.9 recorded it and this card now reads it: the number, a
      stepped sparkline of every value it has held, and how far it moved, when,
      and who moved it. **The first thing in this section that is a trend rather
      than a total.** The two kJ cards below it (22.1.2) are still what they
      were*
- [ ] **22.1.5** A **last ride** card that opens the ride detail (12.2) —
      class name, RPE, and whether it beat the rider's own previous ride of the
      same class, which `leaderboardFor` already computes and nothing renders
- [ ] **22.1.6** Personal bests (16.3.3), suppressed entirely until there are
      enough rides for them to be true. A "best" computed from one ride is noise
      wearing a trophy
- [ ] **22.1.7** Every figure here has to be honest about whose watts it is
      (16.1.6). A rider who moved from simulated to hardware telemetry gets a
      step change in their own history, and an unexplained cliff in a progress
      chart reads as the app being broken
- [ ] **22.1.8** Rebuild `DashboardStats` and the dashboard ViewModel around
      whatever 22.1.1 decides, rather than bolting cards onto the current two
      totals. Keep every query windowed the way 12.1.6 does — this is the first
      screen after profile selection and it must never touch `workout_metrics`

### 22.2 A tablet-shaped dashboard

**Read 11.3.1 first, and note it is not wrong.** It says the dashboard fills
the width with no dead right-hand side, re-checked twice on the bike and once
on a matching AVD, and that is true. The complaint here is the opposite
failure: a single column of full-width cards *stretched* across 1280 dp. A card
1200 dp wide with a two-word label in it is harder to read than the same card
at 600 dp, and the screen is big enough to be showing more than one thing at a
time.

- [x] **22.2.1** Cap the main column and centre it — on the order of 700–800 dp
      — so a card reads as a card rather than a band across the room. Measured
      on the 1280 × 720 dp AVD from `HARDWARE.md`, never on a phone.
      *Capped at 760 dp. A **maximum**, not a width, so nothing changes below
      the breakpoint. The rails it opens up are deliberately left empty —
      22.2.2 and 22.2.3 are the decision about what goes in them, and filling
      them card by card is exactly what 22.2.3 says produces three columns of
      unrelated things. **Observed on the tablet AVD**; 22.2.5's check on the
      bike itself is still owed*
- [ ] **22.2.2** Then use the two rails that opens up **deliberately**, rather
      than leaving symmetrical dead space: for instance who is riding and
      today's context on one side, the last ride and the streak on the other.
      The rails exist only in landscape and must fold back into the column below
      a breakpoint
- [ ] **22.2.3** Decide the three regions as one layout — what the middle is
      for, what a rail is for, and what a rail does when it has nothing to say
      (it disappears; it does not show an empty card). Doing this card by card
      produces three columns of unrelated things, which is worse than one
- [ ] **22.2.4** The same question applies to Settings and History, which are
      also full-width cards on a wide screen. Do the dashboard first and find
      out whether the answer generalises before rolling it out
- [ ] **22.2.5** Verify against the real system furniture — a 48 dp bottom
      navigation bar and no top status bar (`HARDWARE.md`) — and on the tablet
      itself before ticking anything here
- [x] **22.2.6** **Make the width cap a rule rather than one screen's fix.**
      *Done and observed.* `Layout.readableWidth` (760 dp) and
      `Modifier.readableColumn()` in the theme; the dashboard's private
      `DashboardMaxWidth` is gone and Settings, History, ride detail and the
      class library now use the token. Explicitly not the ride screen or the
      overlay. **Observed on the tablet AVD**: the class library's cards start
      at 438 px of 1920 rather than 48, and Settings' Units paragraph wraps at a
      readable measure instead of crossing the whole bike. The original ask
      follows.*
      22.2.1 capped the dashboard's main column at 760 dp and it is right, but
      it is the only surface that has one — Settings, History, ride detail and
      the class library all still run edge to edge on a 1280 dp-wide tablet,
      where a line of body text crosses the whole bike. Wanted: one token (a
      `readableWidth` in the theme, beside `spacing`), applied wherever a
      column of text or form fields lives, so the answer is not re-decided per
      screen. **Not** for the ride screen or the overlay, which are deliberately
      full-bleed and are read at two metres rather than at arm's length

### 22.3 Small things on the dashboard

- [x] **22.3.1** **"Good morning" was a string literal**, shown at every hour
      of the day — cheerfully wrong for two thirds of it, on a bike that mostly
      gets ridden in the evening. It reads the clock now, with a fourth case
      for the small hours because somebody riding at 2 a.m. is not having a
      morning. Read once per composition rather than from a flow: nobody's
      evening turns into night while they look at this screen. *Observed on the
      tablet AVD at 23:42: "Good evening,"*

---

### 22.4 The width cap is not a width *budget* — the owner's note, 3 August 2026

**The owner's words, kept because the distinction is the whole item:** *"just
because I don't want one piece of UI to stretch the full width, doesn't mean we
can't actually use the full width!"*

22.2.6 is being read as a rule about the **screen** when it is a rule about the
**line**. `readableWidth` exists because 1280 dp of body text is hard to read —
that is a fact about a paragraph, not a licence to leave 520 dp of tablet empty
on every screen in the app. The two answers to a wide screen are *cap the
column* and *use the width*, and 22.2.6 only ever supplied the first, so every
screen that got the token quietly chose it by default.

The named example is the right one to start from: **ride detail is a stack of
charts in one 760 dp column**, so a rider scrolls past four cards to compare two
of them, on a screen with room to show all four at once. A chart is not a
paragraph. Nothing about `readableWidth` was ever meant to apply to it.

The rule this phase should end up with, in one line: **cap what is read at arm's
length, tile what is looked at.**

- [x] **22.4.1** **Write the two-answer rule down where the token lives.**
      `Modifier.readableColumn()`'s KDoc currently says what it does and why
      the number exists; it must also say what it is *not* for, and name the
      alternative. Wanted beside it: a companion for the other answer — a
      `Modifier.wideGrid()` or an adaptive-columns helper — so "use the width"
      is as cheap to reach for as "cap it" and does not get re-invented per
      screen. One token each, not a number per screen (the 22.2.6 argument,
      applied to the case it missed)

      *Done. `WideGrid` in `ui/theme/WideLayout.kt`, and each token's KDoc now
      names the other and the rule between them. A composable rather than the
      `Modifier.wideGrid()` this item guessed at — a modifier cannot lay cells
      out in rows, and pretending otherwise would have put the arithmetic back
      in the screens. Row-major, so the order survives the fold. **Two pure
      functions and six tests**, because the failure they guard is silent:
      `columnsFor` counts the gaps between cells (forget them and the grid is
      one column too wide and every cell lands under its minimum), and
      `balancedColumns` stops six figures in a five-wide grid coming out as
      five and a stray — which is what the AVD drew first, and it reads as a
      mistake rather than as a layout*
- [ ] **22.4.2** **Ride detail becomes a grid.** The charts are the case the
      owner named. Two columns at 1280 dp, one below the breakpoint, and the
      **order must survive the fold** — a reader going down column one and back
      up column two is reading the cards in a different order than the phone
      does, so decide whether these cards have a narrative order (power then
      cadence then heart rate) or are a set, and lay them out as whichever they
      are. Note 16.3.4's housemate picker and the ride's own header stay full
      width: they are controls and context, not cards in the set
- [ ] **22.4.3** **Audit every screen that took the token in 22.2.6** and say,
      per screen, which answer it wants — cap, tile, or a capped column *inside*
      a wider frame. Settings and the class library are probably genuinely
      capped (form fields and a list of titles). History is a list of rides and
      may well want two columns. The audit is the deliverable: a screen that was
      capped because the token was handy is the failure this item exists to
      find
- [ ] **22.4.4** **A rail is not a grid, and 22.2.2/22.2.3 are still the harder
      question.** This item is about surfaces that have *one kind of thing* and
      too much room for it, which is a layout with an obvious answer. The
      dashboard has three kinds of thing and no obvious answer, and filling its
      rails card by card is what 22.2.3 already warns against. Do 22.4.2 first
      and see whether the grid helper it produces is any use to the dashboard
      before deciding
- [ ] **22.4.5** **Measured on the 1280 × 720 dp AVD, never on a phone**
      (`HARDWARE.md`, and the same condition as every other item in 22.2). A
      grid that looks right on a phone AVD is a single column, which is the
      thing being replaced
- [x] **22.4.6** **The post-ride summary is the worst offender, and it is the
      one screen a rider cannot avoid.** `PostRideSummaryScreen` is a centred
      column of six label-value rows — `Duration  24:03`, `Average power  188 W`
      — on a 1280 dp screen, so a ride ends on a spec sheet with 500 dp of black
      either side of it. It is the moment the app has the rider's full attention
      and the least it has ever said with the most room. It is **both** answers
      at once: tile the figures (they are looked at, not read) and the RPE
      prompt and the leaderboard can sit beside them rather than a scroll below
      them. Do this one with 26.1.2, which is the same six rows judged for their
      *words* rather than their layout — one pass, not two

      *Done and observed on the tablet AVD over two rides, one class and one
      free. The six figures are one row of tiles across the whole panel, in the
      metric colours the ride screen already uses; the heading is the class the
      rider chose (`Torque Repeats 4×2 20`, then `Simon · 10:54 AM`) rather
      than the words *Ride Summary*, which is 26.1.2's half; the RPE question
      spreads its ten pills across the width instead of bunching them into one
      corner of a card that stretches; and **Done and Discard are pinned below
      the content** rather than scrolling with it. `RideFigures` is shared with
      ride detail, so 12.2.2 still holds — one set of figures, not two.*

      *Two things the AVD decided rather than the design. The stray sixth tile
      is in 22.4.1. And **a figure with nothing behind it is left out rather
      than drawn as `--`** — heart rate is the case, and a dash is a hole in a
      grid that says less than the missing tile does.*

      *The database is the witness for the half of this that is not layout:
      `rpe_rating = 7` on the row after one tap, and the tiles agree with the
      row to the digit — 144 watts against 143.83 recorded, 118 bpm against
      118.29, `01:01` against 61 seconds.*

---

### 22.5 A week is the wrong window — the owner's note, 4 August 2026

**Verbatim:** *"If we work on the assumption people will use the bike once per
week, the 'this week section' is going to be meaningless. Perhaps 'this month'
is better, or 'last 30 days', whatever you think is best. Work on the assumption
people will ride the bike max once per week."*

**This is a fact about the rider that invalidates a design decision, not a
preference between two labels.** The *This Week* card (22.1.2's answer) was
built on an unstated assumption of several rides a week, where a count is a
meaningful number and a streak is an achievement. At one ride a week the same
card reads **"0 rides"** for six days out of seven — so the first thing on the
dashboard, above everything else in the progress section, is the app telling a
rider who is doing exactly what they meant to do that they have done nothing.
That is worse than uninformative; it is discouraging, and it is wrong.

**The streak is the same defect one level down and it is more serious.**
`StreakCalculator` counts *consecutive riding days*, so a rider who has never
missed a Sunday in a year has a streak of 1 — and by the rule already written
into `ThisWeekCard`, a streak of 1 is not shown at all. The most consistent
rider the app can have is invisible to the feature built to reward consistency.

**The recommendation, and the reasoning, because "whatever you think is best"
is a decision handed over rather than a shrug:**

- [ ] **22.5.1** **Last 30 days, not "this month".** A calendar month resets on
      the 1st, so a rider who rode on the 29th and 30th opens the app on the 1st
      to a zero. That is the same defect as the week's, on a 12× longer cycle
      and therefore harder to notice and worse when it lands. A rolling
      30-day window never resets, never lies, and at one ride a week always has
      **four or five rides in it** — which is a number worth putting on a card.
      Cost: `RidingHistory` is built out of whole weeks (`RidingWeek`,
      `startOfWeek`), so the window is a genuine change to the domain and not a
      string. Keep the weekly buckets — the bars on *Your riding* are right —
      and add the rolling total beside them
- [ ] **22.5.2** **The streak has to change unit or go.** A streak of *days*
      cannot survive this assumption. Two candidates: **consecutive weeks with
      a ride in them** (which is the thing a once-a-week rider is actually
      keeping up, and reads as "7 weeks in a row"), or drop the streak and show
      **rides in the last 30 days** alone. Prefer the weekly streak — it is the
      same idea, correctly scaled, and it makes the consistent rider visible
      instead of invisible. `StreakCalculator` is pure and JVM-tested, so this
      is a cheap change with an honest test
- [ ] **22.5.3** ***Your riding* follows the card.** The screen behind it
      (16.3.2 / 16.3.5) is built on weekly bars and a day-square calendar, and
      both are still right at this cadence — a calendar with one square lit a
      week is a *good* picture of a once-a-week rider, which is exactly what
      16.3.5 was for. What must change is the header and any wording that
      implies a week is the unit of progress, and the calendar wants to show
      enough weeks that the pattern is visible rather than the current window
- [ ] **22.5.4** **Check every other surface that assumes a busy week** before
      calling this done — it is the assumption, not the card, that is being
      fixed. The household panel (24.2) counts the household's *week*, and at
      one ride each per week a household of three shows an empty board most
      days. The backup reminder counts rides rather than days and is fine
      (23.3.1). The audit is the deliverable
- [ ] **22.5.5** **The empty state is the case to design for, not the exception.**
      At this cadence the card spends most of its life with a small number on
      it, so "4 rides · 96 min · last Sunday" is the *normal* reading and
      "0 rides" must be unreachable for anyone who has ridden in the last
      month. Judged on the 1280 × 720 dp AVD with a database that has one ride a
      week in it, not with the dense fixture data the current card was built
      against

---

### 22.6 No single card takes the whole panel — the owner's rule, 4 August 2026

**Verbatim, and it is a rule rather than a bug report:** *"Ride summary screen
the 'time in zone' card is stretching full width, same with 'how did it feel'
and 'take it with you'. This violates a design rule. If the rule doesn't exist,
please make the rule. No single card should stretch the full width of the
screen. We can use the full width using grid layout but we shouldn't stretch one
item that far, it doesn't look good. I believe we already have a design token
for the general max width of a column — let's use it and enforce it."*

**It caught this session's own regression, which is the useful part.** 22.4.2
uncapped ride detail so the charts could go two-up, and every card that had no
partner to sit beside then inherited the whole 1232 dp — the zone bar, the
effort question and the export rows. Uncapping a *screen* is not the same as
uncapping a *card*, and until this note the plan only had the first half of
that sentence written down.

The rule, and it now sits in `CLAUDE.md` beside the other two:

> **Cap what is read. Tile what is looked at. And no single card is ever wider
> than `readableWidth`** — a card with nothing beside it stops at the column
> width and leaves the rest of the panel alone.

- [x] **22.6.1** **A third token, `Modifier.loneCard()`**, beside
      `readableColumn` (cap a column and centre it) and `readableText` (cap a
      line where it stands). This one caps a *card* at `Layout.readableWidth`
      and leaves it where it is. Three modifiers for three different things is
      one more than a screen should have to think about, so each one's KDoc
      names the other two and says which question it answers
- [x] **22.6.2** **Applied to the three the owner named** — ride detail's *Time
      in zone*, *How did it feel* and *Take it with you* — and to the two the
      same audit turned up: the post-ride summary's effort card when no
      leaderboard sits beside it, and the FTP screen's *Every change* list.
      *Observed on the tablet AVD.*
- [ ] **22.6.3** **Enforce it, rather than remembering it.** The owner's word
      was *enforce*, and a rule that lives only in `CLAUDE.md` is the kind this
      project has already broken once — within a single session. What is
      wanted is the `CloudAccessFenceTest` treatment: something that fails the
      build when a `Card` is given `fillMaxWidth()` outside a grid or a
      weighted row. It is a lint rule or a source-scanning test rather than a
      unit test, and the hard part is the exception list — a card *inside* a
      weighted row is `fillMaxWidth` and correct

