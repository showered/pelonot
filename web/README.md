# Pelonot on the web

The companion app: your rides off the bike, and the page a bike's QR code points
at (PLAN Phase 17, and 15.6 for the pairing).

**It is a view onto the same Supabase project the bike backs up to.** It can
only ever see riders with accounts — a household profile with no account does
not exist in the cloud at all, not even as an empty row — so a housemate who
has never signed in is invisible here rather than being someone with no rides.
That distinction is 17.10 and it is the one thing about this app that is
peculiar to Pelonot.

---

## Running it

There is **no build step and no dependencies to install** (17.1). Two ways in:

```bash
python3 -m http.server 8000 --directory web
```

then open <http://localhost:8000>. Or just double-click `index.html` — the
Supabase library is loaded as a classic script precisely so that a `file://`
page works.

Copy `config.example.js` to `config.js` and put your project URL and
**publishable** (anon) key in it. `config.js` is git-ignored, for the same
reason `local.properties` is: an endpoint is somebody's private household
project (14.10.4). A page with no config says so on screen rather than failing
into a console nobody has open.

## The pages

| Page | What it is |
|------|------------|
| `index.html` | Sign in, your rides, one ride in detail |
| `link.html` | The QR target: sign in on a phone, and the bike signs itself in |

`link.html` expects the pairing code in the URL **fragment** —
`link.html#ABCD2345` — because a fragment is never sent to a server and never
lands in an access log. A rider who opens the page directly can type the code
instead.

## What it needs on the server

Run the migrations in `../supabase/` in order. `004_device_link.sql` is the one
this app's pairing page depends on; without it, `link.html` reports that the
code has expired, which is the honest thing for it to say.

The **preferred** hand-off also wants an Edge Function deployed:

```bash
supabase functions deploy link-device --project-ref <your-ref>
```

Without it the page still works and falls back to handing over the phone's own
session — which signs the phone out, and says so before it does it. The reason
the good version exists is in `004_device_link.sql`: this project has
refresh-token rotation on, so two devices sharing one token family revoke each
other.

## Redirect URLs

Email confirmation links go to the project's **Site URL** (*Authentication →
URL Configuration*). Point it at wherever this app is served from, or a rider
who signs up will get a link to somewhere that is not running.

## What it deliberately does not do

- **No friend graph, no feed, no public profiles.** Every policy on the project
  is "your own rows and nobody else's" (15.5.6), which is the correct floor to
  build 17.5 on and the wrong thing to relax in advance of one.
- **No writes to a ride.** The bike is the source of truth for what happened on
  it; this reads. Profile editing (17.4) is the first write this app should
  learn, and it is not built yet.
- **No charting library.** The three traces are drawn straight into an SVG, and
  they **break the line across a gap in the series** — the bike stops recording
  when the board goes quiet or the rider stops, and joining across that absence
  would be a claim about seconds nobody measured.
