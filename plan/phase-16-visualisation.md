> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 16: Data visualisation

The post-ride charts (16.1) are close to fundamental — they are what makes a
recorded time series worth recording. The trend work in 16.3 is genuinely
nice-to-have and only becomes interesting after a few dozen rides exist.

### 16.1 The ride itself

All of these are on the **ride detail** screen (12.2), which is where a ride
lives once it is finished. Two columns on the tablet, one anywhere narrower.

- [x] **16.1.1** Power over time with zone bands behind it (was 8.11.53).
      *Drawn as a min/max envelope with the mean through it, over the rider's
      own zone bands and a dashed rule at FTP. Observed on the tablet AVD*
- [x] **16.1.2** Heart rate over time, drawn **only where samples exist** —
      null is unknown, and a line dropping to the axis says the rider's heart
      stopped. *Each contiguous run of samples is its own path; a strap that
      pairs mid-ride charts only the part it saw, and one that drops out gets a
      break rather than a straight line across the gap. Covered by tests both
      ways*
- [x] **16.1.3** Cadence distribution. *Banded at 10 rpm, with coasting
      excluded — without that filter every ride has a large spike in the 0–9
      band that says nothing about how it was ridden*
- [x] **16.1.4** Time in zone as a stacked bar, shared with the HUD's collapsed
      strip (11.2.2). *With a legend: seven colours in a bar is a code nobody
      has been given the key to*
- [x] **16.1.5** The class's prescribed intervals drawn under the actual trace — "what you were asked for" against "what you did" is the single most useful post-ride view.
      *Each interval is an outlined block at its target power band, behind the
      trace so the record is always on top of the prescription. Two things the
      building of it turned up. The band is scaled by `workouts.intent_modifier`
      — the multiplier the ride was **given**, not one re-derived from today's
      preferences — so this half is a record; the FTP half is not, and is 7.8.
      And the plan is **clipped to what was ridden**: a class abandoned part way
      would otherwise hang 18 minutes of prescription off the end of a
      12-minute axis, so the segments stop where the ride does and the sentence
      says the class was longer. Observed on the tablet AVD against a real
      `Torque Repeats 20` ride that stopped at 10:31 of 20:00 — five blocks
      (Z1, Z2, Z5, Z2, and a sliver of the sixth), 28% of the prescribed time
      inside the band, and a free ride beside it with no blocks, no legend and
      no compliance sentence*
- [x] **16.1.5a** The prescribed **cadence** has nowhere to be drawn. An
      interval prescribes a cadence range as well as a zone, and 16.1.3 is a
      *distribution*, not a trace — there is no time axis to lay a target on.
      Either a cadence-over-time chart or nothing; the data is already parsed
      and thrown away by 16.1.5

      *The chart, then — a fifth card, **Cadence over time**, with the class's
      cadence blocks under the trace exactly as 16.1.5 puts its power blocks
      there, and the distribution kept beside it as **Cadence spread**. They
      are the same metric answering two questions and they sit side by side.*

      *Three decisions. The blocks are **absolute rpm**, not scaled by the
      ride's intent multiplier the way the watts are: riding a class easier
      means fewer watts, not slower legs. **Zeros are drawn**, unlike the
      spread, which excludes them — a coast is *measured* and it happened at a
      moment, which is the line between this chart and the heart-rate one,
      where a gap is unknown and drawing it would say the rider's heart
      stopped. And compliance is **counted separately from the power's**,
      because the whole reason this chart exists is that watts dead on with the
      legs wrong is a different session from the one that was written.*

      ***Observed on the tablet AVD*** *against a fixture ride of `CLB-01`
      Torque Repeats: the cyan blocks step down for each torque interval, the
      trace rides above them throughout, and the sentence says* Inside the
      class's target cadence for 0 seconds of 19 minutes 59 seconds prescribed
      — 0% *while the power card beside it says 63%. That is the number the
      item was asking for: the same ride, obedient in watts and not in legs.
      Four JVM tests, including that the intent multiplier moves the power band
      and leaves the cadence alone.*
- [x] **16.1.6** The power card reads from `workout_metrics.power_is_measured`:
      *Measured by the bike* / *Partly measured — the bike's sensor dropped out
      during this ride* / *Estimated from cadence and resistance*. The mixed
      case that had to be decided is a real state rather than a defensive
      branch — a board that drops out and comes back leaves exactly it. A ride
      from before the column says "estimated", which is the safe direction:
      "nobody wrote it down" is not grounds for claiming a measurement

- [x] **16.1.7** **The charts have no axes, and the heart-rate one is
      meaningless without them.** Confirmed on the first real ride: a green
      line rising left to right with the caption *Heart rate from 88 to 170
      beats per minute* underneath. The shape is legible, the values are not,
      and a rider cannot answer "what was I at during the second climb". Every
      trace needs a value scale at minimum and a time reference where it fits.
      **The brief is beautiful, not scientific** — this is a rider's own ride
      on a bike, not a lab plot. A few labelled gridlines at round numbers,
      generous type, no tick forests, no boxed axes. `RideChartBuilder` already
      downsamples into buckets with min/max per bucket, so the data for a scale
      is there; this is a drawing job, not a data one
- [x] **16.1.8** Decide the axis treatment **once, for all four charts**, and
      put it in the shared chart components rather than per card. Power, heart
      rate, cadence distribution and time-in-zone currently each caption
      themselves in their own way, and four different answers is how the ride
      detail screen starts to look assembled rather than designed

      **The answer, and it is one composable.** `ChartScale` (pure, in
      `domain/chart`) picks the numbers: a 1, 2, 2.5 or 5 times a power of ten,
      chosen *nearest* rather than rounded up — 88–170 bpm wants labels every
      25, and rounding up gives it two where four fit — and it drops any that
      would land within 4% of either end, where a label collides with the trace
      that reached it and the caption states the peak exactly anyway.
      `ChartFrame` draws them: hairlines at those values, the number sitting
      *on* the line at the right-hand edge over a scrim the colour of the card,
      and the clock underneath in three marks. No boxed axes, no tick forests,
      no frame.

      Two details that matter more than they look. The gridlines are placed
      with the **same `fractionOf`** the trace is drawn with, so a line cannot
      drift away from the value it claims. And the scrim exists because a ride
      that finishes on a sprint runs its own trace straight through the label —
      a number that cannot be read is not an axis.

      The other two charts get the same idiom rather than the same component,
      because their axes are different questions: the cadence histogram's
      vertical axis is *time*, so it carries its peak duration and a middle rpm
      mark; time-in-zone already lists every zone with its duration and
      percentage, which **is** its axis.

      **Observed on the tablet AVD across all four cards**: power at 100 W and
      200 W over 00:00 / 02:08 / 04:17, heart rate at 100 and 150 on the same
      clock, cadence peaking at 00:59 across 60–100 rpm

### 16.2 Building them
- [x] **16.2.1** Compose `Canvas`, no charting dependency — these are four
      fixed chart types, and a library is a large surface for a small need.
      *Also a library would have to be taught that a null heart rate is
      unknown rather than zero, which is the defect this project has already
      fixed twice*
- [x] **16.2.2** Downsample before drawing: 2,700 points into ~300 buckets
      keeps peaks (min/max per bucket, not mean — averaging erases exactly the
      sprint the rider wants to see). *Bucketed by **elapsed time**, not sample
      index: a recovered ride has gaps, and bucketing by index would compress
      them out and draw a ride that looks continuous. A test rides 2,700
      seconds with a one-second 600 W spike in the middle and asserts the spike
      survives while its bucket's mean does not reach it*
- [x] **16.2.3** Off the main thread, cached on the ride, computed once.
      *Built on `Dispatchers.Default` after the summary is already on screen —
      the totals are what the rider opened the screen for*
- [x] **16.2.4** Accessible: every chart has a text summary, since a chart is
      unreadable to a screen reader and a fair amount of this data is a
      sentence. *The canvas itself carries the summary as its content
      description and the visible copy of it is cleared from the tree, so it is
      announced once rather than twice*

### 16.3 Trends — nice to have

**And the screen they live on now exists.** 16.3.1 was the first of these to
become buildable and it had nowhere to be drawn, so it brought a screen with it:
`FtpProgressScreen`, off the dashboard's FTP card. It is *Your FTP* rather than
*Trends* on purpose — one subject, named after what it is about. The deciding question — whether a
trend is about the **rider** or about the **riding** — is now settled, and it
settled by being used: 16.3.2 and 16.3.5 went together onto a second screen,
*Your riding*, because volume and consistency are the same subject seen twice.
By the same rule 16.3.3 belongs with *Your FTP*, since a personal best is a
claim about the rider.

- [x] **16.3.1** FTP over time, marked with the rides that triggered each change. ~~**Blocked on 7.9**~~ — unblocked by 7.9 and **done and observed**: the stepped line, a mark per change that says whether the app measured it or the rider claimed it, and every change listed newest-first with the ride behind it one tap away. The full write-up, including why the axis runs to *now* and why the first value is not a change, is at **7.10.1**; 7.10.6 is what the line is still not allowed to claim
- [x] **16.3.2** Weekly volume and output — **done and observed**, on a screen
      it shares with 16.3.5. See the note under 16.3.5: they are one subject
      and one screen, *Your riding*
- [ ] **16.3.3** Personal bests by duration
- [x] **16.3.4** This ride against your previous best at the same class (`leaderboardFor` already computes it — see 11.4)

      *It went in the picker the housemates are already in (24.3.1), because
      from the chart's point of view they are the same thing: another ride of
      this class, on these axes, under the same measured-power rule. One list,
      one ghost at a time, one alignment rule. The only difference is the
      label, and it is drawn in the **power colour rather than the second-rider
      grey** — dimmed, because it is behind rather than on top, but it is still
      the rider and not somebody else.*

      ***Previous best, not best-ever***, *which is the one real decision. A
      ride is compared with what the rider had already done when they rode it,
      so the comparison on a ride from March still says the same thing next
      year, and a personal best is never quietly drawn against the ride that
      beat it. `previousBestOfClass` carries `timestamp < :beforeMs` for
      exactly that, and the same measured-power exclusion `householdRivals`
      has.*

      ***Observed on the tablet AVD***: *a* Ride against · Your best · 187.3 kJ
      *chip on a 162.8 kJ ride of the same class, and tapping it draws the
      stronger ride dashed over the efforts and under the recoveries.*
- [x] **16.3.5** A calendar heatmap of ride days. Cheap, and the streak is the thing that gets people on the bike

      ***And it brought the screen that answers where 16.3.2–16.3.5 live.***
      *`RidingScreen` — **Your riding** — named after its one subject the way
      `FtpProgressScreen` is: that screen is about the **rider**, this one is
      about the **riding**. Volume (16.3.2) and consistency (16.3.5) are on it
      together because neither is worth much alone: 200 minutes in a week is a
      different training week depending on whether it was one ride or five, and
      a run of ride days says nothing about how hard any of them were. The door
      is a* This Week *card in the dashboard's progress section — rides, not
      kilojoules, because "have I been riding" is answered by a count.*

      *Three rules, all of them about not asserting what the data does not say.
      **The current week is hollow and never in the scale** — a Monday with one
      ride on it is not a bad week yet, and scaling to include it would make
      every finished week a stub. **A week with no riding is a bar of nothing,
      not a missing bar**, because the fortnight off is the information and it
      should be a fortnight wide. **A day that has not happened is absent, not
      empty** — nothing about Thursday is drawn on a Wednesday.*

      *Minutes and kJ are **two bar rows, not one chart with two axes**: a
      second vertical axis can be scaled to make any two series agree or
      disagree, which is a claim about the rider's training made by the drawing
      rather than by the data. Stacked on one set of week columns, the
      comparison is still there and nothing has been asserted.*

      *`RidingHistoryBuilder` is pure, with the clock and the timezone injected
      — `StreakCalculator`'s argument, and the streak on this screen comes from
      that same object so the two cannot disagree about what a day is. Nine JVM
      tests, including one that rides either side of the October clock change:
      stepping a week on by adding 604,800,000 ms lands an hour inside the
      wrong day and moves a Sunday ride into the following week, invisibly, for
      the other fifty-one weeks of the year. The week starts where the
      **locale** says it does, not on a hard-coded Monday.*

      ***Observed on the tablet AVD*** *against 22 fixture rides spread over
      three months with a fortnight off in the middle of them: seventeen week
      columns with the gap visible, a hairline for the week in progress,*
      Busiest finished week: 70 minutes*, and a calendar whose last column
      stops at today. **One thing the AVD changed**: the not-ridden tile and
      the not-yet cell were both invisible at first, so the distinction the
      code documents existed only in the source — the empty tile is heavier
      now. The fixtures are hand-seeded and, like `ride-simon` before them,
      break the simulated-power invariant by construction: they are evidence
      about drawing and nothing else.*
