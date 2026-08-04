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
- [ ] **17.16.1** **The bike's QR still points at the emulator.**
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
      and today is the only day that has been checked
- [ ] **17.16.3** **Two publishable keys are in play, and the project should
      pick one.** The deployed `config.js` carries the legacy JWT anon key
      (`eyJ…`); the working copy of `web/config.js` carries the newer
      `sb_publishable_…` form. Both are the publishable key and neither is a
      secret, so this is tidiness rather than exposure — but the two forms
      revoke separately, which makes it a trap the day 14.11.4's rotation
      happens: rotating the one you remember leaves the one on the internet
      working. Say which form this project uses, in `config.example.js` and in
      `cloud.properties`, and use it in both places
- [ ] **17.16.4** **Point the project's Site URL at the host now that there is
      one.** Email confirmation links go to *Authentication → URL
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
- [ ] **17.15.1** **Typography is the half not yet shared.** The tokens cover
      colour, spacing and shape; the web app is still on a system font stack
      while the app has its own type scale. Worth doing when the web app has
      more than three screens — before that, a webfont is weight on a page whose
      whole virtue is that it opens instantly with nothing installed (17.1)
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
- [ ] **17.2** Auth shared with the app via the same Supabase project; a rider signs in once conceptually
- [ ] **17.3** Ride history and ride detail, reusing the chart definitions from 16 conceptually if not literally
- [ ] **17.4** Profile customisation: display name, avatar, bio, FTP, units
- [ ] **17.5** Friends — request, accept, block. New `friendships` table with its own RLS; this is the first schema where a rider can see another rider's data and it deserves more care than the rest
- [ ] **17.6** A light activity feed: friends' recent rides, kudos, a comment. Deliberately not a full social network
- [ ] **17.7** **Private by default.** Nothing is visible to anyone until the rider opts in, with per-ride visibility (private / friends / public). Defaulting to visible would publish training history people did not know they were publishing
- [ ] **17.8** Self-hosters get the same deal as 14.10 — the endpoint is configured at build time, not typed in
- [ ] **17.9** Decide what "public" means before shipping it: a public profile URL is an outward-facing surface with moderation and abuse implications a hobby project has to actually think about
- [ ] **17.10** The web app never implies a household member is missing data when they have simply never signed in. See the preamble — this is a copy problem with a data-model cause, and it is the one thing about this phase that is peculiar to Pelonot
- [ ] **17.11** Manage friends, display name and bio here rather than on the bike (see the preamble). The Android side may mirror it read-only and lose nothing
- [ ] **17.12** The web app reads `metrics_payload`, so it is the consumer that makes 14.4.3's `payload_version` matter. Do not start 17.3 against an unversioned payload

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
- [ ] **18.11.1** **Turn public sign-up off, and it is a prerequisite rather
      than a tidy-up.** "Everyone registered" is a safe rule exactly as long as
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
