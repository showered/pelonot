> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 11: The HUD-first experience — the current priority

**Premise:** this app is used almost entirely from the corner of the rider's
eye. They start a class, switch to Netflix, and look at Pelonot in glances for
the next forty minutes. The HUD is not an accessory to the ride screen; it *is*
the product, and everything in this phase is judged by whether it survives
being read in half a second from two metres away while out of breath.

### 11.1 Verify the HUD's own interactions on a device
- [x] **11.1.1** Docked strip renders over another app without covering the middle of the screen
- [x] **11.1.2** Sits below the status bar rather than under the clock
- [x] **11.1.3** Tap-to-collapse and the slim strip it collapses to. *Observed
      on the tablet AVD mid-class: the handle takes the strip from 170 dp to
      115 dp — a third of its height handed back to the film — and what
      survives is the clock, the zone number, the four live numbers and the
      controls. On a free ride the saving is much smaller, because the expanded
      strip has no timeline, zone ring or next-up block to shed in the first
      place*
- [x] **11.1.4** Drag to re-dock between top and bottom, and that the choice
      persists. *Observed in both directions: dragged down, the strip moved to
      the bottom edge and `hud_dock=Bottom` was in the DataStore; dragged back
      up, `Top`. A ride started afterwards raised the strip at the edge the
      last one was left at.* The collapse state deliberately does **not**
      persist — `hide()` resets it, so every ride opens showing everything
- [x] **11.1.5** **Pause, resume and stop from the HUD with the app in the
      background** — driven from the strip on the bike with Netflix in the
      foreground, 31 July 2026. Pause froze the ride at 03:00 and it was still
      03:00 twelve seconds later, so the pause genuinely leaves elapsed alone
      (3.7); resume advanced it again; stop tore down the notification and the
      overlay window and left Netflix undisturbed. The overlay never took focus
      from the video app at any point
- [x] **11.1.6** **Spoken coach audible over a playing video** — but only
      after two defects, and neither was in the coaching logic. `RideCoach`
      configured the engine straight after the `TextToSpeech` constructor,
      before the service had bound, so both the language *and the audio
      attributes* were discarded ("setLanguage failed: not bound to TTS
      engine", in logcat on the bike). And attributes alone ask the system for
      nothing: ducking requires an explicit
      `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` request, and nothing ever made one.
      `dumpsys audio` showed Netflix holding `GAIN` with `loss: none` and an
      empty ducked-players list throughout, and the rider reported the cue
      inaudible under the film. With focus requested per cue and released when
      the last utterance finishes: **observed ducking, and the coach clearly
      audible over Netflix**

### 11.1a Getting between the HUD and the app

Today the HUD and the ride screen are two places with no door between them. The
rider raises the HUD when the ride starts, switches to Netflix, and there is no
way back into the app except through the launcher — and no way back out to the
HUD except by leaving the app again. That is a gap in the product, not a
polish item: it is the single journey a rider makes most often during a class.

- [x] **11.1a.1** **Double-tap the HUD brings the full app forward.** Double
      rather than single, because single tap already collapses and expands the
      strip (11.1.3) and that is the gesture a rider fires by accident while
      reaching past the tablet. A single tap that yanked Netflix off the screen
      mid-scene would be the worst possible mis-fire on this surface.
      *Observed on the tablet AVD: double-tapped the strip with the launcher in
      front, Pelonot came forward on the ride screen with the ride still
      running and the overlay stood down.* One trap for anyone verifying this
      by `adb`: Compose ignores a second tap that lands inside
      `doubleTapMinTimeMillis` (**40 ms**), and two back-to-back
      `input tap`s are about 26 ms apart, so the gesture reads as a single tap
      and nothing happens. Put a `sleep 0.12` between them
- [x] **11.1a.2** A **"back to the HUD" control on the ride screen**, so the
      journey is symmetric and does not route through the launcher or the
      recents switcher. `moveTaskToBack` rather than `finish()` — the ride
      screen has to survive, because the rider is coming back to it. Hidden
      when the HUD is off or ungranted, since it would then only hide the app.
      *Observed: tapped it, the launcher returned and the strip came up with
      live telemetry on it*
- [x] **11.1a.3** **The full app comes forward when the ride ends.** The class
      finishing is the one moment the rider definitely wants the whole screen —
      the summary, the RPE question and any FTP proposal are all there and none
      of them fit on a strip. **A foreground service is *not* exempt from
      Android 10's background-activity-start rules** — the note here previously
      assumed it was. `SYSTEM_ALERT_WINDOW` is on the exemption list, which is
      the same grant the HUD is already drawn under, so the app that can show a
      strip can always open itself from one. *Observed: stopped the ride from
      the strip with the app in the background and the summary came up by
      itself, showing the ride's real figures*
- [x] **11.1a.4** **Discard the ride from the post-ride summary**, for the
      session that was a warm-up, a mistake, or somebody else pedalling for
      thirty seconds. Guests get this today (8.4) and riders with a profile do
      not — they have to finish, leave, open history and delete. It has to name
      what is going and be as hard to hit by accident as the delete in 12.3.2.
      *Observed: the dialog names the duration, and confirming took the ride
      and all 135 of its `workout_metrics` rows with it — checked by count
      against the database, not by the screen returning.* Local only: an
      already-uploaded ride stays in the cloud until tombstones exist (15.3.4)
- [x] **11.1a.5** **The cold-start door — there is no way back into a ride that
      is already running.** 11.1a.1–11.1a.3 all assume the app's task still
      exists: `AppForeground.bringForward` sends `ACTION_MAIN` +
      `CATEGORY_LAUNCHER` precisely so an existing task resumes where it was
      left, which is the right behaviour and covers the common case. When the
      Activity is gone it resumes nothing — the graph's start destination is the
      profile selector, and **nothing outside `WorkoutService` knows a ride
      exists**. So the strip's double-tap, the ride notification and the
      launcher icon all land the rider on "who's riding?" with a class still
      recording behind it and no route to it. The notification is the worse of
      the three, because a ride notification that does not open the ride is the
      one thing a notification is for. Needs the running ride to be knowable
      from outside the service — the incomplete workout row already says so, and
      8.3b has to be fixed first or asking the question raises the recovery
      dialog instead.
      *Done with the same `RideInProgress` 8.3b introduced, and only from the
      start destination — that is what makes it a cold-start door rather than a
      trap, since a ride begun the ordinary way sets it too and the rider is
      already on the ride screen by then. Dashboard is pushed underneath on the
      way in, because otherwise the summary's own `popUpTo(Dashboard)` has
      nothing to pop to and the rider finishes the ride into a dead end.
      **Observed on the tablet AVD**: ride started, task swiped away (0 activity
      records, service still `isForeground=true`), reopened from the launcher →
      the ride screen at 00:26 and counting; ended it there and Done on the
      summary returned to the dashboard*
- [x] **11.1a.6** **The ride notification is missing entirely on Android 13+.**
      `POST_NOTIFICATIONS` is declared in the manifest and **requested by
      nothing** — the only runtime request the app makes is for Bluetooth. On
      API 33+ that means the ongoing ride notification is never posted, so the
      route 11.1a.5 just built has no doorbell: `HARDWARE.md` calls that
      notification "the most reliable read on an in-flight ride" and on a
      modern device it does not exist. The bike's own tablet is Android 11 and
      unaffected, which is exactly why this could sit here unnoticed —
      `targetSdk 34` means any other device the app is installed on is not.
      Same family as the `VIBRATE` and `ACCESS_FINE_LOCATION` corrections, one
      step earlier: not a permission absent from the manifest, but one present
      in it that nobody ever asks for. *Seen on the API 36 tablet AVD:
      `importance=NONE` for `com.pelonot` and no notification during a ride,
      until it was granted by hand with `pm grant`*
      *Done. `NotificationPermission` + `RequestRideNotificationPermission`
      (`ui/permission/`), called from the ride screen. Asked at the first ride
      rather than at launch — the only notification this app posts is the
      ongoing ride, so a rider who has never started one has nothing to say yes
      to — and held back by a `deferred` flag while the overlay prompt is up,
      because two system dialogs on the first ten seconds of a class is how
      both get dismissed unread. A denial is not retried and not surfaced: the
      ride is unaffected either way, and the platform stops offering after two
      refusals. **Observed on the API 36 tablet AVD** with the permission
      revoked: free ride started → the dialog appeared over the ride screen →
      Allow → `POST_NOTIFICATIONS: granted=true` and notification id=101 on
      `workout_channel` in `dumpsys notification`, where before there was
      none*

### 11.1b The HUD getting out of the way

The premise of this whole phase is that the rider is watching something else.
The strip currently sits on top of that film as a solid block, in a fixed size,
pinned to the top or bottom edge. Every item here is about the HUD taking up
less of the screen and less of the attention.

- [x] **11.1b.1** **Adjustable opacity**, from solid down to nearly invisible,
      with the film readable through it. Set once in Settings rather than
      fiddled with mid-ride.
      *Done, and it turned into a redesign rather than a slider. A single alpha
      over a full-width panel is the wrong instrument: a rider asking for more
      of their picture back only ever got a lighter wash over all of it — the
      numbers got harder to read and the picture never came back. **The panel is
      gone.** The strip is a transparent band with a handful of chips floating
      in it, and backing is painted only where a number or a control sits;
      everything between them is untouched film at any setting. The slider now
      moves the chips, defaulting to 0.82. Observed on the tablet AVD over a
      full-white page: expanded, collapsed, and the class timeline*
- [x] **11.1b.2** A floor on how transparent it can go, and a check that the
      text still passes contrast against **moving** video rather than against
      one paused frame.
      *The floor is **calculated, not chosen** — `HudOpacity` composites the
      chip over a backdrop and finds the least opaque it can be while every
      colour the strip draws text in still passes WCAG. Two things fell out of
      building it, both of which had been quietly wrong. A floor derived from
      the brightest colour on the strip says nothing about the rest of it: at
      0.59 the white clock passed at 4.5 and the coral power figure sat at 1.55.
      And **the worst backdrop is not white** — the backdrop is a film, so it is
      every colour there is, and a partly-transparent panel can land the
      composited background right on the text's own luminance, where contrast is
      1.0 and the number is invisible. Bisecting on the over-white contrast
      made coral look fine at zero opacity. The floor is about 0.76 with all
      seven colours counted, and the grey labels are lifted towards the primary
      text colour rather than dragging it to 0.81 for everybody. 9 JVM tests.*
      **The moving-video half of this item is still open and always was**: a
      still frame is kinder than a film, screenshots over DRM video come back
      black (10.4), so this needs the rider's eyes on the bike
- [ ] **11.1b.3** **Resizable**, so a rider who wants three big numbers and a
      rider who wants the whole timeline can both have it. Persisted like the
      dock
- [x] **11.1b.4** **Dock to the left and right edges too**, not only top and
      bottom — **asked for directly by the owner, 31 July 2026**: "I would like
      a version of the HUD on the right and left too, should be able to drag it
      where you want." So this is four edges and one gesture, not two edges and
      a preference. A vertical strip down one side leaves subtitles *and* faces
      clear and is probably the better default on a 16:9 tablet in landscape —
      which is the shape of the device this actually runs on (8.13).
      `HudDock` is a two-value enum with an `opposite()` and a `gravityFor()`
      either side of it; both extend to four cleanly, and the drag detector on
      the handle is currently `detectVerticalDragGestures`, which does not

      ***Done in the sixty-second sitting and watched on the tablet AVD on all
      four edges, expanded and collapsed, with a class running.*** *Three things
      the item did not anticipate, each of which is a rule rather than a detail.*

      ***A vertical dock is a different window, not the same one rotated.***
      *`WRAP_CONTENT` on the width would have worked and is the wrong answer:
      the strip would then be as wide as whatever the tallest chip happened to
      measure that minute, so the amount of film a rider loses would depend on
      whether their class is prescribing a three-digit target. It is a constant,
      `HudDock.VERTICAL_WIDTH_DP` = 244 — which is also what lets the timeline
      bar inset itself by exactly the right amount rather than guessing.*

      ***The timeline does not go to the opposite side.*** *`opposite()` was the
      obvious extension and it is wrong for a vertical dock, because time runs
      left to right: standing the bar on its end is a redesign of the one thing
      on this HUD nobody has complained about. `timelineEdge()` is a second
      function beside it — the far edge for a band, the **top** for a column,
      because the bottom is where subtitles are — and the invariant a test holds
      is only that it never shares an edge with the strip.*

      ***The drag rule left the composable.*** *One axis and a per-callback
      threshold does not survive contact with four edges: a slow drag never
      crosses 12 px in a single `onDrag` and a fast one fires on whichever axis
      happened to move first. `HudDock.dragTarget` is pure, measures the **whole
      gesture**, and lets the **dominant axis** decide, so a drag that wanders
      is read as where it mostly went. The threshold is 40 dp rather than 12 raw
      pixels — which on this 240 dpi tablet was 8 dp, a gesture a rider trips
      over reaching past the handlebars.* **10 JVM tests**
- [x] **11.1b.4a** **Corners, once collapsed** — the owner's observation on
      seeing the compact strip: "in compact mode there are more options, bottom
      left, bottom middle, right bottom, right top." He is right, and it falls
      out of the redesign rather than being extra work. Collapsed, the HUD is
      one pill and a row of buttons, not a band — so it no longer *needs* a
      whole screen edge, and a corner is the least of anyone's film. The
      expanded strip still wants a full edge, so the two states may well want
      different position sets, which is a thing `HudDock` cannot currently
      express: it is one enum shared by both. Settle that before writing the
      drag handling for 11.1b.4

      ***Settled in the sixty-second sitting, and the answer is that the two
      states do not need different position sets after all — because the
      expanded strip stopped wanting a full edge.*** *The vertical window wraps
      its content and is centred on the side (see 11.1b.5 for why), so on a
      vertical dock **both** states are already a floating object against one
      edge rather than a band across it. What `HudDock` would still not express
      is an **alignment along** an edge — bottom-left against bottom-centre —
      and that is one more preference rather than a second enum: the edge and
      the alignment are independent, they persist separately, and the alignment
      is meaningless while the strip spans the edge it is on.*

      **What is left of this item is that alignment, and it is deliberately not
      built.** Collapsed on a *horizontal* dock the strip is still a full-width
      band with a chip at one end, so the corner the owner asked for is a real
      gap there and only there. It is one `Arrangement` and one stored value,
      and it wants the owner's eye on where the default should sit rather than a
      session's guess — **11.1b.4b**
- [ ] **11.1b.4b** **An alignment along the edge, for the collapsed strip.**
      What 11.1b.4a leaves: `HudDock` says *which edge*, and collapsed on a
      horizontal dock the chip sits at the start of a 1280 dp band with the
      controls at the far end. The owner asked for corners and the vertical
      docks now give them for free; the horizontal ones do not. One `Arrangement`
      and one preference — but the **default** is the owner's call and not a
      session's, because it decides which corner of somebody's film is gone
- [x] **11.1b.5** The layout has to genuinely re-flow for a vertical dock, not
      rotate: the timeline, the zone badge and the live numbers each need a tall
      arrangement. Extends 11.1.4, which only ever considered top/bottom. The
      chip redesign in 11.1b.1 makes this materially easier than it was — a
      column of chips is the same components in a `Column` — but the metrics
      chip holds four readouts in a `Row` and a 200 dp-wide dock will not take
      them side by side

      ***Done, and the item was right that the metrics chip is where the work
      is.*** *Four readouts become **two rows of two**, keeping the pairing the
      row already reads left to right — what you change on the top row, what it
      produces underneath — so a rider who has moved the strip does not have to
      re-learn where a number is. They are one list of four arranged either way
      rather than two copies, because four readouts written out twice is four
      places for a target band or a `--` to go quietly missing on one dock and
      not the other.*

      ***Two things are dropped rather than squeezed, and one is a measurement
      the item did not have.*** *The **next-up preview** goes: a column has no
      width to trade against a countdown that is never optional, and the
      timeline bar still runs across the top edge saying the same thing more
      quietly. The **zone name wraps** instead of ellipsising — `ACTIVE
      RECOVERY` had become `ACTIVE RECO…`, and a column has the one thing the
      band never had, which is height.*

      ***And the vertical strip does not span the edge, which is the decision
      that changed 11.1b.4a.*** *Built full-height first, and it put pause and
      stop **400 px clear of the last chip** with nothing in between — two
      objects rather than one instrument, which is 11.1b.7's own open worry
      arriving from a different direction. Wrapped and centred on the side, the
      controls sit under the numbers and the top and bottom of that edge go back
      to the film as well as the middle of the screen.*
- [x] **11.1b.6** Every one of these choices persists, and the HUD comes back
      where and how the rider left it. *Opacity and dock do; the rest of this
      waits on 11.1b.3 and 11.1b.4 existing*

      ***Ticked in the sixty-second sitting for what exists.*** *Watched rather
      than reasoned about: the strip was dragged from Top to Bottom to Right to
      Left mid-ride, the app was restarted between two of those, and Settings'
      **Position** row — four chips now, not two — read back `Left` at the end.
      The write path is unchanged and was already right: `onDockChanged` →
      `settingsRepository.setHudDock`, one owner of the preference.* **11.1b.3
      is the only choice still not persisted, because it does not exist**
- [x] **11.1b.7** **The class timeline moved to the opposite screen edge**, in
      an overlay window of its own. Splits the furniture into two thin bands
      instead of one tall block, and because nothing on it is interactive that
      window is `FLAG_NOT_TOUCHABLE` — every tap in that band goes straight
      through to the film. The strip itself can never make that promise; it has
      a stop button on it. *Observed on the tablet AVD: timeline top, numbers
      bottom, re-docking swaps both.* **The owner is not sold on the split** and
      left it to judgement — so treat it as provisional. If it turns out to read
      as two unrelated things rather than one instrument, the fix is small: the
      bar is a standalone composable in a window of its own, and putting it back
      above the chips is a layout change, not a rewrite. Worth settling on the
      bike rather than by argument, and worth considering alongside 11.1b.4 —
      the answer may well differ for a vertical dock, where the opposite edge
      is a *column* and the timeline would have to run down it

      ***The vertical half of that last sentence is answered by 11.1b.4 and the
      answer is no.*** *The bar stays horizontal on every dock and takes the top
      edge when the strip is down a side, insetting itself by the strip's width
      so the two never meet in a corner — `HudDock.timelineEdge()`. Standing a
      timeline on its end is a redesign of the one element on this HUD nobody
      has complained about, and time running left to right is not a convention
      this app gets to spend. **What is still open is the owner's half**: whether
      the split reads as one instrument at all, which needs the bike*
- [ ] **11.1b.8** **The strip still eats touches between the chips.** The window
      is full-width and the gaps are now invisible, so a rider tapping their
      film in the space between two chips gets nothing and cannot see why. It
      was the same before the redesign — the difference is that the slab at
      least *looked* like something. A window cannot have holes punched in it,
      so this is either a row of narrow windows or nothing; 11.1b.7 shows the
      shape of the fix for anything non-interactive

      ***Read in the fifty-first sitting and the fix has a better shape than
      this text names — but the size of the problem is smaller than it names
      too, and both corrections matter before anybody starts.***

      *The measurement first. The chips are laid out `fillMaxWidth()` with
      `weight(1f)`, so they genuinely fill the strip: the dead area is the
      `spacedBy(small)` gutters between them and `HUD_MARGIN` at each end, not
      the wide empty band the sentence conjures. A rider does lose those
      slivers, and they lose them invisibly, but "tapping their film in the
      space between two chips" is a few dp wide rather than a region.*

      ***The whole vertical extent of the strip is the real cost***, *and that
      is what a row of narrow windows would not fix either. Which points at the
      better shape: **the strip is already two things in one window** — metric
      chips, which nobody can press, and the controls (stop, volume, the drag
      that re-docks it), which they can. Splitting those into two windows and
      giving the metrics half `FLAG_NOT_TOUCHABLE` is **the pattern already in
      this file**: the class timeline is a separate window for exactly this
      reason, and its comment says so —* "a bar the rider can never press should
      never eat a press." *Most of the strip becomes tap-through, and no new
      mechanism is invented.*

      *Two things to know before starting. **A window genuinely cannot have
      holes**: the region APIs that would punch them (`OnComputeInternalInsets
      Listener`, `TOUCHABLE_INSETS_REGION`) are hidden, and a touchable window
      that declines the event drops it rather than passing it down — so the item
      is right that this is windows or nothing. And **it wants the owner's eye
      rather than a session's judgement**, because the split is a layout
      decision on the surface 11.1b.9 already says is not yet beautiful, and it
      cannot be judged on an AVD: `FLAG_SECURE` means a screenshot over playing
      video comes back black, so what a rider loses is only measurable on the
      bike with a film on
- [ ] **11.1b.9** **Revisit the chips as a piece of visual design.** They are
      correct and they are not yet beautiful. Open questions: whether the metric
      accents should hold their colour at low opacity or take a treatment that
      survives any backdrop; whether the chip hairline is doing enough over
      bright scenes; whether the timeline deserves the same silhouette as the
      chips or a deliberately different one; and whether the zone-change flash
      still reads now that it washes chips rather than a whole band
- [x] **11.1b.10** **The grey line across the overlay.** Reported by the owner
      as "a weird grey line on the HUD", and reproduced on the tablet AVD: a
      full-width hairline running edge to edge just below the chips, reading as
      a stray divider rather than as part of anything.
      *It is not a divider — it is the `edge` glow in `HudOverlayMain`, the
      hairline of the current zone's colour that thickens and pulses as an
      interval change approaches. Two things make it read as chrome instead of
      as an alert. **Zone 1's colour is grey**, so during every warm-up and
      recovery block the "accent" is indistinguishable from a rule someone
      drew by accident; and at rest it is `alpha = 0.45` of that, which is
      exactly the weight of a divider. Its comment also says it sits "along the
      very screen edge", which is true only when the overlay is docked Bottom —
      docked Top it is drawn **last**, so it lands on the inside edge, between
      the chips and the film. Candidates, in order: drop the resting alpha to
      nothing so the line exists only when it is saying something (it still
      thickens and pulses on approach, which is the part that earns its place);
      or give a grey zone a non-grey alert colour; or move it to the true screen
      edge for both docks. **This is a design call about an alert, so it is the
      owner's** — it is diagnosed, not decided*

      **Decided, 4 August 2026, from the inbox: *"HUD orange line. There is a
      line that goes across the screen. Can we remove this?"*** Orange rather
      than grey this time, which is the same element seen in a zone above 1 —
      and that it reads as a stray rule in *two* different colours is the answer
      to the "give a grey zone a non-grey alert colour" candidate: the problem
      is not the hue, it is that a hairline drawn edge to edge across a film
      **is** a rule, whatever colour it is. So candidate one: **the resting
      alpha goes to nothing**. The line does not exist while nothing is
      happening, and it still thickens and pulses in the last seconds before an
      interval change, which is the only part of it that was ever earning its
      place — an alert the rider needs no reading for. If the owner wants the
      pulse gone as well, that is a one-line follow-on; removing it entirely
      would leave a silent coach with no peripheral warning at all, which is why
      it is not being removed unasked (see `CueBand` and the countdown chip for
      what else covers the same moment)

      *Done — one alpha, `0.45f` → `0f`, and **observed on the tablet AVD**: the
      overlay docked Top over the launcher with a ride running, and the band
      below the chips is film. Checked on a free ride, where the resting line
      was drawn in the theme's primary rather than a zone colour; it is the same
      single expression on both paths, and the imminent branch is untouched. The
      2 dp of transparent height stays so the strip does not jump when the alert
      thickens it to 6.*

- [ ] **11.1b.11** **Collapsed down a side, the strip is far wider than it needs
      to be — the owner's note, 17 August 2026.** Verbatim: *"Compact mode when
      on left/right needs some work. Should be much more compact width-wise. i.e.
      the play/pause/end buttons should be aligned vertically."* Written the
      morning after 11.1b.4 landed, which makes it the first report on that
      feature from the only person who has watched a film behind it.

      **It is right, and the cause is one constant doing two jobs.**
      `HudDock.VERTICAL_WIDTH_DP` is 244 dp and the window is that wide in
      **both** states, because the previous sitting fixed the width for a good
      reason — a wrapped width would make the film a rider loses depend on
      whether this block prescribes a three-digit target — and then applied that
      one number to a state the reason does not cover. Collapsed, the widest
      thing on the strip is the transport row: three 52 dp buttons side by side
      is 172 dp, and *that* is what has been setting the width of a strip whose
      whole purpose is to give the screen back.

      **The owner has named the fix as well as the fault**, and the two halves
      go together: stack the controls and the width is set by one button rather
      than three, so a second constant for the collapsed state is a genuinely
      narrower strip rather than a tighter squeeze on the same layout. It stays
      a **constant** for 11.1b.4's own reason — nothing about this argues for
      `WRAP_CONTENT`, which is the answer that would put the strip's width back
      in the hands of whatever number is on it.

      Three things it touches beyond the layout. The window has to be resized on
      **collapse**, not only on a dock change, which is the one place the manager
      currently assumes those are the same event. The **timeline bar insets
      itself by the strip's width** (11.1b.7), so it has to inset by whichever
      width is live or it will step aside from a strip that is no longer there.
      And the two states now differ in width by more than a hundred dp, so
      **expanding is a jump** unless something is done about it — worth looking
      at rather than assuming, because the strip is the thing a rider glances at
      while pedalling
- [ ] **11.1b.11a** **What the collapsed column shows is a separate question
      from how wide it is, and it is not answered here.** A narrow strip cannot
      hold `188` and `BPM` side by side four times over, so if the width goes far
      enough the readouts have to stack their units or lose them. **The unit is
      not the thing to drop** — Phase 26 puts the ride surfaces in the small set
      of places a unit belongs, because that is where a measurement is actually
      being read. Written down so that a later squeeze does not quietly take one

### 11.2 What the strip is still missing
- [x] **11.2.1** Resistance, with a prescribed range derived by inverting `PowerModel` at the middle of the cadence target. Shown next to cadence — the two inputs together, then the two outputs. Reports *no* band rather than a clamped percentage when the target is out of the knob's reach at that cadence, because the honest instruction there is "spin faster".
- [ ] **11.2.1a** The resistance band disappears on some Zone 1 intervals for a low-FTP rider: the unloaded curve at 85 rpm already produces more watts than the whole zone allows. That is arguably *true* and worth saying out loud ("you cannot ride this easy at this cadence") rather than saying nothing. Blocked behind **2.2a** (see 2.2a.10) — until this bike is on its own curve it is as likely to be a modelling artefact as a real contradiction, and 2.2.4 has now answered that the shipped curve is 66% out at the median, which makes the artefact reading the likelier of the two.

      ***The symptom this item describes cannot be seen today, and the item
      should not be read as a live report.*** *Checked in the fifty-first
      sitting: **11.7.3 stopped drawing the resistance band anywhere**, so "the
      band disappears on some Zone 1 intervals" is a statement about a thing no
      rider can look at. `RideSnapshot.resistanceTarget` still computes it and
      nothing consumes it — which is exactly the CLAUDE.md note about
      `PowerModel` having two consumers, one of them drawn nowhere. What is left
      of the item is real but is a **modelling** observation rather than a UI
      one: the unloaded curve at 85 rpm already exceeds the whole of Zone 1 for
      a low-FTP rider, and if 2.2a brings the band back that contradiction is
      worth saying out loud rather than swallowing. Left open on those terms,
      still blocked behind 2.2a, and **not** to be picked up as a visible defect.*
- [ ] **11.2.2** Time in zone: a thin stacked bar of how the ride has been spent, for the collapsed strip where the timeline does not fit
- [ ] **11.2.3** A "you are ahead of / behind your usual" line against `leaderboardFor`, which is the one comparison a rider actually acts on mid-ride
- [ ] **11.2.4** Handle a HUD raised while a call or another overlay is on top

### 11.3 Beyond the strip
- [x] **11.3.1** ~~**Landscape layout for the dashboard.**~~ **Stale — there is nothing wrong with it.** Re-checked twice on the real tablet and again on the matching AVD (1920×1080 @ 240 dpi): the FTP card, the Just Ride button and the three action cards fill the width, and the empty right-hand side this item describes does not exist. The original screenshot was almost certainly taken on a wrongly-configured AVD, which is exactly the trap `HARDWARE.md` was written to close. The profile selector *did* have the problem and is fixed in 20.1
- [x] **11.3.2** ~~Post-ride charts: power with zone bands, heart rate, cadence
      distribution (8.11.53–8.11.57)~~ **Built by 12.6.1 and unticked for
      several sittings.** `RideChartsSection` was extracted out of ride detail
      into `ui/components` precisely so the summary would get every chart the
      history screen has, and it did: Power with its zone bands, Heart rate,
      Cadence over time and Cadence spread, all under the effort question.
      *Observed on the tablet AVD in the forty-ninth sitting, on the summary of
      a ride that had just been ended* — which is the point of the item rather
      than of the code: nothing was missing, and nobody had looked

- [x] **11.3.3** ~~Time-in-zone summary on the post-ride screen~~ **Same
      extraction, same sitting, and now two cards rather than one**: `Time in
      zone` and `Time in heart-rate zone` side by side (21.4.1), each with its
      seconds and its percentage per zone. Observed on the AVD at the foot of
      the same summary. **The pair is worth keeping in that order** — power's
      count is the ride and the heart's is what the strap heard, and 21.4.1's
      caption is the only thing that says so

- [ ] **11.3.4** Skip or extend the current interval mid-ride, for a rider who
      needs to take a call

      ***Triaged in the fifty-fourth sitting, and the one line turns out to be
      two different requests with two different answers.***

      ***The stated motivation is already served, and by the thing built for
      it.*** *A rider who needs to take a call pauses — the ride screen's own
      control (11.6.6), auto-pause underneath it (19.1.1), and since 19.1.2b the
      seconds they were away are out of the averages rather than dragging them
      down. The class clock stops with them. Nothing about a phone call needs an
      interval skipped, so if this item is only its own sentence it is closed.*

      ***What is left is a real feature the item never argues for, and it has a
      consequence nobody has written down.*** `prescribedPlan` *maps the class's
      intervals onto the ride by **second** —* `it.startSec until end` *against
      the sample timestamps — so a block skipped or stretched mid-ride slides
      every later block out of register with the seconds the rider actually
      pedalled. The post-ride prescription chart, its compliance percentage and
      **21.6.3's whole verdict** (*"harder than the class asked"*) would then be
      judged against a plan the rider deliberately departed from, and every one
      of them would still be phrased as a fact about their riding. That is the
      same family as 7.8 — a claim derived on read from a source that has since
      moved — arriving from the ride's own side rather than the profile's.*

      ***So it is not buildable as a control alone.*** *Either the departure is
      **recorded on the ride** the way 8.3d records a resume — in which case
      read 8.3d.4 first, because `WorkoutSession` must carry it or the finalise
      writes the default back over it twenty minutes later — or the three
      readers above have to withdraw their claims for a ride that was edited,
      the way 23.4.3 withdraws the compliance percentage for a trimmed one.
      **Withdrawing is the cheaper half and is the honest minimum**: a ride
      whose plan was changed cannot be scored against the plan.*

      *Left open at that, deliberately: the mechanism is now written down, and
      what is missing is whether the owner wants the control at all. It is worth
      asking as* "should a class be editable while you ride it?" *rather than as
      a HUD button.*

- [x] **11.3.5** ~~Screen-on lock during a ride, so the tablet does not sleep
      mid-class~~ **Built on both surfaces and unticked**: `RideScreen` sets
      `view.keepScreenOn` for as long as the ride is unfinished and clears it in
      `onDispose`, and `HudOverlayManager` puts `FLAG_KEEP_SCREEN_ON` on the
      overlay window — which is the case the ride screen cannot cover, because
      the whole point of the overlay is that something else is on top.

      ***Measured rather than reasoned about, in the forty-ninth sitting, with
      the state before and after as its own controls.*** `dumpsys power` on the
      dashboard: `Wake Locks: size=0`. During a free ride: one
      `SCREEN_BRIGHT_WAKE_LOCK` held by `WindowManager/displayId:0` with
      `ws=WorkSource{… com.pelonot}`, and `dumpsys window` naming
      `mHoldScreenWindow=Window{… com.pelonot/com.pelonot.MainActivity}`. The
      moment the ride ended: `size=0` and `mHoldScreenWindow=null`. **The
      release is half the item** — a lock the app forgets to drop is a tablet
      that never sleeps again, which is a worse defect than the one being
      fixed and would look like nothing at all

### 11.4 Re-home the leaderboard
- [x] **11.4.1** Done as **24.1.2**: the household board is on the post-ride summary and on class detail
- [ ] **11.4.2** A single-line "vs your best" on the ride screen (not the HUD)

### 11.5 Volume control — the tablet has nowhere else to change it

**The reason this is not a nicety.** On the bike's tablet there is no status
bar to pull down, so there is **no system volume UI at all**. A rider watching
Netflix with the coach speaking over it has no way to change either level
without leaving what they are doing. The app is the only surface that can
offer it, which makes this closer to fundamental than to polish.

- [x] **11.5.1** **Media volume**, controlling `STREAM_MUSIC` — which is what
      Netflix and everything else plays on. Needs `MODIFY_AUDIO_SETTINGS` in
      the manifest (a normal, install-time permission, no prompt). Declare it
      *before* wiring the slider: an undeclared permission fails silently and
      this project has shipped that bug twice already (8.5, 2.3). *Observed:
      dragged to 73% and `dumpsys audio` moved `STREAM_MUSIC` from 5 to 11 of
      15 — checked against the system's own value rather than the slider's
      position, which is the whole point of 11.5.7*
- [x] **11.5.2** **Coach volume, independent of the media volume.** Do this
      with `TextToSpeech.Engine.KEY_PARAM_VOLUME` in the `Bundle` passed to
      `speak()` — a per-utterance 0..1 scalar — rather than by moving a stream
      volume. A stream-level control would fight the ducking in 11.1.6 and
      could not make the coach quieter *than* the film, which is exactly what
      a rider who finds it shouty will want. A level of 0 returns *before*
      taking audio focus, so a silenced coach cannot duck the film for the
      length of an utterance nobody can hear. **Wired and persisted, not yet
      heard**: the emulator has no TTS engine worth trusting and the tablet's
      `com.onepeloton.tts` is the only one this ever runs against, so whether
      50% actually sounds like half needs the bike
- [x] **11.5.3** Both in **Settings**, as the place they are set deliberately
- [x] **11.5.4** Both reachable from the **HUD**, since mid-ride is when a
      rider actually discovers the film is too loud, and going to Settings
      means abandoning the ride screen and the film together. *Observed: the
      button opens both sliders inside the strip, already showing the levels
      set in Settings*
- [x] **11.5.5** **This is the deliberate exception to 18.6 / 19.4** — "nothing
      on the strip that is not about the next sixty seconds of pedalling". It
      earns its place only because the tablet offers no alternative. Keep it
      out of the resting strip: put it behind the collapse/expand (11.1.3) or
      a single small control that opens the sliders, so the default HUD is
      still three big numbers and a countdown. *One tonal button among the ride
      controls; the resting strip is unchanged*
- [x] **11.5.6** Volume changes persist, and the coach level survives a
      restart. A rider who turned the coach down did not mean "until the next
      ride". *`coach_volume` in the DataStore; the media level is the system's
      own and the system already remembers it.* The HUD and Settings write the
      same preference, so they are one setting rather than two that drift
- [x] **11.5.7** Setting a stream volume can throw `SecurityException` when a
      Do Not Disturb policy is active on API 23+. Catch it and say so rather
      than letting the slider move and nothing happen — a control that lies
      about having worked is worse than one that is absent. `VolumeController`
      also **reads the level back after every write** instead of trusting it:
      the system clamps and rounds to its own step count, and the slider must
      show where the volume actually is
- [ ] **11.5.8** Volume keys: the owner reports **no physical rocker**, and the
      driver picture in `HARDWARE.md` is consistent with that — the only devices
      declaring `KEY_VOLUMEUP` are the headphone jack (`ACCDET`, inline remote
      only) and the MediaTek keypad driver, which declares the capability
      whether or not buttons are populated. **Settle it with `adb shell getevent
      -l` and a press of every physical button**; ten seconds with someone at
      the bike. Honour the keys if they arrive, but nothing may depend on them
- [x] **11.5.9** **A gesture to dismiss the expanded volume panel.** It opens
      and closes from one small button today (11.5.4), so a rider who opened it
      mid-ride has to find that same control again with the sliders now in the
      way. A swipe on the panel towards the strip's own edge should close it —
      *towards the edge*, so the direction follows the dock rather than being
      hardcoded down (11.1.4). Two traps: the strip already carries drag-to-move
      and drag-to-re-dock on the same surface (4.4, 11.1.4), so an ambiguous
      swipe must not both close the panel and move the strip; and a slider is a
      horizontal drag consumer sitting inside whatever gesture this adds, so the
      dismiss has to be on the panel's own chrome or clearly vertical. A timeout
      that closes it after a few idle seconds is worth considering alongside,
      since the panel is the one part of the strip that is not about the next
      sixty seconds of pedalling (11.5.5)
      *Done, and both halves of it: a **vertical** drag towards the dock's own
      edge closes the panel, and it closes itself after eight idle seconds. The
      timeout is keyed on the two volumes, so every adjustment restarts the
      clock and it only fires once the rider has actually stopped fiddling.*
      *Both traps the item names turned out to be real and both are handled by
      the same choice — the gesture is vertical-only and lives on the panel
      rather than on the strip. A `Slider` consumes horizontal drags, so a
      vertical detector above it never fights the sliders for the same finger;
      and because the strip's own drag-to-re-dock is a different surface, a
      swipe that closes the panel cannot also move the strip. **Observed on the
      tablet AVD** docked top: swiped up, the panel closed and the strip stayed
      exactly where it was; then left alone, it closed itself*

### 11.6 The full-screen ride screen — what a rider cannot read on it

The strip gets the attention in this phase because that is where the ride is
watched from. But the ride screen is where a rider looks when they want to
actually *read* something — before the class starts, on a recovery block, when
the film is paused — and everything below came from riding with it in front of
them. All of it is emulator-checkable at 1920 × 1080 / 240 dpi.

*(Note 5.4 says the removed leaderboard panel is "tracked as 11.6"; that work
is **11.4**, and the cross-reference in 5.4 is stale.)*

- [x] **11.6.1** **"Up next" belongs directly under the current interval, not
      across the screen from it.** In landscape the ride screen is three
      columns: `EffortColumn` on the left holds the current interval, and
      `UpNextColumn` on the right holds what is coming, with the whole metric
      grid between them. The two things a rider reads *together* — what I am
      doing, and what I have to be ready for — are at opposite ends of a
      1280 dp-wide screen, and nothing on screen says they are related. Put the
      next interval immediately beneath the current one. This is a re-layout of
      both columns rather than a move of one composable: the right column also
      carries pause, end and back-to-HUD, and those stay where a thumb expects
      them. `UpcomingIntervals` (the rest of the class beyond the next block)
      is a separate question — it can stay on the right, or fold into the
      timeline at the top, which already draws the same information.
      *Done as written. `NextUpBlock` — the preview and the five-second
      countdown it swaps to — hangs off the bottom of `EffortColumn`, directly
      under the current interval card. `UpcomingIntervals` ("THEN") stayed on
      the right with pause, end and the overlay button, which stay put because
      a thumb has learned where End ride is. **Observed on the tablet AVD**:
      "NEXT in 03:53 · ENDURANCE · 85–95 rpm" sitting under "INTERVAL 1 OF 7"*
- [x] **11.6.2** **Which power zone is the rider in *right now*.** The screen
      shows the *prescribed* zone large and unmissable — that is what the
      `ProgressArc` and `ZoneGlyph` in the interval card are — and never says
      which zone the current power actually falls in. The rider learns they are
      off target from an amber number and an arrow, which says "wrong" without
      saying "you are in 3 and you were asked for 4". `PowerZone.forPower`
      already computes it. Three things to decide rather than assume: a free
      ride has no target but a current zone is still meaningful and should
      probably show; the current and target zone must be tellable apart at a
      glance and not two identical badges side by side; and the HUD has exactly
      the same gap, so whatever is designed here should be shrinkable to the
      strip.
      *`CurrentZoneBar` — "NOW  Z2  ENDURANCE … ASKED FOR Z1", amber when the
      two disagree, "ON TARGET" when they do not. Three decisions worth
      recording. It sits **over the metric grid**, not beside the prescribed
      glyph: the zone a rider is in is a reading of their live power, and the
      "asked for" clause travels with it, so the comparison does not need the
      two badges to be adjacent. It is a strip of words against a glyph with a
      shape per zone, so they cannot be confused. And it renders on a free
      ride, where nothing is prescribed.*
      *The find while building it: `RideUiState.currentZone` had to become
      **nullable**. `PowerZone.forPower` answers Z1 for zero watts and for an
      unknown FTP — true, and useless — so a bike nobody is pedalling, or a
      board that has gone quiet, was about to be labelled "Active Recovery".
      Same family as 2.4.4: the absence is the answer. **Observed on the
      tablet AVD** mid-class, both agreeing and disagreeing.*
      *Superseded by **11.6.2a**: the bar has been replaced by the ladder, on
      both surfaces, and the overlay gap this item left is closed with it.*
- [x] **11.6.2a** **Draw the zones as a scale, not as a sentence.** Raised by
      the owner against the 11.6.2 bar above, with a photo of Peloton's own
      indicator: **seven segments in a row, one per zone**, the rider's current
      zone lit, the boundaries labelled in watts underneath (`0 · 123 · 167 ·
      200 · 233 · 266 · 333`), the zone number set large beside it and FTP %
      at the other end. Not a request to copy it — a request for what it does
      better, which is worth naming precisely:
      - **The whole range is on screen at once.** "Z2" tells a rider where they
        are only if they already hold the ladder in their head. A scale shows
        it, and shows how far along the zone they are — Z3-and-just-in is a
        different ride from Z3-nearly-out, and the current bar cannot tell them
        apart.
      - **The boundaries are in watts.** That turns "you are in 2" into "215 W
        gets you into 3", which is an instruction rather than a label. The app
        already has these numbers: `PowerZone.powerRange(ftp)`.
      - **It absorbs the prescribed zone too.** The band the class is asking
        for can be marked on the same scale, which is exactly the comparison
        11.6.2 exists to make — and would let the prescribed glyph go back to
        being decoration rather than the only statement of the target.
      Things to settle rather than assume: what the scale does on a free ride
      (probably the same, minus the prescribed band); whether the watt labels
      survive being shrunk to the overlay (11.6.2 asked the same question and
      the honest answer may be "numbers on the ride screen, segments only on
      the overlay"); and that every watt figure here is FTP-derived, so it
      moves under the rider when auto-FTP accepts a breakthrough (7.8). It
      replaces `CurrentZoneBar` rather than sitting beside it
      *Done, and `CurrentZoneBar` is gone rather than kept beside it.
      `ZoneScale` (`domain/model/`) is pure and tested — boundaries, the
      fraction through the current zone, FTP %, and the watts that reach the
      next rung — and `PowerZoneScale` (`ui/components/`) draws it: zone digit
      large on the left, seven segments, the watts under each one, FTP % on the
      right. The prescribed zone is an outline on its own segment, so "where I
      am" and "where I was asked to be" are one comparison across one object.*
      *The three questions it said to settle, answered by building it. **A free
      ride draws the same ladder** with nothing outlined — the boundaries do not
      depend on a class. **The watt labels do not survive the overlay**, as
      suspected: `compact` drops them and the FTP %, leaving segments and the
      digit, which is what a rider glancing past a film is asking for anyway.
      And **the segments are equal widths, not proportional to watts** — Z7 is
      unbounded and Z1 spans 56% of FTP alone, so a true scale would draw six
      zones as slivers beside two slabs; the watts underneath carry the real
      proportions.*
      *The one structural gain beyond the drawing: `ZoneScale.currentZone` is
      now the app's **single** rule for "is there a zone at all" — no FTP, no
      power, or a stalled board means none — where 11.6.2 had left that rule
      living on `RideUiState` alone, with the overlay free to answer
      differently. 274 JVM tests. **Observed on the tablet AVD**: mid-class in
      Z2 against a prescribed Z1, both marked on the ladder at once, and the
      compact form on the overlay over another app*
- [x] **11.6.3** **Iconography on the live numbers** — a heart for bpm, and the
      same for cadence, resistance and power. The label is `labelSmall` under a
      104 sp number, which makes the only thing identifying the number the
      smallest text on the tile, read from a metre away mid-effort. An icon is
      recognised faster than a word is read. Keep the text label *beside* it
      rather than replacing it — a bare glyph for "resistance" is not something
      anyone recognises unaided — and give the icon `contentDescription = null`,
      because `MetricReadout` already sets a `clearAndSetSemantics` description
      for the whole tile and a labelled icon inside it would be announced twice.
      Same treatment for `SmallStat` (output, distance, avg power) and for the
      HUD's compact readouts.
      *Done, and defined once in `MetricIcons` so the ride screen and the
      overlay cannot drift apart: revolutions for cadence, the knob for
      resistance, a bolt for power, a heart for bpm, a flame for output and a
      rule for distance. `contentDescription = null` on every one, as the item
      asks. **Observed on the tablet AVD** on all four tiles and all three
      totals*
- [x] **11.6.4** **The target gauge does not say what the target is.** This is
      the biggest of these. `TargetGauge` draws a track, a highlighted band and
      the rider's position on it, with **no numbers anywhere** — a rider can see
      they are below the band without ever learning that the interval asks for
      85–95 rpm. `TargetBand` already carries `min` and `max`; show them.
      Prominently on the ride screen, where there is room for "85–95" set large
      next to or under the live value. The HUD strip is a different problem with
      a different amount of space and should be decided separately rather than
      by shrinking one design until it fits both. Two details that will bite:
      the band needs its unit stated once or "85–95" beside a resistance tile is
      ambiguous, and a *missing* band (11.2.1 deliberately reports none when the
      target is out of the knob's reach) must not render as "0–0".
      *`TargetBand.label` rounds to whole units and returns **null**, never
      "0–0", when nothing is prescribed — and null is also what an unreachable
      resistance target gives, so the app never invents an instruction it has
      just decided it cannot give. The ride screen prints "TARGET 80–90 rpm"
      under the gauge with the unit repeated; the overlay does not, and that is
      the item's own instruction not to shrink one design until it fits both.
      The screen reader gets the band on **both**, since the reason for hiding
      it is width and a reader has none. **Observed on the tablet AVD**:
      "TARGET 80–90 rpm" under cadence, "TARGET 0–80 watts" under power, and
      the resistance tile correctly showing no target line at all*
- [x] **11.6.5** **"Back to the HUD" is the wrong label, twice over.** It is
      jargon — "HUD" is a word this project's authors use and a rider does not
      — and it is factually wrong: "back" implies the rider has been there, and
      most of the time they have not been anywhere yet. What the button actually
      does is `moveTaskToBack`: it puts the app away and leaves the strip on top
      of whatever they were watching. Candidates, best first: **"Minimise to the
      strip"**, "Hide the app, keep riding", "Back to my film". The string is
      `R.string.ride_back_to_hud`. Note the same jargon is in the ride screen's
      HUD prompt ("Don't use the HUD") and in Settings, so pick the rider-facing
      word for this thing **once** and change it everywhere, or the app will
      have two names for one feature. This revises copy that 11.1a.2 ticked; the
      behaviour it describes is right and only the label is wrong.
      *Done, and the name is the owner's: **"overlay"**, not "strip", which was
      this session's first answer and was rejected. The button reads **"View in
      Overlay Mode"**. The word was in six rider-facing places and all six
      moved together — the button, the permission prompt's "Don't use the
      overlay", the Settings section, the opacity slider's spoken label, the
      drag handle's, and the Silent coach style's description. **"Overlay" is
      now the rider-facing name for this thing and nothing user-visible may say
      "HUD" or "strip".** The code, this plan and `ARCHITECTURE.md` still say
      HUD internally, which is fine — it is one name in the source and one name
      on screen. **Observed on the tablet AVD***
- [x] **11.6.6** **Ending a ride takes one tap and cannot be undone.** The end
      button is a 72 dp pill at the bottom of the right-hand column, directly
      under pause, pressed with sweaty hands while moving; the HUD's stop is the
      same. There is no resume — `stopWorkout` finalises the row, tears down the
      overlay and stops the service — so a mis-tap at minute 20 of a 45-minute
      class ends the class. The ride itself survives, which is why this is not
      a data-loss item; what it destroys is the remaining twenty-five minutes.
      Confirm it, in the same weight as 12.3.2 and 11.1a.4. Two things to get
      right: the confirmation must be **dismissible by a tap anywhere**, because
      it is raised mid-effort and the common case is "I did not mean that", and
      it must not appear when the class timer ends the ride by itself.
      *Both surfaces, and they had to be answered differently. The ride screen
      gets a dialog naming the elapsed time and what is left of the class —
      that second number is the one the rider does not have in their head
      mid-effort — dismissible by tapping anywhere. **The strip cannot raise a
      dialog at all**: it is `FLAG_NOT_FOCUSABLE` by design, which is the whole
      reason the film keeps focus, so the button asks for itself — first tap
      turns it into "END?", second answers it, four seconds of silence is also
      an answer. Asked in the UI and not the service, so a class that runs out
      of intervals still ends by itself with nobody there to answer. One thing
      found only by looking: an `IconButton` sizes to a 52 dp circle whatever
      is inside it, so the word wrapped to "EN / D?" — it swaps to a pill
      button rather than restyling the icon one. **Observed on the tablet AVD**:
      Keep riding returns to a still-running ride; on the strip, one tap leaves
      the service up, the button reverts on its own, and two taps end it*

- [x] **11.6.11** **The zone ladder recoils at every boundary.** The owner,
      verbatim: *"It looks really good but the transition between zones is still
      not right. There is an elastic bounce on each zone which makes it erratic.
      The flow from 3 to 4 and then back to 3 should be seamless, almost like
      there is just one progress bar that smoothly goes from zone 1 to 7. It's
      almost there!"*

      **The diagnosis is not the animation, it is the quantity being animated.**
      `ZoneScale.fractionThroughZone` is a *per-zone* number — how far through
      *this* rung the rider is — and it is what `PowerZoneScale` springs. So
      crossing out of Z3 takes it from ~1.0 to ~0.0 and the spring drives the
      fill **backwards across the full width of a segment** before growing again
      inside the next one. The rider gets a recoil at the exact moment they
      wanted confirmation they had arrived. Nothing about the value is wrong;
      the ladder is holding **two coordinates** (which rung, how far up it)
      where the rider reads **one** — how hard am I going.

      **The fix is a single continuous coordinate.** Position on the whole
      ladder, 0..1 across all seven rungs, put on `ZoneScale` so it is pure and
      JVM-tested rather than argued about from a screenshot; the segments are
      drawn at equal weight, so it is `(zone.ordinal + fractionThroughZone) / 7`
      and each segment fills by `position × 7 − ordinal` clamped to 0..1.
      Animate **that** and a boundary stops being an event at all: the fill
      leaves one rung and enters the next at the same speed it was already
      travelling. The invariant worth holding in a test is that it is
      **monotonic in power** — more watts never moves the bar backwards — which
      is exactly the property the current build violates twice per boundary.

      Two things it must not break. It becomes one bar filled from the bottom,
      so the rungs *below* the rider fill in their own colour and the ladder
      reads as a ramp — which is what the owner is describing. And **absence
      still lights nothing**: `current == null` is 2.4.4's rule (a dead board or
      a rider who has stopped is not Active Recovery), so the bar drains to zero
      rather than freezing where it was

- [x] **11.6.12** **Watts are whole numbers, everywhere in the UI.** The owner,
      verbatim: *"On the ride screen it has a decimal place but gets cut off.
      I'm making a call — make this number an integer! Well, not in the
      database, but in the UI. The user never (across the app) wants to see
      decimal places for watts."*

      **The call is made and is not to be re-derived.** Note what it does *not*
      touch: 14.4.6 settled that the board's fractional power is real data and
      worth keeping, so this is a display rule and must not reach the recorder,
      the payload or `PowerModel`.

      The live tiles already round — cadence, resistance, power and avg power
      are all `.toInt()` on both surfaces, checked. **The number the owner is
      looking at is OUTPUT**, the first of the three totals under the clock: it
      is drawn `"%.1f"` and labelled kJ, and it is the one thing on the ride
      screen in the watt family carrying a tenth. It is also the one that
      clips — `SmallStat` weights the value `fill = false`, so at 2.7 kJ it fits
      and at 254.9 kJ it eats the "kJ" beside it, which is the *same* defect the
      distance tile already has a comment about ("0.20" clipping "mi" to "m").
      A tenth of a kilojoule is 0.04% of a class and nobody has ever acted on
      it. Drop it, and take the rule across the app rather than patching one
      screen: whatever formats a watt or a kilojoule for a rider rounds it

      *Done, and the snag was **measured rather than reasoned about**: the tile
      was rendering `63.` on the AVD — two digits, a decimal point, and the tenth
      clipped clean off — which is the owner's sentence exactly.
      `Formatters.kilojoules` rounds, `kilojoulesValue` is the unit-less form the
      ride screen and the two dashboard cards use, and `FormattersTest` states
      the rule. One decimal survives on purpose: kJ/kg on the household board,
      where whole numbers would tie two housemates who are genuinely apart.*

- [x] **11.6.13** **A countdown before the ride starts.** The owner, verbatim:
      *"After clicking 'Start ride' you're straight into it. For some reason it
      feels wrong. Please add a countdown. Could be 5 or 10 seconds, whatever
      you feel is best. Could even have a 'skip' button for the impatient among
      us (your call)."*

      **The feeling is right and it has a cause the plan can name.** A class
      starts its first interval and its clock on the same tick as the tap, so
      the rider is already *behind* a Z1 target while still reaching for the
      handlebars — and the first ten seconds of every recorded ride are
      therefore a rider getting on a bike, filed as riding. It is small, but it
      is the same family as everything else here: the record says a thing the
      rider did not do.

      **Ten seconds, skippable, and the ride starts when it ends.** Ten rather
      than five because the job is getting feet into cages and a film started,
      which five does not cover; skippable because the second ride of the day
      does not need it and a countdown nobody can escape is a worse feeling than
      the one being fixed. The thing to get right is **where it sits**: it must
      be *before* `startRide`, not a curtain over a ride already running, or the
      clock, the first interval and the recorder all start behind it and the
      countdown has simply moved the defect. That makes it the last step of the
      pre-ride prompt rather than the first frame of `RideScreen`.

      It is also the natural home for what the rider needs before the pedals
      turn — the class title, the first target — but that is a second item if it
      earns one; this one is the beat itself

      *Done, and it earned the second half straight away: the screen carries the
      class title, a draining arc around the count, and **FIRST UP · Z1 · Active
      Recovery · 75–85 rpm**, which is the one thing worth knowing before the
      pedals turn and had nowhere else to be said at that size. `RideScreen`
      returns early until the countdown clears, so `startRide` is genuinely not
      reached — **observed on the AVD**: the ride came up at interval 1 of 13
      with 01:36 left of a 2:00 block twenty-four seconds in, so the countdown
      cost the record nothing. A resume skips it outright. `Start now` sets the
      count to zero and the effect is keyed on the count, so it cancels the
      second already in flight rather than waiting for it.*
- [x] **11.6.14** **The overlay permission lands on the wrong side of the
      countdown.** The owner's note, 4 August 2026, verbatim: *"First ride — you
      get the countdown timer of 10 seconds, very exciting! But then when it
      gets to 0 it asks you to allow to show over other apps. This should happen
      DURING the countdown (and pause the countdown while you go away and do
      it)."*

      **It is 11.6.13's own defect, one layer up.** That item's whole argument
      was that a ride must not start while the rider is still reaching for the
      handlebars — and the permission dialog is raised by `checkOverlayPermission`
      inside `startRide`, which now runs the instant the count hits zero. So the
      ten seconds the rider spent getting clipped in buy them a modal, a trip to
      the Android settings app, and a return to a class whose clock has been
      running the whole time. The first ride anybody ever takes is the one that
      hits it, because that is the only ride the permission has not been granted
      on.

      **Ask while the count is running, and stop the count while the answer is
      elsewhere.** The check is cheap (`Settings.canDrawOverlays`) and needs no
      service, so it can happen the moment the countdown appears. Two things to
      get right, and both are about the count rather than the dialog:
      - **The countdown pauses whenever the question is outstanding** — while
        the dialog is up *and* while the rider is away in the system settings
        screen. Coming back to `2` and then to a ride they have not sat down for
        is the same defect wearing the other costume.
      - **The countdown branch has no lifecycle observer.** `RideScreen`'s
        `DisposableEffect` on `ON_START`/`ON_STOP` sits below the early return,
        so nothing currently re-reads the permission when the rider comes back
        from granting it. The countdown needs its own resume hook, or the
        dialog is still up over a granted permission

      *Done and observed on the tablet AVD, and the measurement is the whole
      check: the prompt came up over **Just Ride · Get set** with the count
      showing 10, the rider (me) went out to Android's *Display over other
      apps*, walked into Pelonot's own page, turned the switch on and came back
      — **ninety seconds later, and the count was still 10**. Uninterrupted it
      would have started the ride nine times over. It then ran 10 → 0 and the
      ride began, with no second prompt.*

      *Two things the implementation needed that the item did not predict.
      **`requestOverlayPermission` was calling `dismissOverlayPrompt`**, so the
      question was marked answered at the moment the rider left to answer it —
      which would have restarted the count the instant they were sent away, the
      exact defect the owner asked to fix. Hence `awaitingOverlayGrant` beside
      `overlayPermissionNeeded`: the dialog closing and the question being
      answered are different events. And the `LaunchedEffect` is keyed on
      `paused` as well as on the count, so the second in flight is **cancelled**
      rather than allowed to land, and starts whole on the way back. Rounding
      the rider's way is the right rounding on a beat that exists to let
      somebody get onto a bike.*
- [x] **11.6.15** **`Don't use the overlay` is answered once and asked for
      ever.** Not the owner's note, found while reading for 11.6.14 and left as
      its own item because it is a different failure: *Not now* clears the flag
      for this ride only, so the prompt returns on the next one, which is right.
      *Don't use the overlay* writes `hudEnabled = false` — and there is nothing
      on any screen that says the overlay is off or offers it back except the
      Settings row that turned it on. A rider who taps the wrong button on their
      first ride loses the app's primary surface silently. Small; check what
      Settings already says before writing anything new

      *Done, and the item's own last sentence was the right instruction:
      **Settings already says it**. `RideHudSection` is headed **Ride overlay**
      and its first row is a toggle reading "Show the ride overlay over other
      apps", which is both the way back and the same name the dialog uses
      (11.6.5). So nothing new was written. The whole gap was that a rider
      standing in front of three buttons had no way of knowing one of them was
      permanent and reversible somewhere else, and the fix is one sentence at the
      moment of the decision — "Either way you can change it later in Settings,
      under Ride overlay." It covers all three buttons rather than the one,
      which is why it is in the body and not on a button.*

      ***A nag afterwards was the obvious fix and is the mirror of this same
      defect.** A line on the ride screen saying the overlay is off would follow
      a rider who deliberately turned it off for ever, which is exactly the
      complaint here in the other direction. Told once, at the moment they
      choose, and then believed.*

      *Watched on the tablet AVD, and the round trip is the point rather than
      the dialog: the sentence was read on screen over a paused countdown, `Don't
      use the overlay` tapped, and **Settings → Ride overlay** then found with
      the toggle off — so the claim the dialog makes is true, which is the
      standard this project holds a sentence to.*

      *One thing found on the way and deliberately not built: **`hudEnabled` is
      a device-wide DataStore preference, not a per-profile one**, so one rider
      declining the overlay in that modal turns it off for the whole household.
      Defensible — the units and the telemetry source are device-wide too, and
      an overlay is arguably a property of the screen rather than of the rider —
      but worth knowing that it is the only one of the three a rider can switch
      off from inside a modal they did not go looking for. If it ever needs
      changing it is 20.x work rather than 11.x: the question is which
      preferences belong to a profile, and answering it for one is answering it
      for none.*

- [x] **11.6.16** **The countdown grows, and pushes the totals off the bottom
      of the screen.** The owner's note, 5 August 2026, verbatim: *"When the
      'next' section is counting down, it gets a bit bigger. No problem with
      this but it bumps the cards underneath it down, and actually off screen.
      Perhaps the 'next' card can shrink to accomodate it and the bottom
      section (output, distance, power) can then remain entirely static."*

      **The mechanism is known and the note names the fix.** `NextUpBlock` is
      an `AnimatedContent` that swaps `NextUpPreview` for `CountdownBanner` in
      the last seconds of a block (11.6.13's countdown, applied to interval
      changes), and the two are not the same height. The effort column is a
      plain `Column` with a `Spacer(weight(1f))` in it: while there is slack
      the spacer absorbs the difference and nothing moves, and the moment there
      is not, the growth comes off the **bottom** — silently, because a Column
      clips rather than complaining. So the totals row disappears for a few
      seconds at every interval boundary, which is both the least stable
      moment on the screen and the one where the rider is least able to work
      out what happened.

      Three things worth knowing before picking it up:
      - **24.3.13b made this both better and worse**, and it is worth being
        honest about which. Moving *then* into that column took the slack
        away, so this bites at more moments than it used to; moving the
        leaderboard *out* of it gave back more than that. The note arrived
        while looking at the result, which is the sequence that finds these.
      - **The owner's suggested fix is the right shape and it generalises.**
        The problem is not the countdown, it is that *any* growth above the
        totals is paid for by the totals. Reserving the height of the taller
        of the two states inside `NextUpBlock` fixes it once, for both
        directions, and costs a few dp of white space the rest of the time —
        which is 11.6.8's own trade (*reserve the widest string*) turned
        ninety degrees.
      - **The bottom row has to be the fixed point.** OUTPUT, DISTANCE and AVG
        POWER are the numbers a rider looks for without looking, and a row
        that is sometimes there is worse than a row that is smaller.

- [x] **11.6.17 The totals row overflows when the numbers get big.** The
      owner's note, 5 August 2026, verbatim: *"The power, distance, output
      section looks brilliant but i fear it will overflow badly when the numbers
      get large. Please think about this."*

      **It is the same tile that was rendering `63.` in 11.6.12, and the
      failure is the same one: it clips in silence.** `SmallStat` draws its
      value at a fixed 34 sp with `maxLines = 1` and no `overflow`, inside a
      third of the effort column — 360 dp at 1280 dp wide, so roughly 113 dp a
      tile before padding. The value is `weight(1f, fill = false)`, which stops
      it from squeezing the unit label off the tile (11.6.12's fix) but does
      nothing about the digits themselves: past the tile's width the glyphs are
      simply cut off, and a `1080` that renders as `108` is not obviously
      wrong.

      **The numbers that get there are ordinary, not extreme.** OUTPUT is the
      one to design for: an hour at 300 W is 1080 kJ, and the owner's own rides
      are already in three figures. AVG POWER is three digits for anybody and
      DISTANCE is four characters with its decimal point.

      The fix is to let the number shrink to fit rather than be cut — the same
      trade as 11.6.8 and 11.6.16, where reserving or scaling costs a little of
      the ordinary case to make the extreme case honest. **Do not fix it by
      dropping digits**: an output rounded to hundreds mid-ride is a different
      number, and 11.6.12 was the last time this tile lied about its own value.
      Check it at four digits on the tablet AVD, not by reading the diff.

- [x] **11.6.18 The rest of the class scrolls.** The owner's note, 5 August
      2026, verbatim: *"The 'next' section -- it could be scrollable tbh. Why
      not!"*

      **Which section it is matters, and it is the *then* list.** There are two
      things it could mean and only one has anything to scroll: `NextUpBlock` is
      the single next effort and is one block, while `UpcomingIntervals` is the
      rest of the class, capped at `max = 3` since 24.3.13b took its column
      away. It is the cap that the note is asking to be lifted — a rider
      wanting to know what is coming at minute 8 of a 45-minute class currently
      sees three blocks and no way to see the fourth.

      **It is also 11.6.16's fix, and the two should be built together.** The
      effort column is a `Column` with a `Spacer(weight(1f))` absorbing the
      slack; give the weight to a **scrolling** upcoming-intervals list instead
      and the column stops having slack that can run out. Anything growing
      above it — the countdown, a position call, a cue banner — is then paid
      for by the list shrinking rather than by the totals falling off the
      bottom. One change answers the note and closes 11.6.16's *"any growth
      above the totals is paid for by the totals"*.

      Two hazards worth naming before it is picked up:
      - **A scroll position that follows the ride, not the finger.** The list
        is a live thing: when the interval changes, the block a rider is
        looking at stops being the next one. It must re-anchor to the current
        interval when the ride moves on, or a rider who scrolled to the end at
        minute 8 is still looking at minute 45 an hour later.
      - **Nested scrolling on a screen with no other scroll.** The ride screen
        does not scroll and must not start: the totals and the metric grid are
        fixed points. Only this list scrolls, inside its own bounded height.

- [x] **11.6.19 Tap the distance to change its units, for this ride only.** The
      owner's note, 5 August 2026, verbatim: *"The 'distance' number. Clicking
      it could switching between imperial and metric (temporarily, not saved in
      settings) ... why not!?"*

      **"Temporarily" is the whole design and it is what makes this cheap.**
      13.5 already delivers the unit system to every surface through
      `LocalUnitSystem` from `PelonotTheme`, so a per-ride override is a
      `CompositionLocalProvider` around the ride content and a `remember`d flag
      — **nothing is written, so 2.4.6's one-writer rule is not engaged at
      all**. Settings stays the single writer of the preference; this is a
      reading aid, in the same family as tapping a chart to change its axis.

      Three things it must not do:
      - **Nothing recorded may change.** 13.4's rule: SI on disk, converted at
        the edge. This changes a label and a division and nothing else.
      - **It must be reversible by the same gesture.** A rider who taps it by
        accident mid-effort taps it again. That also makes it discoverable
        without a hint, which is the only way it can be discovered at all —
        there is no room on that tile for a caption saying it is tappable.
      - **Decide, deliberately, whether the overlay follows.** The HUD is
        composed from the service and reads the same `LocalUnitSystem` from its
        own `PelonotTheme`, so it will **not** see a ride-screen override and
        will keep showing the stored preference. The recommendation is to
        accept that rather than plumb it: the override exists because a rider
        wanted to read one number a different way for a moment, and a
        preference that leaks onto a surface they are not looking at is the
        preference they said not to save.

      Worth noting it is the second thing on that tile now — 11.6.17 is about
      what it *renders* — so the two are one visit to `SmallStat`.

### 11.7 One instruction at a time — what the rider is actually being asked to do

> The owner, verbatim, from the inbox: *"UX-wise it's difficult to know what to
> focus on. It says Zone 2, but then it also prescribes cadence and resistance.
> I think it needs to be one or the other. Please have a think about best UX and
> provide me a HITL suggestion. I love Power Zones and it means it scales to a
> person's fitness. However there is a time and place for prescribing cadence,
> because spin-ups and climbs are very different exercises. Perhaps there's a
> way we can use both? But overall the impression I get when i'm riding is:
> 'what do i do? do i focus on zone, cadence, or resistance?'"*

This is a design question with a decision in it that is the owner's (11.7.2),
so the items below are written to be *read* before anything is built. But three
of the facts underneath it are measurable rather than matters of taste, and
measuring them moves most of the question.

- [x] **11.7.1** **The diagnosis: it is not three targets, it is one outcome
      and two inputs — and the app gives all three the same weight.** Power is
      not something a rider *does*. It is what happens when you turn the pedals
      at some cadence against some resistance: `power = f(cadence, resistance)`,
      which is literally `PowerModel`. So "zone, cadence or resistance" is not a
      choice between three instructions of the same kind. It is one *result*
      and the two *controls* that produce it, drawn as three tiles of equal
      size with equal off-target signalling. The rider's question — "what do I
      do?" — is the correct question and the screen genuinely does not answer
      it.
      Three things were measured across the 72 bundled classes (1071 intervals)
      rather than assumed, and each one narrows the design:
      - **Every interval prescribes both a zone and a cadence band. All 1071.**
        `Interval.cadenceMin` / `cadenceMax` / `powerZoneNumber` are all
        non-null and required by the asset schema. So there is no such thing
        today as a block that asks only for power — the app *cannot* tell a
        cadence instruction from a cadence suggestion, because the data does
        not distinguish them. That is the root of it.
      - **But the catalogue already knows the difference; it just cannot say
        so.** The bands are not uniformly spread. **574 of 1071 blocks sit in
        the two neutral bands** — 461 at 75–85 and 113 at 80–90, a comfortable
        seated cadence that is plainly *not* the exercise — while **231 are out
        in the tails, where they unmistakably are**: 77 at 50–70 (torque and
        climbing; `CLB-01`'s Z4 repeats are 50–60) and 154 at 105–125 (spin-ups
        and sprints). The 15-rpm-wide band exists exactly 99 times and **every
        one of them is a 110–125 sprint**; every other band in the library is
        10 wide. The owner's "time and place for prescribing cadence" is
        already in the data as a *shape*. It is simply not a field.
      - **Resistance is not a prescription at all, and it is the least
        trustworthy number on the screen.** No class prescribes resistance —
        there is no such field. The band comes from inverting `PowerModel` at
        the middle of the cadence target (11.2.1), and `PowerModel`'s shipped
        curve scores **RMSE 137 W and 66% median absolute error** against 310
        measured samples (`calibration/`, 2.2.4). On real hardware the watts are
        *measured* and the cadence is *measured*, so of the three numbers
        competing for the rider's attention mid-effort, **the derived guess is
        the one presented with equal authority**. 11.2.1a is already a
        symptom of this — the band vanishes on Zone 1 for a low-FTP rider
        because the model says the unloaded bike already exceeds the zone.
- [x] **11.7.1a** **A defect falls out of 11.7.1 and is worth fixing whatever
      is decided above.** `MetricStatus.isOffTarget` drives the amber treatment
      in `RideComponents` uniformly across every tile. So during a threshold
      block — where the class wants the watts and the 75–85 band is the
      library's neutral default — a rider spinning a perfectly good 92 rpm is
      shown **amber cadence**: the app telling them they are wrong about
      something it was never really asking for. Amber is the app's strongest
      glanceable signal (8.11.82) and it is currently spent on all three
      metrics equally. Whatever governs, **only the governing metric should be
      able to go amber**; the others are context and should stay in the accent
      colour. This is a small change and it removes most of the felt confusion
      on its own.
- [x] **11.7.2** **The decision that is the owner's: how a block says which
      metric governs it.** The recommendation below is one instruction per
      block, chosen by the block rather than by the rider — because the owner's
      own sentence contains the answer ("a time and place", "spin-ups and
      climbs are very different exercises"), and a global preference would
      throw away exactly the thing they value. What is genuinely open is *how
      the block says it*, and the two routes differ in more than effort:
      - **(a) Derive it.** No schema change: infer the governing metric from the
        cadence band the class already carries — a band at the 80 rpm default
        means power governs, a band in the tails (≤70 or ≥105) or a 15-wide
        sprint band means cadence governs. The measurements in 11.7.1 say this
        would be right most of the time, and it costs nothing to ship.
      - **(b) Name it.** An optional `governed_by` on the interval —
        `"power"` or `"cadence"`, absent meaning power — written in
        `classlibrary/catalogue.py`, emitted by `build.py`, checked by
        `ClassLibraryAssetsTest`.
      **The recommendation is (b), and the reason is this project's own
      history rather than tidiness.** Deriving an intent from a number band is
      exactly the shape that has cost this plan the most: the `avg_*` columns
      derived on read from a source that had since moved, the ride detail chart
      drawing every past ride against the rider's *current* FTP (7.8), the
      zone bands that silently redrew when a breakthrough was accepted. A
      heuristic over cadence has the same failure mode — it is right until a
      class is written that means something the rule does not encode, and then
      it is confidently wrong with nothing to point at. It also cannot express
      the case the owner explicitly asked about, *"perhaps there's a way we can
      use both"*: a block where the class genuinely wants a specific cadence
      **and** a specific zone (a sweet-spot block at 60 rpm is both), which a
      band-width rule can only guess at. And 25.4.2 settled the general form of
      this argument already — **a class must say what it means rather than let
      the reader infer it from a number**.
      The cost of (b) is low and bounded: the field is **optional and
      additive**, so every class written before it decodes unchanged (same
      shape as `target_position` in 25.1), and no id changes — which is the one
      thing 23.2.6 and 25.4.3 say must not happen while a ride points at it.
      Route (a) is not wasted if (b) is chosen: it is the right way to *seed*
      the catalogue field in bulk, then correct by hand.
      **Decided, 4 August 2026: (b).** The owner chose it directly when asked,
      on the recommendation above. Not yet built — it does not jump ahead of
      15.8 in the queue, but 11.7.1a, 11.7.3 and 11.7.4 are now unblocked
      rather than waiting on a design question.
- [x] **11.7.3** **What the ride screen does with the answer.** Sketched, not
      settled — it depends on 11.7.2. The shape that follows from "one outcome,
      two controls":
      - The **governing** metric keeps the full target treatment — the gauge,
        the numeric band (11.6.4), the amber, and the spoken cue.
      - The **other** metrics stay on screen and stay live, because a rider
        does want to know their cadence; they lose the target band and the
        amber and become plain readings.
      - **Resistance loses its band by default**, on the 11.7.1 argument that
        it is a modelled guess dressed as an instruction. It is the natural
        thing to show only when it is the governing control — i.e. on a climb —
        and even then it is a suggestion, which the wording should admit.
        Note this becomes *more* defensible after 2.2a: a bike on its own
        calibrated curve has a resistance band worth reading.
      - `PowerZoneScale` (11.6.2a) stays exactly as it is either way. It is a
        reading of where the rider *is* against the whole ladder, not a
        competing instruction, and it is the thing that makes power scale to
        fitness — which is the property the owner said they love.
- [x] **11.7.4** **And what the overlay does**, which is the harder half and
      why 11.7.3 should not be built without thinking about it. The strip has
      room for one instruction and the ride screen has room for three, so the
      strip is where "one instruction at a time" either works or does not —
      it is the surface the rider actually watches for forty minutes (11.1).
      If 11.7.2 lands, the strip shows the governing metric's target and
      nothing else's, which is the first time it has had a principled answer to
      what it drops when it runs out of width. Read 11.1b.8 and 11.6.2a's
      closing note first: the honest answer there was "numbers on the ride
      screen, segments only on the overlay", and this is the same conversation.

**The owner returned to this from the inbox, 4 August 2026, flagging their own
uncertainty about it** — *"Pretty sure I already mentioned this so it might
already be in the plan."* It is: this is 11.7, word for word the same
complaint ("three targets", "more or less IMPOSSIBLE to hit them all"), and
the new sentence — *"If the target is powerzone, then no resistance target is
required... pedal at the target cadence and adjust resistance yourself"* — is
11.7.3's already-written treatment (resistance loses its band by default,
present only when it is the governing control) restated from the rider's
side rather than the parser's. It does not by itself answer 11.7.2, the one
decision in this item that is the owner's and is still open: derive the
governing metric from the cadence band (a), or name it in the catalogue (b).
The recommendation stands at (b).

**Built in the twenty-ninth sitting, on the owner's own priority** — *"please
address the 'resistance target' vs 'cadence target' vs 'powerzone target'
issue as a priority"*, the third time it had come up. What is worth writing
down is the two decisions that were not simply reading the items back.

**Where the field lives.** 11.7.2 chose route (b) — name it — over route (a),
derive it from the band. The implementation puts governance on the **cadence
intent** in `classlibrary/builder.py`: `GRIND`, `CLIMB`, `SPIN` and `SURGE`
are cadence-governed and the rest are not, with `POWER(x)` and `CADENCE(x)`
overriding at the call site. That is not route (a) wearing route (b)'s
clothes, and the distinction is the whole reason (b) was chosen: an author who
writes `GRIND` **has said what they mean** — this block is about turning a big
gear slowly — and the 50–60 band is a consequence of that intent rather than
its source. A reader inferring "cadence governs" from seeing 50–60 would be
route (a); a reader looking up what `GRIND` *is* is not. It also cost nothing
to seed: 231 blocks of 1071 came out governed by cadence, which is exactly the
231 the tails measurement above found before the field existed.

**And what the non-governing metric keeps.** 11.7.3 was written as *"they lose
the target band and the amber and become plain readings"*. What shipped keeps
the shaded band and drops everything that says the rider is wrong — the amber
value, the amber marker, the arrow, the `TARGET 80–90 rpm` line and the spoken
cue. Two reasons, and the second is the load-bearing one. A rider does want to
know roughly what cadence the class had in mind, and dropping the band entirely
would say the class never mentioned it. And the gauge appearing and
disappearing between blocks changes the tile's height, which is 11.6.8's
family of defect — something on the ride screen moving for a reason the rider
cannot see. The visible consequence is the thing to judge it by: **exactly one
tile on the ride screen carries a `TARGET` line at any moment**, and that is
the answer to *"what do I do?"*.

Resistance took the harder version: **no band at all, on either surface, ever**
— which is the owner's own *"if the target is powerzone, then no resistance
target is required"*. `RideSnapshot.resistanceTarget` is kept and documented
rather than deleted, because 11.7.3 says exactly when it comes back (2.2a, a
bike on its own calibrated curve).

**Four surfaces changed, not two.** Two were not in the items and were found by
driving the flow rather than by reading the diff:
- the **next-up preview** named a cadence for every upcoming block, directly
  under the zone name that was the actual instruction
- the **class detail list** is the one place both halves stay, because it is
  the screen a rider *studies* a class on — but the governing half is bold and
  the other is dimmed, so the instruction is legible before the ride instead of
  discovered during it

**And the voice had 11.7.1a's exact twin, which the items did not name.**
`adviceFor` checked the cadence first and *returned* on it, so on a threshold
block a rider spinning 92 rpm against the library's neutral 75–85 default was
told to ease the cadence back — and the power drift the class actually cared
about **could never be reached at all**, because cadence had already answered.
Same defect as the amber, one channel louder.

---

### 11.8 "What is a power zone?" — the owner's note, 5 August 2026

**Verbatim:** *"I think we need to: Explain what powerzone is to first time
users — and/or — Make it much clearer on the Ride screen that powerzone is what
you're supposed to work on. In lieu of a human trainer, is it worth us
considering having a designated space for 'subtitles'? Mix of motivational text
but also can help explain powerzones and whatnot? This is just an idea off the
top of my head. It could be a bad one."*

**The note names a gap the plan has never had an item for, and it is a large
one.** This app is built on power zones from end to end: the class library
prescribes them, the ride screen colours by them, the leaderboard scores in
kilojoules derived from them, `FtpEstimator` exists to produce the denominator
they are computed against, and Phase 7 moves that denominator by itself. **And
the app never once says what a zone is.** A rider who has come from Peloton's
own classes has met the phrase; a rider who has not is being asked to work at
"Z4 · Threshold" with no way to find out what that means without leaving the
bike. That is not a copy problem, it is a missing feature, and it is exactly the
kind of thing only a first-time rider can report — which is how it arrived.

**11.7 is the near neighbour and it is not the same item.** That phase settled
*which* metric is the instruction on any given block and made the ride screen say
so — one `TARGET` line, one amber, one arrow. It is the answer to *what do I do
right now*. This is the answer to *what is this thing you keep asking me to do*,
and no amount of getting 11.7 right produces it.

- [x] **11.8.1** ***Done, and the suspicion was right.*** Read on the tablet AVD rather than in the source: the ride screen says `Z1 / ACTIVE RECOVERY` beside a colour bar and `50% OF FTP`; Start Class says `20 min · Endurance · a 1 min effort at Tempo`; the countdown says `FIRST UP · Z1 · Active Recovery · 75–85 rpm`. Every one of those *uses* the word and none of them defines it. **Establish what the app says today before writing a word.**
      Read the ride screen, the zone ladder, the class detail screen and
      Settings for every place a zone number or name appears, and write down
      what a rider could learn from them. The suspicion is "nothing, anywhere",
      but this plan has twice written an item for a thing that turned out to
      already exist (11.7 and 21.4.2 both), and the cheap check is reading four
      screens
- [x] **11.8.2** ***Done and observed on the tablet AVD, on a profile with no finished rides.*** **The countdown**, of the three candidates — the rider is clipped in, sitting still, with ten seconds and nothing to do, and the class's first zone is already on screen directly above, so the sentence has something to point at. Two sentences under a `NEW TO THIS?` label, no modal and no carousel. **`isFirstRide` is a query against the rider's own finished rides**, not a stored flag: the app already has that fact and a flag is a second copy that can disagree. It is per profile, because the third housemate to sign on is as new to this as the first was, and it is suppressed on a free ride — a class that prescribes nothing must not explain a prescription. **Say it once, at the moment it first means something.** The
      candidate moments, in order of how well they fit: **the countdown**
      (11.6.13 — a rider is sitting still, clipped in, with ten seconds and
      nothing to do, and the class's first target is already on screen); the
      **first ride only**, which needs a per-profile "has ridden" flag that
      `workouts` can already answer; or the **profile creation result step**,
      which is where the FTP is explained (20.3.4) and is therefore where the
      denominator is already being talked about.

      **Not** a tutorial, not a carousel, and not a modal in front of a rider
      who wants to pedal. One short passage, dismissible, never shown twice.
      Phase 26 governs the wording and this is one of the places its exception
      applies: a rider being taught what a watt is *is* reading a measurement
- [x] **11.8.3** ***Done and observed.*** One change, as this item asked: **the governing tile carries an outline in its own accent.** 11.7 had already made this *true* — exactly one tile has a `TARGET` line — and the gap was that all four tiles are the same object, so a first-time rider looking at `74 / 38 / 97 / 102` had nothing but a line of small dim text to tell an instruction from information. Deliberately **not** the amber: amber is this app's off-target signal, and spending it here would make a rider who is riding perfectly look like a rider who is wrong. **Make the ride screen say which number is the job**, which is
      the owner's *"and/or"* and is the cheaper half. 11.7 already gives the
      governing metric the amber, the arrow and the `TARGET` line; what it does
      not do is say **why that is the one**. Candidates worth judging on the
      AVD: the zone ladder naming what it is a ladder *of*; the `TARGET` line
      carrying a word rather than only a band; the governing metric's tile
      reading as the primary one at two metres rather than as one of four
      equals. **Change one thing at a time** — this screen is read at speed by
      somebody out of breath and it has been over-decorated before
- [ ] **11.8.4** **The subtitle space — written up as a decision, because the
      owner offered it as a possibly-bad idea and it deserves a real answer.**

      *The case for:* the app has a spoken coach (`RideCoach`) whose lines a
      rider can miss entirely — over music, over a film, or because the tablet
      is muted, which is 11.5's whole reason for existing. Everything that voice
      says is already computed and already timed, and none of it is on screen.
      A caption line is therefore mostly a *rendering* of a thing that exists,
      which is a much smaller job than it sounds and is worth a lot to a deaf
      rider or one riding at 6 am.

      *The case against, and it is specific:* a text line that changes during a
      ride is the single most attention-taking thing that can be put on a screen
      read at two metres by somebody at threshold. The ride screen has been
      **decluttered twice** on the owner's own reports (11.6.16–11.6.19, and
      24.3.16 took the leaderboard off the overlay for exactly this), and a
      moving sentence is a bigger draw than any chip that was removed.

      *The recommendation:* **build it as captions for the coach, not as a
      motivational feed.** Same trigger, same cadence, same latch — so it says
      something roughly once a block rather than continuously, and it is silent
      by default between cues. Motivational filler is what turns it from a
      caption into a slot that must be filled, and a slot that must be filled is
      what produces sentences nobody needed. Off by default is defensible;
      on-the-ride-screen-only and never on the overlay is not negotiable (24.1.5
      survives here even though 24.3.16 overruled it for the board, because that
      overrule was about a *static* card and this is moving text)
- [x] **11.8.5** ***Held.*** Nothing 11.8.2 or 11.8.3 added reads anything but the local database. **Whatever lands, it must not need the cloud, an account or a
      network.** A rider being taught what a zone is has by definition just met
      the app, and rule 1 of the connectivity model says that rider makes no
      request at all
