> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 29: The ride where the rest of the rider's life keeps it — Health Connect and Apple Health

**The owner's note, 16 August 2026, verbatim:** *"Health Connect / Apple Health.
Is that something we can integrate with? If so it's REALLY HIGH importance. But
i wonder if we need to register an app or something. Add it to the plan and tell
me what is needed from me, if anything."*

**The short answers, before the items.** Health Connect: **yes, and it needs
nothing from the owner** — no account, no registration, no key, no fee. Apple
Health: **no, not directly, and no amount of work on this tablet changes that**
— HealthKit has no Android SDK and no server API at all, because the data lives
on the rider's iPhone and nowhere else. The honest route to Apple Health is the
file this app already writes (12.4.3), carried across by the rider. Both of
those are argued out below rather than asserted, because the second one is a
*no* to a note marked REALLY HIGH and it deserves the reasoning.

**Why this is its own phase rather than a line in 19.2.** 12.4.3's export is
*getting your data out*, and it is done. This is the other thing: **the rider's
own health record is somewhere else already**, and a ride that does not reach it
is a ride their year does not count. That is the same argument as Phase 14's for
the cloud, one level up — and unlike the cloud it is free, offline, and asks
nobody to make an account. It sits directly under the connectivity model's
rule 1 (offline by default): **Health Connect is a local write to another app on
the same tablet and touches no network at all**, so it is available to a rider
with no account, which is most of them.

---

### 29.1 Health Connect — what it is, and the one thing that decides whether we can

**Health Connect is Android's on-device health store**, and every app that
writes to it does so through `androidx.health.connect:connect-client`. It is
local storage with a permission model on top: the rider grants this app the
right to write cycling sessions, and any app they own can then read them. No
server, no account, no network.

**The thing that decides this is the tablet, not the code.** Health Connect is
part of the platform from **Android 14**; below that it is a separate
application — `com.google.android.apps.healthdata` — that has to be installed.
**The bike is Android 11** (`HARDWARE.md`), so on the machine this app is
actually for, Health Connect exists only if that APK is on it or can be put on
it. Everything else in this phase is downstream of that one fact.

- [ ] **29.1.1 Check the bike before writing a line of it**, and it is three adb
      commands and no rider: `pm list packages | grep healthdata` for Health
      Connect itself, `pm list packages | grep com.android.vending` for whether
      the Play Store is there to install it from, and `pm list packages -f
      com.google.android.gms` for Play services. The tablet is not stock-stock —
      it has Nova and Netflix side-loaded — so **sideloading the APK is a real
      answer if the Store is absent**, and the check tells us which of the three
      worlds we are in. Do this first: it is the difference between a feature
      and an essay
- [ ] **29.1.2 The dependency has a floor, and this project is under it.**
      `connect-client` declares **minSdk 26** and this app is **minSdk 24**
      (`app/build.gradle.kts`). The manifest merger fails on that rather than
      warning, so the choice is `tools:overrideLibrary` with every call site
      gated on `Build.VERSION`, or raising the floor. **Raising it is probably
      right and is its own decision**: minSdk 24 is not defended anywhere in
      this plan, the only device that matters is Android 11, and API 26 is 2017.
      Check what else in the app claims to support 24 before moving it
- [ ] **29.1.3 Availability is a runtime question and has three answers, not
      two.** `HealthConnectClient.getSdkStatus()` returns *available*, *update
      required* or *unavailable*, and every one of them is a state this app has
      to draw honestly — which is the same rule as `PowerProvenance` and
      nullable `heartRateBpm`: **absent is a claim.** A Settings toggle that
      silently does nothing on a tablet without Health Connect is precisely the
      failure mode 15.4.3 refused to build for the cloud
- [ ] **29.1.4 What a ride becomes on the other side.** One
      `ExerciseSessionRecord` of type `EXERCISE_TYPE_BIKING_STATIONARY` spanning
      the ride, plus the series this app already has, each as its own record
      type: `HeartRateRecord` (and **only where a strap actually reported** —
      nullable `heartRateBpm` all the way through), `PowerRecord`,
      `CyclingPedalingCadenceRecord`, `TotalCaloriesBurnedRecord` and
      `DistanceRecord`. **Two rules from this project carry straight over.**
      First: **never write a modelled watt into a health record as though it
      were measured.** `PowerProvenance` is the gate, and a simulated or
      modelled ride writes the session and no `PowerRecord` — a fabricated watt
      in the rider's permanent health store is worse than a gap, exactly as
      2.4.4 argues for the gap in `workout_metrics`. Second: **a trimmed ride
      cannot write a per-second series it no longer has** (23.4), so what it
      writes is the outline it kept, and the session's totals come off
      `workouts` where they are still true
- [ ] **29.1.5 Distance is the one figure to think about before writing it**,
      because a stationary bike has no distance and this app derives one. If
      what we write is derived, it must not be presented to another app as
      measured — the same claim problem as the watts, with the difference that
      Health Connect has no field for provenance. Candidate answer: **write no
      `DistanceRecord` at all** and let the session, the duration and the energy
      be the record. Decide it, do not default it
- [ ] **29.1.6 Permissions are not ordinary runtime permissions.** They are
      declared in the manifest and requested through
      `PermissionController.createRequestPermissionResultContract()`, and on
      Android below 14 the grant screen belongs to the Health Connect app. This
      tablet has already produced two defects from a permission the manifest did
      not declare (`VIBRATE`, then `ACCESS_FINE_LOCATION`) — **check the
      manifest declares exactly what the code asks for before believing any of
      it**
- [ ] **29.1.7 Write on finalise, and make it recoverable.** The natural hook is
      the same place `synced_at` is written — after the ride ends, never during
      it (8.3d.4's rule: anything written to `workouts` mid-ride is reverted by
      the finalise). A ride written to Health Connect needs its own column for
      the same reason `synced_at` has one, so a tablet that was denied
      permission on Tuesday can carry Tuesday's rides across on Wednesday. That
      is a migration (12.5) and an exported schema
- [ ] **29.1.8 Reading, not just writing, and it is deliberately second.** Health
      Connect would give this app a **resting heart rate** and other apps'
      workouts. 21.1.6's maximum-heart-rate question could be answered from real
      data rather than from a birth year, which is the single most useful read.
      Left second because writing is the thing the owner asked for and reading
      is a feature that has to earn its own permission prompt
- [ ] **29.1.9 The registration question, answered.** Health Connect needs **no
      registration, no developer account, no API key and no fee** — it is an
      on-device API. What does exist is a **Play Store declaration form** for
      apps distributed through Google Play that access health data, and it binds
      us **only if this app is ever published on Play**, which is not how it
      reaches the bike today. So the answer to *"do we need to register an
      app"* is **no**, with the footnote that publishing on Play later would
      bring a form and a privacy-policy requirement with it

---

### 29.2 Apple Health — why the answer is no, and what the real route is

**There is no Android path to Apple Health, and this is not a matter of
effort.** HealthKit is an iOS/watchOS framework; Apple publishes no Android SDK
and **no server API of any kind** for it, because Health data is stored on the
rider's iPhone and is not in a cloud we could ask. An Android tablet cannot
write to it, and no permission, key or partnership changes that.

**So the question becomes: what would carry a ride from this tablet to an
iPhone's Health app?** Three routes, and only one of them is proportionate:

1. **A file the rider imports** — this app already writes `.tcx` (12.4.3), and
   iOS apps that import a TCX and write it into Apple Health exist and are
   ordinary purchases. **This works today with no code at all**, and what it
   needs is a written recipe rather than a feature
2. **An intermediary that already syncs both ways** — Strava is the obvious one
   (19.2.4 is the upload item), and a rider whose Strava writes to Apple Health
   gets the ride there without this project owning any of it
3. **A native iOS companion app** — the only first-party answer, and it is a
   whole platform: a Mac, Xcode, a **paid Apple Developer account** to put it on
   a real phone, an App Store review to put it on anybody else's, and a
   transport from the tablet to the phone that does not exist today. **This
   project's second app is a web app on purpose** (Phase 17)

- [ ] **29.2.1 Say the *no* in the plan and in the app, once**, rather than
      leaving a rider to discover it. Wherever Health Connect appears in
      Settings, an iPhone rider is entitled to a sentence telling them where
      their ride can go instead. One sentence, not an apology
- [ ] **29.2.2 The TCX recipe, written down and tried once.** 12.4.3 already
      writes the file and 12.4.3b already knows its limitation (no per-second
      distance). What is missing is one end-to-end trip: export a ride, get it
      onto an iPhone, import it, and see it in Apple Health — which needs an
      iPhone and is therefore the owner's or their friend's, not a session's.
      **This is the whole of what "Apple Health support" honestly means for
      this app today**
- [ ] **29.2.3 Do not build route 3 speculatively.** If an iOS app ever exists
      for another reason (15.6's QR sign-in is a phone flow already), HealthKit
      writing is a small part of it — but an iOS app built *for* HealthKit is
      the largest thing in this plan, for a feature route 1 delivers with a
      file that is already on disk

---

### 29.3 What is needed from the owner

**For Health Connect: nothing** — no account, no registration, no key, no fee,
no form (29.1.9). The one thing only the bike can answer is 29.1.1, and that is
three adb commands the next session can run itself the next time the tablet is
reachable.

**For Apple Health: an iPhone, once** (29.2.2), to carry one exported ride into
the Health app and say whether it lands. The owner does not use an Apple Watch;
the friend in 21.7 does, and it is the same trip.

**And one decision that is genuinely theirs: minSdk** (29.1.2). Raising the
floor from 24 to 26 costs nothing measurable — every device this app is for is
Android 11 — but it is a stated compatibility claim and this plan does not
change those quietly.
