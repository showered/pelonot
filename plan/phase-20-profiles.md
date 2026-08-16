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
- [x] **20.1.6** **Past about twenty riders the grid clips its last tile and
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

      ***Done and observed on the tablet AVD at 22 tiles.*** Taken the second
      way this item offered — **the heading stays put and the grid scrolls under
      it** — with the hint pinned below it as well, which turned out to be the
      half that does the work: the last visible row is now cut **against a line
      of text** rather than against the edge of the display, and the difference
      between those two is the whole fault. A soft edge fades whichever end has
      tiles past it, **both ends**, because once the grid has been scrolled it
      is the riders *above* that have gone missing.

      **The floor is untouched and so is the ordinary case.** Checked by cutting
      the database back to a household of three: one centred row, no fades, and
      the screen identical to what it was — the tile column still wraps its
      content, so nothing about this is paid for by the household 20.1 was
      designed for. The threshold this item said nobody had measured is now
      *irrelevant* rather than measured, which is the better answer: the screen
      admits an overflow at whatever count it happens at

### 20.2 Avatars

- [x] **20.2.1** A checked-in set of avatars to choose from. Licence first:
      whatever is used has to be genuinely open (SIL OFL, CC0 or MIT), credited
      in the repo, and vendored rather than fetched at runtime — the app starts
      a ride with no network and that is not negotiable (19.4). Generated
      identicon-style avatars derived from the profile name are the other
      candidate and have no licence question at all

      ***Done and observed on the tablet AVD in five states.*** **Eight colours
      and six marks**, and the licence question was answered by not creating
      one: the colours are this project's own hex values and the marks are
      Material icons already in the build, so nothing is fetched, nothing is
      vendored and nothing needs crediting. **The identicon route was
      deliberately not taken** — an identicon is a hash, so it cannot be
      *chosen*, and on a household bike the point of a face is that the rider
      picked it.

      **The palette is the finding, and it was live rather than cosmetic.** The
      selector drew its discs in `PowerZone2Endurance` through
      `PowerZone6Anaerobic`, which is wrong twice. **One in five profiles was
      amber**: `PowerZone4Threshold` is the amber this app uses for *off target*
      (11.8.3), and `RiderScore`'s third rule already says in those words that a
      rider's identity must not wear the colour meaning *you are wrong*. And the
      zone ramp is itself a claim — it runs cool through warm so intensity reads
      without the number, which is exactly why 21.2.1 gave the heart-rate zones
      a *different* ramp rather than sharing this one. A face is not an
      intensity. `AvatarPalette` is now eight colours that are **no zone colour,
      no live-metric accent and nothing amber**, with the reasoning at its
      definition.

      **Superseded in part by 20.6.1 the following day** — the owner's verdict
      on this set was *"the ones we have are not good"*, and the six Material
      marks are gone. **The palette survives untouched and the argument above is
      why**: what replaced the marks is twenty drawn faces *on* these colours

- [x] **20.2.2** `profiles.avatar` in Room, behind a real migration (12.5).
      Store a **reference** — a pack id or a relative file path — never image
      bytes in the row: a database that carries photos is a database that
      cannot be exported, synced or backed up cheaply

      ***Done and observed*** — migration 21 → 22, schema 22 exported,
      `MigrationTest` covering it, and the upgrade watched on a tablet AVD
      carrying 45 rides and two profiles. One short string: `rose:bolt`, read
      back in `sqlite3` after the save rather than off a screenshot.

      **Not backfilled, and that is the whole of the decision.** Null means
      *this rider has never chosen* and `Avatar.defaultFor` answers for them
      from their own row id — so every existing profile keeps exactly the disc
      it already draws, **and the app can still tell a rider who picked that
      colour from one who never looked**. Writing the derived value in would
      have changed nothing visible, which is precisely what makes it tempting,
      and would have collapsed the two claims for ever. Third column with this
      argument after 12 → 13 and 20 → 21. Measured on the device: `rose:bolt`
      on the rider who opened the dialog, still `NULL` on the one who did not

- [x] **20.2.3** Pick from the built-in set at profile creation, with a sensible
      default so nobody is forced through a choice to start riding

      ***Done and observed, with the creation half answered rather than
      built.*** The picker is on the **press-and-hold dialog** the selector
      already had (20.1.5), because that dialog is where a rider is looking at
      their own name; **profile creation was deliberately left alone.** The
      whole of 20.4 is about that path being too long for somebody meeting the
      app for the first time, and 20.4.6 and 20.4.8 are both about things put in
      front of a rider before they had earned the interruption. This item's own
      clause is what makes leaving it correct: the default is *sensible* — one
      of eight, derived from the row id, so a household of three gets three
      faces — and nobody is forced through anything. **If it ever moves into
      creation it belongs at the end, not the start**, and it needs the owner's
      eye rather than a session's, since it lengthens the one journey four items
      have been shortening.

      **Both halves of this item have since moved** (20.6). The picker is now a
      shared component rather than a private one, the marks are twenty Open
      Peeps faces, and **profile creation does carry it** — this item's own
      condition for that, *"it needs the owner's eye rather than a session's"*,
      was met by the note of 16 August.

      Two decisions inside the picker worth keeping. **The rider's own initial
      is the first option in the mark row and is the default**, and it is a
      choice rather than an absence: an initial is unambiguous between two
      housemates with different names, and a mark is what serves the household
      where two names start with the same letter. And **two short rows rather
      than a grid** — eight colours times seven faces is fifty-six tiles and a
      decision nobody asked for (26.3's argument, applied to a control)

- [x] **20.2.3a** **The selected swatch needs a gap, not a ring on the
      colour.** ***Found and fixed by looking at the tablet AVD*** and it could
      not have been found any other way. The selection was a 3 dp border drawn
      *on* the disc in the brand teal: perfectly legible on the mark row, where
      every swatch is a dark grey, and **very nearly invisible on the colour
      row, because one of the eight colours is a turquoise.** The state a rider
      is in most often is the one where the selected swatch is the colour they
      already have, so this was the common case rather than an edge.

      The fill is inset when selected, so the dialog's own surface shows between
      the ring and the colour and the signal is the **separation** — which no
      hue can defeat. A tick on top was the other candidate and is worse: it
      hides part of the thing being chosen. **Same family as every "one rule,
      two colour systems" fault in this plan**: the ring colour was chosen
      against the dark grey it was first drawn on and then reused on a surface
      that could be the same hue
- [ ] **20.2.4** **Set an avatar from the camera or the gallery on Android.**
      `PhotoPicker` on API 33+ and `ACTION_OPEN_DOCUMENT` below it, so the
      common path needs no storage permission at all. Downscale and re-encode
      on import — a 12 MP phone photo has no business being loaded to draw a
      64dp circle — and write it into app-private storage
- [ ] **20.2.5** Strip EXIF on import, and honour the orientation tag before
      discarding it. A gallery photo carries GPS coordinates, and this one will
      end up synced (15) and possibly visible to friends (17.5)
- [x] **20.2.6** Avatars appear wherever a rider is named: the selector, the
      dashboard greeting, history, and any leaderboard. Not on the HUD (18.6)

      ***Done and observed on three surfaces; the fourth and fifth are named
      below and deliberately left.*** `RiderAvatar` is the one component, in the
      `RiderScore` mould with its rules in its own KDoc — never on the HUD,
      scales with what it sits in, silent to a screen reader (a face beside a
      name says nothing a name does not, and "avatar" announced per row is three
      words per rider for no fact), and the colour is never a status.

      **Before it existed the only avatar in the app lived *inside*
      `ProfileSelectorScreen` as a private `Box`**, which is both why it was
      drawn off the zone palette and why nothing else drew one at all. That is
      this project's recurring defect — one rule written in one file — arriving
      on the first screen anybody sees.

      **A guest gets no face**, which is `RiderScore`'s fourth rule's argument
      exactly (26.4): a guest's rides are filed against nobody, so there is no
      profile to be the face *of*, and drawing one promises an identity that
      does not exist. Watched: greeting, household panel and selector all
      showing the same face for the same rider, and the guest greeting showing
      neither face nor badge.

      **It costs no height on the dashboard**, which mattered because 22.8 and
      22.9 are both about that screen: the greeting is one line of headline
      either way, and the household rows grow to the avatar's 32 dp from a
      `RiderScore` pill that was nearly that already. Measured on the AVD at
      about 560 dp of a 664 dp viewport for the household state 22.9 recorded at
      541 dp, and it does not scroll

- [ ] **20.2.6a** **History and the leaderboard are the two surfaces left, and
      the leaderboard is a question rather than a job.** History rows are the
      rider's *own* rides, so there is nobody to name on them — the avatar would
      be the same face repeated down a list, which is decoration. The
      leaderboard is the interesting one: since 24.3.18 a board carries
      **auto-generated ghosts** alongside real housemates, and 24.3.12a is
      *still open* on what those rows should even be called. Putting faces there
      forces an answer to **what a ghost looks like** — give it one and the
      board claims a person who does not exist; leave it blank and the board has
      two classes of row, which is arguably honest and arguably just untidy.
      That is a design decision with the owner's existing open question sitting
      on top of it, so it is written down rather than guessed at
- [ ] **20.2.7** Avatar changes sync with the profile, once 14 and 15 work. A
      custom image is a blob and needs Supabase Storage rather than a column;
      decide deliberately whether it goes up at all before building it

      **Still open, and 21.1.1a is the precedent for how to close it**: that
      item was answered by *reading* `ProfileDto` rather than by building
      anything, and the answer was *neither*. The same is true here — the cloud
      profile row has no avatar column and adding one is a migration only the
      owner can apply, which is 15.3.7's queue. **The colour-and-mark form makes
      this cheaper than the item assumed**: a chosen face is one short string,
      not a blob, so it needs no Storage bucket at all and could ride in the
      profile payload the day that migration happens. Only 20.2.4's photograph
      needs Storage, and that is an argument for deciding the two separately
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
- [x] **20.4.3** **What this pair says about the rest of the flow.** ***Done in
      the fifty-sixth sitting — the whole path walked end to end on the tablet
      AVD from `pm clear`, and it found three defects, every one of them
      invisible to anybody who had driven the flow before.*** They are
      **20.4.7** (the account offer's QR never arrived), **20.4.8** (the
      notification permission asked 21 seconds into the first class) and
      **20.4.9** (*Not now* un-answered by the next resume, which only became
      visible once 20.4.8 was fixed). Two of the three are the *same shape* and
      it is worth naming: **a one-shot trigger that fires before the thing it
      needs exists, and never fires again** — a `LaunchedEffect` keyed on
      something that will not change, and a permission asked from below the
      gate that decides when it may be asked.

      **What the walk confirmed rather than found**, because a walk that only
      reports faults is not evidence the rest works: 20.4.2's keyboard fix (the
      docked keyboard moves the field and *Continue* above it), 21.1.1b's year
      picker, 20.4.4's two routes side by side with a live QR on it for the
      first time, 19.1.6's first-run picker, 11.8.2's `NEW TO THIS?` passage on
      the countdown, 11.6.16's countdown fitting, 12.7's effort question at
      217 dp of a 664 dp viewport, and 26.4's badge reading `LVL 1` for a rider
      whose only ride is three and a half minutes long — which is the
      arithmetic working, not the claim failing: 70 points is a *typical* ride
      and a stub is worth about 26.

      **One thing that looked wrong and is not**, written down so nobody
      "fixes" it: the flow offers pounds and miles because `UnitSystem
      .fromLocale()` reads the AVD's `en-US`. On the bike it will be metric.

      The original argument, kept because it is what made this worth doing:
      neither fault in 20.4.1/20.4.2 was visible to anybody who had driven that
      screen before, and both were found in seconds by somebody who had not.
      That is an argument for **watching the whole first-run path with fresh
      eyes at the AVD's real size** rather than for fixing two controls: profile
      creation, the account offer, the class library, Start Class, the
      countdown, the first ride, the summary. The other two entries in this same
      note (22.7.5, 11.8) came from the same sitting and the same rider, which
      is the evidence that the path and not the screen is the unit
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

- [x] **20.4.7** **The account offer's QR never arrived, and the spinner had no
      end.** ***Done and observed on the tablet AVD in the fifty-sixth sitting,
      found by walking 20.4.3's path from `pm clear`.***

      At the end of setup — the last screen between a rider and their first ride
      — the left half of the offer drew a spinner saying *"Checking…"*, and it
      was still spinning **minutes** later. No `device_link_begin` was ever
      attempted: the `Log.w` in `DeviceLinkRepository` that reports a failed
      mint never fired, because nothing ever asked.

      **`AccountViewModel.startPairing` returns silently without a profile**,
      and `ProfileAccountOfferStep` keyed its `LaunchedEffect` on
      `pairingAvailable` alone. The profile is written to `profiles` a moment
      before the step composes — `last_profile_id` was stamped at the same
      second the offer appeared — so the emission that turned `pairingAvailable`
      true could still carry no profile. And because nothing in the key ever
      changed again, **nothing asked a second time**.

      **`AccountScreen`'s own condition was right the whole time**, because it
      includes `!isGuest`. The two were the same rule written twice with one
      copy missing a clause, which is 12.7's two effort cards and 23.4.12's
      seven leaderboard queries. So there is now **one** answer —
      `AccountUiState.wantsPairingCode` — that both call sites key on, with
      seven cases pinned by `AccountPairingTriggerTest`, and the rule stated in
      its KDoc: **the profile is part of the trigger and not merely a
      precondition.** A condition that cannot change value cannot re-run an
      effect.

      Two things fell out of it. The shared predicate also stops a code being
      minted while the stored session is still loading, which the offer used not
      to check. And the spinner has its own word now — *"Getting a code…"*
      rather than the session check's *"Checking…"* — because `Idle` and
      `Starting` draw identically, so a trigger that never fired looked exactly
      like a request in flight. **Observed**: a QR, the code `XTAS RTQ9`, the
      URL and a 4:57 countdown, on the screen that showed a spinner with no end
- [x] **20.4.8** **The notification permission was asked 21 seconds into the
      first class.** ***Done and observed on the tablet AVD across three
      installs.***

      `RequestRideNotificationPermission` was composed **below the countdown's
      own `return`**, so the earliest it could possibly fire was after the class
      had started. Measured: the platform dialog landing over the ride screen at
      00:21 with the rider pedalling, covering the middle of the screen.

      That is the fault **11.6.14** named for the overlay permission, and its
      answer is the one to copy: the ten seconds a rider spends clipping in is
      when a question can be asked. The call moves **above** the branch so it
      composes in both phases from the same slot, keeping its `asked` flag
      across the transition — which matters more than it looks, because the
      platform shows this dialog **twice ever** and a second call site would
      spend the rider's second refusal on the same ride.

      **The count is held while it is up.** A permission dialog is drawn by
      another process, so the countdown would otherwise run out behind it, which
      is 11.6.14's *"coming back to 2 and then straight into a class"* wearing
      a third costume.

      **And the order is now deterministic, which is the part worth keeping.**
      Deferring on `overlayPermissionNeeded` deferred on *no dialog being up* —
      equally true of *not having looked yet* — so the platform's terse question
      won the race and was asked in front of the app's own.
      `RideUiState.overlayPermissionResolved` is the honest flag: asked and
      answered, **or established as unnecessary**. Observed: the overlay
      question first, the notification question second, both on *Get set*, with
      the count still held twelve seconds later
- [x] **20.4.9** **"Not now" was un-answered by the next resume.** ***Done and
      observed on the tablet AVD*** — and it is 20.4.8's own fix that made it
      visible, which is the argument for walking a path rather than a screen.

      With both questions in the countdown, answering the platform's dialog
      resumes the Activity, `OnResume` re-runs `refreshOverlayPermission`, and
      the check knew only that the permission was still ungranted — **which is
      exactly what *Not now* means**. So the overlay prompt came back thirty
      seconds after it was declined, in the same countdown.

      The re-ask exists for the rider who went to Android's settings and it
      should: that trip is the one where the answer can have changed. What it
      must not do is re-ask the rider who said no, which is **11.6.15**'s rule
      arriving on the other control — a question answered once and asked for
      ever. `overlayPromptDeclined` is per **ride**, not per process, and that
      is the whole scope it needs: the view model is scoped to the ride's own
      back-stack entry, so the next class asks again, which is right for a
      permission that may have been granted in between
- [x] **20.4.10** **The pre-ride goal prompt is the last Title Case on the
      path**, and it is the one screen Phase 26 audited without touching the
      words on the buttons. *Reach New Milestones* and *Just Stay Fit* are
      marketing capitals on a dialog whose every neighbour is sentence case —
      *I ride now and then*, *A good workout*, *Keep as a guest ride*. 26.1.5
      rewrote both **descriptions** on this very dialog at the owner's
      instruction and left the names alone, which is how they survived.

      Cheap and safe: `RideIntent.displayName` is read in exactly one place and
      `id` is what persists, so nothing stored moves. Deliberately not done in
      the sitting that found it — it is a change to the app's voice on a screen
      the owner has already had opinions about (26.1.5, 26.1.6), and it belongs
      beside those rather than smuggled in behind three defect fixes

      ***Done and observed on the tablet AVD.*** *Reach new milestones* and
      *Just stay fit*, matching every neighbour on the path. The item's claim
      was checked rather than trusted: `displayName` is read in exactly one
      place, `PreRideIntentPrompt`, and `id` is what persists.

      **The case changed and the words did not, deliberately**, and the session
      that did it started by changing them and backed out. *Push a bit harder* /
      *Keep it steady* is wrong twice over — it is a rewrite of a dialog the
      owner has already had opinions about, made without asking, and each new
      name would then say exactly what the description beneath it already says.
      **That redundancy is real and is worth the owner's eye**: *Reach new
      milestones* sits above *A bit harder than your zones ask for*, and one of
      those two lines is doing no work. It is not a session's call which

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

---

### 20.6 The face itself, and what is written on it — the owner's two notes, 16 August 2026

**Both notes arrived the day after 20.2 shipped, and that is the useful fact
about them.** The fifty-eighth sitting built a rider a face out of eight colours
and six Material icons, watched it on the tablet AVD in five states, and wrote
down at 20.2.1 that the licence question had been *answered by not creating
one*. The owner looked at it and said the set is not good. That is not a defect
report and nothing in it was broken — it is a judgement about how the first
screen anybody sees actually looks, which is the one thing a session cannot
take from a diff and the owner can take in a glance.

**The owner's words, first note:**

> *"Download a free set of avatars online somewhere and use those. User should
> pick one when signing up. The ones we have are not good."*

**And the second:**

> *"lvl should be part of the avatar (overlaid somehow). FTP score should be
> displayed under the avatar. Well i understand there are repercussions across
> the app. Perhaps just change what i said on the profile selection screen. I
> don't know! Happy to go with what you think. I feel like FTP and lvl are both
> important."*

**The second note is unusual in this plan and worth naming as such: it asks for
something two written rules forbid, it says so itself, and it hands the
judgement over.** 26.1.1 removed `150 W FTP` from the profile tile *at this
owner's request* and their verdict was "SO much better"; `RiderScore`'s second
rule says the level is never drawn beside the FTP. The note does not overlook
either — *"i understand there are repercussions"* — it proposes the narrowest
version (one screen) and then asks what this side thinks. **What this side
thinks is written at 20.6.4 and 20.6.5**, and the honest summary is that the
level-on-the-face half is straightforwardly good and the FTP half is a real
reversal that is worth making on exactly one screen and nowhere else.

**Two decisions were taken to the owner before anything was written**, because
both are taste rather than reasoning and the note is itself a complaint about
taste: which set, and how many. **Open Peeps** (Pablo Stanley, CC0 1.0) and
**twenty faces**.

- [x] **20.6.1** **A downloaded set of faces, vendored — Open Peeps, CC0.**
      ***Done and watched on the tablet AVD*** — twenty PNGs in
      `res/drawable-nodpi`, 268 KB, written by `avatars/fetch.sh` and committed.
      20.2.1's rule stands unchanged and is what picked the set: whatever is
      used has to be genuinely open (CC0, SIL OFL or MIT), credited in the repo,
      and **vendored rather than fetched at runtime**, because the app starts a
      ride with no network and that is not negotiable (19.4).

      **Five CC0 candidates were rendered on the app's own disc at both sizes
      that matter and looked at** — Open Peeps, Lorelei, Pixel Art, Notionists
      and Thumbs — with the small column at **32 dp**, which is the household
      row and the dashboard greeting. That column is the whole of the decision:
      the line-art sets (Notionists, Lorelei) are the most attractive at 104 dp
      and the palest at 32, where they collapse to a hairline and a hair
      silhouette. Open Peeps is flat bold colour with a hard outline and is the
      only one of the five still legible as *a particular person* small. Thumbs
      is more legible still and is not a person; Pixel Art reads as a game
      character, which is a whole-app style choice rather than an avatar choice.

      **The owner chose Open Peeps and twenty of them.** Twenty is more than
      26.3's instinct would allow for a *judgement* and this is a *pick* — the
      ceiling on a pick is how many are told apart at a glance, not how few
      questions the screen asks, and twenty different haircuts are told apart
      where twenty shades of one colour are not.

      **What must survive the download:** the images go in the repository, the
      licence and the attribution go in a file beside them, and nothing in the
      app calls an API. The DiceBear HTTP API is how they were *fetched once*,
      by a script that is checked in so the set can be regenerated — it is not a
      runtime dependency and must never become one.

      **The set is fitted to the household that will use the bike, at the
      owner's instruction, and it is a settings question rather than a hand-pick
      one.** Their words: *"more white-skinned avatars and fewer BAME. Not
      because of racism but because the people that are going to be using this
      are all white and non-religious."* This is a family's tablet and not a
      product with a public audience, and a picker where nobody recognises
      themselves is a picker nobody opens. Two lists at the top of `fetch.sh`
      answer it — `SKIN`, where a repeated value is simply a heavier weight, and
      `HEADS`, which names every option Open Peeps offers **except `hijab` and
      `turban`**. Naming the whole list rather than an exclusion is deliberate:
      the API has no *not this*, so an option added upstream would otherwise
      arrive in the set unasked. **Measured rather than eyeballed** — the
      vendored PNGs were decoded and their dominant skin hex counted against
      Open Peeps' own five-value palette: **10 lightest, 7 light, 3 mid, none
      darker**.

      **`maskProbability=0`, found the same way.** The default is a 5% chance of
      a surgical mask, which at twenty faces put one on `haze` — a thing that
      happened to the world in 2020 rather than a face.

      **And the door is left open to picking them by hand**, which the owner
      asked for in as many words. A seed is opaque and nobody should have to
      guess what one looks like, so `avatars/browse.sh` renders a sheet of
      candidates in a browser with the seed printed under each face: pick the
      words, paste them into `SEEDS`, run `fetch.sh`, commit. An entry may also
      carry its own query after a `|` to pin one face's hair, glasses and skin
      exactly.

      **One trap worth writing down, because it nearly produced a false
      conclusion.** The browser preview pane served a *stale* render of a local
      file twice in a row — identical pixels after the PNGs on disk had
      changed — and the mask looked like it was still there after it had gone.
      What settled it was reading the PNG itself. Same family as *the database
      is the witness, not the screenshots*: **the artefact is the witness, not
      the previewer**

- [x] **20.6.2** **Pick one when signing up, which reverses 20.2.3's deliberate
      decision — and the owner is the one who may.** ***Done and walked end to
      end on the tablet AVD, twice, with the row read in `sqlite3` after each
      walk.*** That item put the picker on
      the press-and-hold dialog and left profile creation alone, on the grounds
      that the whole of 20.4 is about that path being too long for somebody
      meeting the app for the first time. It also wrote down the condition for
      moving it: *"If it ever moves into creation it belongs at the end, not the
      start, and it needs the owner's eye rather than a session's."* This note
      is that eye.

      **So it goes at the end and it stays skippable.** A face is the one
      question on that path a rider can answer without knowing anything — it is
      not the weight, the birth year or the FTP — and it is the only one that is
      *fun*, which is an argument for it being last rather than an argument
      against it being there. The default remains derived from the row id
      (20.2.2), so a rider who taps straight past still has a face and the
      column still says *never chose*.

      **"The end" turned out to mean *after the questions and before the
      number*, and 15.8.1 is why.** The obvious reading — last step of all —
      cannot be built: the profile is written the moment the rider leaves the
      FTP reveal, deliberately, so that walking away mid-account-offer still
      leaves a rideable bike. A face step *after* that point would either write
      the row twice or hold it back, and holding it back is the thing 15.8.1
      exists to forbid. So the reveal stays the ending and *Pick a face* is the
      last thing the rider **chooses**.

      **Both claims were watched on the device rather than reasoned about.**
      *Sam* touched the picker and the row reads `lilac:reed`; *Jo* pressed
      Continue without touching anything and the row is **NULL**, with `Avatar
      .defaultFor(5)` drawing the sky disc on the selector. That is 20.2.2's
      distinction surviving its first contact with a screen that shows a face
      before the rider has chosen one.

      **The step has no skip control and does not apologise for existing.**
      There is a face on screen from the first frame and `Continue` is live
      throughout, so *"optional"* would be a word spent on something the rider
      can already see (Phase 26). The subheading says what the step is for
      instead: *"So the bike knows you at a glance. Change it any time."*

      **The suggested colour is a guess and is honest about being one.** A new
      row has no id, so `Avatar.defaultFor` cannot be asked the question it
      exists to answer; the caller passes the number of profiles already on the
      bike, which is what the autoincrement usually hands out next. It matched
      on both walks. A rider who picks gets exactly what they picked; a rider
      who does not care may see a different colour later, and that is the price
      of not writing a value down for somebody who expressed no preference

- [x] **20.6.3** **What happens to a rider who already chose `rose:bolt`.**
      ***Done, and confirmed against a real row rather than a fixture*** — the
      test device's profile 1 has said `rose:bolt` since the fifty-eighth
      sitting and now draws as rose with an `R`, with the column untouched. The
      other six retired ids are pinned in `AvatarTest`.
      `AvatarMark` is retired by 20.6.1, not renamed: the six icon marks stop
      being offered and the ids stop being recognised. `Avatar.parse` already
      handles this exactly as it should — a known colour with an unknown mark
      keeps the colour and falls back to the initial — so the one profile on the
      test device that chose a bolt becomes *rose with an initial* rather than a
      crash or a blank disc. **That is the graceful-degradation clause in
      `Avatar`'s KDoc being cashed in for the first time**, and it is worth
      noting that it was written for a *newer* app's value being read by an
      older one and is doing the opposite job here. Nothing is migrated: the
      column keeps the string it has

- [x] **20.6.4** **The level, overlaid on the face — and this half needs no
      argument.** ***Done and watched on the tablet AVD at two tile sizes***,
      three profiles and five, where the badge scales with the face exactly as
      rule 2 asks. *"lvl should be part of the avatar (overlaid somehow)."* A
      level badge sitting on the corner of a face is what every game the owner
      is thinking of does, and it says the thing `RiderScore` exists to say:
      this is **who somebody is**, not a measurement of them. It also buys back
      a row on the tile, which is where the FTP goes.

      **It stays one component**, which is the whole point of `RiderScore` and
      of `RiderAvatar` — a second badge drawn by the selector is how the avatar
      came to be on the power-zone palette in the first place. So `RiderAvatar`
      gains the level as an optional parameter and `RiderScore` gains a compact
      form, and **all four of `RiderScore`'s rules survive**: it still says
      `LVL` and a number, it is still never amber, and a guest still gets
      neither face nor badge.

      **It is not drawn at 32 dp**, and that is a rule rather than an accident:
      the household row and the dashboard greeting keep the pill *beside* the
      name, because a badge shrunk onto a 32 dp disc is two illegible things
      instead of one legible one. The caller decides by not passing a level —
      and below `LEVEL_BADGE_FLOOR` (56 dp) it is not drawn even if one is
      passed, which is the single place `RiderAvatar` declines to draw something
      it was handed. The alternative is a call site shipping an unreadable badge
      without ever seeing it.

      **Centred on the bottom edge rather than tucked into a corner, and the
      artwork is the reason.** An Open Peeps figure is a head and a pair of
      shoulders, so the bottom *corners* of the disc are where the drawing is
      and the bottom *centre* is a collar. A badge on the corner covers a
      shoulder and reads as a sticker; on the collar it reads as part of the
      same object, which is what *"part of the avatar"* asks for. It overhangs
      the disc by half its height so it is attached to the face rather than
      printed on it

- [x] **20.6.5** **The FTP under the avatar — the reversal, on one screen, and
      the reasoning is the item.** ***Done and watched on the tablet AVD***, and
      the tile is the same height it was: the level moving onto the face is what
      paid for the row. This is 26.1.1 undone where 26.1.1 was made,
      and it is worth being plain about that rather than filing it as a tweak.

      **What 26.1.1's argument actually was:** the screen's only question is
      *which of you is it*, nobody picks their profile by their FTP, the number
      moves on its own (Phase 7), and `150 W FTP` is two pieces of jargon on a
      tile. Every one of those sentences is still true.

      **What the note adds that the argument did not have:** *"I feel like FTP
      and lvl are both important."* That is not a claim about the picker's job —
      it is a claim about the rider's relationship with their own number, and it
      is the same instinct that produced 26.4 (a level shown consistently) and
      Phase 28 (achievements the rider owns). A number the app moves by itself
      and never shows is a number the rider cannot argue with.

      **So it is drawn, once, quietly, and the placement carries the whole
      distinction.** Under the name rather than beside it, in the caption style
      the tile already uses for a secondary fact, so the eye still lands on the
      face and the name first and the FTP is read *second, by somebody who
      wanted it*. `150 W FTP` — number first, label last — is what was rejected
      and is not what goes back: `FTP 150 W` reads as a fact about the rider,
      where the old form read as a headline.

      **And it goes nowhere else — which the third note widened and then the
      surfaces themselves narrowed back.** *"Reintroduce FTP score somewhere
      close to avatar/name in locations where appropriate"* is an instruction to
      look at all of them rather than one, so all of them were looked at:

      - **The profile selector** — the FTP is genuinely absent beside a name
        here, this is the screen 26.1.1 took it off, and it is the rider's own
        number on their own tile. **Yes.**
      - **The dashboard greeting** — the dashboard already draws the FTP, big,
        in `FtpGlanceCard`, with its trend line and a route into *Your FTP*.
        Putting it beside the greeting too is the same number twice on one
        screen, and the greeting row is the one row in this app where a level
        badge and an FTP would genuinely share a line. **No, and it is not a
        refusal — the number is already there.**
      - **The household panel and any leaderboard** — these are *other people's*
        numbers. An FTP beside a housemate's name turns a presence card into a
        fitness ranking, which is 24.2's competition-nobody-entered arriving by
        the back door, and it is a measurement of a person published to the rest
        of the house without them being asked. **No.**
      - **The overlay** — 18.6. **No.**

      So the scope the owner first proposed and the scope the surfaces support
      are the same one, arrived at from both ends. See **26.4.8**, which is
      where the rule change is written down rather than left implicit in a diff

- [ ] **20.6.7** **"Someone could be lvl 20 but only 50 FTP, so not a very good
      rider. The goal should be FTP, not lvl."** ***The owner's third note, and
      it is the sharpest thing said about `RiderLevel` since it was built.***
      It is also, read carefully, **not a complaint about the level being
      wrong** — it is the observation that the level does not measure what the
      owner wants the app to be pointing at.

      **The level is already built to say exactly that**, and this note is worth
      keeping beside the rule rather than treated as a contradiction of it.
      `RiderScore`'s own KDoc: the number's only honest claim is *has ridden
      more*, never *is fitter*. A rider at level 20 on an FTP of 50 is a rider
      who has ridden a great deal and is not strong, and the badge is telling
      the truth about them. **The note's real content is that the app is
      currently loudest about the accumulation and quietest about the ability**,
      on the one screen where a rider is looking at themselves.

      **What follows for this sitting is small and deliberate:** the level does
      not change, is not rescaled, and is not recoloured. The FTP appears beside
      it on the tile (20.6.5), which is precisely the correction the note asks
      for — *"for now can we at least reintroduce FTP score"*.

      **What follows later is the owner's, and they said so:** *"Maybe i should
      go away and design something and then come back to you on this."* **That
      is an open item with their name on it, in the same family as 24.3.12a**,
      and a session that finds it still open should say so rather than invent a
      progression system. Two things worth handing them when they come back:

      1. **Phase 28 is the other half of this thought** and is deliberately
         unbuilt at their own weighting of *"one for the backlog"*. Achievements
         are things a rider *owns*; a level is a thing the app *says*. A design
         that makes ability visible probably lives there rather than in a
         bigger number.
      2. **7.11 is the honest version of "the goal should be FTP"** — auto-FTP
         that can go **down** as well as up. An FTP that only ever rises is an
         accumulation wearing a measurement's clothes, which is the exact
         criticism this note makes of the level

- [ ] **20.6.6** **What is deliberately not decided here: a photograph.**
      20.2.4 and 20.2.5 (the camera, the gallery, and the EXIF stripping that
      must travel with them) are untouched by these notes and stay open. A
      vendored set answers *"the ones we have are not good"* completely and a
      photograph is a different feature with a privacy surface on it — the note
      asked for a downloaded set, in those words, and got one
