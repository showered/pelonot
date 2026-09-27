# App visuals

The launcher icon and dashboard backgrounds were generated with ChatGPT image
generation on 27 September 2026. The icon replaces the original vector in the
adaptive launcher icon and has a 72 px legacy launcher copy for the bike's
240 dpi display. Its source raster is
`app/src/main/res/drawable-nodpi/ic_launcher_art.png`.

The dark and light app backgrounds are
`app/src/main/res/drawable-nodpi/dashboard_background_{dark,light}.png`. Both
are 1920 × 1080 px. `MainActivity` selects one from the active theme and draws
it behind the navigation graph, so it persists across screens. Scaffold and
top-bar surfaces are transparent to let it show through. They contain no text
or UI; the right-edge track stays behind the cards rather than carrying ride
data. The ride screen retains its translucent zone-colour wash over the image.

Settings is divided into Rider, Ride, Devices, and App. The bike's 1280 dp
landscape screen shows these in a left menu with one scrollable category to
the right. Narrower screens use category chips above the content. Changing
category returns the content to its top. The in-ride settings sheet remains a
short direct route to the controls needed during a ride.
