# Dashboard design — 21 September 2026

The owner requested ChatGPT image-generation concepts, shown before implementation,
and then a working redesign. Generated using the built-in image generation tool.

## Designs and implementation

- [Dark concept](concept-dark.png) / [light concept](concept-light.png)
- Built app: [dark guest](guest-dark.png) / [light guest](guest-light.png)
- Built app: [household](household-dark.png) / [solo rider](solo-dark.png)
- Checks: [130% text](household-large-text.png), [account offer](solo-account.png),
  [new rider with household](new-rider.png)

The native implementation uses a split greeting, outlined navigation and cards,
clear arrow affordances, a gentle background glow and a drawn track in the primary
action. Decoration stays out of measurement charts. The large background track
from the concept is reduced to a glow to keep populated dashboards quiet.
Both themes use existing semantic colours; light mode gives the primary action
a deeper teal for readable white type. The entry transition is a short fade,
without scaling the whole screen. Shared household and riding cards carry the
same outline treatment into their existing uses.

The generated images are references, not runtime assets. All UI remains native
Compose, with no network dependency or bitmap text. Existing rider identity,
class profiles, FTP evidence and navigation callbacks are retained.

## Verification

`./gradlew assembleDebug testDebugUnitTest` passes: 984 JVM tests, no failures.
`git diff --check` passes. Manually checked on Pelonot_Tablet, 1920 × 1080,
240 dpi, hardware keyboard disabled: guest in both themes, a new rider,
a populated household, a solo rider, account offer, backup reminder, and
household at 130% font scale. The main actions remain visible. Class Library,
History, Settings and the Base Ride starter destination were exercised.
Font scale was restored to 1.0. No real bike was involved.

The existing emulator app has a different signing certificate, so `installDebug`
could not replace it. A temporary Gradle init script set only the preview APK's
application ID to `com.pelonot.designpreview`; that copy was installed alongside
it. Synthetic offline profiles and modelled ride records were inserted only into
the preview database for layout checks. No production data or signing configuration
changed. The final standard APK is in `app/build/outputs/apk/debug/app-debug.apk`.

## Image-generation prompts

Dark concept (reference: screenshot of actual guest dashboard):

> Use case: ui-mockup. Input image is a reference screenshot of the actual Pelonot Android stationary-bike dashboard. Redesign this same screen to look much slicker, refined and distinctive with a little visual energy. Produce one high fidelity 1920x1080 landscape UI design, full screen flat screenshot, no device mockup. Retain dark charcoal and teal brand, readable large typography, generous touch targets. Main actions Choose a class and Just Ride, History and Settings in header; greeting Good evening, Guest. Keep three Somewhere to start cards: Zone 2 Steady / 20 min · Endurance; Base Ride / 45 min · Endurance; Long Base / 60 min · Endurance. Preserve the actual stepped class profiles from reference rather than inventing ride data. Make main action feel designed with tasteful abstract flowing teal cycling track arcs, strong typographic hierarchy and an arrow affordance; secondary action quiet. Subtle borders, intentional spacing, sophisticated tonal depth. Could split greeting into small salutation and larger rider name. Keep layout compact enough that populated dashboards can show history cards below: action row around 120 logical pixels tall maximum, cards never individually full panel width. No new features, fake scores, invented streaks, stock photos, large promotional copy or garish neon. Realistically implementable in Jetpack Compose using gradients, strokes and typography. Bottom system navigation area 48 dp. This is a guest so absolutely no level badge and no invented personal history. Improve visual quality significantly rather than just recolour screenshot.

Light concept (reference: generated dark concept):

> Use case ui-mockup. Create the light theme counterpart of this Pelonot dashboard design reference. Same exact layout, words and class profile shapes; full screen landscape UI screenshot. Warm white background, crisp near-black text, subtle grey outlined white cards, sophisticated deep teal primary action with pale text and flowing track arcs. Quiet mint track accent in header. Maintain high contrast, retain original violet and teal class profile colors, no new features, no fake guest records or level badge. This is a polished implementable Android tablet interface, no device frame. History and Settings readable with teal icons. Preserve compact proportions.
