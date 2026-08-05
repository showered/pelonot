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
- [x] **15.1.2** Email magic link and/or OAuth. Prefer flows with no password field: the app should not be in the business of handling credentials

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
- [x] **15.1.2a** **Email and password, with the confirmations that go with
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
- [x] **15.1.3** Session persisted and refreshed; expiry never interrupts a ride or blocks a screen
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
- [x] **15.2.2** `profiles.auth_user_id UUID REFERENCES auth.users` in the cloud schema; `cloud_id` on the local `UserEntity`
- [x] **15.2.3** Household guests never sync. A guest ride has no owner by definition
- [x] **15.2.4** Two local profiles on one tablet may be two different accounts — nothing may assume a single signed-in user per device
- [x] **15.2.5** **`auth_user_id` on the local `UserEntity` is the flag the whole consent gate reads.** 23.1.1 asks one question — may this profile talk to the cloud? — and this column is the answer. Nullable, and null is the default rung of the ladder rather than a missing value
- [x] **15.2.6** A signed-in rider and an offline rider must be able to share a bike with no friction and no nagging. The offline one sees no sign-in prompt on a screen they did not open looking for one
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
- [x] **15.3.1** On first sign-in, backfill the whole local history, batched and in the background
- [ ] **15.3.2** Pull on a new device: restore rides and profile
- [x] **15.3.3** Idempotent by the local workout UUID, so a retry or a re-install cannot double a ride
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
- [x] **15.5.4** Verify each policy from a second account, not by reading the SQL. This is the one place where being wrong is a breach rather than a bug.
      **Unchanged and now the item that matters most in this phase.** Reading
      `003` is not this check and neither is running it successfully. Two real
      sessions, pointed at each other's rows, bouncing. Until that has happened
      the policies are a hypothesis

      ***Done, 3 August 2026. Two real accounts, 21 probes, 0 failures*** —
      `supabase/verify_rls.py`, committed so it can be re-run when 17.5 adds the
      first schema where a rider may see somebody else's data.

      *The sessions were minted without a password: the accounts were created
      and confirmed by the owner, and each session came from an admin
      `generate_link` exchanged at `/auth/v1/verify`. That matters beyond
      hygiene — it means this check is **repeatable in CI** against a throwaway
      project, rather than something a person has to sit and do.*

      *What it actually proved, in the shape that matters: A cannot create,
      read, rename or delete B's profile; A cannot record a ride owned by B,
      cannot see one, cannot edit one, cannot delete one, and **cannot hand
      their own ride to B** — which is the `WITH CHECK`-without-`USING` hole
      15.5.1 was written to close, tested rather than assumed. A sees exactly
      one workout out of the 17 in the table. Both can read the class library,
      neither can write it, and neither can read `device_link` at all.*

      *One detail that nearly made the check theatre: PostgREST answers `204`
      with an empty body to a DELETE whether it touched a row or not, so a
      policy that silently allowed a cross-account delete looks identical to one
      that refused. Every probe sends `Prefer: return=representation` for that
      reason.*
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
- [x] **15.6.4** **The bike gets its own session, minted by an Edge Function.**
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
- [x] **15.6.5** **What the phone shows before it commits.** *"Sign in to
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
- [ ] **15.6.11** **The bike has to say the link worked, on the bike** — the
      owner's note of 5 August: *"Make sure that after you sign in / sign up the
      bike automatically responds to it and says 'successfully linked account'
      or something similar/better."*

      **Read 15.6.6 before assuming this is unbuilt**, because half of it is:
      the bike polls every two seconds and the poll is what redeems the pairing,
      so *the mechanism* exists and a linked bike does end up signed in. What
      the note is about is the **moment** — a rider who has just typed a
      password into their phone is looking at the bike waiting to be told it
      worked, and a QR screen that quietly closes has answered a different
      question. The two are easy to confuse from the code and impossible to
      confuse from the tablet, which is where this is judged.

      Three things to establish before writing anything, in this order: what the
      bike actually draws in the second after a successful redeem; whether it
      draws anything different from a **timeout**, which is the failure this
      screen must not render as silence; and whether the confirmation names the
      account — *"Signed in as simon@…"* is a fact the rider can check, where
      *"Success"* is a claim they cannot. Phase 26's rule applies to the wording
      and this is not a place to be sparing: it is the one moment in the app
      where a rider has done work on another device and needs to be told it
      landed
- [ ] **15.6.12** **A sign-*up* by QR does not finish where a sign-*in* does,
      and 15.6.11 must not pretend otherwise.** `link.js` already handles this
      correctly in its own copy — a sign-up with no session tells the rider to
      confirm by email first — but it means the bike can be sitting on a live
      pairing code that is *never* going to be redeemed within its five minutes,
      because the rider has gone to their inbox. That is not a bug in the
      pairing; it is 15.6.7's remaining half arriving through the back door, and
      it wants the bike's waiting screen to be honest about it rather than
      counting down to a failure it can predict. **Read 15.7.6 with this** — the
      confirmation link is the other end of the same journey
- [x] **15.6.13** **Android back from the QR code left the journey entirely —
      and the code went on running without it.** ***Done and observed on the
      tablet AVD.*** The owner's inbox, 5 August 2026, verbatim: *"If during
      onboarding you try to sign up and you ask to see a QR code, and then click
      'back' on android then you get taken all the way to the profile selector
      screen and your new account is already there. Please analyse this whole
      journey and make it correct. Back should just take you to the previous
      screen, not the whole way back to the start."*

      **The reported half has a one-line cause.** The offer is inside a
      `Dialog`, and a dialog dismisses itself on back — so the press never
      reached the step machinery at all. Reproduced exactly: one press on the
      QR, and the rider was on *"Who's riding?"* with the new profile sitting
      there unselected. **20.4.6 is the same cause on the three steps before
      this one**, where it was throwing away a name and three answers; the two
      items are one fix and are separate only because they are separate screens.

      Back is one step now, and the offer owns its own because only it knows
      which of its two states is showing: from the code, back to the two routes;
      from the two routes, back is *Not now*, the answer this app ships as its
      default and the control already drawn at the foot of the screen. Only
      `PairingState.Completing` refuses — a handover is in hand and dropping out
      mid-adoption is the one outcome worse than a press that looks ignored.

      **Analysing the journey as asked found a second fault, and it is worse
      than the one reported.** `AccountViewModel` outlives the dialog, so
      backing out left `startPairing`'s loop polling with a five-minute code
      still live. Measured: a rider backed out of **Ada**'s code, created a
      second profile, and the offer for **Bee** opened on *Ada's* QR — the same
      `Y4TM VX5W`, counting down from where it had got to. And `startPairing`
      captures the local profile id it was *started* for, so a phone scanning
      that code would have handed the session to **Ada** while **Bee** was the
      profile on screen: two riders, one code, and the wrong one signed in.

      `AccountViewModel.abandonPairing` is the fix and it is on the view model
      rather than in a `DisposableEffect` deliberately — the decision is about
      the *state*, not the screen, and only a code still being waited on may be
      abandoned. Observed after: a fourth profile's offer opens on the two
      routes with no code in sight

---

### 15.7 The emails come from Supabase — the owner's note, 4 August 2026

**Verbatim:** *"Any communication with a user sent by Supabase should be branded
Pelonot using our design system, and also the 'From' field and basically just
any mention of Supabase should be removed, within our control. Please use my API
key to jump in and make all that happen if possible."*

**Two of these are one setting and one of them is not, and the difference is the
whole item.** Everything a rider receives today comes out of Supabase's stock
templates: *Confirm your signup*, *Reset password*, *Magic link*, *Invite*,
*Change email*, *Reauthentication*. The subjects and the bodies are ours to
replace through the Management API — `PATCH /v1/projects/{ref}/config/auth`,
`mailer_subjects_*` and `mailer_templates_*_content`, with the same `sbp_`
personal access token `mint_session.py` and `publish_class_library.py` already
read out of `local.properties` (14.11.2 — account-wide, never in `BuildConfig`).

**The From address is the one that is not.** On the default sender the mail
leaves as `noreply@mail.app.supabase.io`, and no auth setting moves it: the
address is a property of the SMTP relay, so changing it means **configuring
custom SMTP** — a domain, a sender address on it, and credentials from whichever
provider sends the mail. That is the owner's to obtain and not a session's to
invent, which is why it is its own item below rather than a line in the first
one. It is also worth knowing that the default sender is **rate limited to a
handful of messages an hour and is explicitly not for production**, so this is
not only a branding question.

**And there is a live-service caution that outranks the work.** This is the
owner's real project, with real accounts on it, and `PATCH .../config/auth`
takes the whole auth config object. Read it, keep a copy, and send back only the
mailer fields — an accidental omission here does not break a screen, it breaks
signing in.

- [ ] **15.7.1** **Read the current auth config and check the copy in**, before
      changing anything. `supabase/` already has the pattern (`mint_session.py`,
      `verify_rls.py`): a small script, token out of `local.properties`, nothing
      secret written to the repo. The saved config is the rollback
- [ ] **15.7.2** **Six templates, written in the app's own voice** (Phase 26 —
      it applies to email as much as to a screen), and rendered with the design
      system's colours transcribed the way `web/tokens.css` was (17.15). Email
      is the one surface where the CSS cannot be shared: no external stylesheet
      survives a mail client, so it is inline styles and a table layout, and the
      dark-mode question is answered by choosing colours that work in both
      rather than by a media query most clients ignore. **Every mention of
      Supabase goes**, which is the owner's ask taken literally: the stock
      footer, the stock heading, the word in the subject line
- [ ] **15.7.3** **The sender needs a domain, and that is the owner's to
      supply.** Custom SMTP is `smtp_host` / `smtp_port` / `smtp_user` /
      `smtp_pass` / `smtp_admin_email` / `smtp_sender_name` on the same config
      object. Until it exists, 15.7.2 gets the body right and the From line
      still says `mail.app.supabase.io` — which is worth doing anyway rather
      than waiting, since the body is what a rider reads. Note the domain also
      wants SPF and DKIM or the mail lands in spam, which is a worse outcome
      than an odd sender name
- [ ] **15.7.4** **Check what is actually sent today before writing six
      templates.** Confirmation may well be off (18.11.1 is about sign-up being
      open, which is a different setting), and a template nobody receives is
      six templates' worth of work for the two that matter. The order that
      follows from the app as it stands: confirm signup, then reset password,
      then the rest
- [ ] **15.7.5** **Seen in a real inbox, not asserted from a 200.** The
      Management API returning success says the template was stored, not that it
      renders — and an email that renders wrong renders wrong in *somebody
      else's* client. Send one to a real address through the real flow, on a
      phone, which is where it will be read

---

### 15.8 The account is a thing you go and find — the owner's note, 4 August 2026

**Verbatim:** *"UX feels like an afterthought. It should be front and centre.
When you create a new profile it should be sending you a QR code so you can sign
up online (and then automatically it should link it to the account you just made
offline). And if you log in with a previously created offline-only account, it
should prompt you to link account online (dismissable with don't remind me
again)."*

**The note is right about the symptom and the diagnosis is worth stating
precisely, because "front and centre" and rule 1 of the connectivity model can
be made to sound like opposites and are not.** Rule 1 says a rider with no
account makes no request to Supabase; 15.2.6 says an offline rider sees no
sign-in prompt on a screen they did not open looking for one. Neither says the
account has to be *hidden*. What is actually true today is worse than either
rule requires: creating a profile is a bare `AlertDialog` with three text fields
and no mention that a cloud exists (`ProfileCreationDialog`), and the only route
to an account is Settings → *Back up my rides* — a destination a rider reaches
by going looking for it, which is exactly what nobody does. **The account is not
under-advertised on principle. It is under-advertised by omission**, and 23.3.1
(the reminder after ten unprotected rides) is the app's only current admission
that the rider might want one.

**So the ask is: offer it at the two moments the rider is already thinking about
identity**, and only those two. Creating a profile *is* the rider saying who
they are; selecting a profile that has ridden for months and never been backed
up is the other. Every other screen stays silent, which is 15.2.6 kept rather
than traded away.

**One conflict to settle before building, and it is with 20.3.** Profile
creation is also the screen 20.3 says *cannot go into production* — it asks a
new rider for an FTP in a text box. Both notes want that dialog rebuilt, and it
should be rebuilt **once**: a first-run flow that asks who you are, offers the
account, and gets to a usable FTP without a text box. Building 15.8 on top of
the current dialog and then rebuilding it for 20.3 is two designs for one
screen, and this project has an item about that (18.9).

**And that is exactly how it landed.** 20.3 built the screen with `Step
.Account` and an `accountOffer` slot already in it, null on a build with no
cloud; this sitting filled that slot in rather than adding a second flow
beside it.

- [x] **15.8.1** **The QR at profile creation, offered and never required.**
      **Done and observed on the tablet AVD.** `ProfileCreationScreen` writes
      the profile the moment the rider leaves the result step — `finish()`
      runs before `Step.Account` is ever composed, not inside it — so a rider
      who force-stops mid-offer still has a real, rideable profile; checked in
      `sqlite3` while still sitting on the offer screen, not taken on trust.
      `ProfileAccountOfferStep` draws the cost line, the QR (`ScanToSignIn`,
      reused rather than copied from the Settings account screen) and the
      typed form beneath it, with **"Not now" the same width and weight as the
      controls above it** rather than a grey link. Tapping it lands on the
      dashboard exactly as a build with no cloud does
- [x] **15.8.2** **Sign *up* through the QR, not just sign in.** Already done
      — this is 17.16.6, closed in the twenty-fifth sitting: `link.html`
      offers *Create an account* beside *Sign in* and reaches the confirm step
      from a brand-new account in one sitting. Nothing further needed here
- [x] **15.8.3** **Linking is automatic and the rider is never asked which
      profile.** **Done, and it needed no new plumbing.** `AccountViewModel`
      already scopes every screen it drives to
      `SettingsRepository.settings.lastProfileId`, and `createProfile` sets
      that id — via the same `onCreated` callback `UserRepository.save` has
      always returned — before `Step.Account` composes. So the QR offer at
      profile creation is automatically scoped to the profile just made, the
      same mechanism 15.6's pairing already used from Settings; there is no
      second copy of "which profile" to keep in step
- [x] **15.8.4** **The prompt for a profile that has ridden offline, and the
      dismissal that sticks.** **Done and observed**, including the dismissal
      surviving a relaunch. `AccountOfferCard` on the dashboard, gated on
      `authUserId == null`, `!accountOfferDismissed` and `stats.hasRidden`
      — never during a ride, because the dashboard is not shown during one.
      "Not now" writes `profiles.account_offer_dismissed` (migration 14 → 15,
      `UserRepository.dismissAccountOffer`) rather than holding it in memory:
      checked in `sqlite3` after a force-stop, one profile's dismissal `1`
      and another's still `0`, then confirmed the card stayed gone for the
      dismissed profile after a fresh launch
- [ ] **15.8.5** **Reconcile with 23.3.1 rather than shipping a second
      reminder.** **Half done.** The two cards cannot both show — the
      dashboard checks the account offer first and falls back to
      `BackupReminderCard` only when it does not apply — so a rider is never
      shown two nags about the same risk at once. **What is not done is the
      trigger itself.** The plan asked for 23.3.1's ten-ride count to govern
      *both* cards; this uses a simpler one instead — any completed ride at
      all (`DashboardStats.hasRidden`) — because 23.3.1a is still open: the
      count that exists is per-tablet (`SettingsRepository`) and this offer is
      per-profile, and wiring one to the other now would give a second
      profile's rides a say in whether *this* profile gets asked. Revisit once
      23.3.1a moves the count to sit beside `account_offer_dismissed`
- [x] **15.8.6** **Say what it costs, in one line, at the moment of offering.**
      **Done**, in both places 15.8 offers an account: *"Your rides get
      copied to your account. Everything keeps working without one"* on the
      profile-creation step, and the dashboard card's own wording of the same
      argument
- [x] **15.8.7** **Neither prompt may appear on a build with no cloud
      configured.** **Done and observed the negative** — `cloudConfigured` is
      read once from `ServiceLocator.accountRepository.cloudConfigured` in
      `PelonotNavGraph` and gates both `accountOffer` at profile creation and
      `showAccountOffer` on the dashboard from the same value, so a
      self-hoster's build shows neither by construction rather than by two
      separate checks that could drift

- [ ] **15.7.6** **The confirmation link points at `localhost` — the owner's
      note, 5 August 2026.** Verbatim: *"Verification email on supabase points to
      localhost. Please fix, you have access to my supabase with API key."*

      **This is the most severe defect in the onboarding path and it is not a
      cosmetic one.** Everything else in the owner's note is a screen that could
      be nicer; this one *ends the journey*. A first-time rider creates an
      account on their phone, is told to check their email, taps the link, and
      arrives at `http://localhost:3000` — a page that does not exist on the
      device they are holding. There is no recovery from that inside the flow,
      and the rider has no way to know their account was in fact created.

      **It is two settings and one line of client code, and they are genuinely
      different fixes:**

      1. **`site_url` on the project's auth config** is the default any email
         template's `{{ .ConfirmationURL }}` is built from, and it is still
         Supabase's `http://localhost:3000` scaffold value. It wants
         `https://pelonot.showered.workers.dev` — which is **17.16.4**, already
         written down and open since the site went up, and this note is the
         evidence for it arriving as a rider-visible fault rather than as
         tidiness.
      2. **`uri_allow_list`** has to contain any redirect the app asks for, or
         Supabase silently falls back to `site_url`. A fix to (1) that does not
         also widen this looks like it worked and then does not.
      3. **`link.js` passes no `emailRedirectTo` on `signUp`**, so it is relying
         entirely on (1). It should name where it wants the rider back —
         `link.html` with the pairing code still on it, so the confirmation
         lands them on the page they left rather than on the site root, which is
         the difference between finishing the pairing and starting it again.

      **Same live-service caution as the rest of 15.7, and more of it**, because
      these are the two fields that decide whether *anybody* can complete a
      sign-in: `PATCH /v1/projects/{ref}/config/auth` takes the whole object,
      so read it, keep the copy, send back only these keys. **And confirm the
      wipe of nothing** — unlike the mailer templates, a wrong value here breaks
      the existing accounts' password-reset flow too.

      Check it the way 15.7.5 asks: **a real address, a real sign-up, the link
      tapped on a phone** — a 200 from the Management API says the value was
      stored, not that the mail that arrives tomorrow carries it
