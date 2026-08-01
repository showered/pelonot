> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Corrections — items previously ticked that were not working

Recorded so the same mistakes are not repeated, and because several of these
were the *reason* a downstream feature looked broken.

| Item | Was ticked | Reality |
|------|-----------|---------|
| 1.11 Supabase repository | ✅ | Passed `Map<String, Any?>` to kotlinx.serialization, which has no serializer for `Any`. Every call threw and was swallowed by `runCatching`. |
| 2.3 BLE heart rate | ✅ | `connectGatt(null, …)`; CCCD descriptor never written, so the strap was never asked to notify. No reading could ever arrive. |
| 2.5 Metrics calculator | ✅ | Integrated power against sample *count*, not elapsed time — a 45-minute ride over-reported energy by ~1000×. |
| 3.4 Per-second metric recording | ✅ | `workout_metrics` has a foreign key onto `workouts`, but the workout row was only written at ride *end*. Every insert violated the constraint. No ride ever stored a time series. |
| 5.7 Power zone calculator | ✅ | Zone ranges left gaps (0.55–0.56 etc.); a lookup that matched nothing fell through to Z7, so an easy warmup was reported as Neuromuscular Power. |
| 6.6 Class detail screen | ✅ | Interval model used camelCase field names against snake_case assets. Every decode threw and was caught into `emptyList()`. No class ever displayed an interval. |
| 8.3 Crash recovery | ✅ | `getIncompleteWorkout()` returned the most recent workout with no completion filter, so the app offered to resume the ride you had just finished, on every launch. |
| 8.7 Unit tests | ✅ | `PostWorkoutAnalyzerTest.kt` was committed empty. |
| 8.8 Instrumented tests | ✅ | `WorkoutDaoTest` referenced `localUserId` / `classTemplateId`, fields that never existed on the entity. It had never compiled. |
| 8.11.17 Dashboard redesign | ✅ | Shipped with `"12.5"` and `"8.3"` kJ hardcoded, and a constant "FTP Stable" badge, shown as the rider's own statistics. |
| 8.5 Haptic feedback | ✅ | The app never declared `android.permission.VIBRATE`. Every call threw `SecurityException` into a `runCatching`, so the buzz simply never happened. Found by reading logcat during a real ride — it is invisible from the UI. |
| 8.8 Instrumented DAO tests | ✅ | Rewritten so they compiled, and ticked — but `@Before fun setup() = runBlocking { … }` infers its return type from the last expression, and `insertUser` returns a row id. JUnit rejects a `@Before` that is not void, so the class failed to initialise and **all ten tests silently never ran**. They pass now. |
| 8.3a Crash recovery prompt | (untickable) | The service exposed `recoverableWorkout`, but nothing binds to the service on a cold start — which is exactly the situation a crash leaves behind — so the prompt could never have appeared wherever it was rendered. |
| 2.1 Serial telemetry | (never ticked) | Not a false tick, but the same failure shape and the most expensive one yet: **the entire premise was wrong and no test could have said so.** The app spent its whole history preparing to read a character device that either does not exist (`ttyS1`) or belongs to Bluetooth (`ttyS2`), on a bike it assumed was jailbroken and is not. `SerialSensorSource`, `SerialProtocolParser`, `CadenceTracker` and `PowerModel` are all correct code aimed at the wrong target. Fifteen minutes on the actual hardware settled it. See 2.1a. |
| 2.3 BLE heart rate (again) | ✅ | **The manifest never declared `ACCESS_FINE_LOCATION`**, which below API 31 *is* the BLE scan permission. A runtime request for an undeclared permission is denied instantly — no dialog, nothing in the log — so on the bike's own Android 11 tablet no strap could ever be found. The rewrite in 2.3 was correct code behind a door that was nailed shut. Behind it, the Scan button reported `PermissionRequired` and never requested anything, so there was no way to grant from inside the app either. Found the first time a strap was put on. |
| 8.6 TTS audio cues | ✅ | Two defects, neither in the coaching logic. `RideCoach` set the language and audio attributes immediately after the `TextToSpeech` constructor, but the engine binds asynchronously, so both were discarded — the device said `setLanguage failed: not bound to TTS engine` and nothing read it. And audio attributes only *describe* a sound; they request nothing. Ducking needs `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, which nothing ever asked for, so the cue was spoken at full volume underneath a film at full volume. The tick claimed "the rider's video ducks under them" and the video had never ducked once. |
| 3.4 / 9.5.2 Average heart rate | (untickable) | `workouts.avg_hr` was written as **79.0 against a true mean of 105.4** over the ride's own 314 metric rows — 79 being the lowest reading of the warm-up. The running mean was rounded to an `Int` every tick, so once one sample moved it by less than 1 bpm the whole increment was discarded and the average froze. `avg_power` and `avg_cadence` beside it, both `Double`, were exact. A second bug sat behind it: heart rate divided by the *tick* count rather than the heart-rate sample count, which would have buried the readings of any strap connecting mid-ride. Invisible from every screen; found by comparing the stored aggregate against the samples it claimed to summarise. |
| 2.6 Telemetry source toggle | ✅ | The chip persisted and was **applied to the running pipeline only in the session it was tapped in**. `SensorRepository.setMode` had one caller, `SettingsViewModel`; nothing applied the stored preference at startup, so every launch silently reverted to `Auto`. Settings kept drawing the rider's choice because Settings reads DataStore and the pipeline reads its own field. **Hardware** — the mode that exists so a ride never records fabricated numbers — therefore could not survive the app being closed. See 2.4.6. |
| 1.11 / 7.4 Cloud sync (again) | ✅ | **No row had ever reached the cloud** — `profiles` and `workouts` were both empty, by count. The cause was not the DTOs: `migration.sql` never granted `anon` a single table privilege, so every request failed `42501` before RLS was consulted. Behind that sat four more defects, one of which (epoch millis into a `TIMESTAMPTZ`) was invisible to code reading and only appeared on a real insert. Failures returned `SyncOutcome.Failed`, which nothing displays. Full detail in **14.0**. |

All of the above are now fixed and covered by tests, **except the last** —
the wire path is proven and `WorkoutSyncWorker` driving it end to end is not,
which is 14.1.6.

Note the shape of that one, because it is the most expensive kind here: the
first diagnosis, made by reading the code against the migration, was confidently
wrong about the cause and wrong about a security claim. Only a request to the
live project produced the real answer. **For anything involving the cloud, read
the code to form a hypothesis and then go and hit the endpoint.**

The pattern is worth naming, because it has now happened eleven times: a
failure path that is caught, logged and returned as a value nothing reads is
indistinguishable from success from every surface anyone looks at. Where a new
phase below adds a feature that can fail quietly — sync, login, export,
deletion — **it also has to add somewhere the rider can see that it failed.**

The three added on 31 July 2026 sharpen it into a second rule. All three were
found within an hour of a rider sitting on the bike with a strap on, and none
of them could have been found any other way:

- **A permission absent from the manifest is denied with no dialog and no log
  line.** Two of the project's defects are now this exact shape (VIBRATE, then
  ACCESS_FINE_LOCATION). Before trusting any runtime permission path, check the
  manifest declares what the code asks for.
- **Configuring an Android service object before it has bound silently does
  nothing.** `TextToSpeech` warns and continues.
- **An aggregate is only trustworthy when checked against the rows it
  summarises.** `avg_hr` was wrong for the whole project's history beside two
  neighbours that were right.

> **Verify against the source data, not the surface.** Every one of these
> looked correct from the UI. The database, `dumpsys` and logcat are where they
> were visible — and for the ducking, only the rider's ears.
