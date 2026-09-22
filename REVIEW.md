# Update preparation safeguard — 22 September 2026

A ride starting while the downloaded APK is copied into PackageInstaller now
prevents commit. The coordinator supplies the final ride check; the installer
runs it after copying and syncing, alongside a cancellation check. Its existing
exception path abandons the uncommitted session, and the coordinator deletes the
cached APK, displays the ride message and permits a later retry.

Verification: **987 JVM tests**, debug build and diff checks pass. Two new tests
suspend installation preparation to exercise a newly active ride and coroutine
cancellation, including cleanup and successful retry. Removing the final ride
check makes the new race regression fail. No package replacement was performed;
the permanent-certificate platform rehearsal at 30.7.3 remains open.

---

# Rider choice and FTP evidence — 17 September 2026

- Replaced the suggested-class dashboard hero with **Choose a class**, alongside
  **Just Ride**. Removed the redundant All Classes door and suggestion card.
- FTP now says **Starting value**, **Ride-supported**, **Ready to move up**, or
  **Worth a review**, based on evidence. An unchanged manually entered 200 can
  become supported. A typed number alone cannot. Older support is retained
  independently of the recent review window.
- FTP detail puts the number and evidence side by side, with history below,
  supporting-ride navigation and expandable explanation. Review accepts an
  explicit new value; history and the profile update atomically, guarded against
  stale settings. New proposals use up to 20 qualifying rides in 90 days and
  respect the last setting change/decline. No past ride snapshots change.
- Fixed post-ride effort answers not triggering another reduction assessment.
  Upward dialog copy now explains the measured effort and calls FTP an estimate.

Verification: tablet emulator only, 1920 × 1080 at 240 dpi. Temporary fixture
rides explicitly modelled measured evidence; simulation was never presented as
real rider evidence. Tested an unsupported 200, supported unchanged 200,
200 → 209 and 200 → 185. SQLite confirmed the correct values, source and
supporting ride links. Dashboard/library navigation and FTP layouts checked at
100% and 130% text. **983 JVM tests** pass. The focused instrumented FTP suite
passes, including effort-answer reassessment and retained measured bests.
The final full emulator suite passes **132 tests**. Debug/release builds and
lint pass. Emulator app data is restored from the pre-test archive, with original
files compared byte-for-byte.
Evidence is temporarily in `/tmp/pelonot-ftp-review/` and screenshots in
`/tmp/pelonot-review-sep17/`. No hardware or production release was touched.

---

# Continued emulator pass — 17 September 2026

- Fixed CSV/TCX export handoff losing its pending operation when Android
  recreates the app while the document picker is open. Save only the format;
  rebuild content from the named ride after the destination returns.
- Ride detail collects profiles once per ViewModel, avoids redundant reloads,
  and cancels obsolete ride/comparison requests.
- Added duration filtering alongside category filtering, reset scroll on a
  filter change, and added Clear filters when no classes match.
- Backup confirmation retains its selected file through activity recreation.

Verified on `emulator-5554`, 1920 × 1080 at 240 dpi (API 36.1):

- Opened the CSV picker, killed the background app with `am kill`, verified no
  app PID remained, then saved. All **297 samples** matched the original
  database, with numeric rounding limited to CSV precision and null heart rates
  preserved. The initial file exists empty while the recreated app starts;
  after completion its size was 7,946 bytes.
- Duration/category combinations, the empty-state Clear filters action, class
  detail/back navigation, and library layout at 100% and 115% system text.
- Selected an existing local backup and changed system text size with its
  confirmation open. The confirmation survived; cancelled without restoring.
- **968 JVM tests and 128 instrumented tests pass**. Debug and minified release
  builds and lint pass. No hardware device used. Original emulator app files
  restored byte-for-byte after instrumented tests.

Evidence for this continuation is in `/tmp/pelonot-review-sep17/` (temporary).
The earlier release limitations below still apply: no production publication or
permanent-key update rehearsal was performed.

---

# Emulator bugfix and UX pass — 14 September 2026

This pass focused on the Android app and release readiness. No hardware device
was contacted, no GitHub release was published, and no signing key was created
or read.

## Changes

- Onboarding refuses blank/out-of-range manually entered FTP instead of silently
  accepting the estimate. Answers and the selected step survive recreation.
- The account offer puts “Not now” above the QR code and form. Its old footer
  disappeared below the viewport at larger system text, making sign-in look
  compulsory.
- Ride-total labels shrink to fit, fixing the clipped “AVG POWER” label at
  larger system text. Overlay preference flows are remembered to satisfy lint
  and avoid restarting them if their containing composition runs again.
- Update checks show an in-progress state and reject repeated taps. A manual
  check can re-offer a version declined by an automatic prompt. Checks and
  installation are blocked during a ride, including a ride starting during
  download.
- Update manifests are bounded during reading; APK downloads are capped and
  observe cancellation. Malformed HTTPS addresses are rejected.
- Installation keeps its state until PackageInstaller answers, exposes failures
  and cancellation, prevents duplicate downloads, runs session I/O off the main
  thread, and abandons failed sessions. Unknown-source APIs are guarded below
  Android 8.
- The migration 22→23 test now supplies all required old-schema ride columns.
  Previously its insert failed before the migration ran. The manifest declares
  coarse location alongside the API≤30 fine-location permission. Three lint
  false positives on explicit `produceState.value` assignments are suppressed
  at the navigation graph, with a reason.
- `tools/release.sh` prepares a verified release artifact locally and publishes
  only through an explicit second command. The update manifest is copied into
  the web app only after GitHub accepts the APK. See [RELEASE.md](RELEASE.md).

## Verification

- `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, and `lintDebug` pass.
  **968 JVM tests, zero failures.** The release build uses minification and
  resource shrinking; it is not evidence of a permanent release certificate.
- **128 instrumented tests pass** on `emulator-5554`, including the corrected
  migration test. Installed and invoked by explicit serial, avoiding Gradle's
  all-connected-devices behavior.
- **Five Python release-tool tests pass**, covering preparation without
  publication, rollback on verification failure, failed uploads, changed
  binaries, and manifest publication order. The real debug APK was refused
  by its signing certificate. Publishing was mocked, not performed.
- UI exercised on `Pelonot_Tablet`: profile creation with a full non-floating
  keyboard; invalid FTP and the estimate escape; recreation via system text
  size; profile selection; dashboard; class filtering/detail; countdown;
  simulated riding; pause/resume; the in-ride settings sheet; overlay over the
  launcher; ending the ride; summary and history. The account skip button and
  full “AVG POWER” label were visually rechecked at 115% text after reinstalling.
- The recorded test ride lasted **110 seconds** with **100 samples**. Database
  averages matched SQL over the samples exactly: power **121.934606168906 W**,
  heart rate **113.49 bpm**. It finalised with `is_complete=1` and
  `power_provenance=Modelled`. A 12-second injected sensor silence produced a
  **10-second maximum sample gap**, rather than fabricated continuous readings.

## Limits and release follow-up

The AVD uses **1920×1080 at 240 dpi**, with 100% and 115% system text. Its
installed image is **API 36.1**, with Android's status/task/navigation bars;
the bike is API 30 and has a different system-bar arrangement. These checks
cover tablet layout and behavior, not exact Android 11 platform parity. No
physical sensors, Bluetooth strap, DRM video, or audio ducking were tested.
The existing instrumented suite covers local cloud/restore logic; this pass
did not sign into an account or test live backup, deletion, or the web app.

The permanent signing configuration and a production update manifest remain
outstanding. Rehearse a same-certificate update, cancellation, incompatible
certificate, and app-data survival on an emulator before shipping. The revised
coordinator is unit-tested, but its real system callbacks have not been put
through a new end-to-end production update in this pass. Manual re-offering of
a declined update also awaits a real manifest. Phase 30 records these as open.

Build/test output and screenshots from this pass are in
`/tmp/pelonot-review/`. The original emulator app data was backed up there
before testing. Existing hardware data was untouched.
