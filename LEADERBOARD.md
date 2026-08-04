# The live leaderboard — racing while you ride

A plain-English description of what got built for PLAN.md
**24.3.10–24.3.13**. `plan/phase-24-household.md` has the reasoning behind
each decision; this is what it *does*.

It replaces the single-rival ghost described in [RIVALS.md](RIVALS.md), which
still exists behind a build flag.

---

## The idea in one paragraph

Start a class somebody has ridden before — you, or anybody else on the bike —
and a leaderboard appears on the ride screen. It ranks everybody live, at this
point in the class, on the score Peloton uses: **total kilojoules**. You see
three rows: the person above you, you, and the person below you. As you pass
people and are passed, the three rows change.

Nobody picks anybody. You start the class and the race is there.

---

## Who is on it

Four kinds of row, and they are four different questions rather than four
formats:

- **Your best** — your best ride of this class, ever.
- **12 months** — your best of the last twelve months.
- **30 days** — your best of the last thirty days.
- **Anybody else on this bike** — their best, by name.

The two windows are the interesting part, and they are the owner's own idea:
*"Not only can it include your own PB as a 'ghost' to chase on the leaderboard,
but also it could be PB this month, PB this year, and all your friends scores
too. Just something to always be reaching for, you know?"* A rider who is
improving has an unreachable ghost all-time and a very reachable one in the
last thirty days.

**They are rolling windows, not calendar ones** — the last thirty days, not
"this month". A calendar month resets on the 1st, so a rider who rode on the
29th and the 30th would open a class on the 1st and find the ghost they were
chasing had vanished. Same reasoning as the *Last 30 days* card on the
dashboard, and the same window, so the two never disagree.

**One ride only ever appears once.** If your best ever ride was three weeks
ago, it answers all three of your own questions — and a board showing that one
ride three times, level with itself, is worse than useless. It appears once,
under the widest label it earns: *Your best*.

**Friends on other bikes are not on it yet.** That needs accounts and their
data in the cloud (Phase 18). When it lands it will be a fifth kind of row, and
it will simply be absent on a bike with no account rather than an error —
household social is a database query on this tablet and never touches the
network.

---

## What you see

Top right of the ride screen, in the column that used to hold the rest of the
class:

```
6TH OF 6

 4  12 MONTHS        +14
 5  30 DAYS          +10
 6  YOU            36 kJ
```

- **The header is the part the three rows hide.** Looking at three rows you
  cannot tell whether there are two more below you or twenty. When you are
  first it says `LEADING` instead, because that is the thing you were trying
  to do and *"1st of 6"* is a worse way of saying it.
- **Your row shows your total. Every other row shows the gap to you.** A
  number with a sign is a difference; a number without one is a total. It
  stays unambiguous because it agrees with the ranking: the row above you
  always has a `+`, the row below always has a `−`.
- **Three rows always**, even when you are first or last — the window slides
  rather than shrinking. A card that changed height as you were passed would
  be unreadable at 90 rpm.
- **Nothing is coloured for winning or losing.** Being behind a stronger
  housemate is not a mistake and the ride screen's red is the colour of an
  error. The board is in the output colour, because the score is kilojoules.

---

## The rules it follows

**It compares you at the same point in the class, not at the same wall-clock
time.** At twelve minutes into your ride, it looks up what everybody had done
twelve minutes into theirs.

**It's cumulative, not instantaneous.** Total work done so far, not this
second's watts against that second's watts — a second-by-second comparison
flickers too fast to read. The owner settled this directly: *"The 'score' for
any class should be total kilojoules for that class. E.g. a 20 min class maybe
your high score is 200."*

**Pausing doesn't cost you the race.** It reads the same clock the rest of the
ride does, which already excludes paused time. Stop for a phone call, come
back, and you are exactly where you were.

**A crash doesn't either.** Resume an interrupted ride and the board is rebuilt
from the class and the rider — both of which are already on the ride record, so
there is nothing to have lost.

**If somebody's ride ends before yours, their row says `FINISHED` and their
number stops.** It never draws a line forward into a ride that isn't there. On
a board this is more useful than it was as a single number: their total sits
there while yours climbs towards it.

**Both sides have to be real measured watts.** The bike's own sensor reports
power directly. The app can also *estimate* power from cadence and resistance,
but that estimate is badly wrong — about 137 W out on average — so a race
against an estimate would look exact and be fiction. So rides with estimated
power are never on the board, and if *your* current ride turns out to be
estimated the board disappears for that ride and does not come back.

That last rule is why the leaderboard shows nothing on the emulator, where
there is no bike. There is a debug switch to look at it anyway
(`com.pelonot.debug.RACE`) and it is careful about what it changes: it lets the
*live* board draw, and changes nothing that gets written down. The ride still
records honestly that its watts were estimated, and is still excluded from
every board afterwards.

---

## What it does *not* touch

**Nothing about the race is written into your ride record** — not who was on
the board, not where you finished. When a ride ends the app rebuilds the whole
ride record from scratch, so anything stored there that the rebuild does not
know about gets silently wiped. Rather than risk that, the race is not part of
the record.

**Nothing appears on the overlay** — the floating strip you get over a film.
That strip has about half a second of your attention and it belongs to the next
sixty seconds of pedalling.

**A class nobody has ridden draws nothing at all.** No empty board, no "be the
first" — an empty comparison is a message about the people who are not on it.

---

## What is settled and what is open

**Settled:** the score is kilojoules, the board is cumulative, three rows, and
the rival picker is gone.

**Open:** racing by **distance** instead of output. The data already works that
way — a race is one cumulative series against another, and distance is the same
arithmetic over cadence instead of power — but nothing lets a rider choose it
yet. That is PLAN.md 24.3.15, and it has one genuinely useful property waiting
in it: a distance race needs no measured power, so it works on rides the output
board has to exclude.
