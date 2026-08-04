# Riding against someone — how the rival feature works

> **Superseded, and switched off** (PLAN.md 24.3.11). What a rider gets now is
> the **live leaderboard** — [LEADERBOARD.md](LEADERBOARD.md) — which is this
> same race with the limit of one person taken off it. The owner's reasoning:
> *"It has scope for including unlimited number of people whereas rivals is (i
> think) just one person you race against. Let's not waste all the effort
> though, let's feature flag the Rivals feature and keep it hidden away."*
>
> So this is hidden rather than deleted, and everything below is still true of
> it. Build with `RIVAL_GHOST` set to `true` in `app/build.gradle.kts` and the
> picker and the one-number card come back. **Almost none of what is described
> here is behind that flag** — the elapsed-second alignment, the measured-power
> rule and the crash survival are all still live, because the leaderboard is
> built on top of them. What the flag hides is choosing one person before the
> ride, and reading the race as a single number.

A plain-English description of what got built for PLAN.md **24.3.3–24.3.9**,
written because the owner asked for one. `plan/phase-24-household.md` has the
reasoning behind each decision; this is what it *does*.

---

## The idea in one paragraph

Before you start a class, you can pick a finished ride of that same class to
race — a housemate's, or your own best. While you ride, one number sits on the
ride screen telling you how far ahead of or behind that ride you are **at this
point in the class**. If you're eighteen kilojoules up at minute twelve, it
says `+18 kJ`. If you're four down, `−4 kJ`.

That's it. It is not a leaderboard, not a position, not a percentage. One
number.

---

## Choosing who you're racing

It happens on the **class detail screen**, in a card called *Ride against*,
directly under the household board. You tap a chip to pick somebody, tap it
again to unpick. Nobody is selected by default.

You get two kinds of chip:

- **"Your best"** — your own best previous ride of this class.
- **A housemate's name** — their best ride of this class.

**Racing yourself is the important case.** On a household bike you're usually
the only person who has ridden a given class, so a feature that needed a
housemate would be one most riders never saw.

**If there is nobody to race, the card is not drawn at all.** No empty state,
no "nobody has ridden this yet" — an empty comparison is a message about the
people who aren't on it.

The choice is made *before* the ride and never during it. Opening a menu over
somebody who is already pedalling is the thing this deliberately avoids.

---

## What you see while riding

A small card in the bottom-left of the ride screen, just above OUTPUT /
DISTANCE / AVG POWER:

```
YOUR BEST
−1 kJ
```

Some deliberate choices in that:

- **It is in the same colour as OUTPUT**, not green-for-winning and
  red-for-losing. Being behind a stronger housemate is not a mistake, and
  colouring it as one would say it was. (This was got wrong first time and
  fixed — the original colour read as red on the tablet.)
- **It is not in the middle of the screen.** The centre column already has the
  zone ladder and four target gauges. The gap is something you glance at, so it
  sits with the other things you glance at.
- **It is never on the overlay** — the floating strip you get over a film. That
  strip has about half a second of your attention and it belongs to the next
  sixty seconds of pedalling.

---

## The rules it follows

**It compares you at the same point in the class, not at the same wall-clock
time.** At twelve minutes into your ride, it looks up what they had done twelve
minutes into theirs.

**It's cumulative, not instantaneous.** It compares total work done so far, not
this second's watts against that second's watts — a second-by-second comparison
flickers too fast to read.

**Pausing doesn't cost you the race.** It reads the same clock the rest of the
ride does, which already excludes paused time. Stop for a phone call, come
back, and you are exactly where you were. The same is true if the app crashes
and you resume: the rival choice is stored, so the race picks up too.

**If their ride ends before yours, it says so and stops.** The card changes to
`YOUR BEST · FINISHED` and the gap freezes at the final number. It never draws
a line forward into a ride that isn't there.

**Both sides have to be real measured watts.** The bike's own sensor reports
power directly. The app can also *estimate* power from cadence and resistance,
but that estimate is badly wrong (about 137 W out on average), so a race
against an estimate would look exact and be fiction. So:

- Rides with estimated power are never offered as rivals.
- If *your* current ride turns out to be estimated — which is what happens on
  the emulator, where there is no bike — the gap disappears and doesn't come
  back for that ride.

This is why the feature shows nothing at all on a simulated ride, and why it
only really exists on the actual bike.

---

## What it does *not* touch

**Nothing about the race is written into your ride record.** Your ride doesn't
remember who you were racing or whether you won. The choice lives in a small
side table (`active_ride_rival`) that exists purely so a crash mid-ride doesn't
lose it, and it is deleted the moment the ride ends.

There's a specific reason for that: when a ride finishes, the app rebuilds the
whole ride record from scratch, so anything stored on that record that the
rebuild doesn't know about gets silently wiped. Rather than risk that, the race
simply isn't part of the record.

If a future feature wants *"you beat Kilo's best"* saved onto the ride, that
has to be added in a particular place to survive — see 24.3.9.

---

## What's confirmed working, and what isn't

**Seen working on the real bike:** the picker showing a real 238 kJ ride, the
gap card appearing during a ride with real measured power, the choice surviving
in the database mid-ride, and being cleaned up afterwards.

**Seen working on the tablet emulator:** the picker with two riders, and the
gap correctly *refusing* to appear on a simulated ride.

**Not yet seen:** the number actually moving while somebody pedals. That needs
a rider on the bike and hasn't been done. Everything around it is confirmed;
the arithmetic behind it is unit-tested; but *"I watched it count up as I
rode"* is still owed.

---

## The bigger idea that's queued behind this — and has now been built

The owner's own suggestion, recorded as **24.3.10**: do what Peloton does and
show a proper live leaderboard — several rows, your PB and a friend's PB,
ranked as you ride.

**That is what shipped**, as 24.3.11–24.3.13, and it is described in
[LEADERBOARD.md](LEADERBOARD.md). One of the two tensions it opened turned out
not to be a tension at all: the owner settled the score as **the class total in
kilojoules**, so the board is cumulative and this page's *"cumulative, not
instantaneous"* rule stands unchanged. The other — one number against a list —
the leaderboard resolves by showing **three rows**: you, the one you are
chasing, and the one chasing you.
