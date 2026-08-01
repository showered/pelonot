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
- [ ] **15.1.1** Add the Supabase `auth-kt` module — only `Postgrest` is installed today
- [ ] **15.1.2** Email magic link and/or OAuth. Prefer flows with no password field: the app should not be in the business of handling credentials
- [ ] **15.1.3** Session persisted and refreshed; expiry never interrupts a ride or blocks a screen
- [ ] **15.1.4** Sign in from Settings, never as a gate on launch or on starting a class
- [ ] **15.1.5** The copy calls it what it does — **"Back up my rides"**, not "Log in". A rider on a bike is not looking for an account; they are deciding whether their history is safe

### 15.2 Identity model
- [ ] **15.2.1** Local Room profiles stay the source of truth. An account **attaches to** one local profile rather than replacing the profile system — the bike is a shared household device and that is the whole reason profiles exist
- [ ] **15.2.2** `profiles.auth_user_id UUID REFERENCES auth.users` in the cloud schema; `cloud_id` on the local `UserEntity`
- [ ] **15.2.3** Household guests never sync. A guest ride has no owner by definition
- [ ] **15.2.4** Two local profiles on one tablet may be two different accounts — nothing may assume a single signed-in user per device
- [ ] **15.2.5** **`auth_user_id` on the local `UserEntity` is the flag the whole consent gate reads.** 23.1.1 asks one question — may this profile talk to the cloud? — and this column is the answer. Nullable, and null is the default rung of the ladder rather than a missing value
- [ ] **15.2.6** A signed-in rider and an offline rider must be able to share a bike with no friction and no nagging. The offline one sees no sign-in prompt on a screen they did not open looking for one

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
- [ ] **15.5.1** Rewrite every policy against `auth.uid()` — currently all six are `USING (true)`
- [ ] **15.5.2** A rider can read and write only their own profile and their own workouts
- [ ] **15.5.3** `class_templates` stays world-readable; it is public data
- [ ] **15.5.4** Verify each policy from a second account, not by reading the SQL. This is the one place where being wrong is a breach rather than a bug
