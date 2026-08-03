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

- [ ] **17.1** Stack and repo layout. A separate top-level `web/` directory or a separate repo — **the Android build must never depend on it**

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
- [ ] **17.13** **The pairing page — `link.html`, and it is the first thing to
      build here** (15.6). The whole of the QR sign-in flow lands on this page,
      it is the reason the web app exists before it has anything pretty to show,
      and it is one screen: sign in (or sign up), see which bike is asking, and
      confirm. **It must name the device** (15.6.5), and it must work on a phone
      held in one hand, which is the only device it will ever be opened on
- [ ] **17.14** **Where the endpoint comes from, on a static page with no build
      step.** The same rule as 14.10 and for the same reason: not in the source.
      A `config.js` beside the page, git-ignored, with a checked-in
      `config.example.js` — and the page says plainly that it is unconfigured
      rather than failing in the console, which is where this project's cloud
      defects have historically gone to die
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

- [ ] **18.1** Friends list and requests, mirroring 17.5
- [ ] **18.2** A feed of friends' recent rides on the dashboard, below the rider's own stats and never above them
- [ ] **18.3** Kudos, and nothing that requires typing during or just after a ride
- [ ] **18.4** Compare a class you both rode — same class, both traces, one chart. This is the version of a leaderboard that is actually motivating
- [ ] **18.5** Friend leaderboard on the post-ride summary, alongside the rider's own history (11.4.1)
- [ ] **18.6** **The HUD stays social-free.** Nothing on the strip during a ride. It has half a second of attention and it belongs to the interval
- [ ] **18.7** A comparison across riders is honest when both sides are measured watts off their own boards (2.1a), and misleading when either side is modelled. Carry the caveat on the modelled ones specifically rather than on all of them — a blanket disclaimer nobody reads is the same as none
- [ ] **18.8** Mute, block and report exist from the first version that has a feed, not the version after someone needs them
- [ ] **18.9** Every screen in this phase is built on top of its Phase 24 equivalent rather than beside it. If 18.5 and 24.1 are two implementations of a leaderboard row, one of them will drift and it will be the one nobody rides against
- [ ] **18.10** A friend's numbers arrive over the network, so this phase inherits every rule in the *Corrections* table about failures that are caught and shown nowhere. An empty friend leaderboard must say whether it is empty or unreachable
