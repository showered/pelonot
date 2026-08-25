> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 30: Getting a new version onto a bike that is not yours

**The owner's note, 19 August 2026, verbatim:** *"OTA Updates. I'm happy to do
grade installs over adb but my friend probably won't be bothered. Can we
introduce free OTA updates somehow so when he opens the app it'll say 'do you
want to install an update?'"*

**The short answers, before the items.** Yes, it can be done, and it costs
nothing: the repository is public, so **GitHub Releases hosts the APK for
free**, and the companion web app — already live, already deployed by `git
push`, already the URL the bike carries in `BuildConfig.PELONOT_WEB_URL` — can
serve the small JSON that says what the latest version is. The app downloads it
and hands it to `PackageInstaller`. **The friend taps twice**: once, ever, to
allow this app to install packages, and once per update to accept it.

**But there is a prerequisite that gets more expensive every day, and it is the
real content of this phase.** Android refuses to update an app whose signing
certificate has changed. Every copy of Pelonot that exists — the AVD's, the
bike's, the friend's — is a **debug** build, signed with the `~/.android/debug
.keystore` on the owner's laptop. `release` has no `signingConfig` at all, so
`assembleRelease` today produces an APK that cannot be installed on anything.
The first properly signed release therefore **cannot update what is on the
friend's bike**: it has to be uninstalled first, and an uninstall takes the
database with it. **Every ride the friend takes between now and that day is a
ride that has to be backed up and restored across the gap** (19.1.3 is the
backup; it exists). Doing this before he has ridden it much is cheap; doing it
in six months is a rider's whole history riding on a `.zip`.

**Why this is its own phase rather than a line in 19.2.** Everything else in
this plan is about what the app does once it is on a bike. This is the only item
about *how it gets there*, and it is the difference between a project with one
user and a project with two. The owner's note names the whole of it: the second
rider will not run `gradlew`.

**Where it sits against the connectivity model — settled, by the owner, 21
August 2026.** An update check is a network request made by a rider who has no
account, which is the first thing in this project to be that. It does not break
rule 1 as written — *"a rider with no account makes no request to
**Supabase**"* — and it carries nothing about the rider: no id, no name, no
ride, not even a profile count. It asks *what is the newest version* and is
told. But rule 1's spirit is "offline is the mode", so it was put to the owner
rather than assumed, and **the answer is yes, with a switch in Settings and the
switch defaulting on** (30.5.1). It is not routed through `CloudAccess` and must
not be: gating updates on having an account would withhold them from exactly the
rider the note is about.

---

### 30.1 Signing — the prerequisite, and the only irreversible thing here

**This is first because it is the only item with a deadline.** Nothing else in
the phase is harmed by being built later; this one costs the friend's ride
history if it is.

**30.1.2 and 30.1.3 are built** (sixty-ninth sitting), and rather than stop at
compiling, **the whole of 30.1.4 was rehearsed on the tablet AVD** — signed,
installed, run, and the fixture put back. Four things came off that and every
one of them is a thing this project did not know.

**A release build is 3.4 MB**, against the debug build's 24. R8 and resource
shrinking take seven-eighths of it, which moves the hosting question (30.3)
from *forced* to *chosen*.

**`assembleRelease` works**, which nothing here had ever established: minify and
`shrinkResources` have been on since Phase 0 and never once exercised, so the
first person to run it could as easily have met a `proguard-rules.pro` that had
drifted for sixty-eight sittings. Signed against a throwaway key, verified with
`apksigner verify --print-certs`, and the key deleted.

**And the minified build *runs*, which is the part that mattered.** R8 strips
`kotlinx.serialization` generated serializers for a living and this app reads 72
JSON classes out of its assets on first launch. Measured rather than assumed:
installed onto an empty tablet, the first-run screen drew, `Guest` opened a
dashboard, and the classes card read **72 to choose from** with the interval
bars drawn off `intervals_json`. Room, Compose and the serializer chain all
survive minification as the rules stand today. Settings' new line read
`Pelonot 1.0.0 (1)` with **no** `· debug`, which is the other half of 30.2.2
checked.

**The consequence nobody had noticed is that `run-as` dies with the debug
build.** A release APK is not debuggable, so `run-as com.pelonot cat
databases/…` — the technique CLAUDE.md leans on for every data-integrity
question this project has ever settled — answers *package not debuggable* and
nothing else. **The database stops being the witness on any bike running a
release build.** That is an argument inside 30.5.2 rather than a blocker, and it
is why the answer there may end up being two channels after all.

- [ ] **30.1.1 A release keystore exists and is not in the repository.** One
      `keytool -genkeypair` with a **long validity** — 30 years, because an
      expired signing key on an app with no store behind it means the same
      uninstall-and-lose-everything event a second time — kept wherever the
      owner keeps such things and backed up somewhere that is not the laptop.
      **Losing it has exactly the same cost as never having made it**, which is
      worth saying plainly: there is no recovery, no Play Store key rotation to
      fall back on, and the only fix is uninstalling every copy
- [x] **30.1.2 `app/build.gradle.kts` gains a release `signingConfig` read from
      `local.properties`**, the same four-level resolution the Supabase values
      use — and **not through `secret()`**, which `CloudConfigFenceTest` counts
      as its fence (`app/build.gradle.kts` says why). A store password is not a
      `buildConfigField` and must never become one. **The build must still work
      with no keystore**: a fresh clone and CI both have none, and `14.10.3`'s
      rule that absence is a supported configuration applies here too — no
      keystore means an unsigned release build and a working `assembleDebug`,
      not a failure
- [x] **30.1.3 The gitignore is checked against the real filenames** before the
      first key is generated, not after. `*.jks`, `*.keystore`, and whatever
      `local.properties` names. A signing key in a public repository is the one
      mistake in this phase that cannot be undone by a force-push
- [ ] **30.1.4 The changeover is planned before it is performed**, and the
      owner has chosen **soon, with the history carried across** rather than
      started clean (21 August 2026) — so the backup is load-bearing rather
      than a courtesy, and 30.1.4a is what has to happen before the friend's
      tablet is touched at all. The steps: back the friend's bike up through 19.1.3, uninstall,
      install the first release-signed APK, restore. **Verify the restore before
      the uninstall**, on the owner's own tablet — a backup nobody has put back
      is a belief rather than a backup, and 19.1.3a is this project's own
      evidence that a restore path can be broken while its tests are green
- [x] **30.1.4a The rehearsal that has not been done is the one that uses the
      app's own backup.** 30.1.6 moved the database across the gap with
      `run-as` and `tar`, which proves the *shape* of the changeover and not the
      route the owner will actually take — 19.1.3's *Back up now* and *Restore
      from a backup*, driven from Settings, with a real file. Do that before
      touching the friend's bike, not on it: 19.1.3a is this project's own
      evidence that the restore path can be broken while its tests are green.
      **Done, on the tablet AVD, on the same 55-ride five-profile fixture as
      30.1.6.** Settings → *Back up now* wrote a real file into Downloads
      through the SAF save dialog (709 kB); `pm clear` then stood in for the
      uninstall, since the point being tested is the app's own restore path
      rather than the certificate change 30.1.6 already rehearsed; Settings →
      *Restore from a backup* picked that file back up through the SAF open
      dialog, took the *Replace everything with this backup?* confirmation, and
      restarted. **5 profiles, 55 workouts, 5278 metrics, 72 classes** — counted
      out of `sqlite3` after, not read off a screen — came back exactly. One
      thing came back different rather than missing: Robin's profile photo
      restored to her derived-colour `R` rather than the picture, because
      `DatabaseBackup` copies the SQLite file and nothing else, and a photo
      lives in `files/avatars/` beside it rather than inside it. **This is not a
      new fault** — 20.2.4 named this exact gap and watched the same fallback
      by deleting a photo file by hand, and the rehearsal is a second
      confirmation of a decision already made rather than a discovery. Worth
      carrying into 30.1.4 in one sentence: the friend's photo, if he sets one
      before 20.7's removal is reverted, will not survive his changeover either,
      and that is by design, not a thing to fix first
- [x] **30.1.6 The changeover was rehearsed on the tablet AVD**, on the 55-ride
      five-profile fixture, and the fixture came back byte-identical: pull, full
      uninstall, install the signed release, drive it, uninstall, reinstall
      debug, put it back — **5 profiles, 55 workouts, 5278 metrics, 72 classes**
      before and after, Robin's photograph included. **Two operational traps
      were met doing it and both are worth having written down.** A tar taken
      with `tar cf - -C /data/data/com.pelonot .` carries a `./` entry, and
      extracting it sets the app's home directory to the host's `0755` — after
      which `run-as` refuses everything with *readable or writable by others*
      and the app looks bricked from a session's point of view. `chmod 700` from
      inside the same `run-as` is the whole fix. And the emulator is a **Play
      Store image**, so `adb root` is refused and there is no way round a
      permission mistake once made
- [x] **30.1.5 After 30.1.4, `installDebug` onto that bike stops working**, and
      that is correct rather than a regression: a debug build and a release
      build are different certificates and neither can replace the other. The
      owner's own bike may keep taking debug builds; the friend's takes releases
      only. **Written in `HARDWARE.md`** where the adb recipes are, because it
      will otherwise be met as a confusing error a month from now — together
      with 30.1.6's finding that `run-as` dies with it, which is the half that
      turns a confusing error into a session that thinks the tablet is broken

### 30.2 A version that moves

**`versionCode` is 1 and `versionName` is `1.0.0`, and neither has changed in
the life of the project** — which has been harmless because nothing has ever
compared them. An update check is nothing *but* a comparison, so this stops
being cosmetic.

- [x] **30.2.1 One scheme, decided once.** The recommendation is a monotonic
      integer `versionCode` that nobody edits by hand and a `versionName` the
      owner chooses — the code is for the machine and the name is for the
      screen, and conflating them is how a hotfix ends up unable to describe
      itself. Where the integer comes from is the choice: the git commit count
      (`git rev-list --count HEAD`) is automatic and monotonic but is not
      reproducible from a tarball; a number in `version.properties` is explicit
      and can be forgotten. **`version.properties` is what was built**, on the
      same argument as `cloud.properties`: a fresh clone should be able to see
      what the answer is without running anything. `app/build.gradle.kts` reads
      it, with the historical `1` / `1.0.0` as the fallback so a clone missing
      the file builds something honest rather than nothing
- [x] **30.2.2 The version is visible in the app, and today it is nowhere.**
      Measured rather than assumed: `BuildConfig.VERSION_NAME` and
      `VERSION_CODE` are referenced by **nothing** in `app/src/main/java`, so
      the app has never once said which build it is. That is survivable while
      one person installs it over a cable and unsurvivable the moment somebody
      else has a copy — the first question about any update problem is *which
      one are you on*, and the friend is not going to run `dumpsys package`.
      Settings, at the bottom, where an about line belongs. **Built and watched
      on the tablet AVD**: `Pelonot 1.0.0 (1) · debug`, centred, `bodySmall` and
      `onSurfaceVariant`, with no heading and no card — Phase 26's rule is to
      say less, and this is not a measurement anybody reads. The `· debug`
      suffix appears on debug builds only and earns its place from 30.1.5: when
      a debug copy and a release copy start refusing to replace each other, this
      is the line that tells them apart from across a room
- [ ] **30.2.3 A downgrade is refused, and this project already knows why.**
      12.5.1 kept `fallbackToDestructiveMigration` on **downgrade** on the
      argument that it only ever happens on a development device. An OTA channel
      that could offer an older APK makes that false and would wipe a rider's
      database from a dialog they tapped *yes* on. The update check compares
      `versionCode` and offers **strictly greater**, never equal and never less

### 30.3 The manifest — what the bike asks, and who answers

**Two hosts, one command.** The JSON lives in `web/`, because it is this app's
contract and this repository should own its shape rather than inherit GitHub's;
the APK lives on a GitHub release, because that is where a binary with a
version history belongs. **The size argument that used to be here is gone**: a
release build is 3.4 MB, comfortably under Cloudflare's 25 MiB per-asset
ceiling, so both files *could* sit on the Worker. The reason not to is that
`git push` would then carry a 3.4 MB binary into the repository on every
release, and a repository that accumulates every APK it has ever shipped is a
repository nobody can clone in a year. Both hosts are free and neither needs an
account on the bike.

- [ ] **30.3.1 `web/update.json`** — `version_code`, `version_name`, `notes`
      (one sentence a rider can read, not a changelog), `url` and `sha256`.
      Served from the same origin the app already carries, so **no new
      configuration value and no new secret**: it is
      `BuildConfig.PELONOT_WEB_URL` plus a path. A self-hoster's is theirs,
      exactly as 17.14 decided for the endpoint. **The bike's half is built**:
      `UpdateManifest` is the shape, snake_case on the wire with `@SerialName`
      doing the matching for `intervals_json`'s reason, and `UpdateRepository`
      fetches and parses it. **The file itself is not published**, deliberately
      — a manifest is a promise that an APK exists at a URL, and no release
      exists yet. A 404 is already *no update today* (30.3.4), so the live site
      is correct as it stands. This box ticks when `tools/release.sh` writes a
      real one
- [ ] **30.3.2 `tools/release.sh` writes both**, in the manner of
      `tools/status-figures.sh`: bump the version, `assembleRelease`, compute
      the hash, create the GitHub release with the APK on it, rewrite
      `web/update.json`, and stop before committing so the owner reads the diff.
      **One command, because a two-step release is a release where step two gets
      skipped** — and the failure mode of skipping it is a manifest advertising
      a build that does not exist, or worse, a hash that does not match one that
      does
- [x] **30.3.3 The check is cheap and rare.** Once per app open at most, and not
      more than once every 24 hours — a timestamp in preferences, and no
      background work, no `WorkManager`, no polling. The note says *"when he
      opens the app"* and that is the whole requirement. `UpdatePolicy
      .isCheckDue` and `UpdateRepository.check` exist, `last_update_check_at`
      is stored, and **now the app does ask at launch**: `AppViewModel`'s
      `init` runs it once, the same way `refreshRecoverableWorkout` already
      does for 8.3d, and 30.4 is what gave it somewhere to put the answer.
      **One thing the rule already handles that the write-up had not thought
      of**: a clock that has gone *backwards* — which this tablet does at every
      boot, correcting itself off the network — must not lock a bike out of
      updates until the stored timestamp comes round again
- [x] **30.3.4 A failed check is silent.** No network, a 404, a malformed
      manifest, a captive portal answering everything with a login page — all of
      them mean *no update today*, and none of them is a thing to tell a rider
      about. This is the same rule as `SyncOutcome.Disabled`: the offline tier is
      the mode and its failures are not errors. The one place it may be visible
      is a manual *Check for updates* in Settings, where the rider asked.
      `UpdateCheck` is the built shape, and it names the three
      nothing-happened cases apart — `Disabled`, `NotDue`, `NotConfigured` —
      beside `Unreachable`, for `SyncOutcome.Disabled`'s reason: a Settings
      screen has to be able to tell *"you turned this off"* from *"the internet
      did not answer"*, and a nullable cannot say which. **Settings' *Check for
      updates now* is built on exactly that distinction** — a snackbar names
      which of the six nothing-happened-or-decided cases it got, and only
      `Offer` opens the install dialog instead. **Confirmed against the real
      endpoint rather than a stand-in**: with 30.4.6's rehearsal finished and
      the AVD pointed back at the true `pelonot.webUrl`, a manual check against
      the live site's still-unpublished manifest (30.3.1) came back
      *"Couldn't check just now"* — the honest `Unreachable` answer for a 404,
      exactly as this item specifies, on the actual production endpoint rather
      than an assumption about it

### 30.4 Installing it

**`PackageInstaller`'s session API, not `ACTION_INSTALL_PACKAGE`.** The old
intent is deprecated, needs the file to exist somewhere a second process can
read it, and on Android 11's scoped storage that means a `FileProvider` and a
grant. A session takes an `OutputStream` — the download can be written straight
into it — and the confirmation is a `PendingIntent` the system raises.

- [x] **30.4.1 `REQUEST_INSTALL_PACKAGES` is declared in the manifest.** First,
      and before believing anything about the flow: CLAUDE.md's rule is that a
      permission the manifest does not declare is **denied instantly, with no
      dialog and nothing in logcat**, and this project has lost two sittings to
      exactly that (`VIBRATE`, then `ACCESS_FINE_LOCATION`)
- [x] **30.4.2 The one-off grant is asked for at the moment it is needed.**
      Above API 26 the rider must also allow this app to install unknown apps,
      which is `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` and **cannot be
      granted programmatically**. `canRequestPackageInstalls()` says whether it
      is already given. Ask when they have said yes to an update, not before —
      the same rule 11.6.14 settled for the overlay permission, and 20.4.8 for
      notifications: a permission asked outside the moment it is for is a
      permission refused
- [x] **30.4.3 The download is verified before it is committed.** SHA-256
      against the manifest. This does not replace the platform's signature check
      — that is what actually makes an OTA channel safe, and it is 30.1's whole
      point — but a bike on household wifi truncating a 24 MB download is the
      likely failure, and a truncated APK should be a retry rather than a
      failed install
- [x] **30.4.4 Nothing is asked during a ride.** Installing replaces the
      process. The check and the prompt happen when the app is opened and
      `WorkoutService` is idle, and **a ride in progress is `is_complete = 0`
      exactly like a crashed one** (8.3b) — so *idle* means asking the service,
      not asking the database. **Built as a withholding rather than a filter**:
      `AppViewModel.uiState.updateOffer` is the manifest or null, and it is
      null whenever `RideInProgress.active` is non-null — the same live,
      process-scoped answer `recoverableWorkout` already asks for 8.3d, not a
      second read of the database. A screen reaching into the state directly
      sees the same *no* the dialog would, which is the point: nothing has to
      remember to check twice
- [x] **30.4.5 Once refused is refused for that version.** Store the
      `versionCode` the rider said no to and do not ask again until there is a
      newer one. Being asked the same question at every launch is the failure
      7.11.8 already refused for the FTP proposal, and it is the failure that
      turns an update prompt into a thing people learn to dismiss without
      reading. **The storage half was already built** (30.5.1's
      `SettingsRepository.declineUpdate`, feeding `UpdatePolicy.decide`'s
      `declinedVersionCode`); what this box adds is the *Not now* button
      actually calling it, from both the automatic dialog and Settings' own
      check
- [x] **30.4.6 Watched working on the AVD, both halves.** Install a build, raise
      the version, publish, open the app, take the prompt, and confirm the new
      version is running and **the database survived** — which is the assertion
      that matters and the one an install test would otherwise skip. This is
      fully observable without the bike and without the friend. **Done, on the
      tablet AVD, end to end and for real** — not a stand-in for the mechanics,
      the actual flow: a genuine second APK (`versionCode 2`, built from this
      tree), a manifest and the APK served over **HTTPS** from a local server
      (a self-signed cert installed as a user CA on the AVD only, and
      `pelonot.webUrl` pointed at it, both reverted before anything was
      committed — 30.3.1's rule that the real manifest stays unpublished until
      `tools/release.sh` exists was not touched). The automatic prompt appeared
      unprompted at launch reading *"Pelonot 1.0.1 is ready"*; **Install**
      correctly detected the missing *install unknown apps* grant and asked for
      it by name; returning from that settings screen **retried on its own**
      rather than leaving the rider to tap Install twice; the download,
      checksum and `PackageInstaller` session all worked, raising the system's
      own *Update this app?* confirmation (Play Protect's scan gate came after
      it, unprompted by this app, and was navigated the same way a rider
      would). **The database survived** — 5 profiles, 55 workouts, 724 kB,
      counted out of Settings' own Storage card rather than assumed — and
      Settings' about line read `Pelonot 1.0.1 (2)` afterwards, both figures
      the update check itself compares. The AVD was left exactly as found:
      uninstalled back to a clean `versionCode 1` (Android refuses to
      downgrade a same-signature APK, so a plain reinstall could not do it) and
      the fixture put back through 30.1.4a's own restore path — which is a
      second, incidental confirmation of that item, on a build it had not been
      tried against before

### 30.5 The decisions in it

- [x] **30.5.1 Whether an account-less bike may make this request at all —
      answered *yes*, 21 August 2026, and the switch defaults on.** The
      reasoning is at the head of this phase and the owner took it as put: the
      request is about the *app* and not the *rider*, so it is not the thing
      rule 1 forbids, and gating it on an account would withhold it from the
      person the note was written about. **The switch is the part that keeps the
      rule honest** — a rider who wants an offline tablet to be an offline
      tablet can have one — and the default is **on**, because an update nobody
      is offered is the state we are in now. **Nothing about the rider goes on
      the wire**: no id, no name, no profile count, no ride. That sentence is an
      invariant rather than a description, and whatever ends up making the
      request should be readable enough that a stranger can check it in one
      screenful. **Built**: the switch is *Updates → Tell me about new versions*,
      on by default, and the sentence under it says what leaves the tablet
      rather than what the feature is called. The request itself is
      `UpdateRepository`, deliberately written on `HttpURLConnection` rather
      than the ktor client the Supabase SDK drags in, so the update path shares
      nothing with anything cloud-shaped. **`UpdateChannelFenceTest` is the
      promise held structurally rather than in prose** — one file knows where
      the manifest lives, that file may not mention Supabase, ktor or
      `CloudAccess`, the URL is a constant path with nowhere to hang a query
      string, and the request is a GET that cannot carry a body. Each check was
      watched failing against its own violation, which is `RiderScore`'s rule 2
      not being stated four times and enforced by nobody (26.4.10)
- [x] **30.5.2 Which builds the two bikes carry — answered, 21 August 2026:
      the friend's takes releases, the owner's stays on debug.** The write-up
      recommended one channel, on the argument that a bug the friend reports
      should reproduce on the same binary. **30.1.6 found the argument on the
      other side and it won**: a release build is not debuggable, so `run-as` is
      gone and with it every database query in CLAUDE.md's *Verifying UI work*.
      A bike this project can no longer interrogate is a bike whose defects
      arrive as sentences rather than as rows — and almost everything this
      project has found, it found in `workout_metrics`. His tablet is a bike;
      the owner's is also the test rig. **What it costs is written here rather
      than discovered later**: a report from the friend's bike may not reproduce
      on a build anybody can inspect, and when that happens the answer is to put
      a debug build on his tablet for the length of the investigation — which
      30.1.5 makes a reinstall rather than an update, so it costs his database
      again. That is the trade

### 30.6 What was considered and is not being built

- **The Play Store.** A developer account is **$25** — not free, which the note
      asked for — and it wants review, a privacy policy, and a target API level
      that moves every year. It also wants a Play Store on the tablet, which
      29.1.1 has not yet established exists. And this app binds another
      application's private service for telemetry, which is not a shape a review
      is likely to enjoy explaining
- **F-Droid.** Free and genuinely the right home for a project like this one
      day, but updates arrive through the F-Droid client, which the friend would
      have to side-load first — so it does not answer the note. It also wants
      reproducible builds and a submission that a person reviews. Worth
      revisiting when there is a third rider, not a second
- **Firebase App Distribution.** Free, and the closest off-the-shelf fit, but
      each tester needs a Google account and installs a distribution app of
      Google's, which is two more things than *tap yes*. It also puts a Google
      dependency into an app whose whole premise is not needing a subscription
- **Automatic installation with no prompt.** The note asks for a question and
      it is right to. An app that replaces itself without asking, on a machine
      the rider does not administer, is a thing to be uneasy about — and the
      one-off *install unknown apps* grant makes it impossible anyway, which is
      the platform agreeing
