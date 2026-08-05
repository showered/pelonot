> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 20: Who's riding — the profile selector and avatars

The first screen anyone sees, and the one that has had the least thought. It is
also the screen that makes the shared-household story work: a bike in a living
room has three or four riders and picking the right one has to take one glance
and one tap, from two metres away, by someone who has already got their shoes
on.

The obvious reference is a TV streaming app's profile picker, and it is the
right one — same device shape, same distance, same job.

### 20.1 The profile selector

- [x] **20.1.1** **Centre the profiles and make them big.** Today they are
      small cards in a grid pinned to the top-left of a 1920×1080 screen, with
      the rest of it empty. Confirmed by screenshot on the tablet emulator, 31
      July 2026. *Rebuilt as a TV-picker: one centred row of square tiles with
      the heading above it, observed at 1920×1080/240 dpi*
- [x] **20.1.2** Landscape-first, centred both ways, sized off the screen rather
      than a fixed dp — this app runs on a tablet bolted to a bike, not a phone.
      *Tile size is derived from the available width and the number of tiles,
      bounded at both ends: a floor so a household of six stays tappable with
      sweaty hands, and a ceiling so one lone rider does not get a comic 500 dp
      square. The avatar and its initial scale with the tile — a fixed type
      style left a small letter marooned in a large circle*
- [x] **20.1.3** Guest keeps its distinct treatment (6.1) but stops competing
      with the real riders for the eye. It is the exception, not a peer.
      *A peer in layout, deliberately not in weight: riders are filled cards,
      Guest and New rider are outlined, so the eye lands on a real rider
      without having to read anything*
- [x] **20.1.4** "Create a new profile" belongs alongside the riders as one more
      tile, not as a full-width bar at the bottom of an otherwise empty screen
- [x] **20.1.5** Edit and delete a profile from here. Deleting one has to say
      what happens to their rides — `workouts.user_id` is `ON DELETE SET NULL`,
      so the rides survive as unattributed rather than being destroyed, and the
      dialog should say so rather than letting the rider guess. *Press and hold
      a rider. Rename is here because it is the one field Settings cannot
      change; FTP and weight stay there and the dialog says so. Removal reads
      "Their rides are kept — they stop being filed against anyone and stay in
      the history as unattributed."* Deleting the selected profile also clears
      `lastProfileId`, or the dashboard would go on greeting a rider who has
      been removed.
      **One trap worth carrying forward:** `Card(onClick = …)` has no
      long-press, and the first version put `onLongClick` in `semantics` only.
      That is an accessibility action, not a gesture — a real press-and-hold
      fell straight through to the click and opened the dashboard. It needs
      `Modifier.combinedClickable`
- [ ] **20.1.6** **Past about twenty riders the grid clips its last tile and
      nothing says it scrolls.** Seen on the tablet AVD at 23 tiles: the heading
      is on screen, three full rows are on screen, and the fourth row is a bare
      `+` cut off at y 1080 with *New rider* and *Add a profile* below the fold.
      It **does** scroll and the tile is reachable — so this is an affordance
      fault rather than a dead end, which is exactly the kind this project has
      shipped before (22.4.3's charts pushed below the fold, 20.4.4's *Not now*).

      **It is 20.1.2's floor doing its job and the screen not admitting it.**
      Tiles are sized off the width and the count, bounded below so a household
      of six stays tappable with sweaty hands, so past some count the grid must
      overflow and the floor is the right thing to keep. What is missing is the
      screen saying so — the first paint should end mid-row rather than on a
      row boundary, or the heading should stay put while the grid scrolls under
      it, so a rider can see there is more.

      **Found with 23 profiles, which no household has**, and the honest note is
      that the count came from a session's own test riders. It is written down
      because the failure is silent and the threshold is unknown: nobody has
      measured where it starts, and *"a household of six"* is 20.1's own stated
      target rather than a ceiling

### 20.2 Avatars

- [ ] **20.2.1** A checked-in set of avatars to choose from. Licence first:
      whatever is used has to be genuinely open (SIL OFL, CC0 or MIT), credited
      in the repo, and vendored rather than fetched at runtime — the app starts
      a ride with no network and that is not negotiable (19.4). Generated
      identicon-style avatars derived from the profile name are the other
      candidate and have no licence question at all
- [ ] **20.2.2** `profiles.avatar` in Room, behind a real migration (12.5).
      Store a **reference** — a pack id or a relative file path — never image
      bytes in the row: a database that carries photos is a database that
      cannot be exported, synced or backed up cheaply
- [ ] **20.2.3** Pick from the built-in set at profile creation, with a sensible
      default so nobody is forced through a choice to start riding
- [ ] **20.2.4** **Set an avatar from the camera or the gallery on Android.**
      `PhotoPicker` on API 33+ and `ACTION_OPEN_DOCUMENT` below it, so the
      common path needs no storage permission at all. Downscale and re-encode
      on import — a 12 MP phone photo has no business being loaded to draw a
      64dp circle — and write it into app-private storage
- [ ] **20.2.5** Strip EXIF on import, and honour the orientation tag before
      discarding it. A gallery photo carries GPS coordinates, and this one will
      end up synced (15) and possibly visible to friends (17.5)
- [ ] **20.2.6** Avatars appear wherever a rider is named: the selector, the
      dashboard greeting, history, and any leaderboard. Not on the HUD (18.6)
- [ ] **20.2.7** Avatar changes sync with the profile, once 14 and 15 work. A
      custom image is a blob and needs Supabase Storage rather than a column;
      decide deliberately whether it goes up at all before building it
- [ ] **20.2.8** Change your avatar from the companion web app — **much later**,
      and strictly after 17 exists. Listed here so it is not re-invented as a
      separate feature when it is the same field

---

### 20.3 The first question the app asks is one nobody can answer — the owner's note, 3 August 2026

**The owner's words:** *"the way it is right now can't go into production. When
a user first creates a profile they are asked for their FTP. Nobody in their
right mind would know this. We should make the UX beautiful. Either they don't
get one at all and we infer their FTP from their first ride. Or we try and infer
their FTP via their user profile — e.g. age, weight, self-assessed fitness
rating. Priority is GREAT UX."*

**What is there today.** `ProfileCreationDialog` is an `AlertDialog` with three
fields, and the third is `OutlinedTextField(label = "FTP (Watts)")` prefilled
with the string `"200"`. There is no explanation of what FTP is, no way to say
"I don't know", and no consequence stated for getting it wrong. It is the third
thing the app has ever said to a rider.

**Two facts that constrain the answer, and they pull against each other:**

- **The app cannot have no number.** FTP is the denominator of the whole zone
  system — the ride screen's zone ladder and FTP percentage, the overlay, the
  prescribed resistance band (11.2.1), every chart's zone bands, and
  `workouts.ftp_watts` which is written at ride *start* (7.8). "They don't get
  one at all" cannot mean the column is null on the first ride; it has to mean
  the rider is never *asked*, and something else supplies it.
- **Inferring it from the first ride is slower than it sounds.** Auto-FTP only
  proposes off a ride whose power is `Measured` all the way through (7.10.7), so
  on the emulator — and on any bike where the board drops out — the first ride
  proposes nothing. A first-ride inference is a good second act and cannot be
  the first one.

So the shape is: **a number the app is willing to defend, arrived at without
asking the rider a question they cannot answer, corrected by their riding as
soon as it has evidence.** Which of the owner's two routes that is depends on
20.3.2.

- [x] **20.3.1** **Take the watt field off profile creation.** Whatever replaces
      it, the literal question "FTP (Watts)" with a text box does not survive
      this item. Nothing else about the dialog is in scope here — name and
      weight are answerable questions and 13.8 already fixed the one that
      wasn't
- [x] **20.3.2** **Decide between the two routes, and write down which and
      why.**

      ***Decided: Route B, and the argument that settled it was not in this
      item.*** The three-way balance below was genuinely close, and 20.3.9 had
      already tilted it by making date of birth free. What made it one-sided is
      a fact about `PostWorkoutAnalyzer` that nobody had brought to this
      question:

      **auto-FTP can only ever propose a rider's FTP *upward*.** `analyze()`
      surfaces a proposal only when it clears `currentFtp × MIN_MEANINGFUL_GAIN`
      — 1.02 — so there is no path anywhere in this app by which a first FTP
      that is too *high* is ever corrected. That makes the two errors completely
      unlike each other:

      - **Too low is temporary.** The first hard ride reads high against it, the
        app proposes the real number, and the estimate is gone. The rider spends
        a few early rides drawn a zone hot, which feels like generosity.
      - **Too high is permanent.** Nothing proposes bringing it down, no
        breakthrough ever clears the threshold, every ride sits in Zone 2, and
        the rider concludes the app does not work. That is 20.3.4's own stated
        failure, and it is unrecoverable rather than merely unpleasant.

      **Route A cannot express that asymmetry and Route B can.** A single
      default is too high for some riders and too low for others *by
      construction*, and the ones it is too high for are stuck there for ever.
      An estimate can be deliberately pitched below the published mid-range for
      every description, so that it is wrong for nearly everybody by a little,
      **in the direction that fixes itself**. `FtpEstimator`'s class comment is
      that argument and `FtpEstimatorTest.estimateIsBelowPublishedMidRange`
      pins it, so a later change to the coefficients has to argue against the
      asymmetry rather than around it.

      **A second fact pushes the same way and is worth recording, because it is
      about a field this app deliberately does not have.** Published
      FTP-per-kilogram tables differ by roughly 15% on sex, and the app does not
      collect it and should not start — a bike in a garage does not need it, and
      the ask was for *fewer* unanswerable questions rather than more. Pitching
      at the mixed mid-range would therefore run high for a large share of
      riders, in the one direction nothing corrects. Pitching low is how the
      estimate stays honest without the field.

      ***And the "skip" the synthesis worried about is not built***, which is
      the sub-decision this item asked 20.3.3 to make by looking. There is no
      *Skip* on the questions. Instead the questions are made cheap enough not
      to be worth skipping — a date wheel and three cards, no text box, no
      keyboard — and **the escape sits on the *answer*, not in front of it**:
      the rider is shown the number the app worked out and can change it there.
      That inverts the failure the item named. A skip before the questions makes
      them feel optional and produces Route A for everybody who is in a hurry; a
      change-it link after them is only reached by a rider who has seen the
      estimate and disagrees with it, which is exactly the rider who should be
      typing a number.

      The original three-way balance, kept because its reasoning still holds:

      Route A: no question at all — seed a default and let the first
      rides move it. Route B: two or three *answerable* questions (age, and a
      self-assessed "how would you describe your riding?") feeding a published
      estimate. The owner offered both and the choice is a real one, so it gets
      an item rather than an assumption:
      - **Route A is honest and starts wrong for everyone.** A single default is
        150 W for a 25-year-old racer and for a 70-year-old starting out, and
        their first ride is drawn in the wrong zones for both. `UserEntity
        .DEFAULT_FTP` is 150 and the dialog's fallback is 200, which is the same
        problem twice with two different answers — see 20.3.6
      - **Route B is a guess with a method.** Weight is already collected, and
        W/kg by self-described category is a published, defensible mapping. It
        costs two more questions on the first screen and it is *materially*
        better than one number for everybody
      - **A likely synthesis, and the reason not to prejudge it:** Route B's
        questions with a prominent *Skip* that lands on Route A's default. But
        "skippable" is the design decision that makes the questions feel
        optional and therefore ignorable, and whether that is right depends on
        how the screen reads, which is 20.3.3's job to find out
- [x] **20.3.3** **Design it as a screen, not a dialog.** "Priority is GREAT UX"
      is the owner's emphasis and an `AlertDialog` with three stacked
      `OutlinedTextField`s on a 1280 × 720 dp tablet is the opposite of it. This
      is the first thing a rider sees and it is currently the least designed
      surface in the app. Full-bleed, readable at the distance a bike is set up
      from, and it may well be more than one step

      ***Done and observed on the tablet AVD.*** `ProfileCreationScreen`, three
      steps, and "more than one step" turned out to be the load-bearing part of
      this item rather than the width: the old dialog's real failure was
      **five controls in front of a rider who has not yet done anything**, and
      no amount of layout fixes that. One question at a time does.

      1. *Who's riding?* — a name and nothing else.
      2. *A bit about you, <name>* — weight with its unit chips (13.8 kept),
         the birth year, and three cards across the panel for the riding
         description. `WideGrid` with `equalHeightRows`, which is 22.7.2's
         opt-in earning its second caller.
      3. *Here's where we'll start you* — the number, once.

      It is a full-bleed `Dialog` (`usePlatformDefaultWidth = false`) rather
      than a nav destination, and that is deliberate: **both** call sites raise
      it conditionally over a screen they must return to — the profile selector
      and a guest keeping their ride from the post-ride summary — so a
      destination would need a back-stack entry each and a result channel to
      carry the answer home, for no gain.

      Two things came out of looking at it rather than building it, and both
      are copy rather than layout, which is itself the finding: the layout was
      right first time and **the sentences were not**. See 20.3.4
- [x] **20.3.4** **Whatever number it lands on, say where it came from.** The
      rider must be able to see that this is an estimate and not something they
      told the app. It matters directly: an estimated FTP that is too high draws
      every early ride in Zone 2 and makes the app feel like it is not working,
      and a rider who knows the number is a guess will change it. This is the
      same rule as 16.1.6's power caption and 7.10.1's measured-vs-claimed mark
      — **and it needs a source on `ftp_history`**, which today has
      `AutoBreakthrough`, `AutoBreakthroughReverted` and the rider's own. An
      estimate is a fourth thing and must not be filed as a claim the rider made
- [x] **20.3.5** **Then let the riding correct it, and say that it will.** The
      estimate's whole defence is that it is temporary. Auto-FTP (7) already
      does the correcting; what is missing is that the rider is never told the
      app is going to do it. A line at signup — *"we'll work this out properly
      from your first few rides"* — is most of what makes an estimate
      acceptable rather than an error
- [x] **20.3.6** **One default, in one place.** `UserEntity.DEFAULT_FTP` is 150
      and `ProfileCreationDialog`'s `?: 200` fallback disagrees with it, so a
      rider who clears the field gets a different number than one created any
      other way. Whatever 20.3.2 decides, exactly one constant expresses it

      *Done. It was **twice**, not once — the field opened on the string `"200"`
      as well as falling back to `200` — so the disagreement was not an edge
      case a cleared field reached, it was **every profile ever created on this
      screen**, 50 W above what `UserEntity`, Settings, the ride detail chart
      and the nav graph all believe. Both are `UserEntity.DEFAULT_FTP` now.*

      ***Observed on the tablet AVD***: the dialog opens on **150**, and a
      profile created with the field deliberately cleared lands on
      `ftp_watts = 150` in `profiles` — beside the profile created before this
      change, still sitting on 200, which is the defect visible in one query.

      **This does not settle 20.3.2 and must not be read as settling it.**
      150 is what the app already believed; the item that decides what a new
      rider's first number *should* be is still open, and the value it lands on
      changes one constant now rather than three literals in two files
- [x] **20.3.7** **Route B needs columns, so it needs a migration** (12.5) —
      age or year of birth, and the self-assessed category, both nullable
      because every existing profile has neither and a backfilled guess is
      indistinguishable from an answer. Do not add them until 20.3.2 is decided;
      Route A needs none of it

      *Half of this is already done and it happened for another reason:
      `profiles.birth_date` landed in migration 11 → 12 as heart-rate zones'
      fallback input (21.1.1), nullable for the same argument stated in the same
      words. So Route B's cost is now one column — the self-assessed category —
      rather than two, which is a real change to the balance in 20.3.2*
- [ ] **20.3.9** **One question, two answers — and it is why the owner has now
      asked for the same thing twice.** Their FTP note (the head of this
      section) and their heart-rate note (21.1.6) are the same complaint about
      two different fields: *nobody in their right mind would know this*, and
      *no normal person knows their max bpm*. Taken together they are a rule
      rather than two fixes: **the app does not ask a rider a question they
      cannot answer, and where it needs a number they cannot give, it derives
      one and says that it did.**

      The practical consequence is a saving rather than a cost. **Date of birth
      is the input to both** — Tanaka for the maximum heart rate (21.1.2, built)
      and age-adjusted W/kg for the FTP estimate (20.3.2 Route B) — and it is
      already on the profile, already nullable, already collected by a date
      picker in Settings. So an onboarding flow that asks it once has paid for
      two estimates, which materially changes 20.3.2's arithmetic: Route B's
      "two more questions" is really one more question (the self-assessed
      category), because the other one is a question the app wants anyway for a
      feature the rider will meet in their first ride.

      It also sets the order of the two phases: **20.3.3's screen is where the
      date is asked**, and 21.1.6 is that same screen's second consumer. Build
      the screen once
- [ ] **20.3.8** **Guests skip all of this.** A guest ride has no profile and no
      FTP, and adding an onboarding flow in front of "just let me ride" would
      break the one thing the guest rung is for. Check what a guest ride's zone
      display does today before changing anything here

---

### 20.4 The first thing a new rider meets, watched over somebody's shoulder — the owner's note, 5 August 2026

**Verbatim, the onboarding half:** *"Year born and weight inputs are different,
visually"* and *"After typing in weight, and then trying to select weight, it
doesn't register the tap because of keyboard still being open"*.

**The provenance is what makes these worth more than their size.** The owner
watched somebody sign up for the first time — their wife — and reported it as
*"a clunky onboarding"*. That is the only kind of evidence this screen can get
that is not a session driving its own flow knowing where every control is. A
first-time rider does not know the birth-year control is a picker, so its not
looking like the thing beside it is not a cosmetic complaint: it is the screen
failing to say what kind of answer it wants.

**Both faults are on `AboutStep`, which is the one step that asks for three
things at once**, and it is worth noticing that the step's own doc comment
already claims *"no keyboard on any of them except the weight"* — true, and
exactly the condition under which a stray keyboard is most disruptive, because
it is the only one.

- [x] **20.4.1** **Two questions of the same kind, drawn as two different kinds
      of control.** ***Done and observed on the tablet AVD.*** `PickerField` —
      Material's own text-field metrics (56 dp, the extra-small corner, a 1 dp
      outline, the label above the value in the same two styles), drawn
      directly rather than as a `readOnly` `OutlinedTextField`, because a
      read-only field still takes focus and shows a caret in a box the rider
      cannot type into. It carries `Role.Button`, which is what it is.
      *Measured beside its neighbour, which is where the fault lived:* the two
      fields are **375 dp and 373 dp** where they were 328 and 438, because the
      unit chips were being taken out of the weight's half of the row and out
      of nothing on the year's. With a value in both, the screen now reads
      `Weight (lb) / 68` beside `Year you were born / 1986`. Weight is an `OutlinedTextField` with a floating label
      inside a border; the birth year is an `OutlinedButton` whose whole content
      is a sentence — *"What year were you born?"* — with no label, a different
      height, a different corner radius and text centred rather than left. Side
      by side in one `Row` they read as a field and an action, when they are two
      facts about the rider.

      **The fix is not to make the year a text field.** 21.1.1b settled that a
      year is picked and not typed, and the reason stands: a keyboard on it is
      the very thing 20.4.2 is about. What is wanted is a control that *looks*
      like the field beside it and *behaves* like the button it is — same
      height, same border, same label in the same place, with the value where
      the value goes and the question in the label rather than in the value
      slot. `OutlinedTextField` has a `readOnly` mode that keeps every one of
      those affordances, but it also keeps a focusable text cursor, so the
      honest version is a shared `PickerField` composable that draws the box
      itself and is judged **beside** the weight field on the tablet AVD rather
      than on its own
- [x] **20.4.2** **The keyboard eats the first tap.** ***Done, and the cause
      was not the one this item guessed at first.*** Both halves were
      measured on the AVD rather than reasoned about, and the second is worse
      than what was reported:

      **The step slides under the finger.** With the keyboard up, `kg` moves
      from y 337 to y 220 and *I ride regularly* from 544 to 427 — 117 px,
      because `StepScaffold` centres its content (22.7.1) so an IME-driven
      resize moves every control by half of it. Every control that is not the
      weight field now clears focus *as part of its own tap*, so the keyboard
      goes, the layout settles and the action still happens: **one tap, one
      outcome**. Doing it the other way round — dismissing on any touch outside
      the field — spends the rider's first tap on the dismissal, which is the
      behaviour being complained about. The field also offers `ImeAction.Done`.

      **And `imePadding()` was a no-op, which is how the *first* screen was
      worse than the one reported.** `ProfileCreationScreen` is hosted in a
      `Dialog`, and a dialog gets a window of its own that reports no IME
      insets to Compose while `decorFitsSystemWindows` is true. So the keyboard
      was drawn straight over the step: on the **Name** step — the first thing
      this app asks anybody — *Continue* sat at y 603–632 with the keyboard's
      top edge at 590. It is at y 381 now, and the whole step stands clear.
      Nobody reported this one; it was found by turning the AVD's hardware
      keyboard off, without which the emulator shows a floating IME and no
      resize ever happens. **That is the check worth keeping: `hw.keyboard=no`
      or this class of fault is invisible on an emulator.** After typing a weight, the
      next tap — on the kg/lb chips, on a fitness-level card, on the birth-year
      control — does nothing except dismiss the keyboard, and the rider has to
      tap twice. Two causes are possible and they want different fixes, so
      **measure before changing anything**: either the IME window is consuming
      the touch outright, or `adjustResize` is re-laying the step out as the
      keyboard goes and the tap lands where the control *was*. The second is the
      more likely on this screen, because `StepScaffold` puts the content in a
      `verticalScroll` `Box` with `Alignment.Center` — so every control moves
      when the available height changes, and the movement is doubled by the
      centring.

      Candidate fixes, cheapest first: give the weight field an `ImeAction.Done`
      that clears focus so the keyboard is gone before the rider reaches for
      anything; anchor the step's content to the top once a keyboard is up so it
      stops sliding under the finger; declare the activity's soft-input
      behaviour explicitly rather than inheriting whatever the manifest's
      default is (`MainActivity` declares no `windowSoftInputMode` today).
      **The check is a tap that works first time, on the AVD, with the keyboard
      up** — not a reading of the diff
- [ ] **20.4.3** **What this pair says about the rest of the flow.** Neither
      fault is visible to anybody who has driven this screen before, and both
      were found in seconds by somebody who had not. That is an argument for
      **watching the whole first-run path with fresh eyes at the AVD's real
      size** rather than for fixing two controls: profile creation, the account
      offer, the class library, Start Class, the countdown, the first ride, the
      summary. The other two entries in this same note (22.7.5, 11.8) came from
      the same sitting and the same rider, which is the evidence that the path
      and not the screen is the unit
- [x] **20.4.4** **The account offer did not fit the panel, and the only visible
      control on it was a dead button.** ***Found by doing 20.4.3 — walking the
      whole first-run path with fresh eyes — and it is worse than either fault
      the owner reported.***

      Measured on the tablet AVD: the whole offer was stacked in a 640 dp column
      on a 1280 dp panel, so the sign-in password field ended at y 890 and
      **`Not now` was off the bottom of the screen**. That is the answer this
      app ships as its default (15.8.1: *"skip is a first-class answer"*), and a
      first-class answer a rider cannot see is not one. Underneath it,
      `StepScaffold` drew a *Back* — which on this step does nothing by
      construction, since `Step.Account.previous()` returns itself, deliberately,
      because going back would re-ask a question about a profile that already
      exists.

      **So a rider who had just made their first profile was looking at a screen
      whose only apparent way onward was signing in.** Both halves fixed: the two
      routes go side by side (22.4's rule — a set of things being *chosen
      between* wants the width, not a reading column), and `backLabel` is
      nullable so the step draws no control rather than a dead one.

      One thing worth carrying forward from the fix itself: `SignInForm` emits a
      **sequence of siblings** for a `ColumnScope`, so wrapping it in a `Box` to
      give it a weight stacked all five children on top of each other. It drew as
      an illegible smear and compiled perfectly
- [x] **20.4.5** **The same two controls, reported a second time — *"Year born
      and Weight are out of alignment"*.** ***Done and observed on the tablet
      AVD.*** The owner's inbox, 5 August 2026, the sitting after 20.4.1 was
      ticked. Two reports of one pair of controls is worth more than the fault
      is: **20.4.1 was fixed by measuring the controls against each other and it
      was not enough**, because the thing that was wrong was not a measurement
      either time.

      **What was actually wrong.** 20.4.1 built `PickerField` out of Material's
      *numbers* — 56 dp, the extra-small corner, a 1 dp outline, the label above
      the value in the two styles a text field uses. Every number was right and
      the pair still did not line up, because an `OutlinedTextField` is **taller
      than the box it draws**: when it has a label it applies a fixed top
      padding, and the outline is the *bottom* 56 dp of a 64 dp control. So two
      children of one `Alignment.Top` row, laid out from the same y of 307 px,
      drew their outlines 12 px apart — and once the year was answered its label
      floated to y 295, **outside its own control**, while the weight's sat on
      its border at 307.

      **The fix is to stop copying and start calling.** `PickerField` is
      `OutlinedTextFieldDefaults.DecorationBox` now, with a `Text` where the
      editor would be, so the two controls are one component drawn twice: the
      same notched outline, the same label floating onto the border when the
      question is answered, the same insets at any font scale. Measured after:
      both outlines `[319 … 403]`, both labels `[307 … 331]`, `68` and `1986` on
      one baseline.

      **Three things it took to get there, all of them invisible in the diff:**
      - The decoration box takes **no modifier**, so the width the caller gave
        the control has to reach it as a *minimum constraint*
        (`propagateMinConstraints`) or it draws at its intrinsic width and the
        label spills past the outline. Seen: the year box 225 px narrower than
        the weight's, with *"Year you were born"* running out of it.
      - The reserve is **8 sp, not 8 dp** — the room a floated label needs is a
        property of the text, so it moves with the rider's font scale. Material
        keeps the constant `internal`, which is the one thing here that has to
        be copied rather than called, so the check for it is the screen and not
        this file.
      - The kg/lb chips were the third child of the row and correct against the
        *old* picker. With both fields now 64 dp tall around a 56 dp outline,
        top-aligned chips sit 8 dp high against both. They align to the row's
        **bottom**, which is the outline they belong to.

      **What it costs.** The empty state loses *"Tap to choose"*: a text field's
      placeholder appears only while it is focused, and this control never is,
      so the question sits centred in the box exactly as *"Weight (lb)"* does
      beside it. That is the trade this item is here to make — 20.4.1's own
      argument is that a control which does not look like the thing next to it
      is the screen failing to say what kind of answer it wants, and *looking
      like it* has to mean in every state or the rider finds the state where it
      does not
- [x] **20.4.6** **Android back threw away every answer on every step.**
      ***Done and observed on the tablet AVD.*** Found by taking the owner's
      inbox note about the QR code (15.6.13) at its word — *"analyse this whole
      journey"* — rather than fixing the screen it was reported on. The cause is
      one line and it is the same one: `ProfileCreationScreen` is hosted in a
      `Dialog`, a dialog's own callback dismisses it on back, and nothing in
      this screen ever saw the press. So back on *A bit about you* threw away a
      name, a weight, a year and a fitness level and put the rider on *"Who's
      riding?"* — while the *Back* control two inches below it went one step, as
      it always had.

      **Two controls for one gesture, disagreeing.** `BackHandler` now takes the
      same lambda `StepScaffold` is given, so there is no step on which they can
      differ, and the account step turns it off because that step owns its own
      (15.6.13) and two enabled handlers would race. Observed: back from *About*
      lands on *Name* with the name still in the field; back from the estimate
      lands on *About* with all three answers still set

---

### 20.5 What is left on the first-run path

- [x] **20.5.1** **Two writers of one column, and only one of them had a
      fence.** ***Done and observed on the tablet AVD.*** Found by walking
      20.4.3's path as a rider who had never seen it: `68` typed into a field
      labelled `Weight (lb)` produced *"Here's where we'll start you: 65 W"*,
      said with exactly the confidence of any other estimate. `FtpEstimator` is
      linear in weight and had nothing to disbelieve.

      **The honest account of that particular number is that the fence does not
      catch it**, and the item is worth having anyway. 68 lb is 31 kg, which is
      inside any bound this app could defend — a 31 kg rider is a child, not a
      typo. What went wrong there was a *unit*, and the defence against a unit
      is the label and the chips beside the field, which were both correct and
      which the rider (me) did not read. **What the fence catches is the
      neighbouring failure**: a missing digit, or pounds typed into a kilogram
      field, where the number is not merely unlikely but impossible.

      **What made it worth building is what the walk turned up beside it:
      Settings has fenced this column since 13.8 and profile creation never
      did.** So the two screens that write `weight_kg` disagreed about what a
      weight is, and the one with no fence is the first screen a rider ever
      meets. `RiderBounds` is now the single answer and Settings' own constants
      point at it, because two copies of a bound is how they came to differ.

      **Reject, never clamp**, and the message quotes the range **in the rider's
      own unit** — a range in kilograms shown to somebody typing pounds explains
      nothing, and explaining is the whole job of that line.

      Note **absent is not out of range**: the weight is optional here, and an
      unanswered question is a different claim from a wrong answer. Same family
      as `heartRateBpm` and `target_position`
- [ ] **20.5.2** **The estimate does not show its working, and it is the last
      screen that could.** *"Our estimate, from your weight, your age, and that
      you're riding now and then"* names the **inputs** and not their
      **values**, so a rider who mistyped a weight two screens ago has no way to
      see it — and by the next screen the number is on their profile and in the
      denominator of every zone. Naming the values (*"from 68 lb, born 1986…"*)
      would catch exactly the fault 20.5.1 could not fence.

      **It is not obviously right, which is why it is written down rather than
      built.** Phase 26's standing rule is to say less and to keep the geeky
      words where a measurement is being *read*; three numbers echoed back on a
      screen whose whole job is to say one number is the opposite of that.
      There is a cheaper version — make the *Back* on this step the answer, and
      it already is — so this may be a real improvement or may be clutter, and
      the owner's eye is the right judge. **20.3.4 is the item it argues with.**
