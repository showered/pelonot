> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Phase 15: Accounts — the thing that unlocks the cloud tier

**The rule this phase must not break:** the app works with no account, no
network, and no cloud, exactly as it does today. A signed-out app is not a
degraded app.

**What an account is *for*, in the order a rider cares about it** — settled by
the connectivity model, and worth writing down because the phase used to be
called "login and multi-device sync", which is engineering's order and not the
rider's:

1. **Backup.** The rider's history stops living on one tablet that can be
   dropped, wiped or replaced. This is the whole pitch, and until 15 exists the
   only answer is a manual file (12.4.4).
2. **Restore onto another bike.** The same thing from the other end.
3. **Friends on other bikes** (17, 18). Real, and third.

Note what is *not* on that list: household social. Rule 3 of the connectivity
model gives that to everyone, account or not, and Phase 24 builds it without
touching any of this. **Signing in must never be the way a rider reaches
something they could have had offline.**

### 15.1 Auth
- [x] **15.1.1** Add the Supabase `auth-kt` module — only `Postgrest` is installed today
- [ ] **15.1.2** Email magic link and/or OAuth. Prefer flows with no password field: the app should not be in the business of handling credentials

      **Amended by the owner, 3 August 2026**, who asked for *"profile setup
      (email, password, confirm email, etc)"* directly. Both halves are built,
      and the tension between them is resolved by **which one is the default**
      rather than by picking one:

      - **Email and password is the floor** (15.1.2a). It is what works with no
        second device, no email client on the tablet, and no network round trip
        through an inbox. A bike in a garage may be the only screen the rider
        has to hand.
      - **The QR hand-off is the door** (15.6), and it is the one that honours
        the original instinct: a rider who scans it never types a credential on
        the bike at all, their password manager does the work on a device
        built for typing, and the tablet only ever holds a session it was
        handed.

      The thing the original wording was really protecting against is a bike
      tablet, in a shared household room, becoming a place where passwords are
      typed and possibly remembered. 15.6 removes that for anyone with a phone
      and 15.1.2a keeps the app usable for everyone else
- [ ] **15.1.2a** **Email and password, with the confirmations that go with
      them.** Sign up takes an email, a password and a **repeated password** —
      the second field is not ceremony on a bike, where the keyboard is a
      touchscreen at arm's length and a typo in a password is a support
      problem no offline app can solve. The project's Supabase instance has
      `mailer_autoconfirm` off, so **sign-up does not produce a session**: it
      produces an email the rider has to open. That state is real, it lasts
      minutes to hours, and it must be drawn as a state of its own — *"check
      your email, then come back and sign in"* — rather than as a failure or,
      worse, as success. Rate limits on the built-in mailer are low (a handful
      an hour), so a rider who asks twice must be told that rather than shown
      a generic error
- [ ] **15.1.3** Session persisted and refreshed; expiry never interrupts a ride or blocks a screen
- [x] **15.1.4** Sign in from Settings, never as a gate on launch or on starting a class
- [x] **15.1.5** The copy calls it what it does — **"Back up my rides"**, not "Log in". A rider on a bike is not looking for an account; they are deciding whether their history is safe
- [x] **15.1.6** **Nothing in this phase may be reachable during a ride, and
      nothing may block one.** Sign-in lives in Settings, and Settings is
      reachable mid-ride through the sheet (11.6.10) — so the failure to avoid
      is a rider who taps *Back up my rides* at minute 12 and gets a modal over
      a class they are pedalling. Auth screens are their own destination, not a
      dialog

### 15.2 Identity model
- [x] **15.2.1** Local Room profiles stay the source of truth. An account **attaches to** one local profile rather than replacing the profile system — the bike is a shared household device and that is the whole reason profiles exist
- [ ] **15.2.2** `profiles.auth_user_id UUID REFERENCES auth.users` in the cloud schema; `cloud_id` on the local `UserEntity`
- [x] **15.2.3** Household guests never sync. A guest ride has no owner by definition
- [ ] **15.2.4** Two local profiles on one tablet may be two different accounts — nothing may assume a single signed-in user per device
- [x] **15.2.5** **`auth_user_id` on the local `UserEntity` is the flag the whole consent gate reads.** 23.1.1 asks one question — may this profile talk to the cloud? — and this column is the answer. Nullable, and null is the default rung of the ladder rather than a missing value
- [ ] **15.2.6** A signed-in rider and an offline rider must be able to share a bike with no friction and no nagging. The offline one sees no sign-in prompt on a screen they did not open looking for one
- [ ] **15.2.7** **One account, one local profile — checked, not hoped for.**
      Two local profiles pointing at the same `auth_user_id` is not a household
      arrangement, it is one rider's history split in half: the cloud keys a
      profile *by* the account id, so the two rows fight over one record and
      their rides pool under a single owner with no way to separate them
      afterwards. And it is an easy mistake to make on a shared bike — sign in
      while the wrong profile happens to be selected. So attaching queries for
      an existing holder first, refuses by name (*"that account is already
      backing up Priya on this bike"*), and **signs the session back out**,
      because a session with nothing attached is a tablet that looks signed in
      and will never send anything
- [x] **15.2.8** **The SDK holds one session; a household holds several riders.**
      15.2.4 says nothing may assume a single signed-in user per device, and
      that is right about the *data model* — `auth_user_id` is per profile and
      two riders may have two accounts. But the client library holds exactly one
      session per process, so at any moment the tablet can only be *carrying*
      one rider's credentials. Those two facts have to be reconciled somewhere,
      and the honest place is the gate: **having an account and being signed in
      on this tablet are different questions**, and only the second one can send
      a request.

      The failure it prevents is specific. Priya finishes a ride while the
      tablet holds Simon's session; her profile has an `auth_user_id`, so a gate
      that only looked at the column says yes, and the upload goes out under
      Simon's JWT carrying Priya's `user_id`. `003`'s `WITH CHECK (user_id =
      auth.uid())` refuses it — correctly — so the outcome is not a leak but a
      **permanent silent failure**, reported to the rider as a network problem
      forever. `CloudAccess` therefore asks who the session belongs to and
      matches it against the profile, and it *waits* for the SDK to finish
      loading from storage before answering, because a cold-start "nobody" would
      stop a backlog drain that had every right to run.

      The consequence to state on screen rather than hide (15.2.6 governs how):
      **a second rider's rides wait until that rider signs in**, which is
      correct, is not a failure, and is invisible unless somebody says so

### 15.3 Sync in both directions
- [ ] **15.3.1** On first sign-in, backfill the whole local history, batched and in the background
- [ ] **15.3.2** Pull on a new device: restore rides and profile
- [ ] **15.3.3** Idempotent by the local workout UUID, so a retry or a re-install cannot double a ride
- [ ] **15.3.4** Conflict rule, written down and one line long: **local wins for a ride in progress, last-write-wins for RPE and profile fields, tombstones win over everything** (12.3.5)
- [ ] **15.3.5** Metric series are large — a 45-minute ride is ~2,700 samples. Decide deliberately whether the full series goes up or only the aggregates plus a downsampled trace, and record the reasoning
- [ ] **15.3.6** Sync never runs on the ride's critical path and never blocks the HUD

### 15.4 Leaving
- [ ] **15.4.1** Sign out keeps every local ride. A rider signing out has not asked to lose their training history. **They drop from Account to Local profile** — a rung down the ladder, not out of the app: the household leaderboard (24.1) still has them on it, with all the same rides
- [ ] **15.4.4** Deleting the cloud copy (15.4.2) or the whole account (15.4.3) likewise changes nothing on this tablet. Say so in the confirm dialog, in those words, because the natural fear is exactly the opposite
- [ ] **15.4.2** "Delete my cloud data" as a separate, explicit action, with the local record untouched
- [ ] **15.4.3** Account deletion end to end, since GDPR applies to a hobby project too

### 15.5 RLS, properly

**Applied 3 August 2026. Verified against the endpoint for everything that does
not need a second account; 15.5.4 itself is still open and is still the item
that matters.** The eighteenth sitting wrote the policies as part of 14.2.1,
because the identity decision and the policies are the same decision: once
`profiles.id` **is** the auth user id, every policy below is one line, and
before that they could not be written at all. None of these boxes may be ticked
on the strength of the file existing — 15.5.4 is the whole point.

- [x] **15.5.1** Rewrite every policy against `auth.uid()` — currently all six are `USING (true)`.
      *Written in `003`. Eight policies rather than six, split by operation, and
      **`WITH CHECK` as well as `USING` on every write** — they answer different
      questions (which existing rows you may touch, versus what the row is
      allowed to look like afterwards), and a policy with only `USING` lets a
      rider `UPDATE` their own row and set its id to somebody else's*
- [x] **15.5.2** A rider can read and write only their own profile and their own workouts.
      *Written. Also in `003` and worth knowing: `workouts.user_id` becomes
      `ON DELETE CASCADE`, which is the **opposite** of the local rule. Locally,
      deleting a profile keeps its rides — a housemate leaving does not erase
      their training history off the bike. In the cloud, deleting the account is
      the rider asking for their data to be gone (15.4.3, GDPR), so it must take
      the rows with it. `SET NULL` would leave orphans no `auth.uid()` matches:
      invisible to every policy and therefore **undeletable by the rider they
      belonged to**, which means 15.4.2 would not have deleted their cloud data.
      Same-looking constraint, opposite requirement, and backwards in either
      direction is a serious bug*
- [x] **15.5.3** `class_templates` stays world-readable; it is public data.
      *Written — `SELECT` to `anon` and `authenticated`, and **no write policy
      at all**, which under RLS means nobody but the service role can author a
      class. A class comes from `classlibrary/build.py` and ships in the APK;
      nothing in the app has any business writing one*
- [ ] **15.5.4** Verify each policy from a second account, not by reading the SQL. This is the one place where being wrong is a breach rather than a bug.
      **Unchanged and now the item that matters most in this phase.** Reading
      `003` is not this check and neither is running it successfully. Two real
      sessions, pointed at each other's rows, bouncing. Until that has happened
      the policies are a hypothesis
- [x] **15.5.5** **The grants move too, and RLS cannot do it for you.** RLS
      *narrows* access a role already has and can confer none — the lesson of
      14.0, where every request died `42501` before a policy was ever evaluated.
      `003` revokes `anon`'s access to `profiles` and `workouts` entirely and
      grants DML to `authenticated` instead, including `DELETE`, which 002
      granted on nothing: a rider who cannot delete their own data does not have
      an account, they have a submission
- [x] **15.5.7** **The privileges nobody granted.** Reading
      `role_table_grants` back after `003` — rather than trusting the run —
      showed `anon` holding `TRUNCATE`, `TRIGGER` and `REFERENCES` on
      `class_templates`, and then on `device_link` the moment `004` created it.
      Neither migration granted them: they come from Supabase's own default
      privileges, which grant ALL on new tables in `public` to `anon` and
      `authenticated`. `003`'s `REVOKE ALL` happened to catch them on `profiles`
      and `workouts`, so the two tables it did not name kept the full set.

      **TRUNCATE ignores row-level security**, being a table-level operation, so
      no policy could have stopped it — the 72-class library was, on paper, one
      statement from empty. It is not reachable: PostgREST speaks only SELECT,
      INSERT, UPDATE and DELETE over HTTP, and `anon` has no login of its own.
      That is precisely the state 14.0 described the old `USING (true)` policies
      in — *a loaded gun rather than a fired one* — and the answer is the same.
      `005_revoke_truncate.sql`.

      The rule it leaves behind is the one worth keeping: **after any migration
      that creates a table, read the catalogue back.** What a migration granted
      and what a table ends up holding are different questions, and only the
      second one is the answer. It is also why this was found at all — the
      alternative was re-reading `003`, which is correct and would have told us
      nothing
- [ ] **15.5.6** **No cross-rider visibility yet, on purpose.** Every policy in
      `003` is "your own rows and nobody else's". That is the correct floor to
      build a friend graph on (17.5) and the wrong thing to relax speculatively
      in advance of one — the first schema where a rider can see another
      rider's data deserves its own item, its own review and its own second
      account to test from

---

### 15.6 Signing in by QR code — the owner's note, 3 August 2026

**The owner's words:** *"when logging in (and signing up?) you can scan a QR
code and do it on your phone. Good for password managers etc and generally good
experience. Not sure if that's feasible with our supabase setup."*

**It is feasible, and it is the flow this app should have led with.** The bike's
tablet is the worst keyboard in the house — 1280 dp of glass at arm's length,
no password manager, an on-screen keyboard that eats half the screen, and a
shared room. A phone is the opposite of all four. What the rider gets is the TV
flow they already know from every streaming app: a code on the big screen,
scanned, and the big screen signs itself in. It is also the honest reading of
15.1.2's *"the app should not be in the business of handling credentials"* — in
this flow **the bike never sees one**.

**Feasibility, since the owner asked directly.** Supabase has no device
authorization grant of its own, so this is built rather than configured, out of
three parts it does have: a table with row-level security, a `SECURITY DEFINER`
function (the standard way to let an unauthenticated caller touch one row of a
locked table without granting it the table), and the admin API's ability to mint
a one-time email OTP for a user who is already authenticated somewhere else.
Nothing exotic and nothing that needs the anon key to be trusted.

**The shape, and why it is this shape.** The naïve version — the phone hands the
bike its own refresh token — is the one to avoid, and the reason is specific:
**this project has refresh-token rotation on** (`refresh_token_rotation_enabled`,
with a 10-second reuse interval). Two devices sharing one token family means the
first one to refresh invalidates the other, and a detected reuse can revoke the
family and sign out *both*. So the bike must end up with a **session of its
own**, minted for it, not a copy of the phone's.

- [x] **15.6.1** **`device_link`, a table that holds a pairing for five
      minutes.** `code` (short, unambiguous, typeable — the QR is the fast path
      and not the only one), `secret_hash`, `label`, `created_at`,
      `expires_at`, `claimed_at`, `claimed_by`, and the payload. RLS on, and
      **no policy at all for `anon` or `authenticated`** — every access is
      through a function, which is what stops a leaked anon key enumerating
      pending pairings
- [x] **15.6.2** **The code is not the credential.** The bike generates a random
      **device secret**, sends only its SHA-256, and shows the *code*. The QR
      and the printed code are therefore safe to be photographed off a wall:
      collecting the session needs the secret, which never leaves the tablet.
      Without this split, anyone who can see the bike's screen from across the
      room can race the bike for its own session
- [x] **15.6.3** **Three functions, `SECURITY DEFINER`, one grant each.**
      `device_link_begin` (to `anon`: create a pairing, return the code),
      `device_link_claim` (to `authenticated` only: attach my account to this
      code), `device_link_poll` (to `anon`: given code **and** secret, return
      the payload once and delete the row). Single use and short TTL are
      enforced in the function, not in the caller. `search_path` pinned on all
      three — a `SECURITY DEFINER` function without it is the classic Postgres
      privilege-escalation footgun
- [ ] **15.6.4** **The bike gets its own session, minted by an Edge Function.**
      The phone calls it with its own access token; the function verifies that
      token, asks the admin API for a one-time email OTP for that user, and
      writes it into the pairing row. The bike collects it and verifies it like
      any OTP, which gives it an independent session and its own refresh-token
      family. The service-role key stays inside the function and reaches
      neither device. **This is the only part that needs anything deployed
      beyond SQL**, and if it cannot be deployed the fallback is written down
      in 15.6.9 rather than improvised

      *Deployed 3 August 2026 and **half verified**: the refusals are checked,
      the success path needs an account. With no token it answers `401`, and
      with a garbage one `401 that session is not valid` — which is its own
      check rather than the platform's, and that distinction is the reason
      `verify_jwt` is **false** on the function. The platform's gate would
      accept the **anon key** as a valid JWT, which proves nothing about who is
      calling; the function asks `auth.getUser(token)` instead. It also claims
      the pairing row **before** minting anything, so an expired code cannot
      leave a live one-time credential for the rider's account sitting in a
      log.*
- [ ] **15.6.5** **What the phone shows before it commits.** *"Sign in to
      Pelonot on **PLTN-RB1VQ**?"* — the label the bike sent, its own words,
      shown to the rider before anything is claimed. A pairing flow that does
      not name the device being paired is a phishing primitive: the QR is a URL
      and a URL can be printed by anyone
- [x] **15.6.6** **What the bike shows while it waits, and what it does when it
      stops.** A QR, the code underneath it in large type, a plain fallback URL,
      and a countdown that is honest about the five minutes. On expiry it
      offers a fresh code rather than sitting on a dead one — and it polls at a
      human interval (two seconds), not a spin
- [ ] **15.6.7** **Signing *up* by QR too**, which is the owner's parenthesis
      and is the easier half: the phone is a browser, so it can run the whole
      email-and-password-and-confirm dance with a password manager, and the
      pairing is just the same hand-off afterwards. The bike's own sign-up
      (15.1.2a) stays for the rider with no phone
- [ ] **15.6.8** **It must degrade to nothing.** A build with no cloud
      configured shows no QR, no code and no mention of any of this — rule 1 of
      the connectivity model. A build with a cloud but no web app configured
      shows the code and the URL and says plainly that the pairing page is not
      set up, rather than drawing a QR that leads nowhere
- [ ] **15.6.9** **The fallback if 15.6.4 cannot be deployed**, written down now
      so it is a decision rather than a scramble: the phone hands over a
      `session` for a **second** sign-in it performs itself, or the flow is
      dropped to *"type this code into the web app, then type the six digits it
      gives you into the bike"* — which is worse UX but needs no service role
      anywhere. **What must not happen is the phone's own refresh token being
      copied to the bike**, for the rotation reason above
- [ ] **15.6.10** **The pairing table is the one piece of schema in this project
      that an unauthenticated stranger can write to**, and it should be treated
      that way: expired rows deleted on every `begin`, a cap on how many
      unclaimed pairings can exist, and nothing in the row that identifies a
      rider until the moment one claims it
