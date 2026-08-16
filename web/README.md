# Pelonot on the web

The companion app: your rides off the bike, and the page a bike's QR code points
at (PLAN Phase 17, and 15.6 for the pairing).

**It is a view onto the same Supabase project the bike backs up to.** It can
only ever see riders with accounts — a household profile with no account does
not exist in the cloud at all, not even as an empty row — so a housemate who
has never signed in is invisible here rather than being someone with no rides.
That distinction is 17.10 and it is the one thing about this app that is
peculiar to Pelonot. The Riders view says it in words, on the page, because it
is the sentence a rider would otherwise get wrong on their own.

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

**Which publishable key**: the `sb_publishable_…` form, not the legacy JWT that
begins `eyJ` (17.16.3). Both work and neither is a secret; they revoke
separately, which is what makes having one of each a trap the day one is
rotated.

## The pages

| Page | What it is |
|------|------------|
| `index.html` | The app: your rides, one ride, the leaderboard, the other riders, and you |
| `link.html` | The QR target: sign in on a phone, and the bike signs itself in |

`link.html` expects the pairing code in the URL **fragment** —
`link.html#ABCD2345` — because a fragment is never sent to a server and never
lands in an access log. A rider who opens the page directly can type the code
instead.

`index.html` routes on the fragment too: `#/rides`, `#/ride/<id>`, `#/board`,
`#/riders`, `#/you`. One document, five sections, no router and no history API
— a link to a ride survives a reload and that is the whole requirement.

### What each view is for

- **Rides** — your own history, with all-time totals and twelve weeks of output
  as bars. Twelve rather than one because a week is the wrong window for
  somebody who rides once of them (22.5). A list row answers *which ride*, so it
  carries the time, the date, the class and the distance and not five figures
  (26.1.3).
- **One ride** — the three traces with **power zones** behind the watts and
  **heart-rate zones** behind the pulse (21.4.2), time in zone, and the
  provenance sentence. It is also where a ride gets **a name** and where it can
  be **hidden**: the bike has no keyboard worth the name, which is this app's
  whole premise.
- **Board** — every registered rider's best effort at one class, on total output
  or on output per kilo. Only rides where the bike **measured** the watts are
  ranked; the shipped power curve is 137 W RMSE against the real board, so a
  modelled watt and a measured one are not the same number.
- **Riders** — everyone's recent rides, with kudos and one comment each, and the
  directory of who is here at all. The feed shows nothing until riders turn
  sharing on: it is off by default.
- **You** — name, bio, FTP, weight, maximum heart rate, units, the sharing
  switch, and a JSON export of everything the cloud holds about you.

## What it needs on the server

Run the migrations in `../supabase/` in order. Two matter to this app:

- `004_device_link.sql` — the pairing page. Without it, `link.html` reports that
  the code has expired, which is the honest thing for it to say.
- `008_companion_web.sql` — the bio, the units, the sharing switch, ride titles,
  kudos and comments. Without it the Rides, Board and one-ride views still work
  and the Riders view does not.

The **preferred** hand-off also wants an Edge Function deployed:

```bash
supabase functions deploy link-device --project-ref <your-ref>
```

No CLI? The Management API takes it as a multipart upload, which is how this
one was actually deployed:

```bash
curl -X POST "https://api.supabase.com/v1/projects/$REF/functions/deploy?slug=link-device" -H "Authorization: Bearer $TOKEN" -F 'metadata={"entrypoint_path":"index.ts","name":"link-device","verify_jwt":false};type=application/json' -F "file=@supabase/functions/link-device/index.ts;type=application/typescript"
```

`verify_jwt` is **false** on purpose and it is not a hole: the platform's own
JWT gate would accept the anon key as a valid token, which proves nothing about
who is calling. The function verifies the caller's token itself with
`auth.getUser(token)` and answers `401 that session is not valid` to anything
else — checked with no token and with a garbage one.

Without it the page still works and falls back to handing over the phone's own
session — which signs the phone out, and says so before it does it. The reason
the good version exists is in `004_device_link.sql`: this project has
refresh-token rotation on, so two devices sharing one token family revoke each
other.

## Deploying it

The owner's deployment is <https://pelonot.showered.workers.dev/> — a Cloudflare
Worker serving these files as static assets, which also **trims `.html`**:
`/link.html` answers `307` to `/link`, and the QR's fragment survives it, which
is the thing that had to be measured rather than assumed (17.16).

**The deploy is `git push`, and that is the whole of it** (17.16.2, answered by
the owner on 16 August 2026). Cloudflare is watching the repository, so the
branch reaching GitHub republishes the site: no build step, no `wrangler`
invocation, nothing to install.

```bash
git push
```

`wrangler.jsonc` in this directory is a **manual alternative**, and it is a
reconstruction rather than a transcript. The `name` in it is taken from the live
URL, so deploying with it updates that Worker rather than standing up a second
one — but it is not the route anyone takes, and it has never been run from this
repository.

```bash
cd web && npx wrangler deploy    # not the route; kept as a fallback
```

**Then run the check, which is the part that actually settles anything:**

```bash
./web/check-deployed.sh
```

It fetches every file, diffs it against this working tree, and now also reports
which publishable key form the host is serving. It needs no credentials and
deploys nothing. `config.js` is not diffed on purpose — it is git-ignored, so
the deployed one is *meant* to differ.

**And `config.js` is the one thing a push does not carry.** It is untracked, so
it cannot ride along with a commit, and the host serves it anyway — `200`, still
on the legacy JWT key form. Something on the hosting side supplies it. Nobody
has established what, so **17.16.3 cannot be closed from this repository**:
moving the deployed key needs the Cloudflare side, not a change here.

**Nothing about deploying *used* to be automatic, and that cost something.** A
fix to `link.js` landed, was verified against the live endpoint from a local
copy, and never reached the host; the next day the owner scanned a QR and met
the unfixed page (17.16.6). The check exists because of that, and it is still
worth running — a push that deploys is not the same as a push that deployed.

## Redirect URLs

Email confirmation links go to the project's **Site URL** (*Authentication →
URL Configuration*). Point it at wherever this app is served from, or a rider
who signs up will get a link to somewhere that is not running.

## What it deliberately does not do

- **No friend graph.** The owner's decision, 3 August 2026: with three or four
  riders who already know each other, request/accept/block is ceremony around a
  fact everybody already agrees on. Everyone registered is on everyone's board
  (`007_everyone_leaderboard.sql`), and the friend graph that was written for
  17.5 was dropped in the same sitting it was applied.
- **No public profiles, and no public anything.** Every function requires a
  session; the anon key reaches the class library and nothing else. There is no
  URL here that shows a rider's rides to somebody who is not signed in, which is
  17.9's question answered by not building the thing that raises it.
- **No mute, block or report** (18.8). What exists instead is that **the rider
  whose ride it is can delete any comment on it**. At four accounts on an
  invitation-shaped project that is the moderation floor that matters; 18.8 is
  the item to build if this ever has more riders than 18.11 was written for.
- **No writes to what happened on a ride.** The bike is the source of truth for
  the numbers; this app writes a ride's *name* and *visibility*, and the rider's
  own profile. Nothing here can change a watt.
- **No charting library.** The traces are drawn straight into an SVG, and they
  **break the line across a gap in the series** — the bike stops recording when
  the board goes quiet or the rider stops, and joining across that absence would
  be a claim about seconds nobody measured.
