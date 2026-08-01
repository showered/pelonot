> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## The connectivity model — offline by default, account by choice

**Settled by the owner, 1 August 2026.** This section is the canonical
statement of how offline and online relate in this app. Where an older item
below contradicts it, this section wins and the item is wrong.

### The four rules

1. **Every rider is offline by default.** A fresh install makes **no request to
   Supabase at all** — no cloud backup, no class fetch, no anonymous upload,
   nothing. Offline is not a degraded mode or a fallback. It is the mode.
2. **Creating an account unlocks cloud backup.** That is what an account is
   for, first and mainly. Signing in *is* the consent; there is no second
   consent to collect and no separate switch to find.
3. **An offline rider still gets social — with the people on their own bike.**
   Everyone with a profile on this tablet is a household, and a household
   leaderboard is a Room query. It needs no network and no account from
   anybody, including the rider looking at it.
4. **A signed-in rider gets both.** Household social with everyone on the bike
   — account or not — *plus* friends signed in on other bikes.

### The identity ladder — three rungs, not two

| Rung | What it is | What it gets |
|------|-----------|--------------|
| **Guest** | No profile. No owner, by definition | The ride, kept only if saved to a profile afterwards. Never synced (15.2.3), never on a leaderboard |
| **Local profile** | A row in `profiles` on this tablet. No account | Everything the app does locally, plus household social. The cloud does not exist for them, and nothing on screen implies it should |
| **Account** | A local profile with `auth_user_id` attached | The above, plus cloud backup, restore onto a second tablet, and friends elsewhere |

**The middle rung is the one that matters**, and it is the one the plan has
never had a name for. It is where most riders will live, it is where the
default lands, and until now every feature was implicitly designed for the
rung above or below it.

Four consequences that must not be broken:

- **An account attaches to one local profile, not to the device.** Two profiles
  on one tablet may be two different accounts, or one account and one offline
  rider. Nothing may assume a single signed-in user per device (15.2.4).
- **Signing out drops a rider from Account to Local profile and takes nothing
  away.** Not a ride, not a place on the household leaderboard (15.4.1).
- **Household social must not consult the network at all.** Not "degrade
  gracefully offline", not "fail quietly" — if a feature needs a network call
  to work, it is not in the household tier. This is what makes rule 3 true
  rather than aspirational.
- **A comparison between two riders on the same bike is the fairest one this
  app will ever produce** — same board, same knob, same calibration, often the
  same week. Cross-bike comparison is subject to per-bike differences (2.2a)
  and it is *that* one 18.7's honesty caveat is for. The household one needs no
  caveat, which is a good reason to build it first.

### What the model makes false today

Three things in the shipped code contradict rule 1. None of them is a defect
against the plan as it stood — the plan asked for exactly this — which is why
they are here rather than in *Corrections*.

**All four are now fixed — 1 August 2026, ninth sitting.** The table is kept
because the fourth row is the interesting one: it was not on the list, and it
had been true for longer than any of the others.

| Was | Under the model | Item |
|-----|-----------------|------|
| `ClassTemplateSeeder` fetched the class library from Supabase on first launch whenever the build carried credentials | Ship the library in the APK. The cloud becomes an *update* channel that only a signed-in rider uses | **23.2** ✅ |
| `WorkoutService` enqueued `WorkoutSyncWorker` after every profile ride — no account, nobody's consent, and the ride arrived in a shared pool with no `user_id` on it (14.2.1) | Enqueue only for a profile with an account attached | **23.1.2** ✅ |
| The only gate was build-time: `SupabaseModule.isConfigured`, which asks whether *this build* has a URL and a key | The gate is runtime and per-profile: is **this rider** signed in? A build detail is not a consent | **23.1.1** ✅ |
| `UserRepository.save` upserted a profile's **name, weight and FTP** to the cloud on every create, rename and edit — so a rider who never signed in was in Supabase from the moment they typed their name | Same gate. It was never listed here because the search had been for the features known to sync, not for the client itself | **23.1.1** ✅ |

**The one with real teeth was the first.** `assets/classes` held **5** class
JSONs; the cloud holds **72**. Under the old model that gap was a fallback
nobody would hit with a configured build. Under this one, five classes is what
the default rider would have got, forever, and the class library is most of the
product.
