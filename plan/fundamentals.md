> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## What is fundamental and what is not

Phases 12–19 were requested together, but they are not the same kind of work,
and building them in the order they were listed would be a mistake. The
ordering below is the one to work in.

**Fundamental — the app is incomplete without these:**

| # | Why it is not optional |
|---|------------------------|
| 12 Ride history + delete | The app records rides and offers the rider no way to see them or get rid of a bad one. Everything downstream — charts, sync, social — is a view onto a history screen that does not exist. |
| 13 Units | A UK rider is shown kilometres with no way to change it. It is an afternoon's work and it is currently wrong for a large fraction of the audience. |
| 14 Working sync | Cloud sync was ticked as complete having **never written a single row** — see 14.0. The schema is fixed and writes now land; the app driving it is still unproven. Every feature in 15, 17 and 18 sits on top of this. **Re-scoped by the connectivity model: fundamental to the *cloud tier*, not to the app.** A rider who never signs in is not missing anything here, which is a demotion in urgency and a promotion in how carefully it must be gated (23.1). |
| 15 Accounts | Sync without an identity puts every rider's data in one anonymous pool. This is also where the current RLS policies stop being a placeholder and start being a security problem. **Same re-scoping as 14** — and note that the anonymous pool is not hypothetical: 14.2.1 is open and every ride the app has ever uploaded went up unattributed. |
| 12.7 Room migrations | `fallbackToDestructiveMigration()` deletes the rider's entire training history on any schema change. Phases 12–19 all change the schema. This has to go first. |

Two more that belong in the fundamental list, added after riding the app on the
tablet:

| # | Why it is not optional |
|---|------------------------|
| 11.1a Getting between the HUD and the app | There is no door between the HUD and the full app in either direction, and the app does not come forward when the class ends. This is the journey a rider makes most often during a ride and it currently routes through the launcher. |
| 20.1 The profile selector | It is the first screen anyone sees and the thing that makes a shared household bike work, and it is a cluster of small cards in the corner of a 1920×1080 screen. |

And two more, added by the connectivity model on 1 August 2026:

| # | Why it is not optional |
|---|------------------------|
| 23.1 The consent gate, and 23.2 the bundled class library | Rule 1 says an install with no account makes no request to Supabase. Today it makes two, and the one on the first-launch path is the reason the default rider would see **5 classes instead of 72**. This is the difference between the offline tier being the product and being a stub. |
| 24 Household social | It is the *only* social tier most riders will ever be in, it needs no account, no network, no RLS and no schema the app does not already have, and the comparison it makes is the fairest one this app can produce. It is also the cheapest thing on this list. |

**Nice to have — real value, none of it load-bearing:**

16 (beyond the post-ride charts), 17, 18, most of 19, and the avatar work in
20.2. A companion web app
and a friends feed are good ideas for an app people already use daily; they
are not what makes people use it daily. The bike, the HUD and an honest record
of the ride are.

> Note what the connectivity model did to this list: it **moved social from
> "nice to have" to "half fundamental"** without moving 17 or 18. The half that
> is fundamental is the half that needs nothing — Phase 24. The half that needs
> accounts, RLS, a friends graph and a moderation policy is still nice to have,
> and now has a reason to wait rather than merely a lack of urgency.

> One caution that applies to all of 16–18, **now much narrower than it was**:
> a ride on the bike records the board's own measured watts (2.1a), so those
> numbers are as comparable between riders as the hardware is. It is
> `PowerModel` that stays uncalibrated until a bike fits its own curve (2.2a),
> and it only ever governs simulated rides and the 11.2.1 resistance band —
> a suggestion and a fiction, never a record. Charts, leaderboards and
> friend comparisons should read `SensorReading.powerIsMeasured` and say which
> they are showing, rather than captioning everything "estimated" — or, worse,
> presenting a modelled figure as fact.
