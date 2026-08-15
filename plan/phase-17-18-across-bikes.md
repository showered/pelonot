> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 17: Companion web application — nice to have, account tier only

Only worth starting once 14 and 15 work; it is a view onto the same Supabase
project and has nothing to show before rides are reaching it.

**Where it sits on the ladder, and the thing it must not get wrong.** The web
app can only ever see riders with accounts. A household profile with no account
**does not exist in the cloud at all** — not as an empty row, not as a
placeholder — so the web app has to be built knowing that a rider's household
is mostly invisible to it. The failure to avoid is the obvious one: showing
"3 of your household have no rides" when the truth is "3 of your household
chose not to have accounts, and their rides are on the bike where they belong".

The upside is worth naming too. The bike's tablet is a bad place to type, and
the web app is the natural home for everything social that involves words —
friend requests, display names, bios, ride titles. **Anything that requires a
keyboard should be possible on the web and optional on the bike.**

- [x] **17.1** Stack and repo layout. A separate top-level `web/` directory or a separate repo — **the Android build must never depend on it**

      *Decided, 3 August 2026, and the owner's reason for bringing it forward is
      worth recording: the web app is not only a destination, it is **the
      cheapest way for either of us to see what the bike put in the cloud**.
      A ride that reaches Supabase is currently invisible without a
      Management-API query, which means every cloud defect in this project's
      history has been found by SQL rather than by looking.*

      ***`web/`, static, and with no build step*** — hand-written HTML, CSS and
      ES modules, with the Supabase JS client loaded from the CDN and pinned by
      version. The reasoning is the same one that keeps `cloud.properties` empty
      and the class library in the APK: this is a hobby project on a household
      endpoint, and a toolchain is a thing that rots between sittings. It opens
      from `file://` for development and drops onto any static host for real
      use. **No npm, no bundler, and nothing the Gradle build can see.**
      If it ever needs a framework it can grow one; starting with one buys
      nothing today and costs a maintenance surface immediately
- [x] **17.1a** **Is this a monorepo? — the owner's note, 3 August 2026.**
      Verbatim: *"Do we need to restructure the repo a bit to become a monorepo,
      so the android app can sit alongside other apps such as the Web app, and
      further down the road — who knows! Just those two for now anyway. Just my
      2 cents. Do what you think is best."*

      **The recommendation is: it already is one, and the restructuring is not
      worth its cost.** The repository has held several deliverables for a long
      time — `classlibrary/` is a Python generator, `calibration/` is a dataset
      with its own method, `supabase/` is the schema, `plan/` is the plan — and
      none of them is part of the Android build. Adding `web/` beside them is
      the monorepo shape the note is asking for, with no move required.

      What "restructuring" would actually mean is **moving the Gradle root**:
      `settings.gradle.kts`, `gradlew`, `build.gradle.kts` and `app/` down into
      an `android/` directory. That is a real cost against a benefit that is
      currently zero — every path in `CLAUDE.md` and `HARDWARE.md`, the CI
      workflow, `app/schemas`, the `run-as` database recipes and
      `CloudConfigFenceTest`'s `File("..")` all name the current layout, and
      none of them gets simpler afterwards. The Gradle build does not reach
      outside `app/` today and must not start; **that property, not the
      directory depth, is what makes the two apps independent.**

      So two rules are written down instead of a move being made:

      - **No build may depend on another's output.** 17.1 already says the
        Android build must never depend on the web app; the reverse is equally
        binding, and the web app must be openable with nothing installed —
        which 17.1's *no build step* decision is what guarantees.
      - **The root README says what each top-level directory is**, because a
        repo with seven of them and no map is where a monorepo actually goes
        wrong. Not in the nesting.

      Revisit the day a third *build* arrives, not a third directory: two Gradle
      projects, or a shared TypeScript package between two web apps, would make
      a root-level workspace file earn its keep. Until then this is a monorepo
      without the tooling, which is the cheap end of the trade
- [x] **17.13** **The pairing page — `link.html`, and it is the first thing to
      build here** (15.6). The whole of the QR sign-in flow lands on this page,
      it is the reason the web app exists before it has anything pretty to show,
      and it is one screen: sign in (or sign up), see which bike is asking, and
      confirm. **It must name the device** (15.6.5), and it must work on a phone
      held in one hand, which is the only device it will ever be opened on
- [x] **17.14** **Where the endpoint comes from, on a static page with no build
      step.** The same rule as 14.10 and for the same reason: not in the source.
      A `config.js` beside the page, git-ignored, with a checked-in
      `config.example.js` — and the page says plainly that it is unconfigured
      rather than failing in the console, which is where this project's cloud
      defects have historically gone to die
- [x] **17.15** **One design system across both — the owner's note, 3 August
      2026.** Verbatim: *"I believe we already utilise a design system but now,
      more than ever, since we are building a companion web app, they should be
      singing from the same hymn sheet. This shouldn't need to create much
      overhead, in fact it should save time and compute, if we can already know
      what colour, spacing and typography palettes we're working with. Native
      and web app should have a similar feel."*

      **Agreed, and the "saves time" part is the true part.** The web app's
      first stylesheet had already invented its own greens and its own gaps, an
      hour after the app's own were sitting in `Color.kt` — which is how two
      surfaces of one product start feeling like two products, and it costs
      *more* effort rather than less, because every new element is a small
      colour decision taken again.

      `web/tokens.css` is now the shared sheet: surfaces, text, outlines, the
      brand teal, the four live metric accents, the spacing steps, and the
      760 dp readable-width cap — each value naming the Kotlin original it was
      transcribed from. `app.css` holds no literal colour or gap.

      Three decisions inside it worth keeping:

      - **The metric accents do not flip with the theme**, exactly as they do
        not in the app. Coral means watts; a rider who learnt that at night
        must not relearn it in daylight. They identify a measurement rather
        than decorating a surface.
      - **The zone palettes are deliberately not copied.** The web app draws no
        power or heart-rate zone today, and a palette copied ahead of a use is
        one that will be stale by the time it has one — the same argument as
        21.1.4's *do not collect a field nothing reads*.
      - **The width cap travels**, because 760 is a fact about eyes rather than
        about Android (22.2.6)
- [x] **17.16** **It is hosted — the owner's note, 4 August 2026.** Verbatim:
      *"Website is now running here: https://pelonot.showered.workers.dev/"*

      **Confirmed live, and the deployed bytes are the repo's.** `link.html`
      came back byte-identical to `web/link.html`; `index.html` serves from `/`;
      `config.js` is deployed beside them, which is what makes the page work at
      all and is the same decision as 17.14 with the git-ignore removed at the
      far end.

      Two facts measured rather than assumed, both about the host rather than
      the code:

      - **The host trims `.html`.** `/link.html` answers **307** to `/link`.
        That matters because the QR code the bike draws is
        `…/link.html#CODE`, and a fragment is the one part of a URL a redirect
        could plausibly drop. It does not: loaded in a browser, the page ends
        at `/link#ABCD2345` with the fragment intact, reaches the live project,
        and answers *"That code has expired"* — which is `device_link_describe`
        working as `anon` from the deployed page, the thing 15.6.6 needed and
        the flow this note makes real.
      - **The endpoint and its publishable key are now genuinely published**,
        which is what hosting a static Supabase client means and is not a leak.
        It is also exactly the condition 18.11.1 was written against, and that
        item stops being hypothetical the moment this URL exists — read it
        next, because as of this note the setting it names is still open
- [x] **17.16.1** **The bike's QR still points at the emulator.**
      *Done by the owner in their own `local.properties`, 4 August:
      `pelonot.webUrl=https://pelonot.showered.workers.dev`. The original ask
      follows.*
      `pelonot.webUrl` in `local.properties` is `http://10.0.2.2:8000` — the
      dev server's address *as seen from inside an AVD*, which is unreachable
      from a phone and meaningless off that emulator. So the QR the bike draws
      today is a code nobody can scan, on a build that has a real page to point
      at. One line, and it is the owner's file rather than the repo's:
      `pelonot.webUrl=https://pelonot.showered.workers.dev`. Worth doing
      before the next bike sitting, because 15.6's remaining unknowns
      (15.6.4's hand-off, the confirmation the owner has not yet given it) all
      need a phone that can open the page
- [ ] **17.16.2** **How it is deployed is written down nowhere.** `web/README.md`
      says how to *run* the app — `python3 -m http.server`, or open the file —
      and says nothing about where it now lives or how a change reaches it.
      That is the same gap 14.10 closed on the Android side: a fact that lives
      only in the owner's shell history is a fact the project does not have.
      The README should name the host, the command that redeploys it, and the
      one thing a static host adds to this app's threat model — **nothing
      checks that the deployed copy is the committed one**. Today they match,
      and today is the only day that has been checked.

      *And they no longer do. Measured 4 August, the same day: `link.js` on the
      host is the pre-17.16.5 version and `link.html` is missing the element
      that fix added. It took under a day, and it cost the owner a broken
      pairing flow they then had to report — see 17.16.6. The prediction in the
      paragraph above was right, which is an argument for writing the deploy
      down rather than a consolation.*
- [ ] **17.16.3** **Two publishable keys are in play, and the project should
      pick one.** The deployed `config.js` carries the legacy JWT anon key
      (`eyJ…`); the working copy of `web/config.js` carries the newer
      `sb_publishable_…` form. Both are the publishable key and neither is a
      secret, so this is tidiness rather than exposure — but the two forms
      revoke separately, which makes it a trap the day 14.11.4's rotation
      happens: rotating the one you remember leaves the one on the internet
      working. Say which form this project uses, in `config.example.js` and in
      `cloud.properties`, and use it in both places
- [x] **17.16.4** **Point the project's Site URL at the host now that there is
      one.** ***Done — see 15.7.6***, where it arrived a second time as a
      rider-visible fault rather than as tidiness, which is what got it built.
      `site_url` and `uri_allow_list` are both set and the change was measured
      against the link `generate_link` mints. Original note follows.

      Email confirmation links go to *Authentication → URL
      Configuration → Site URL*, so with it still pointing at a dev address
      every account created from the live page gets a confirmation link to
      somewhere that is not running — and 15.1's "signed up, not confirmed, no
      session" state is exactly where that rider is stranded. `web/README.md`
      already warns about this in the abstract; it is concrete now
- [x] **17.16.5** **A copy defect on the live pairing page — and underneath it
      a state bug, which is why it was worth opening the file for.** With an
      unknown or expired code the card read *"This will sign in"* above *"That
      code has expired"*: a promise about a device the page had just said it
      could not find.

      **The form was showing too, and that is the part that matters.** `route()`
      decided whether the code was known by **reading the rendered text** —
      `el('device-label').textContent !== 'That code has expired'` — and
      `onAuthStateChange` fires its first event immediately, usually while the
      label still reads `…`. So the page asked an unknown code's rider for an
      email and a password, directly under a card saying it could not find the
      bike. **That is the exact thing 15.6.5 exists to prevent**: this is a
      page a QR code can point anybody at, and *naming what is asking, before
      anything is asked for*, is the whole of its defence against being a
      phishing primitive. A protection that reads its own DOM loses a race it
      has no reason to be in.

      `described` is now a variable — `null` until the server answers, then
      true or false — and nothing is offered until it is true, which also
      covers the case nobody had thought about: the seconds *before* the first
      answer. The expired card now says *"Nothing to sign in"*, and it reopens
      the type-a-code box, so a rider whose code lapsed has a way forward
      instead of a dead end.

      ***Observed against the live endpoint***, both paths, in a browser. A
      pairing code minted for the purpose (`device_link_begin`, five-minute
      life, secret never leaving the shell) is described back under its own
      label with the form shown and the caption intact; an unknown code gets no
      form, no confirm, the honest caption and the retry box. One incidental
      finding, harmless and worth knowing: **changing only the fragment does
      not re-describe**, because a hash change does not reload a document. The
      QR always opens a fresh page and the retry box goes through the button,
      so nothing reaches that path today
- [x] **17.16.6** **"There was no way of actually signing in" — the owner's
      note, 4 August 2026.** Verbatim: *"The 'link my account' doesn't work. I
      scanned the QR code and it sent me to the page with the code, but there
      was no way of actually signing in. Please check this."*

      **Checked, and it is two faults stacked, both measured against the live
      host rather than reasoned about.**

      **The first is that the fix was never deployed.** `link.js` at
      https://pelonot.showered.workers.dev/link.js is the **pre-17.16.5**
      version — no `described` variable, `route()` still reading its own DOM,
      and `describe()`'s error path returning **without** reopening the
      type-a-code box. So on a code the server did not recognise, the deployed
      page rendered the expiry card and *nothing else*: no form, no input, no
      way forward. That is the owner's sentence exactly. 17.16.5 was observed
      working, in a browser, against the live endpoint — from a local copy. The
      repo was fixed; the internet was not. **17.16.2 predicted this in the same
      sitting** — *"nothing checks that the deployed copy is the committed one.
      Today they match, and today is the only day that has been checked"* — and
      it drifted within a day. `lib.js` still matches; `link.html` and `link.js`
      do not

      **The second is the one worth changing, because 17.16.5's own fix still
      leaves the rider stuck.** Even on the current repo version, an expired
      code gets the honest card and the retry box and **still no way to sign
      in** — the sign-in form is gated on `described === true`. That gate is
      over-broad, and the reason is worth writing down: 17.16.5 collapsed two
      different questions into one flag. *May the rider hand a session to this
      device?* must be gated on the device being named — that is 15.6.5 and it
      is the page's whole defence against being pointed at a stranger's bike.
      *May the rider sign in to Pelonot on their own phone?* has nothing to do
      with any bike. It is the same sign-in `index.html` offers, to the same
      project, and gating it on a five-minute pairing code buys no safety at all

      **So the page inverts**: signing in is always available, the pairing is
      confirmed separately, and **the confirm step still names the device** —
      15.6.5 kept where it actually lives. A rider whose code lapsed signs in,
      asks the bike for a fresh code, types or re-scans it, and confirms; the
      session persists, so the second attempt is the fast one

      **And the five minutes is the proximate cause, which is a UX fact rather
      than a bug.** 15.6.1's TTL is right for an unclaimed pairing sitting in a
      table. It is short for a human journey that includes unlocking a phone,
      opening a camera, waiting on a browser, finding a password manager and
      authenticating to it — especially the *first* time, which is the only time
      the owner has had. Inverting the page is the fix rather than a longer TTL:
      after the first sign-in the phone is already signed in, and the remaining
      journey is two taps well inside five minutes

      ***Done, and all four states measured against the live project*** — a
      pairing minted for the purpose under the bike's own label, and an unknown
      code beside it:

      | session | code | what the page offers |
      |---------|------|----------------------|
      | signed out | live | the bike named, the sign-in form |
      | signed out | expired | *"Nothing to sign in / That code has expired"*, the retry box, **and the sign-in form** — the owner's case |
      | signed in | live | the bike named, *"Signed in as …"*, **Sign this bike in** |
      | signed in | expired | *"Signed in as …"* and the retry box, no confirm |

      The recovery path was driven end to end rather than reasoned about: from
      the expired card, typing `ymmh d7za` — lower case, with the space a person
      reading eight characters off a screen would put in — names the bike and
      clears the retry box.

      *Three things fixed on the way that were not in the note.* The
      `signOut()` inside the fallback hand-off came back through `route()` and
      **redrew the sign-in form underneath the word "Done"**, which the old
      gating had hidden by accident. Who you are is now said once, at the top,
      rather than duplicated inside the confirm card. And a `hashchange`
      listener re-describes the code: 17.16.5 found that changing only the
      fragment does not reload a document and correctly noted nothing reached
      that path — **this item makes it reachable**, because a rider whose code
      lapsed is now told to go and fetch another one and may well re-scan into
      the open page.

      **It is fixed in the repo and not on the internet.** Redeploying is the
      owner's, and `./web/check-deployed.sh` (17.16.7) says whether it has
      happened

      ***And it has — the owner redeployed, 4 August, and the check says so***:
      all seven files the same, exit 0. See 17.16.8. So this item is closed on
      both halves at last — the fix exists and a rider can meet it
- [x] **17.16.7** **Nothing tells anyone the deployed copy has drifted, and now
      it has cost something.** 17.16.2 asks for the deploy to be written down;
      this is the smaller half that should not wait for it. `curl` the deployed
      files and diff them against the working tree — it is a handful of lines,
      needs no credentials, and turns *"is the live page the one I fixed?"* from
      a thing nobody can answer into one command. It is the same argument as
      `CloudConfigFenceTest` and `ClassLibraryAssetsTest`: **the shipped
      artefact is what a rider meets, and a generator nobody runs cannot vouch
      for it**

      ***Done — `web/check-deployed.sh`, and its first run is the evidence for
      17.16.6.*** Seven files fetched and diffed, `config.js` skipped because it
      is git-ignored by design and the deployed one is *meant* to differ.
      Against the live host today: `index.html`, `app.css`, `tokens.css`,
      `lib.js` and `rides.js` the same, `link.html` and `link.js` drifted. It
      exits non-zero when they differ, so it can be a CI step (19.1.4) the day
      the deploy is written down
- [x] **17.16.8** **The redeploy happened, and the check is what says so — the
      owner's note, 4 August 2026.** Verbatim: *"Live site deployment — The live
      URL is now up to date"*.

      Confirmed rather than taken on trust, which is the entire point of having
      built 17.16.7 the sitting before: `./web/check-deployed.sh` now reports
      **seven files the same and exit 0**, against `link.html` and `link.js`
      drifted twenty-four hours earlier. So 17.16.6's fix — sign in whenever,
      confirm separately — is on the internet, and the state that produced the
      owner's bug report no longer exists.

      **What is worth taking from this is the shape of the loop rather than the
      deploy.** A fix landed in the repo; a note in the plan said nothing checked
      whether it had shipped (17.16.2); it had not; a rider met the unfixed page;
      one command was written; the owner deployed; the same command closed it.
      The gap that made all of this possible was open for exactly one drift, and
      the reason it was one is that the check exists — **not** that the deploy is
      now reliable. It still is not: 17.16.2 is untouched, `web/README.md` still
      names neither the host nor the command, and the next drift will be found
      only by somebody choosing to run the script.

      So this item closes and **17.16.2 is what it hands forward**, with its
      priority raised by having been paid for twice. Two things belong in
      `web/README.md`: the redeploy command the owner has now run twice, and a
      line saying to run `check-deployed.sh` after it. The second is worth more
      than the first — a written-down command is a fact the project has, but a
      command that *verifies* is the one that survives the command changing
- [x] **17.15.1** **Typography is the half not yet shared.** The tokens cover
      colour, spacing and shape; the web app is still on a system font stack
      while the app has its own type scale. Worth doing when the web app has
      more than three screens — before that, a webfont is weight on a page whose
      whole virtue is that it opens instantly with nothing installed (17.1)

      ***Done, 15 August 2026, and the item's own condition is what triggered
      it***: it said *"worth doing when the web app has more than three
      screens"*, and this sitting took it from two to five. `tokens.css` now
      carries the six type steps and the two weights the web app actually uses,
      each naming its `Type.kt` original, and `app.css` names no font size of
      its own.

      **Still no webfont, and the refusal is the same argument the item made.**
      `Type.kt` sets Inter everywhere, so the stack names Inter *first* — a
      device that already has it matches the app exactly, and every other device
      gets its own system face. A 100 KB download to make a heading match is the
      wrong trade on a page whose whole virtue is that it opens with nothing
      installed.

      One decision inside it: the sizes are the `sp` values written as `px`.
      What travels between a 240 dpi tablet and a browser at its default 16 is
      the *ratio* between the steps rather than the absolute, and pinning the
      ratios is what makes the two surfaces feel like one product
- [ ] **17.15.2** **Nothing keeps the two in step, and that is stated rather
      than hidden.** There is no shared build by design (17.1a), so a colour
      changed in `Color.kt` does not change `tokens.css`. What keeps the drift
      small today is that the file holds only what both surfaces genuinely
      share and every value names its original, so checking is a grep. If it
      ever needs to be mechanical, the cheap version is a script in
      `classlibrary/`'s spirit — parse `Color.kt`, emit `tokens.css`, and fail
      if the checked-in file differs. **Do not build that until the drift has
      actually happened once**: a generator nobody runs is worse than a copy
      somebody reads

      **Measured on 13 August 2026, and it has not drifted**, which is why this
      box is still empty and no script exists. All 28 colour declarations, the
      six spacings and the 760 dp cap match `Color.kt` and `Theme.kt` at
      `09f5a86`. The forty-fifth sitting took the measurement precisely because
      the *pattern* — a written claim nobody re-checks — had paid out twice
      already (R10, then 23.2.8), and here the honest outcome is that the claim
      is true and the decision above stands.

      **Two things the grep turned up that it could not have.** Two light-theme
      values named no Kotlin original, so the file's own promise — that checking
      is a grep — was false for them; they are named now. And
      **`--radius-control: 10px` was never a copy at all**: it sat under a
      comment reading *"Shapes, matching the app's card and control radii"*, and
      there is no 10 dp shape in `Shapes.kt`, nor has there been — the app's
      buttons are pills and its text fields are 4 dp. The **comment** is
      corrected rather than the number, because a pill on a full-width form on
      a phone is a design change and this was a sentence rather than a decision.
      Worth knowing before the generator above is ever written: it would have to
      allow for a token that is deliberately the web's own
### The sitting that built the rest of it — 15 August 2026

**Ten of the eleven items below were one line each and had never been touched;
the whole of Phase 17 past the pairing page was still `link.html` and a list of
dates.** They are ticked together because they were built together, in one
sitting, against the live project — and because eight of the ten turned out to
be *one* piece of work: a page with five views instead of two, and the schema
underneath it. `supabase/008_companion_web.sql` is that schema and its header
carries the reasoning that does not fit here.

**Everything below was driven in a browser against the live endpoint**, signed
in as a throwaway confirmed account created for the purpose and deleted after,
with three seeded rides — one with a stop in it, one with a gap in the series,
one condensed and one with no heart rate — because a page that has only ever
been looked at with one shape of data has not been checked.

- [x] **17.2** Auth shared with the app via the same Supabase project; a rider signs in once conceptually

      *Was already true and had never been ticked* — `index.html` has offered
      the same email and password against the same project since 17.13. What
      this sitting adds is that signing in now leads somewhere: five views
      rather than a list. The sign-up path also stops repeating Supabase's own
      sentence at a rider — a project with sign-up closed answers *"signups not
      allowed"*, which is a fact about a setting, and the page says *"this
      Pelonot is invitation only, ask whoever set the bike up"* instead. That
      is dead copy on this project today (18.11.1) and is one dashboard toggle
      away from not being

- [x] **17.3** Ride history and ride detail, reusing the chart definitions from 16 conceptually if not literally

      **Conceptually, and the concepts are the ones that were hard-won.** The
      traces break across a gap (16.1.2); the power trace has **zone bands**
      behind it from the rider's FTP and the heart rate has its own five-zone
      ramp, which is 21.4.2's rule and 21.2.1's insistence that the two ramps
      are not the same statement; time in zone counts the stopped seconds
      separately and names them, because **a stop is not Active Recovery**
      (19.1.2c) and `PEDALLING_RPM` is the app's one definition of pedalling.

      ***Two defects were found in code that has been on the internet for a
      fortnight***, and both are the same shape — a reader that had never been
      run against a real payload:

      - **`pm` is `1` and `0`, not `true` and `false`.**
        `MetricsPayload.CompactBoolean` writes digits deliberately (`true`
        across 2,700 samples is 13 KB on a 49 KB payload and puts the ride over
        its budget), and the page compared against `true`. So **every genuinely
        measured ride said *"this ride does not record where its watts came
        from"*** — the provenance caption, the one thing on that card whose
        entire job is not to overclaim, quietly underclaiming instead. Measured
        on a seeded ride: the caption flipped to *"Power measured by the bike"*
        the moment the comparison was fixed.
      - **The resolution key is `d`, not `detail`**, so a condensed ride never
        said it was one — 23.4.3 applies to any surface drawing the trace and
        this one was silently exempt.

      And a third that is not a defect but was wrong on the page: the list
      showed `CLB-01`. `class_templates` is world-readable, so the ride now
      carries its class's *title*

- [x] **17.4** Profile customisation: display name, avatar, bio, FTP, units

      Name, bio, FTP, weight, maximum heart rate and units, all on one screen
      and saved in **one write** — 7.9's read-modify-write defect has a web
      shape, and it is two `update` calls off one button.

      **The avatar is deliberately not an upload.** It is the app's own idea —
      a deterministic colour from the five in `ProfileSelectorScreen` and the
      rider's initial — so nobody has to store, moderate or resize a
      photograph, which is a surface a hobby project should think twice about
      (17.9's argument, one field along). What differs from the app is only
      what indexes the palette: the app has a local profile id and the cloud
      has an account uuid, so one rider can have two colours across the two
      surfaces. That is a consequence of 003's key choice rather than a bug.

      **The units are on the profile rather than in `localStorage`**, so they
      are the same answer on a phone and on a laptop; the weight field is read
      in whatever unit its label says, which is the whole reason the label
      carries one

- [x] **17.5** Friends — request, accept, block. New `friendships` table with its own RLS; this is the first schema where a rider can see another rider's data and it deserves more care than the rest

      ***Closed as* not *to be built** — and it was already closed, in code,
      before this sitting: `007_everyone_leaderboard.sql` **drops** the
      `friendships` table it had created minutes earlier, on the owner's
      instruction of 3 August. Verbatim, in 18.11: *"there will only be 3 or 4
      users! So I think everyone should just have visibility over everyone's
      scores."* Four people who already know each other do not send each other
      requests.

      The item is ticked rather than deleted because **the care it asks for is
      the part that survived**: this is still the first schema where a rider
      can see another rider's data, and what it got instead of its own RLS is
      narrower — `SECURITY DEFINER` functions that return the columns they
      name, with the policies on `profiles` and `workouts` untouched at "your
      own rows and nobody else's". A function cannot leak a column added after
      it was written; a relaxed policy can

- [x] **17.6** A light activity feed: friends' recent rides, kudos, a comment. Deliberately not a full social network

      Built as written, including the restraint. `activity_feed` is everybody's
      recent rides with the two counts beside each one, in a single request;
      `give_kudos` / `remove_kudos` return the new count so the page never asks
      a second question to redraw; and there is **one comment** — a body, an
      author, a ride, no threads, no replies, no mentions, no edit.

      **The moderation floor is a delete rather than a graph.** The author can
      delete their own comment and **the rider whose ride it is can delete any
      comment on it**. 18.8 asks for mute, block and report from the first
      version that has a feed, and with public sign-up left open (18.11.1) that
      argument is live rather than theoretical — but at four invited accounts
      the delete is the thing that would actually be used, and 18.8 is the item
      to build the day this has more riders than 18.11 was written for.

      The feed does not carry heart rate, RPE or the sample series. That is
      007's line held one feature along: a leaderboard's worth of visibility

- [x] **17.7** **Private by default.** Nothing is visible to anyone until the rider opts in, with per-ride visibility (private / friends / public). Defaulting to visible would publish training history people did not know they were publishing

      **Kept, and it is the item that decided the shape of the whole
      migration.** `profiles.share_activity` defaults to **false**, so on the
      day `008` runs the feed is empty for everybody and stays empty until
      somebody turns their own sharing on.

      **The reason it had to be false is not the principle, it is a
      measurement.** 18.11.1 states the accepted blast radius of leaving public
      sign-up open in exactly these words: a stranger who registers sees
      *"display names, class ids, durations, output — not ride dates, not RPE,
      not heart rate, not anyone's rows"*. **An activity feed is ride dates by
      construction.** Switching one on for everybody would have widened a risk
      the owner sized and accepted, without anybody deciding to — which is the
      quiet kind of change this plan exists to stop.

      The per-ride half is `workouts.hidden`, and it is deliberately *stronger*
      than the profile switch: a hidden ride leaves the leaderboard and the
      ghosts as well as the feed. Three visibility levels became two, because
      "friends" is not a thing this project has (17.5) and "public" is not a
      thing it does (17.9). ***Measured***: hiding one ride took its rider off
      that class's board and the class's rider count from three to two

- [x] **17.8** Self-hosters get the same deal as 14.10 — the endpoint is configured at build time, not typed in

      Already true since 17.14 and never ticked: a git-ignored `config.js`
      beside the page, a checked-in `config.example.js`, and a page that says
      it is unconfigured **on screen** rather than in a console. What this
      sitting adds is 17.16.3's answer to *which* publishable key form, in that
      example file and in `cloud.properties`, and a `check-deployed.sh` that
      reports which form the host is serving

- [x] **17.9** Decide what "public" means before shipping it: a public profile URL is an outward-facing surface with moderation and abuse implications a hobby project has to actually think about

      **Decided: nothing here is public, and the way that is enforced is that
      no unauthenticated surface exists.** Every function on the project
      requires `auth.uid()`; the anon key reaches the class library and nothing
      else. There is no URL in this app that shows a rider's name, ride or
      number to somebody who is not signed in, and no profile page addressable
      from outside.

      That is the question answered by **not building the thing that raises
      it**, which is the honest answer for a hobby project rather than a
      deferral: moderation, abuse and take-down are real obligations and the
      way to not owe them is to not publish. If a public profile is ever
      wanted, this item reopens with everything it originally asked for still
      unanswered

- [x] **17.10** The web app never implies a household member is missing data when they have simply never signed in. See the preamble — this is a copy problem with a data-model cause, and it is the one thing about this phase that is peculiar to Pelonot

      The Riders view is the whole of this, and it is a sentence rather than a
      mechanism: *"This is everyone with an **account**. Somebody who rides
      your bike without one is not missing from here — they were never in the
      cloud at all, and their rides are on the bike where they belong."*

      `rider_directory` is what makes it sayable: it lists every account and
      shows a rider who is not sharing as a name and a bio **with no numbers**,
      rather than as somebody with zero rides. Zero is a claim about training;
      absent is a claim about consent, and they are not the same sentence

- [x] **17.11** Manage friends, display name and bio here rather than on the bike (see the preamble). The Android side may mirror it read-only and lose nothing

      Name and bio are here; there are no friends to manage (17.5). **Ride
      titles are the third thing and were not in the item** — they belong to
      the same argument and are the clearest case of it, since a ride's name is
      the one piece of text a rider actually wants to type and the bike's
      tablet is the worst keyboard in the house.

      **The Android mirror does not exist yet and the plan should not pretend
      otherwise**: `ProfileDto` carries `id`, `name`, `ftp_watts` and
      `weight_kg`, so a bio, a maximum heart rate, the units and a ride's title
      are cloud-only today. Nothing is lost — the upsert only writes the
      columns it names, so the bike cannot wipe them — but a rider who sets a
      bio will not see it on the bike. That is **15.3.7a**

- [x] **17.12** The web app reads `metrics_payload`, so it is the consumer that makes 14.4.3's `payload_version` matter. Do not start 17.3 against an unversioned payload

      Honoured, and it paid for itself twice. `decodeMetrics` reads **both**
      shapes — an absent `v` is the pre-14.4 array-of-objects, and one row in
      the cloud is still in it — and rejects a ragged columnar payload outright
      rather than lining sample 900's power up with sample 900's cadence by
      luck. That is the same rule `MetricsPayload.toMetrics` applies on the
      Android side.

      What the *version* bought is smaller than what **reading the payload
      properly** bought: 17.3's two defects were both in this file, and both
      were a reader that had never met the real bytes

---

## Phase 18: Social across bikes — the networked tier, nice to have

**Read Phase 24 first.** The connectivity model splits social in two, and this
is the half that needs the network: friends signed in on *other* bikes.
Everything a rider can have with the people on their own bike is Phase 24, it
needs no account from anybody, and it should be built first — both because it
serves more riders and because it is a fraction of the work.

Everything here is behind a signed-in account and must vanish cleanly when
signed out — not grey out, not prompt, not appear at all.

Two shapes to keep straight, because they will otherwise be built twice:

- **A household leaderboard is a Room query. A friend leaderboard is a network
  call.** They render the same and they are not the same feature. Design the
  row rendering so 24.1 and 18.5 share it and nothing else.
- **A friend who happens to live in your house is on both lists.** The
  household one is authoritative — it is the same bike and it is offline —
  and a rider must never see themselves or a housemate twice.

- [x] **18.11** **No friend graph — the owner's note, 3 August 2026.** Verbatim:
      *"If this application were to scale to millions of users then we would
      need to add proper follow, unfollow, block, all that kind of stuff. But in
      this case, for now, there will only be 3 or 4 users! So I think everyone
      should just have visibility over everyone's scores for now. Leaderboards
      and ghosts should contain ALL registered users, in addition to ALL
      household users."*

      **Agreed, and a `friendships` table with a request / accept / block
      lifecycle had already been written and applied before this note arrived.
      It is dropped.** It was the right answer to a question nobody had asked:
      four people who already know each other do not need to send each other
      requests, and the graph is three tables of ceremony around a fact they all
      already agree on. 17.5 stays open for the day the answer changes.

      What replaces it is `supabase/007_everyone_leaderboard.sql`: two narrow
      `SECURITY DEFINER` functions, `class_leaderboard` and `class_ghost`.
      **The policies on `workouts` and `profiles` are unchanged** — still "your
      own rows and nobody else's" — because "everyone can see everyone's
      *scores*" is not the same sentence as "everyone can read everyone's
      rows", and the difference is ride dates, RPE ratings, the whole sample
      series and every column those tables grow later. The ghost even strips
      heart rate: a leaderboard's worth of visibility is what was agreed, and a
      resting heart rate is a medical-shaped fact rather than a sporting one
- [x] **18.11.1** **Turn public sign-up off** — ***closed by the owner as* not
      *to be done, 4 August 2026.*** Verbatim: *"Leave on public signup. It
      doesn't matter — it requires email validation anyway. Let's assume there's
      only valid signups."* The item is kept in full below, because the exposure
      it measures is real and the decision is to accept it rather than to
      disagree that it exists.

      **The owner's reason is factually supported and was re-measured in the
      twenty-sixth sitting**: the project answers `"mailer_autoconfirm": false`,
      so an account is not usable until somebody has opened a link in the inbox
      they claimed. That does not make registration private — anyone with an
      email address can still register — but it changes what "the public" means
      here from *anyone who finds the URL* to *anyone who finds the URL and
      wants an account on a stranger's exercise bike*, which at this project's
      scale is nobody.

      **What settled it was a collision the item had not spotted, and it is the
      thing to carry forward.** 18.11.1 and **15.8.2** are direct contradictions
      and neither mentioned the other: 15.8.2 says a rider creating their first
      profile *has no account by definition*, so the pairing page must let them
      sign **up** from their phone rather than sending them to the bike's own
      keyboard. Invite-only makes that flow impossible. So this is not a
      security item that lost to convenience — it is two items that could not
      both be built, and the one that serves the rider won. **The consequence is
      that 15.8.2 is unblocked and is built as written.**

      Two things that were true before this decision and stay true: the blast
      radius is still exactly what the measurement below says (leaderboard
      entries and ghost traces, never rows), and **17.16.3 is now the item that
      matters more** — if the door is deliberately open, which key is on the
      internet is the question worth being careful about.

      The original item, kept:

      **"Everyone registered" is a safe rule exactly as long as
      registering is not open to the public, and those are two settings in two
      places: 18.11 makes every account visible to every other, and Supabase's
      `disable_signup` decides who can become an account. **Hosting the web app
      publishes the anon key** — that is what the key is for — so with sign-up
      open, anyone who finds the URL can create an account and land on the
      household's leaderboard. Authentication → Providers → Email → *Allow new
      users to sign up*, off; add the household by invitation

      ***Measured, 4 August 2026, and it is now live rather than
      hypothetical.*** The project's own public settings endpoint answers
      **`"disable_signup": false`** with `email: true`, and 17.16 is the other
      half: the web app is hosted, its `config.js` publishes the endpoint and
      the publishable key, and `index.html` and `link.html` both draw a
      ***Create an account*** tab. `007` is applied, so a new account is on the
      household's board — with a display name and a score, and, through
      `class_ghost`, a second-by-second trace to ride against.

      **This is the owner's to change and nobody else's**: it is a setting on
      their Supabase account, not a line in this repo, and no session should
      touch it. Two minutes in the dashboard. Until it is done, the exposure is
      bounded and worth stating exactly, because "the leaderboard leaks" would
      be the wrong summary — `workouts` and `profiles` still hold "your own
      rows and nobody else's" (15.5.4, verified from a second account), so a
      stranger who registered would see **leaderboard entries and ghost
      traces**: display names, class ids, durations, output. Not ride dates,
      not RPE, not heart rate, not anyone's rows. That is the blast radius of
      one dashboard toggle, which is the argument for turning it off today
      rather than the argument that it can wait

      *(That last sentence is the one the owner overruled, and the paragraph is
      left standing because the blast radius it states is now the accepted risk
      rather than the argument against it. It is also the paragraph to re-read
      the day this project has more than four riders, which is the condition
      18.11 itself was written under.)*
- [ ] **18.1** Friends list and requests, mirroring 17.5
- [ ] **18.2** A feed of friends' recent rides on the dashboard, below the rider's own stats and never above them
- [ ] **18.3** Kudos, and nothing that requires typing during or just after a ride
- [ ] **18.4** Compare a class you both rode — same class, both traces, one chart. This is the version of a leaderboard that is actually motivating
- [x] **18.5** Friend leaderboard on the post-ride summary, alongside the rider's own history (11.4.1)
- [ ] **18.6** **The HUD stays social-free.** Nothing on the strip during a ride. It has half a second of attention and it belongs to the interval
- [x] **18.7** A comparison across riders is honest when both sides are measured watts off their own boards (2.1a), and misleading when either side is modelled. Carry the caveat on the modelled ones specifically rather than on all of them — a blanket disclaimer nobody reads is the same as none
- [ ] **18.8** Mute, block and report exist from the first version that has a feed, not the version after someone needs them
- [x] **18.9** Every screen in this phase is built on top of its Phase 24 equivalent rather than beside it. If 18.5 and 24.1 are two implementations of a leaderboard row, one of them will drift and it will be the one nobody rides against
- [ ] **18.10** A friend's numbers arrive over the network, so this phase inherits every rule in the *Corrections* table about failures that are caught and shown nowhere. An empty friend leaderboard must say whether it is empty or unreachable

---

### 18.12 The ghost across bikes — the networked half of the owner's note

**The owner's note is written up in Phase 24** (after 24.3.2), because the
household half needs no account from anybody and 18.9's rule is that every
screen here goes *on top of* its 24 equivalent rather than beside it. This
section is only what the network adds.

**There is a fact worth knowing before starting: the server side is already
built and nothing calls it.** `007` ships `class_ghost(p_class_id,
p_account_id)`, a `SECURITY DEFINER` function returning a second-by-second trace
with heart rate stripped, granted to `authenticated` and verified from a second
account by `supabase/verify_leaderboard.py`. It was written for 18.11's
leaderboard and no Android or web caller exists. So the across-bikes ghost is a
client feature against an endpoint that is already there and already tested —
which is the opposite of the usual order in this phase and worth not
rediscovering.

- [ ] **18.12.1** **The rival list is one list, household and cloud together,
      with the household authoritative.** 18.11's board already merges them and
      the preamble's rule applies unchanged: a friend who lives in your house
      appears once, from the Room query, because that side is offline and cannot
      fail
- [ ] **18.12.2** **The trace is fetched before the ride starts, never during
      it.** 15.3.6's rule — sync never runs on the ride's critical path — and
      the ghost is a worse offender than sync because it is *wanted* mid-ride: a
      rider on a garage wifi that drops at minute six must lose nothing but the
      network. Fetch at the moment the rival is chosen (24.3.3), hold it in
      memory, and a ride that has begun never touches the network again
- [ ] **18.12.3** **A ghost that could not be fetched says so before the ride,
      and there is no ghost.** 18.10's rule: empty and unreachable are different
      sentences. The failure to avoid is a rider who starts a class believing
      they are racing and finds out at minute two that they are not
- [ ] **18.12.4** **A cloud ghost cannot be checked for measured power the way
      24.3.7 checks a household one**, and that is a real gap rather than a
      detail: `class_ghost` returns a trace, and 14.4.7 puts `pm` in the payload
      per sample, but the function does not project it. Either it starts to, or
      the across-bikes ghost carries the caveat 18.7 asks for — on the modelled
      ones specifically, never as a blanket disclaimer

- [x] **17.16.9** **The pairing page tells the rider about the machinery — the
      owner's note, 5 August 2026.** **Done in the thirty-fifth sitting**, in
      the same change as 15.6.14 so that one deploy carries both.

      What the page says on the happy path now, measured against the live
      project with a real code minted by the tablet AVD: *"Link a bike /
      **Scanned — this will sign in** / sdk_gphone64_arm64 / Sign in here and
      the bike signs itself in."* and the form. That is the owner's sentence
      almost word for word — the link worked, now sign in — and the device name
      survives it, because 15.6.5 is not a style question.

      Three cuts, one per bullet below. **The echoed pairing code is gone** from
      the device card: the rider scanned it and it is in the URL bar above them,
      so printing it back is the page showing its working; it stays on the
      `need-code` card, where somebody is typing it. **The subtitle is gone** —
      a heading, a subtitle and a status line narrating the same thing is three
      voices, and the one sentence that earned its place has moved down to the
      card where the rider is about to act. **And `fallback-warning` was
      checked rather than rewritten from the armchair**, which the item asked
      for: `POST /functions/v1/link-device` answers **401, not 404**, so the
      Edge Function is deployed and this project mints the bike a session of its
      own — the warning never fires here. It keeps its first sentence, since a
      deployment without the function still needs it, and loses the second,
      which was a maintainer explaining himself inside a rider's sentence.

      All four states seen: unknown code and live code against the live project,
      the confirm step and *Done* by driving `route()` directly, which is as far
      as a session can take it without an account (15.7.7).

      The original note follows. Verbatim: *"After scanning QR code the
      'link device' page could be much nicer. Get rid of all the geeky fluff and
      just tell the user what they want to hear -- the link worked and now they
      just need to sign in / sign up."*

      **This is the third report against this one page and the first that is
      about its voice rather than its function.** 17.16.5 was a copy defect,
      17.16.6 was a dead end with no sign-in on it; both are fixed and both were
      about whether the rider *could* proceed. This one is about what the page
      says while they do, and it is Phase 26 arriving on the web app — the
      standing rule is the same one, and `link.html` breaks it in the place a
      first-time rider meets it.

      **What is on the page that the rider did not ask about**, reading it as
      somebody who has just pointed a camera at a bike:

      - **The device card is a fact about pairing, not about them.** *"This will
        sign in / PLTN-RB1VQ"* plus the pairing code underneath. The device name
        earns its place — 15.6.5 is a real anti-phishing argument and must not
        be lost — but the **code** is machinery: the rider has already scanned
        it, it is in the URL, and printing it back is the page showing its
        working. It is needed only on the `need-code` path, where the rider is
        typing it.
      - **`fallback-warning`** explains that this deployment hands over the
        phone's own session and *"you will be signed out on this phone"*,
        followed by a sentence distancing the app from its own limitation. That
        is a maintainer's note in a rider's sentence. Whether it is even true is
        deployment-dependent (15.6.9), so the fix is partly to find out which
        route the live project actually takes before writing anything.
      - **The status line and the heading say the same thing twice** — *"Link a
        bike"* over *"Sign in here and the bike signs itself in"* over a
        `#status` paragraph that also narrates.

      **What the rider wants to hear is exactly what the owner said**: the link
      worked, and now sign in. That is two sentences and one form, with the
      device named on the confirm step where the naming does its job.

      **Do not fix this by deleting text alone.** The page has four states
      (`need-code`, `signed-out`, `confirm`, `done`) and 17.16.6's whole lesson
      was that collapsing them is what broke it last time; they were all four
      measured against the live project and should be again. And the fix reaches
      a rider only when it is deployed, which is 17.16.2's still-open gap —
      **run `./web/check-deployed.sh` before believing this closed** (17.16.7)
