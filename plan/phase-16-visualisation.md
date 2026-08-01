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
- [ ] **16.1.5a** The prescribed **cadence** has nowhere to be drawn. An
      interval prescribes a cadence range as well as a zone, and 16.1.3 is a
      *distribution*, not a trace — there is no time axis to lay a target on.
      Either a cadence-over-time chart or nothing; the data is already parsed
      and thrown away by 16.1.5
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
- [ ] **16.3.1** FTP over time, marked with the rides that triggered each change. **Blocked on 7.9** — this was written as a charting task, and the data it charts does not exist: a profile holds one FTP and the previous value is overwritten. See 7.10.1 for what to draw and 7.10.6 for what the line is allowed to claim
- [ ] **16.3.2** Weekly volume and output
- [ ] **16.3.3** Personal bests by duration
- [ ] **16.3.4** This ride against your previous best at the same class (`leaderboardFor` already computes it — see 11.4)
- [ ] **16.3.5** A calendar heatmap of ride days. Cheap, and the streak is the thing that gets people on the bike
