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

- [x] **22.1.1** **Decide in one sentence what the dashboard is for**, and let
      the section follow from that rather than from what fits. The candidate
      answer: *"should I ride today, and what should I ride?"* — anything that
      is really "what have I done" belongs to history (12) and trends (16.3)

      ***Settled as the candidate answer, and the section now follows from
      it.*** The sentence is in `DashboardStats`' KDoc, where the next person
      to add a card will meet it:

      > **The dashboard answers *should I ride today, and what should I
      > ride*.** A total of work already done answers neither, and belongs on
      > history (12) and *Your riding* (16.3.2) where it is being **read**
      > rather than glanced at.

      *What makes it worth writing down rather than assuming is that the
      section had drifted the other way by accretion, one honest card at a
      time, until the first screen of the app was three summaries of the past
      and no answer to the question a rider standing next to a bike is
      actually asking.*
- [x] **22.1.2** Replace the two kJ cards with **consistency**: rides this week
      against the rider's own recent norm, and the calendar heatmap (16.3.5).
      What gets somebody onto a bike is a streak they do not want to break, not
      a kilojoule total they cannot interpret

      ***Done, nine sittings after the *Last 30 days* card answered the first
      half of it.*** What was left was the two kJ cards themselves, and the AVD
      settled it before any argument had to: on 10 August the section read
      **`Today's Output 73 kJ`** beside **`Recent Ride 73 kJ`** — the same
      number, twice, because the last ride happened to be today. That is this
      item's own sentence (*"both are the same quantity on the same axis"*)
      arriving as a screenshot.

      **`Today's Output` is deleted outright**, and the reason is 22.5's rather
      than this item's: at one ride a week it reads `0.0 kJ` for six days out of
      seven. That is the *This Week* defect surviving on the card immediately
      beside the one built to fix it, and 22.5.4's audit — which checked the
      household panel and the backup reminder — walked straight past it because
      it was looking for weeks and this one counts hours.

      **`Recent Ride` became 22.1.5**, which is the other half of the same
      change and is written up there. The section is two cards now: *have I
      been riding* and *how did the last one go*.

      *The calendar heatmap this item also asks for is **not** on the
      dashboard, deliberately: it is one tap away on* Your riding*, the card
      that opens it says the same thing in a sentence, and a second copy of
      16.3.5 on the first screen is a third answer to a question already
      answered twice. 22.1.3 is where that argument is made in full.*
- [x] **22.1.3** **A trend that is genuinely a trend** — output or minutes per
      week over the last six to eight weeks, sparkline-sized. The history query
      already returns what this needs

      ***Answered, and answered "already built" rather than by building a third
      one.*** Two trends are on this screen: the FTP card's stepped sparkline
      of every value it has ever held (22.1.4), and the thirty-day count with
      the weekly streak beside it (22.5.1), which is a trend in the only unit a
      once-a-week rider has.

      **What this item asked for specifically — weekly minutes over six to
      eight weeks — is the wrong picture at the cadence 22.5 established.** One
      ride a week makes those bars a row of near-identical stubs with the
      occasional zero, which reads as *inconsistency* rather than as the
      metronomic rider it actually describes. The picture that works at this
      cadence is the day calendar, and it exists, at seventeen weeks, on *Your
      riding* — where there is room to see the pattern rather than a
      thumbnail of it.

      *So the decision is that the dashboard carries the **number** and the
      **door**, and the drawing lives behind the door. Reopen this if the
      cadence assumption changes; it is the assumption doing the work, not the
      chart.*
- [x] **22.1.4** FTP, with the date it last changed and what changed it
      (7.10.2, 16.3.1). The app already computes this (7.1) and it is the
      closest thing to a real progress number it owns — but it currently keeps
      only the latest value, so the history this card wants has to be recorded
      first (7.9). *7.9 recorded it and this card now reads it: the number, a
      stepped sparkline of every value it has held, and how far it moved, when,
      and who moved it. **The first thing in this section that is a trend rather
      than a total.** The two kJ cards below it (22.1.2) are still what they
      were*
- [x] **22.1.5** A **last ride** card that opens the ride detail (12.2) —
      class name, RPE, and whether it beat the rider's own previous ride of the
      same class, which `leaderboardFor` already computes and nothing renders

      ***Done and observed on the tablet AVD, across all four branches.***
      `Last ride / Zone 2 Steady / Today · 8 min · best you've ridden it`, with
      the verdict in the primary colour, opening the ride it names.

      **It says no kilojoules, and that is the point of it rather than an
      omission.** The card it replaced was `Recent Ride 73 kJ`: a measurement,
      on a screen where nothing is being measured, answering a question nobody
      standing next to a bike asks. Phase 26's rule is that a unit belongs where
      a measurement is being *read*, and the kJ are one tap away on the ride
      itself, where they are one again. What is here instead is the name of the
      thing they did, when, how long, and whether to be pleased about it.

      **The verdict is one phrase, and three of its four branches are
      refusals** — which is where the work went. `RideStanding` is `Best`,
      `NotBest` or `Unclaimed`, and `Unclaimed` covers a free ride (not a
      repeat of anything), a first ride of a class (a best from one ride is
      noise wearing a trophy — **22.1.6's own argument, applied here**), and
      watts that were not measured on either side (22.1.7). `NotBest` and
      `Unclaimed` draw the same nothing and are still kept apart, for
      `PowerProvenance`'s reason about `Unknown` against `Modelled`: *"they did
      not beat it"* and *"nothing here can be compared"* are different claims,
      and folding them together is how a screen ends up asserting the first
      when it only knows the second.

      **RPE was in the ask and is deliberately not on the card.** It is the
      rider's own answer to a question the app asked them an hour ago, so it
      tells them nothing they do not know, and it is a fourth fact on a card
      whose job is a glance (Phase 26). It is on the ride behind the tap.

      ***The shortfall is not drawn either.*** *"3 kJ off your best"* was
      written and taken out: a number, in a unit, turning a perfectly good ride
      into a small failure on the first screen of the app.

      **What was observed, and how**, since the interesting half of this cannot
      happen on an emulator (24.4.2 — every AVD ride is modelled):
      - *Simulated ride, straight off the AVD:* `Today · 8 min`, no verdict.
        That is 22.1.7 refusing, and it is the branch every emulator ride
        takes.
      - *Then by hand, the technique in CLAUDE.md:* today's ride and one 13 kJ
        ride of the same class marked measured, the three seeded 180/210/240 kJ
        rides marked modelled — `best you've ridden it` appears.
      - *Then the 240 kJ ride put back to measured:* the verdict **disappears**
        while nothing else on the card moves. That is the pass that makes it a
        comparison rather than a constant, and the ride detail behind the tap
        corroborates it with its own `Ride against · Your best · 240 kJ` chip.
      - *And the tap lands on the right ride* — `Zone 2 Steady`, Monday 10
        August 2026 2:29 PM, 73 kJ — on the same `RideDetail` destination
        history and the FTP trend use, not a third rendering of one.

      **One layout fault was found by looking and is worth keeping.** The
      verdict started as a line of its own, which made the card four lines
      deep; `WideRow` equalises heights, so the whole row grew and pushed the
      good news off the bottom of a screen 22.4.3 had got fitting without a
      scroll. It is a clause of the detail line now, coloured rather than
      broken out. *A card that grows is a row that grows.*
- [x] **22.1.6** Personal bests (16.3.3), suppressed entirely until there are
      enough rides for them to be true. A "best" computed from one ride is noise
      wearing a trophy

      ***Answered as a rule rather than a card, and the rule is enforced in
      22.1.5.*** A best-by-duration table belongs on *Your FTP*, where 16.3.3
      built it and where it is being read; what the dashboard needed from this
      item was its *argument*, and `LastRideStanding` applies it directly — a
      class with no earlier ride returns `Unclaimed`, so a rider's first ever
      ride of anything is never congratulated for topping a field of one.

      *Kept open only as a heading in case a bests **card** is ever wanted here;
      the failure mode it names is closed.*
- [x] **22.1.7** Every figure here has to be honest about whose watts it is
      (16.1.6). A rider who moved from simulated to hardware telemetry gets a
      step change in their own history, and an unexplained cliff in a progress
      chart reads as the app being broken

      ***Done, and the section no longer draws a figure that could lie about
      it.*** The two kJ totals are gone (22.1.2), so nothing here sums
      modelled watts and measured ones into one number; the thirty-day card
      counts **rides and minutes**, which are the same quantity whoever
      measured them; and the one claim that *is* a comparison —
      *best you've ridden it* — is gated on
      `PowerProvenance.isTrustworthyAsMeasured` on this ride and on the same
      `NOT EXISTS` clause every raceable query in the app carries for the ones
      it is put above.

      **A gap was found in that clause while building it and is fixed here.**
      `NOT EXISTS (a sample that is not a measurement)` is passed *trivially*
      by a ride with **no samples at all**, so an evidence-free ride would have
      arrived as "measured all the way through". `householdLeaderboard` guards
      it with an `EXISTS` beside the `NOT EXISTS` and
      `ownTotalsForClassExcluding` now does too — because the other side of
      this same comparison asks `PowerProvenance`, which answers `Unknown` for
      a ride of no samples (`of(0, 0, 0)`), and two sides of one comparison
      disagreeing about what counts as measured is how a rider gets told they
      beat something nobody rode. **`ownTotalsForClass`, which feeds the
      `Usual` ghost (24.3.18b), still has the gap** and is flagged separately.
- [x] **22.1.8** Rebuild `DashboardStats` and the dashboard ViewModel around
      whatever 22.1.1 decides, rather than bolting cards onto the current two
      totals. Keep every query windowed the way 12.1.6 does — this is the first
      screen after profile selection and it must never touch `workout_metrics`

      ***Done. `DashboardStats` is one field*** — `lastRide: LastRide?` — where
      it was a kilojoule total and a whole `WorkoutEntity`. The entity is the
      quiet half of this: a screen handed thirty columns **finds uses for
      them**, and that is how `Recent Ride 73 kJ` got onto a surface whose
      question is *should I ride today*. `LastRideRow` is a six-column
      projection, so the next card added here has to ask for what it wants.

      **The one instruction in this item is broken on purpose, and it is worth
      saying plainly.** *"It must never touch `workout_metrics`"* — the
      provenance check does, once, through `getPowerProvenanceCounts`, which
      counts a single ride's samples by primary key. The alternative was
      drawing a comparison whose honesty this app has spent two phases
      establishing, and the cost is bounded: **it is not paid at all for a Just
      Ride**, since the class id is what the whole standing hangs off and there
      is nothing to ask about without one. The rule the item was protecting —
      never scan the series table across a rider's whole history on the first
      screen — is intact.

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
- [x] **22.2.2** Then use the two rails that opens up **deliberately**, rather
      than leaving symmetrical dead space: for instance who is riding and
      today's context on one side, the last ride and the streak on the other.
      The rails exist only in landscape and must fold back into the column below
      a breakpoint

      ***Answered by 22.4.3, and the answer is simpler than the question.***
      There are no rails: the dashboard is **rows**, stacked and each one used —
      the FTP beside Just Ride, the three actions abreast, the progress cards
      abreast — with `WideRow` folding every one of them below 900 dp, which is
      the "must fold back into the column" clause satisfied by construction
      rather than per row. *The item's own guess at the content was close: the
      last ride and the consistency count are indeed a pair, they are just
      side by side in the flow rather than parked in a margin.*
- [x] **22.2.3** Decide the three regions as one layout — what the middle is
      for, what a rail is for, and what a rail does when it has nothing to say
      (it disappears; it does not show an empty card). Doing this card by card
      produces three columns of unrelated things, which is worse than one

      ***Answered by 22.4.3 with 22.2.2.*** There are two regions rather than
      three and they are rows, so the warning this item exists to give — three
      columns of unrelated things — cannot arise. **Its rule about the empty
      case survived and is load-bearing elsewhere**: a card with nothing to say
      draws nothing at all rather than an empty version of itself, which is
      what the household panel does below two riders (24.1.6), what the FTP
      sparkline does before the number has moved, and what 22.1.5's verdict
      does when it cannot honestly be made.
- [x] **22.2.4** The same question applies to Settings and History, which are
      also full-width cards on a wide screen. Do the dashboard first and find
      out whether the answer generalises before rolling it out

      ***Done as 22.4.3's audit — seven screens, a verdict each — and this
      item's instinct to check before generalising was right twice over.***
      Settings kept the cap and History did not, and then **History changed its
      mind again** (22.7.6): the owner's note settled it as one centred column,
      which is neither of the two answers the audit was choosing between. Three
      passes at one screen is the evidence for this item's caution rather than
      against it.
- [ ] **22.2.5** Verify against the real system furniture — a 48 dp bottom
      navigation bar and no top status bar (`HARDWARE.md`) — and on the tablet
      itself before ticking anything here

      *Still open and **owed**, on everything in 22.1, 22.5.5, 22.7.4 and
      22.7.6. All of it is measured on the 1280 × 720 dp AVD, which is the
      right geometry and the wrong furniture. The owner had no access to the
      bike on 10 August and said so at the start of the sitting, so this is
      deferred deliberately rather than skipped.*
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
- [x] **22.4.2** **Ride detail becomes a grid.** The charts are the case the
      owner named. Two columns at 1280 dp, one below the breakpoint, and the
      **order must survive the fold** — a reader going down column one and back
      up column two is reading the cards in a different order than the phone
      does, so decide whether these cards have a narrative order (power then
      cadence then heart rate) or are a set, and lay them out as whichever they
      are. Note 16.3.4's housemate picker and the ride's own header stay full
      width: they are controls and context, not cards in the set

      *Done, and the owner found it before the session did — they saw the
      charts "disappear" and they had not, they had been pushed below the fold.
      That is this item in one observation: the screen was capped at 760 dp, so
      the new figures grid came out three wide and two deep and shoved the
      first chart off the bottom of a panel with room for four. Uncapped, it is
      one row of six figures and then Power beside Heart rate, both above the
      fold. The charts already knew how to go two-up at 900 dp; they had never
      been given 900 dp. The order question this item raised answered itself:
      the cards are a **set**, laid out row-major, which is the same order a
      phone's single column gives. **Observed on the tablet AVD.** It also
      produced `Modifier.readableText()` — a cap that does not move the thing
      it caps, because `readableColumn` would have centred this screen's two
      paragraphs of prose over left-aligned cards — and, one note later, 22.6.*
- [x] **22.4.3** **Audit every screen that took the token in 22.2.6** and say,
      per screen, which answer it wants — cap, tile, or a capped column *inside*
      a wider frame. Settings and the class library are probably genuinely
      capped (form fields and a list of titles). History is a list of rides and
      may well want two columns. The audit is the deliverable: a screen that was
      capped because the token was handy is the failure this item exists to
      find

      *Done, and the owner's second note of 4 August settled the criterion
      before the audit had to guess at one: **use the full width, and no ONE
      CARD goes full width; grids where they fit.** Seven screens carried the
      token. The verdict, per screen:*

      - ***Dashboard** — tile. FTP beside Just Ride, the three actions abreast,
        the three progress cards abreast; the whole screen now fits without
        scrolling. `WideRow` stacks below 900 dp. This is what 22.2.2's rails
        turn out to be in practice, and it is simpler than they were: no rails,
        just rows. 22.2.2 and 22.2.3 can be read as answered by it.*
      - ***History** — tile. Two ride cards across, day headings still
        spanning, equal heights per row so the recovered-after-a-crash note
        does not leave its neighbour short. Its prediction was right.*
      - ***Class library** — tile. Three across: 21 classes visible where 7
        were, which is most of a category at once.*
      - ***Your riding*, *Your FTP** — tile. Two cards abreast.*
      - ***Settings**, *the account screen* — **cap, and its prediction was
        right too**: they are form fields and prose, which is what the cap was
        always for.*

      *All observed on the 1280 × 720 dp tablet AVD (22.4.5).*
- [x] **22.4.4** **A rail is not a grid, and 22.2.2/22.2.3 are still the harder
      question.** This item is about surfaces that have *one kind of thing* and
      too much room for it, which is a layout with an obvious answer. The
      dashboard has three kinds of thing and no obvious answer, and filling its
      rails card by card is what 22.2.3 already warns against. Do 22.4.2 first
      and see whether the grid helper it produces is any use to the dashboard
      before deciding

      ***Done, and the instruction it gave itself is what answered it.*** 22.4.2
      was done first, and the helper it produced turned out **not** to be the
      one the dashboard wanted: `WideGrid` lays a *set* out in rows, and the
      dashboard's cards are pairs and triples of unlike things rather than a
      set. So the dashboard got `WideRow` instead — one row, cards abreast,
      stacked below 900 dp — and the rails never happened (22.2.2, 22.2.3).

      *The rule this leaves behind, which is the useful residue: **the number
      of kinds of thing on a surface decides which helper it takes.** One kind
      and too much room is `WideGrid`; several kinds is a row per group; prose
      is the cap. And a **list** is none of the three — that is 22.7.6, learnt
      the hard way on History a fortnight later.*
- [x] **22.4.5** **Measured on the 1280 × 720 dp AVD, never on a phone**
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

- [x] **22.5.1** **Last 30 days, not "this month".** A calendar month resets on
      the 1st, so a rider who rode on the 29th and 30th opens the app on the 1st
      to a zero. That is the same defect as the week's, on a 12× longer cycle
      and therefore harder to notice and worse when it lands. A rolling
      30-day window never resets, never lies, and at one ride a week always has
      **four or five rides in it** — which is a number worth putting on a card.
      Cost: `RidingHistory` is built out of whole weeks (`RidingWeek`,
      `startOfWeek`), so the window is a genuine change to the domain and not a
      string. Keep the weekly buckets — the bars on *Your riding* are right —
      and add the rolling total beside them

      *Done and observed: "Last 30 days · 8 rides · 124 min" on the tablet AVD.
      `RidingWindow` is computed at build time from the days the history
      already holds, so it costs no extra query. Inclusive of today, tested at
      both edges — 30 days back is inside and 31 is out.*
- [x] **22.5.2** **The streak has to change unit or go.** A streak of *days*
      cannot survive this assumption. Two candidates: **consecutive weeks with
      a ride in them** (which is the thing a once-a-week rider is actually
      keeping up, and reads as "7 weeks in a row"), or drop the streak and show
      **rides in the last 30 days** alone. Prefer the weekly streak — it is the
      same idea, correctly scaled, and it makes the consistent rider visible
      instead of invisible. `StreakCalculator` is pure and JVM-tested, so this
      is a cheap change with an honest test
- [x] **22.5.3** ***Your riding* follows the card.** The screen behind it
      (16.3.2 / 16.3.5) is built on weekly bars and a day-square calendar, and
      both are still right at this cadence — a calendar with one square lit a
      week is a *good* picture of a once-a-week rider, which is exactly what
      16.3.5 was for. What must change is the header and any wording that
      implies a week is the unit of progress, and the calendar wants to show
      enough weeks that the pattern is visible rather than the current window

      *Done: "8 rides in the last 30 days · 124 minutes, 835 kJ". The 17-week
      calendar was already wide enough and is untouched.*
- [x] **22.5.4** **Check every other surface that assumes a busy week** before
      calling this done — it is the assumption, not the card, that is being
      fixed. The household panel (24.2) counts the household's *week*, and at
      one ride each per week a household of three shows an empty board most
      days. The backup reminder counts rides rather than days and is fine
      (23.3.1). The audit is the deliverable

      *Done, and the audit found three surfaces rather than one.*

      ***The household panel** is the one this item predicted and it was worse
      than predicted, because the panel's row does not exist for a rider with no
      rides in the window (24.2.4, made structural by an inner join) — so a
      housemate riding once a week was not shown with a zero, they were **absent
      from the household**. Now the same rolling 30 days the rider's own card
      uses, off the same `RECENT_WINDOW_DAYS`, because two figures on one screen
      disagreeing about what "recently" means is worse than either window alone.
      **Observed on the tablet AVD**: with the two oldest rides reassigned to a
      second profile by hand, the panel reads* Simon (you) 9 rides · 612 kJ *and*
      Cl 2 rides · 310 kJ *— and under the old week both of Cl's rides were
      outside it, so Cl was not on the board at all.*

      ***Its streak went with it**, for 22.5.2's reason applied to somebody
      else's riding: it counted days, so it could only ever have said "2-day
      streak" about a housemate who rode two days running, and never anything at
      all about the one who has ridden every week since March. It says "6 weeks
      in a row" now.*

      ***And the names were lying.*** `HouseholdRiderWeek`, `householdWeek`,
      `HouseholdWeekCard`, `HouseholdWeekRow` — four types and functions naming
      a window that is no longer a week. Renamed to `HouseholdRider`,
      `householdRecent`, `HouseholdPanelCard`, `HouseholdRiderRow`: the window
      is a parameter and the name should not claim one. This project has already
      spent a session on a name that outlived its meaning (25.4.2).*

      *Two surfaces checked and **left alone**, which is the other half of an
      audit: the weekly bars and the day-square calendar on* Your riding *are
      right at this cadence — a calendar with one square lit a week is a good
      picture of a once-a-week rider (22.5.3) — and the backup reminder counts
      rides, not days.*
- [x] **22.5.5** **The empty state is the case to design for, not the exception.**
      At this cadence the card spends most of its life with a small number on
      it, so "4 rides · 96 min · last Sunday" is the *normal* reading and
      "0 rides" must be unreachable for anyone who has ridden in the last
      month. Judged on the 1280 × 720 dp AVD with a database that has one ride a
      week in it, not with the dense fixture data the current card was built
      against

      ***Done, and judged against the fixture this item insisted on rather than
      the dense one.*** Simon's cluster of seven rides on 5 August was spread to
      one a week — 2 Aug, 26 Jul, 19 Jul, 12 Jul, 5 Jul, 28 Jun, 21 Jun — and
      the section read:

      > **Last 30 days** · 6 rides · 32 min · **8 weeks in a row**
      > **Last ride** · Zone 2 Steady · Today · 8 min

      **That is the design working, and the weekly streak is the part doing the
      work.** *8 weeks in a row* is 22.5.2's whole argument arriving as a
      number: under the old day-counting streak this rider scored **1** and was
      shown nothing at all, because a streak of 1 is not a streak. The rolling
      window never resets, so `0 rides` is unreachable for a rider who has
      ridden in the month — which is what this item asked to be true.

      **The one state that *does* reach zero was measured too, and it is
      correct rather than a hole.** With every ride pushed 60 days back the pair
      reads `Last 30 days · 0 rides` beside `Last ride · Zone 2 Steady ·
      Jun 11, 2026 · 8 min`. That is a rider who genuinely has not ridden in a
      month, and 22.5's objection does not apply to them: the complaint was
      never that zero is unsayable, it was that the app was saying it to
      somebody **doing exactly what they meant to do**. **22.1.5's card is what
      makes it bearable** — the zero no longer stands alone, it has the date of
      the last ride beside it, so the screen says *when* rather than only
      *nothing*. Neither card was written with the other in mind and the pairing
      is the accident worth recording.

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
- [x] **22.6.3** ~~**Enforce it, rather than remembering it.**~~ **Closed by the
      owner, 4 August 2026, and not by being built.** The item asked them
      directly — *"you said enforce, and I've only written the rule down; it was
      broken inside the single session that wrote it, so it wants a build-time
      check"* — and the answer was: *"Don't enforce it deterministically, just
      bear it in mind when designing screens. You've done a good job. Will let
      you know if I spot any problems."*

      **Kept as a decision rather than deleted**, because the reasoning is worth
      having the next time this project reaches for a fence. The three fences it
      does have — `CloudAccessFenceTest`, `CloudConfigFenceTest`,
      `ClassLibraryAssetsTest` — all guard things that are **invisible when
      broken**: a cloud call with no rider on it, a key checked into a file, a
      class the generator would have refused. This rule is the opposite. A card
      banded across 1232 dp is visible from the other side of the room, and the
      owner is the person looking at it — which makes a source-scanning test
      with a hand-maintained exception list a cost with no failure to catch.
      The original ask follows.
      What was wanted is the `CloudAccessFenceTest` treatment: something that
      fails the build when a `Card` is given `fillMaxWidth()` outside a grid or
      a weighted row. It is a lint rule or a source-scanning test rather than a
      unit test, and the hard part is the exception list — a card *inside* a
      weighted row is `fillMaxWidth` and correct


---

### 22.7 Two screens the panel rebuild left behind — the owner's notes, 4 August 2026

22.4.3 audited seven screens and gave five of them the width. These are the two
notes that came back after riding with the result, and neither is a
disagreement with that audit: one is a piece of it that was not finished, and
one is a screen the audit never reached.

- [x] **22.7.1** **History's panels are not centrally aligned — *"look in
      particular at the bottom"*.** The owner's whole note is those two
      sentences, so the diagnosis is the work. Three candidates, and the first
      is almost certainly it:
      - ***Show older rides* is `fillMaxWidth()`** — one outlined button banded
        across 1232 dp, at the bottom of the screen, which is exactly where the
        owner was told to look. It is 22.6's rule applied to a control instead
        of a card, and the rule reads the same way: a lone thing stops at a
        column's width. `Modifier.loneCard()` is probably not the token for a
        button; decide whether it centres or takes the width of one ride card.
      - **The last row of a day leaves its gap on the right**, by the deliberate
        rule in `WideGrid` that the final row's cells keep every other row's
        width. That is right *inside* a set — but a day with one ride in it is a
        whole day drawn as a half-empty row, and at one ride a week (22.5) most
        days are exactly that. Worth looking at before assuming the rule
        transfers.
      - **The empty state and the guest empty state** are centred columns in a
        screen that is otherwise a left-aligned grid.
      Screenshot it on the 1280 × 720 dp AVD **with a database that has one ride
      on some days and two on others** before changing anything — the shape of
      the complaint depends on the data, and dense fixture data hides it (22.5.5
      is the same trap)

      *Done, and the screenshot settled it: **both** of the first two candidates
      were real and they are the same defect in two costumes. At the bottom of
      the list were two days holding one ride each, every one of them drawn hard
      against the left edge with 600 dp of nothing beside it, and under them a
      single outlined button stretched across 1232 dp. A day is a row now, and a
      row that does not fill the grid **centres** what it has — the gap split
      into two half-spacers, so the card keeps its cell width rather than
      growing to the width of two (a lone ride stretched to fill its day would
      read as a more important day than the one above it — 22.6). The button is
      centred at its own width.*

      ***The third candidate turned out to be the important one and it was
      about the data, not the layout:*** at the cadence 22.5 established — one
      ride a week — **most days hold exactly one ride**, so what looked like an
      edge case at the bottom of a dense fixture list is the ordinary reading
      of this screen. That is 22.5.5's warning arriving on a second surface,
      and it is the reason this is worth a change rather than a shrug.*
- [x] **22.7.4** **The date header did not move when the row did**, and it is
      22.7.1's own fix leaving a loose end. Centring a day that holds one ride
      was right — a lone card stretched across the panel reads as a more
      important day than the one above it — but the day's *heading* is still
      hard against the left edge at x = 24 dp while the card it labels now
      starts at x ≈ 500. **Seen on the tablet AVD** in the last shot of the
      demo recording, against a seeded history where every day holds exactly
      one ride, which 22.7.1 itself established is the ordinary reading of this
      screen rather than the edge case.

      A section header that does not sit over its section is the same family of
      fault as the two 22.7.1 fixed — an alignment that reads as a mistake
      rather than as a layout — and it is now the most visible thing on the
      screen, because the fix moved the content out from under the label.

      **Not changed on 5 August, deliberately.** The centring is a decision the
      owner looked at and accepted, and the two ways out point in opposite
      directions: centre the heading over its row, or left-align the row and
      lose 22.7.1's reason for centring. That is a design call rather than a
      defect fix, so it is written down with the measurement instead of being
      guessed at. Judge it on the AVD with one ride on some days and two on
      others (22.7.1's own instruction, and it is what makes the difference —
      a two-ride day fills the row and its header lines up already)

      ***Done, and the owner decided it rather than a session guessing.*** Asked
      as the four-way it actually was — centre the heading over its row,
      left-align everything and lose 22.7.1's centring, a middle path that
      left-aligns both but keeps the card at cell width, or leave it — and the
      answer was **centre the heading over its row**.

      **The heading takes the same inset as the row it labels**, and the reason
      it is one function rather than two call sites is this item itself: a
      heading at x = 24 while its card starts at x ≈ 500 is two computations of
      one offset drifting apart, and writing it twice again is how it comes
      back. `DayGridRow` is the row's centring, used by the day's rides *and* by
      its heading. **The first row is what decides** — a day with three rides in
      a two-wide grid has a full first row and a half-empty second, and the
      heading belongs over the first, not over the average of them.

      *Measured on the 1280 × 720 dp AVD against exactly the database this item
      asked for — one ride on some days, seven on another:*

      | Day | Rides | Heading x | First card x |
      |-----|-------|-----------|--------------|
      | Today | 1 | 498 | 498 |
      | Wednesday, August 5, 2026 | 7 | 24 | 24 |
      | Friday, July 31, 2026 | 1 | 498 | 498 |

      Both branches in one screenshot, which is what makes it convincing: the
      full row is untouched at the left margin and the two lone rides carry
      their headings out with them.

      **And then the owner looked at that screenshot and 22.7.6 replaced the
      whole mechanism**, which is worth leaving here rather than tidying away:
      this fix was correct and it was a fix to the wrong thing. The inset only
      needed keeping in step because the list changed shape between days.
- [x] **22.7.6** **A list should not change shape with how much is in it** —
      the owner's note, 10 August 2026, verbatim: *"keep it all constrained to
      one narrower grid column in the middle, rather than expanding widthways
      for days with large number of workouts."*

      **This overturns 22.4.3's verdict on History and only on History**, and
      the audit's own prediction is what was wrong: it said *"History is a list
      of rides and may well want two columns"*, and the two columns were built
      and shipped. What the note adds is the thing an audit of seven screens at
      once could not see — a **list** is read down, and a set of tiles is
      looked at, and History is the first one dressed as the second. A day with
      seven rides drew two columns 1232 dp wide; the day above it drew one card
      centred at 616. Same screen, same scroll, two shapes.

      So the column is **one grid cell wide and centred**, and every day is the
      same shape as every other. The arithmetic is unchanged — `columnsFor`
      still decides how wide a cell is here, so a phone is untouched and the
      bike gets the width a single-ride day already had.

      **It subsumes two items rather than sitting beside them.** 22.7.1's
      centring is structural now instead of per-row: nothing *can* be hard
      against the left edge with 600 dp of nothing beside it, because the whole
      list is in the middle. And 22.7.4's heading is over its day by
      construction — one left edge for the labels and the cards, with no inset
      to keep in step and therefore nothing to drift. The `DayGridRow` written
      an hour earlier is deleted.

      *Observed on the 1280 × 720 dp AVD, top of the list and bottom:* headings
      at x = 495 and cards at x = 497 for **every** day on screen — Today with
      one ride, Wednesday 5 August with seven, Friday 31 July, Saturday 6 June
      and Tuesday 1 July with one each.

      **One thing was got wrong and it is worth knowing about**, because it
      looks exactly like a different fault: `BoxWithConstraints` with only
      `fillMaxHeight` **wraps its child horizontally**, so `maxWidth` still
      reported the full 1280 dp and the column came out at precisely the right
      width, hard against the left edge, with nothing to centre it in. It reads
      as `align` failing rather than as the box shrinking.
- [x] **22.7.2** **The Start Class screen wants real design work.** Verbatim:
      *"I'm sure this is already on the todo list but it needs some work.
      Visualisation should be much more beautiful and also adhere to the rules
      of sticking inside a max-width container, unless full-width is necessary
      and appropriate."*

      **It was not already on the list**, which is worth saying plainly:
      `ClassDetailScreen` is the last screen between a rider and a ride, it is
      the only place the *shape* of a class is ever shown before it is ridden,
      and no item in this plan has ever been about how it looks. 22.4.3 audited
      the class **library** (three cards across) and never opened the screen
      behind it.

      Three things this has to answer, in this order:
      - **What is the visualisation *of*?** The class is a list of intervals
        with a zone, a cadence band, a duration and sometimes a position
        (25.4). The app already draws that twice — `IntervalTimeline` on the
        HUD and `UpcomingIntervals` on the ride screen — both designed to be
        read at two metres mid-effort, which is not this screen's job. Here the
        rider is deciding *whether to ride this*, and the questions are how long,
        how hard, and what shape: where the work is, how many efforts, how long
        the recoveries. A profile of the whole class — height for zone, width
        for time — answers all three at a glance and none of them are a number.
      - **Which rule applies where.** The class profile is *looked at*, so it
        takes the width (22.4). The description and the details are *read*, so
        they are capped. And Start is one control, not a card the width of the
        room. This is the "capped column inside a wider frame" case 22.4.3
        named and no screen has yet used.
      - **Less is more (Phase 26).** This screen currently states the same
        facts several times over. A rider choosing a class is answering *do I
        want this tonight*, not reading a spec — the same test as 26.1.1's
        profile tile.

      Do it **after** 22.7.1, which is small, and judge it on the AVD (22.4.5)

      ***Done and observed on the tablet AVD, and the three questions are
      answered in the order they were asked.***

      **The visualisation is the class itself — height for zone, width for
      time.** `ClassProfile` (pure, `domain/chart`, nine tests) and
      `ClassProfileChart`. It is the one thing on the screen that is *looked
      at*, so it takes the panel: time is the horizontal axis and a 30-minute
      class capped at 760 dp loses the proportion between the work and the
      recoveries, which is the whole question. **No value axis, deliberately**
      — the vertical is a zone *ordering*, and the gap between Z1 and Z2 is not
      the gap between Z6 and Z7 in watts, so gridlines would claim something
      untrue. Three clock labels underneath, matching `ChartFrame`'s idiom.

      *Measured against two real classes and they read as different workouts
      from across the room:* `The Long Climb 30` is a ramp into one long orange
      block; `Torque Repeats 4×2 20` is four spikes with recoveries between
      them. Two facts fell out of building it that are worth keeping. **Zone 1
      needs a floor** — strictly proportional it is a seventh of the plot, and a
      warm-up then reads as an empty left-hand edge rather than as riding.
      **And adjacent blocks at the hardest zone are one effort**: the library
      splits a fifteen-minute threshold block in two to change the cadence, and
      calling that two efforts describes a workout with a rest in it that
      nobody gets.

      **The sentence says the same thing in words, and it agrees with the
      title.** *"20 min · Climbs · 4 × 2 min at Lactate Threshold"* over a class
      called `Torque Repeats 4×2 20`, and *"30 min · Climbs · one 15 min effort
      at Lactate Threshold"* over `The Long Climb 30`. Minutes rather than
      `mm:ss`: a clock format is for reading a measurement, and this is
      describing a shape to somebody deciding what to ride (Phase 26). The
      interval count is gone from the header — the picture shows every block and
      the list names them, which was three answers to one question.

      **Which rule applies where, which is 22.4.3's "capped column inside a
      wider frame" case finally used.** The profile and the interval grid take
      the width; the summary sentence is `readableText()`; the leaderboard is
      `loneCard()`; and **Start is a control rather than a card the width of the
      room** — 420 dp, centred, the same height it had. Before this, six
      interval rows each spanned 1872 dp to carry four facts down their left
      edge, and the seventh block of a 30-minute class was **below the fold on
      the one screen whose job is to show the whole class**. It is a `WideGrid`
      now: seven tiles as 4 + 3, all of them on screen.

      *Two things found while looking rather than while planning.* The content
      needed **centring when it does not fill the panel** — 22.7.1's rule
      arriving on a third screen, because most classes are seven or eight blocks
      and top-aligning them hangs everything off the app bar with a hole above
      the button. And `WideGrid` grew an opt-in `equalHeightRows`: one tile
      carrying a position chip (25.3) is 20 dp taller than its neighbours and a
      ragged row reads as a mistake. **Opt-in and it has to be** — equal heights
      need `IntrinsicSize.Min`, and a `Canvas` throws rather than answering an
      intrinsic query, so the callers with charts in their cells must keep the
      layout that works for anything. History was re-checked after the change
      and is unmoved.

      **One path was not seen and should not be claimed**: the household
      leaderboard card above the grid. It needs two riders with *measured*
      rides of the same class (24.1.6, 24.4.2) and this AVD's database has one
      rider and simulated watts, so the card does not draw at all — correctly.
      The change to it is a width cap and nothing else

- [x] **22.7.3 The Start Class screen again, with the board on it this time.**
      The owner's note, 5 August 2026, under *Interim class screen, where you
      click 'start class'*, verbatim: *"Looks bad. We've added the leaderboard
      in there and the whole screen doesn't look good. Please totally re-assess
      how this screen looks. THink about what information is being shown and
      make it look brilliant with great UX."*

      **This is 22.7.2's own last paragraph coming true, and that is the most
      useful thing about it.** That item shipped with an explicit admission:
      *"One path was not seen and should not be claimed — the household
      leaderboard card above the grid… this AVD's database has one rider and
      simulated watts, so the card does not draw at all."* The screen was
      designed and judged **without** the card that is now the complaint. The
      rule it leaves behind is not "look harder", it is that **a card gated on
      data the test device cannot produce is a card that has not been
      designed** — the same class of blind spot as everything gated on measured
      power (24.4.2), and the bike is where it was finally seen.

      **Do not start by moving the card.** The note says *totally re-assess*
      and names the information as the question — *"think about what
      information is being shown"* — so the first pass is an inventory, not a
      layout. What the screen holds today, in order: a one-line summary
      sentence; the class profile chart; the household board; the *Ride
      against* picker (behind `RIVAL_GHOST`, so drawing nothing today); a grid
      of interval tiles, one per block; and Start. That is **three separate
      descriptions of the same class** — the sentence, the picture and the
      tiles — plus a comparison, on a screen whose whole job is *do I want this
      tonight*. 22.7.2 removed the interval count from the header for exactly
      that reason and then left the three descriptions in place.

      Four things to settle, and the first is the one the note is really about:
      - **What the rider came here for.** Two questions, not five: *what is
        this ride* and *start it*. Everything on the screen either serves one
        of those or is furniture. The interval grid is the strongest candidate
        for demotion — it is a spec sheet the profile chart already draws, and
        a rider reads it once ever, not once a class.
      - **Where the board belongs, if it belongs.** It is a comparison, and it
        is genuinely a reason to pick a class (24.1.2's argument, and it is
        still good). But it is currently a full card in the middle of the
        vertical flow, between the picture of the class and the list of its
        blocks, which is the one place it interrupts the description of the
        class with a description of other people. Beside the profile rather
        than under it, or below Start rather than above it, are both worth
        drawing before choosing.
      - **The board's own height (24.1.8).** The same card, the same unbounded
        row count, and the same fix. These two items must not each invent a
        ceiling.
      - **The screen scrolls and should not have to.** It is a `LazyColumn`
        with centred arrangement, which is right for a short class and hides
        how much is on it for a long one. 22.7.2's own success criterion was
        *the whole class on screen at once*; measure that again with the board
        drawing, on the bike or on a seeded AVD, before and after.

      ***Done and observed, 5 August 2026 — on the tablet AVD with a seeded
      household of twelve, and on the real bike.***

      **The class on one side, the people on the other.** The board is not a
      fact about the class, so it stopped being a card stacked into the
      description of one: it is a fixed 400 dp column beside the class, and it
      is **absent entirely when there is nobody on it** rather than leaving a
      third of the panel empty. What that buys is the vertical space the screen
      was actually short of — the 20-minute class's chart and all six of its
      blocks are on screen at once again, with the board drawing.

      *Seen on the AVD:* `1 Ava / 2 Ben / 3 Cleo / ⋮ / 8 Hana / 9 Simon / 10
      Ivy / and 6 more` beside the class, with 24.1.8's window doing the
      bounding. *Seen on the bike:* `Endurance Build 30` with no board at all —
      the household's second rider has one ride of it at 0 kJ, so
      `isWorthShowing` is false — and the eight interval tiles take the full
      1920 px four across. That is the branch the AVD could not show and the
      one 22.7.2 was judged on.

      **A 30-minute class with sixteen blocks and no board still does not
      scroll.** With a board the left column is ~880 dp and tiles go two
      across, so a long class scrolls there and the board stays put, which is
      the right thing to give up.

      *Original notes follow.*

      **It cannot be judged on an empty AVD.** Seed two profiles with measured
      rides of the same class — the technique is in CLAUDE.md and the seeding
      the thirtieth sitting used for the live board is directly reusable — or
      look at it on the bike, which has the owner's real rides on it

---

### 22.7.5 "A million panels" — the Start Class screen, again, and this time it is about the rider rather than the layout

**The owner's note, 5 August 2026, verbatim:** *"The Class screen, the one where
you click 'begin class', it doesn't make much sense. I like the chart, but then
underneath it are a million panels (all the intervals) and it's just too much
and is confusing to new users. Completely rethink this page. It doesn't need to
say much! It can be centred in the middle if you like. And maybe we could spend
some time adding a rich 'description' to each class which tells you what the
ride is and what it focuses on. That is more useful. Let's make this page
simple."*

**This is the fourth note on this screen and the previous three were all
answered by moving things.** 22.7.2 replaced six full-width rows with a chart
and a tile grid; 22.7.3 moved the leaderboard out into a column of its own;
22.4.3 capped the prose. Every one of those was a real improvement and **none of
them questioned whether the intervals belong on the screen at all** — the grid
just got tidier. So this item is not "shrink the tiles". The owner has said the
page shows the wrong things, and the number is the evidence: `CLB-01` is a
20-minute class with **thirteen** blocks, and thirteen cards each naming a zone,
a cadence band, a watt band and a duration is 52 facts on the last screen before
a rider starts pedalling.

**Why the tiles cannot be defended even though each one is accurate.** The
argument that put them there was that this is the screen a rider *studies* a
class on (11.7's own words, in `IntervalCard`). That is true of a rider who
knows what Z4 is. For a first-time rider it is a wall, and worse than a wall: it
is a wall of **the same information the chart above it already draws**, which is
Phase 26's "ten answers where three will do" (26.3) on the screen with the most
riding on it. The chart is height-for-zone and width-for-time and it is the good
answer — the owner says so directly, *"I like the chart"*.

- [x] **22.7.5a** ***Done and observed on the tablet AVD.*** The escape was built after all, and the reason is that it makes the removal a *move* rather than a deletion: the distinction is **who is asking**. A first-time rider is deciding whether they want the ride; a rider who taps *See the blocks* has asked the question the tiles answer, and for them 11.7's *"this is the screen a class is studied on"* was always right. Same `IntervalCard`, same grid, in a `ModalBottomSheet` — no new work and nothing lost. **The intervals come off this screen.** Not shrunk, not
      collapsed behind a disclosure by default — off. What replaces them is
      nothing, because the chart is already the picture of them. **Keep one
      escape**: a rider who genuinely wants the block list is a real rider and
      the data is already parsed, so it can live behind a plain *"See the
      blocks"* that opens over the screen. Build the removal first and add the
      escape only if the screen looks like it is missing something, rather than
      keeping the grid on a technicality
- [x] **22.7.5b** ***Done, and it found a layout bug that had been hidden by the thing being removed.*** `Alignment.CenterVertically` had never once run: the `LazyColumn` takes `weight(1f)` for its *horizontal* share of the Row and so was only ever as tall as its own content, and with thirteen tiles in it the content always overflowed, so the column filled the panel by accident. Taking the tiles out left it hanging off the app bar. It is `fillMaxHeight()` now. **The screen is centred and short.** The owner's own
      permission — *"It can be centred in the middle if you like"* — and it lines
      up with 22.7.1, which has now been the right answer on four screens. The
      target shape: the class's name, one line saying how long and how hard, the
      chart, the description (23.2.7), and Start. Nothing else. The leaderboard
      column stays where 22.7.3 put it, because that item's reasoning is about
      *whose* facts they are and is untouched by this one
- [x] **22.7.5c** ***Done — kept, and centred with the rest.*** **The empty-interval branch must survive the rewrite.** A class
      with unreadable interval data currently says so in the error colour and
      offers a free ride. That path has nothing to do with the tiles and is easy
      to delete by accident while removing them
- [x] **22.7.5d** ***Done.*** Seen on the tablet AVD on `CLB-01` (thirteen blocks, no board) and `END-01` (six blocks, a board of ten with `⋮` and *and 6 more*). Both draw whole, both centred, neither scrolls. **Judge it against the first-time rider, not against the
      diff.** This whole note came from the owner watching somebody meet the app
      for the first time. The question the screen has to answer for that person
      is *what am I about to do and will I like it* — and the honest reading is
      that today it answers *here are 52 numbers*. Look at it on the tablet AVD
      at 1280 × 720 dp, on a long class and a short one, with a board and
      without

---

### 22.8 "Very stretched" — the dashboard reconsidered — the owner's note, 10 August 2026

**The owner's words, verbatim:** *"Seems very stretched. Feel like more info can
be shown 'above the fold'. I won't try and design it for you but perhaps try and
rethink the design of the dashboard with this in mind. Primary CTA should
probably be Begin Class rather than 'Just Ride' though as i feel like 95%+ of
usage will be classes. Maybe some kind of social feed. With achievements in mind
(next section) that should be considered too for the future."*

**This is the fourth note on this screen and the first one about its vertical
axis.** 22.2 was a single column stretched across 1280 dp, 22.4 was the width
being capped rather than used, and 22.1 was the progress section not showing
progress. Every one of those was answered, and the sitting that answered the
last of them said the dashboard *"fits on one screen without scrolling"*. **That
claim was measured against the screen without a household on it.** With one, it
does not — and the part below the fold is the only part of this surface that is
about anybody else.

**The measurements, taken on the 1280 × 720 dp AVD before any of this was
written** (px at 240 dpi; ÷ 1.5 for dp):

| What | Bounds | In dp |
|------|--------|-------|
| The scrolling viewport | `[0,36][1920,1032]` | 1280 × **664** |
| Everything in it | — | ~**993 dp tall** — *one and a half screens* |
| Greeting block | `[24,60][255,189]` | 86 dp tall, three lines |
| FTP card | `[24,225][948,453]` | 616 × **152** |
| Just Ride card | `[972,225][1896,453]` | 616 × **152**, of which its text is **43** |
| Three action tiles | `[24,477][1896,644]` | 405 × **111** each |
| *Your Progress* heading + subtitle | `[24,680][338,746]` | **44 dp** of label |
| Two progress cards | `[24,770][1896,890]` | 616 × **80** |
| Household panel | from y 930, clipped | 616 × **413**, **entirely below the fold** |

**"Stretched" is the right word and it is vertical.** The horizontal complaint
was fixed in 22.4 and the fix is holding — nothing here bands across the panel.
What is left is that **eight cards occupy 993 dp of a 664 dp screen**, and the
reason is not how many things there are but how much room each one takes to say
what it says: a card 152 dp tall carrying 43 dp of text, three navigation tiles
at 111 dp each, and 130 dp of headings and greetings introducing cards that
already name themselves. **The screen is not full. It is loose.**

**And it does not answer the second half of its own question.** 22.1.1 settled
that the dashboard answers *should I ride today, and what should I ride* —
and there is nothing on it that says what to ride. *Begin Class* is a door to a
list. That is the gap the owner's primary-CTA note is standing next to, and
22.8.6 is where it is picked up.

- [x] **22.8.1** **Begin Class is the primary action; Just Ride is the
      secondary.** The owner's instruction and their reasoning — *"i feel like
      95%+ of usage will be classes"* — and the current screen has it exactly
      backwards: *Just Ride* is a 616 × 152 dp card in `primaryContainer` teal
      beside the FTP, and *Begin Class* is the leftmost of three identical grey
      tiles that also contain *History* and *Settings*. **A rider whose next
      action is a class has to pick it out of the furniture.** Note this is a
      swap of *emphasis*, not a deletion: Just Ride keeps its place, and it
      should keep a place a rider can hit without reading, because it is what
      somebody already on the bike reaches for

      ***Done and observed on the tablet AVD.*** *Begin Class* is a
      `primaryContainer` card at `weight(2f)` with *Just Ride* beside it at
      `weight(1f)` in the secondary treatment — the exact inverse of what was
      there. Both were tapped through: *Begin Class* lands on the class
      library's category row (`All / Climbs / Endurance / Recovery / Sprints /
      Sweet Spot`) and *Just Ride* on *What's your goal today?*, which is the
      check worth doing rather than assuming, because **swapping two lambdas
      between two call sites is exactly the change that goes silently wrong**.

- [x] **22.8.2** **Take the vertical fat out, itemised against the table
      above.** Named because "make it denser" is how a screen loses the
      breathing room 22.4 bought it, and each of these is a specific claim that
      can be argued with:
      - *"Ready to ride?"* under the rider's own name is a third line of
        greeting saying nothing (Phase 26). The greeting is 86 dp
      - *Your Progress* + *"Track your performance over time"* is 44 dp
        introducing two cards that say `Last 30 days` and `Last ride` on
        themselves. A section heading earns its place when a surface has
        sections to tell apart; this one has one
      - The two hero cards are 152 dp tall for 43 dp of content, because a
        `WideRow` equalises heights and the FTP card sets the height with its
        caption. *"Functional Threshold Power — your baseline for all training
        zones"* is a definition, and a definition is read once
      - The three tiles are 111 dp each for a 32 dp icon and two words

      ***Done, and each of the four was taken for its own reason.***
      - *"Ready to ride?"* is gone and the greeting is **one line**, 86 dp of
        stacked block down to a 44 dp row that also carries 22.8.3's doors.
      - *Your Progress* and *"Track your performance over time"* are gone. 44 dp
        of heading over two cards that say `Last 30 days` and `Last ride` on
        their own faces.
      - **The FTP definition moved rather than being deleted**, and that turned
        out to be the interesting part. It was setting the height of a whole
        row — `WideRow` equalises heights, so a caption on the FTP card decided
        how tall *Just Ride* was — and *Your FTP*, the one screen in this app
        where the number is genuinely **read**, had never spelled the acronym
        out at all. Phase 26's rule about where a *unit* belongs is the same
        rule about where a *definition* belongs. It opens that screen now, at
        `readableText` width, because the rest of the surface is charts and this
        one line is prose.
      - The primary card's padding is `large` rather than `extraLarge`: at the
        wider figure it was 107 dp tall to hold 43 dp of text, and it is the
        widest card on the screen, so the emptiness read as a field of teal.

- [x] **22.8.3** **Navigation is not content, and three doors should not weigh
      the same as the ride.** *History* and *Settings* are the same kind of
      thing as the bottom navigation bar the tablet already draws
      (`HARDWARE.md`), and they are given 405 × 111 dp each on the first screen.
      Whatever replaces them has to stay reachable — this is the only way to
      either — but it does not have to be a card. **Do this one after 22.8.1**,
      since the tile row is what *Begin Class* is currently hiding in and
      changing both at once makes neither measurable

      ***Done — they are labelled doors in the header row.*** Two 405 × 111 dp
      cards became two text buttons at the right-hand end of the greeting,
      costing nothing vertically because that row already existed. **They keep
      their words**: a bare gear-and-clock pair would have been smaller again,
      and 20.4's whole lesson is about the rider meeting this app for the first
      time — a glyph is a guess and *Settings* is not. Both tapped through, to
      the ride list and to the rider's own settings.

- [x] **22.8.4** **The empty rail below the fold.** The household panel is a
      lone card holding a column's width (22.6, correct) with **633 dp of
      nothing beside it**, at the bottom of a screen the owner says is
      stretched. Whatever comes out of 22.8.6 goes there first, because it is
      free space that costs no density anywhere else

      ***Done — the rail is what the rider's own cards now sit in.*** The
      household is a `weight(1f)` column with the FTP, thirty-day and last-ride
      cards stacked in the other, so nothing was invented to fill 633 dp of
      tablet: **the thing that fills it is the thing that used to be under
      it.** The two nags (the account offer, the backup reminder) were also
      being held to half a row by a weighted spacer, which is `loneCard`'s cap
      arrived at by accident and 100 dp narrower than the token says; they use
      the token now, and the sentence stopped wrapping to three lines.

      ***And the no-household branch had to be built rather than inherited.***
      A household of one is the ordinary case for a new rider, and the first
      version of this change would have drawn the three own-cards at
      `fillMaxWidth` across 1232 dp — the owner's rule broken by the change that
      cites it. They go abreast through `WideGrid` instead, which is the token
      for a set that is *looked at*. **Seen** by turning `household_visible` off
      for every profile but one: three cards at 405 dp, `9 rides · 35 min · 3
      weeks in a row` still on one line, and a large honest emptiness below
      them, which is 22.2.3's rule about the empty case rather than a gap to be
      filled.

- [x] **22.8.5** **Then judge whether the household belongs above the fold.**
      18.2's rule — the rider's own training first, everyone else's second — put
      it last, and that rule is right about *order* and says nothing about
      *visibility*. A panel nobody scrolls to is a panel nobody has. This is a
      real decision and not a tidy-up: the owner's social-feed note and Phase 28
      both want room in the same place

      ***Decided: yes, and beside rather than above.*** 18.2's ordering is
      kept by left-before-right and both are above the fold, which is the
      answer this item was hoping for rather than the trade it expected.
      **Measured on the tablet AVD**: the whole dashboard, household included,
      is **609 dp with the backup nag showing and 513 dp without**, against a
      664 dp viewport — and the check was that a 700 px swipe moved nothing,
      with the two frames differing only in the clock.

- [~] **22.8.6** **Decide what earns the space before filling it, and keep
      22.1.1's sentence as the test.** *Should I ride today, and what should I
      ride.* The candidates, ranked by how much of that sentence they answer:
      1. **A class to ride** — the second half of the question, unanswered
         today. The library is bundled, `ClassTemplate` is already queryable,
         and *"here is one, start it"* is one tap where *Begin Class* is two and
         a decision
      2. **The household, promoted** (22.8.5)
      3. **A social feed** (22.8.7) — the owner's *"maybe"*, and it is a maybe
      4. **Achievements** (Phase 28) — the owner's own *"for the backlog"*
      **A rethink is not a licence to fill it.** The dashboard got to three
      summaries of the past by accretion, one honest card at a time, and 22.1.1
      exists because of it

      ***Half-answered, and deliberately so: the room is made and nothing has
      been invented to fill it.*** 22.8.4 and 22.8.5 took candidate 2 — the
      household, promoted rather than added — and that is the whole of what
      this sitting put in the space, because it was already on the screen and
      only in the wrong place. **What is left below the cards is honest
      emptiness**: 22.2.3's rule is that a card with nothing to say draws
      nothing, and the solo-rider branch shows what that looks like at 273 dp of
      nothing under three cards. *A dashboard with room on it is a better place
      to be than a dashboard without, and filling it is the next decision
      rather than a consequence of this one.*

      **Candidate 1 is still the one to build and its argument got stronger.**
      Nothing on this screen says what to ride, and the primary action is now a
      door labelled *Begin Class* — so the screen invites a choice it does not
      help with. What it needs before it can be built is the rule: *which*
      class, on what basis, and what it says when the rider has ridden nothing
      yet. That is a design decision with the owner's name on it rather than a
      layout job.
- [ ] **22.8.7** **A social feed — what it would be, and the two rules it
      cannot break.** The owner's *"maybe some kind of social feed"*. What a
      feed item is here: *Kilo rode Zone 2 Steady, 22 min, this morning*; *Ava
      beat your time on Climb Builder*; *Ben's first ride in three weeks*. Two
      rules, both from things already settled:
      - **The connectivity model, rule 3.** An offline rider gets social with
        the people on their own bike, so the household feed is a Room query and
        must never touch the network. The across-bikes feed is Phase 18 and
        needs an account. **Two tiers, one component**, the way 18.9 says every
        social screen is built on top of its Phase 24 equivalent
      - **The first screen must not wait for the network.** 22.1.8's rule about
        `workout_metrics` is the same rule one level up: this surface is what a
        rider sees between choosing a profile and riding, and a spinner where
        the feed goes is worse than no feed
      **And a feed is a thing you scroll**, which is in tension with a screen
      whose complaint is that it already scrolls. The honest shape is probably
      *three lines and a door*, the way `RecentRidingCard` is a number and a
      door — not a timeline
- [ ] **22.8.8** **The achievements slot is deliberately left as a hole.**
      Phase 28 is where achievements are designed, at the owner's weighting
      (*"one for the backlog"*), and 28.6 is the dashboard's share of it. What
      this item asks of 22.8 is only that the layout it lands on has somewhere
      for it to go — which 22.8.4's empty rail already is
- [x] **22.8.9** **Judged on the tablet AVD at 1280 × 720 dp with a household on
      it, and 22.2.5 still applies.** The claim *"fits on one screen without
      scrolling"* was made against a dashboard with no household panel and is
      the reason this note was needed. Seed one before measuring anything here.
      **The number to beat is 993 dp**, and the target is the fold: 664 dp on
      the AVD, 672 dp on the bike

      ***Done, and the number is the item.*** **993 dp → 609 dp with the backup
      nag showing, 513 dp without**, against a 664 dp viewport on the AVD and
      672 dp on the bike. The check that it genuinely does not scroll was a
      700 px swipe with the two frames compared: identical but for the clock.
      Measured with a household of fourteen seeded on the device — which this
      item asked for, and which is the condition the previous sitting's *"fits
      on one screen without scrolling"* was **not** measured under.

      **22.2.5 is still owed on all of it.** Everything here is the AVD's
      furniture, not the tablet's.

- [x] **22.8.10** **A defect nobody had met, found by deleting the thing that
      carried it.** `WideRow` — this screen's private "cards abreast where there
      is room, stacked where there is not" — **did not stack.** Its content is
      written against `RowScope`, so the narrow branch could not put the
      children in a `Column`; it wrapped them in a *second* `Row` instead,
      weights and all, and a phone would have got three cards at 130 dp each.
      The comment above it asserted the opposite in as many words (*"in the
      stacked case the weights are ignored"*), which is what kept it invisible,
      and the bike is 1280 dp so nothing ever took that branch.

      It is gone with the layout that used it, and the residue is a rule worth
      more than the fix: **a helper that takes `RowScope` content can only ever
      make rows.** `WideGrid` takes *items* and that is why it can do both, and
      it is the reason 22.8.4's no-household branch could be built at all. The
      comment that lied is preserved in the source where `WideRow` used to be —
      **a wrong comment above working code on the only path anybody walks is
      the cheapest possible place for a defect to hide.**
