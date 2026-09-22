# The live leaderboard — racing while you ride

A plain-English description of what got built for PLAN.md
**24.3.10–24.3.19**. `plan/phase-24-household.md` has the reasoning behind
each decision; this is what it *does*.

It replaces the single-rival ghost described in [RIVALS.md](RIVALS.md), which
still exists behind a build flag.

---

## The idea in one paragraph

Start a class somebody has ridden before — you, or anybody else on the bike —
and a leaderboard appears on the ride screen. It ranks everybody live, at this
point in the class, on the score Peloton uses: **total kilojoules**. You see a
window onto it rather than all of it — up to six rows around your own, with the
rest a scroll away. As you pass people and are passed, the rows change.

Nobody picks anybody. You start the class and the race is there.

---

## Who is on it

Kinds of row, and they are different questions rather than different formats:

- **Your best** — your best ride of this class, ever.
- **Year best** — your best of the last twelve months.
- **Recent best** — your best of the last thirty days.
- **30m best / 30m year best** — your best at this duration across classes
  and matching-length free rides, ever or within the last twelve months. The
  number follows the current class length.
- **Anybody else on this bike** — their best, by name, and their last ride
  beside it, because a best is a monument and a last ride is news.
- **Targets nobody rode** — *Class target* (the class ridden at the middle of
  every band it asks for), *Best +5%*, *Your usual*,
  *30m average* (your average at this duration over the last year, once
  there are at least three rides), and a round number that rises as you do.
  These carry a `○` and are never mistaken for people. See *A row that is a person* below for the other half of that.

**Best +5% follows the recorded pace.** At each elapsed second it is five per
cent above the highest total among your personal-best rows on this board,
including the class, duration and recent windows. It can switch which best it
follows, so it is explicitly a generated target, not a ride anybody completed.
The original version spread one class's finishing total evenly across the ride;
a fast start could put *Your best* ahead of *Just past your best*. That is fixed.
The real rides keep their original traces, and all rows still rank by their
actual cumulative values.

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
○ 150                     22
(face) ALEX               20
       FTP 190 W
(face) YOU                20
       FTP 155 W
○ CLASS TARGET            11
```

- **No positions, no units, no header.** The owner cut all three at once, and
  the argument for the biggest cut is about what this board actually is: most
  of the rows are your own past rides, so calling you *4th of 6* describes a
  field that is mostly one person.
- **Every row is its own total**, including yours. The first version gave the
  other rows the gap to you — `+12` — which is one subtraction away from the
  two totals it came from, and only means anything to somebody holding their
  own number in their head at 90 rpm. Four totals in a column are compared by
  eye.
- **The window slides rather than shrinking**, even when you are first or last.
  A card that changed height as you were passed would be unreadable at 90 rpm.
  What that costs is the one thing the header used to say: you cannot tell
  whether there are two more below you or twenty. That is accepted rather than
  solved — only the rows next to you were ever something you could act on.
- **The card bounces when you move.** Passing somebody is two rows quietly
  swapping otherwise, now that no number announces it.
- **Nothing is coloured for winning or losing.** Being behind a stronger
  housemate is not a mistake and the ride screen's red is the colour of an
  error. The board is in the output colour, because the score is kilojoules.

---

## A row that is a person

The owner sent a picture of Peloton's own board — *"something like that for
leaderboard please!"* — and this is what came of it. **A row that is a person**
carries their face inside a ring, the ring is how far they are through their
current level, the level itself sits on the bottom of it, and their FTP is a
quiet caption under their name. Their total is still the large number on the
right.

**A row this app invented gets none of that**, and neither do your own past
rides. Give a made-up target a face and the board is claiming somebody who does
not exist; put your own face on four rows and it is decoration. So the board
has two visibly different kinds of row, on purpose, and that is honest rather
than untidy.

**Showing a housemate's FTP is allowed here and nowhere else social.** On the
household panel it would publish a measurement of somebody who was never asked;
a leaderboard is the one surface every row on it has opted into being ranked
on, and the household opt-out is exactly that opt-in — turn it off and your
face, your level and your FTP all leave together.

**A person's row is taller, so fewer fit at once.** The card is the same height
it always was and the rest scrolls, which it already did past the sixth row.

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

**Real bike rides compare measured watts with measured watts.** Generated
class targets and milestones remain available without measured history. The
emulator's explicit debug race mode instead uses a separate modelled-history
lane. This changes the live comparison only; recorded provenance, FTP evidence
and the measured-only static leaderboards remain unchanged.

---

## What it does *not* touch

**Nothing about the race is written into your ride record** — not who was on
the board, not where you finished. When a ride ends the app rebuilds the whole
ride record from scratch, so anything stored there that the rebuild does not
know about gets silently wiped. Rather than risk that, the race is not part of
the record.

**The expanded overlay shows the nearest competitor**, using the same names
and standings as the ride screen. The collapsed overlay keeps its compact form.

**A class nobody has ridden still gets generated targets**, including the
class target and a milestone, so there can be something to chase without
inventing another person.

---

## What is settled and what is open

**Settled:** the score is kilojoules, the board is cumulative, six rows and a
scroll, no positions and no units, a face and a level and an FTP on the rows
that are people and on nothing else, and the rival picker is gone.

**Open, and it is the owner's own:** whether the **rank** comes back. Their
reference picture has `41` and `42` down its left edge and it was deleted nine
days earlier on their own argument. Peloton's board is thousands of strangers;
this one is a household plus your own history, so *4th of 6* where four of the
six are you is a category error rather than an overstatement. Written down
rather than acted on in either direction (PLAN.md 24.3.19d).

**Labels shortened on 22 September:** *Best +5%*, *Year best*, *Recent best*,
*30m best*, *30m year best*, and *30m average*. *Your best*, *Your usual* and
*Class target* retain their short, descriptive labels.

**Open:** racing by **distance** instead of output. The data already works that
way — a race is one cumulative series against another, and distance is the same
arithmetic over cadence instead of power — but nothing lets a rider choose it
yet. That is PLAN.md 24.3.15, and it has one genuinely useful property waiting
in it: a distance race needs no measured power, so it works on rides the output
board has to exclude.
