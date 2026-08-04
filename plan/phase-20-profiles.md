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

- [ ] **20.3.1** **Take the watt field off profile creation.** Whatever replaces
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
- [ ] **20.3.3** **Design it as a screen, not a dialog.** "Priority is GREAT UX"
      is the owner's emphasis and an `AlertDialog` with three stacked
      `OutlinedTextField`s on a 1280 × 720 dp tablet is the opposite of it. This
      is the first thing a rider sees and it is currently the least designed
      surface in the app. Full-bleed, readable at the distance a bike is set up
      from, and it may well be more than one step
- [ ] **20.3.4** **Whatever number it lands on, say where it came from.** The
      rider must be able to see that this is an estimate and not something they
      told the app. It matters directly: an estimated FTP that is too high draws
      every early ride in Zone 2 and makes the app feel like it is not working,
      and a rider who knows the number is a guess will change it. This is the
      same rule as 16.1.6's power caption and 7.10.1's measured-vs-claimed mark
      — **and it needs a source on `ftp_history`**, which today has
      `AutoBreakthrough`, `AutoBreakthroughReverted` and the rider's own. An
      estimate is a fourth thing and must not be filed as a claim the rider made
- [ ] **20.3.5** **Then let the riding correct it, and say that it will.** The
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
- [ ] **20.3.7** **Route B needs columns, so it needs a migration** (12.5) —
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
