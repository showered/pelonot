> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 13: Units and display preferences — fundamental, small

Distance is hardcoded to kilometres (`Formatters.kilometres`), which is the
wrong default for a UK or US rider looking at a Peloton bike whose own display
is in miles.

- [x] **13.1** `UnitSystem` (`METRIC` / `IMPERIAL`) in `SettingsRepository`, defaulting from the device locale. Absent-means-never-chosen, so the locale is consulted on every read rather than metric being pinned on first launch
- [x] **13.2** Settings toggle, next to the existing FTP and weight fields. **Observed on the emulator**: an `en-US` device opens on Imperial with no prompting
- [x] **13.3** `Formatters` takes the unit system: distance km/mi, speed km/h/mph, body weight kg/lb. No no-argument overload survives — a caller that forgot the preference is a compile error rather than a silent kilometre
- [x] **13.4** **Store SI, convert at the edge.** **Observed**: 160 lb typed into profile creation is `72.5747792016057` in `profiles.weight_kg`, read back as `160.0 lb`, and switching to Metric mid-session redraws it as `72.6 kg` without touching the row
- [x] **13.5** Every surface reads the same setting, delivered through `LocalUnitSystem` from `PelonotTheme` — which is what lets the HUD read it, since the overlay is composed from the service and has no ViewModel to thread it through. Ride screen, post-ride summary, HUD strip, settings and profile creation all consume it
- [x] **13.6** Watts, RPM, BPM and kJ are unit-agnostic and stay as they are. No calories, and the Settings copy says why
- [x] **13.7** JVM tests for the conversions, the settings-field round trip, and locale-derived defaults — **plus** `FormattersTest`, which pins every number under `fr-FR` and `hi-IN-u-nu-deva`. A missing `Locale.US` is the same defect class that put epoch millis into a `TIMESTAMPTZ` (14.0)
- [x] **13.8** **Profile creation asks for weight in pounds and never asks
      which.** The field is labelled `Weight (lb)` regardless of the rider's
      unit preference, and it is the *first* screen a new rider touches —
      before they have seen Settings, and before anything has told them the
      number is being converted. 13.4 is right that SI is stored and the edge
      converts; the defect is that the edge has no unit picker on it. Either
      seed the label from `UnitSystem.fromLocale()` like everything else does,
      or put a kg/lb toggle beside the field. Same question applies to the
      guest-ride "save to a new profile" dialog, which is the same component

      **The first half of that was already true and is not the fix**: the label
      does come from the preference, which on a fresh install is
      `UnitSystem.fromLocale()`. The locale is simply not the answer to this
      question — a rider in the UK weighs in kilograms and rides in miles — and
      there is no route to Settings before a profile exists, so the guess could
      not be argued with. So: **kg/lb chips beside the field**, opening on the
      current preference and converting with the rider's choice. It does not
      write the app-wide unit setting: distance and body weight are separate
      questions and this dialog is not the place to answer both. The guest-ride
      dialog is the same component and got it for free.

      **Observed on the tablet AVD, in the database rather than on the screen**,
      which is what makes the cost of it clear: 77 typed into the old dialog on
      an `en-US` device is stored as `34.9266124907727` — **half a rider**, and
      the number kJ/kg divides by on the household leaderboard (24.1). 77 with
      `kg` chosen stores `77.0`
