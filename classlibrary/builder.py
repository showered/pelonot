"""Block vocabulary and structures for authoring a class.

A session is a flat list of `(seconds, zone, cadence, position)` blocks.
Everything here returns such a list, so a class is written by concatenation and
the author never touches a timestamp. `build.py` does the arithmetic and the
checking.

`position` is `STANDING`, `SEATED` or `None`, and **`None` is the default and means
the rider chooses** (PLAN 25.1). Most blocks should leave it alone: a class
that prescribes a position for every one of them is nagging, not coaching.
"""

# --- Position (PLAN 25.1) --------------------------------------------------
#
# Named in full so they cannot be confused with the STAND *cadence* intent
# below, which is a different axis: STAND is 70-80 rpm, STANDING is whether the
# rider is on the saddle at all.

STANDING = "standing"
SEATED = "seated"

# --- Cadence intents (PLAN 23.2.6 / README R5) -----------------------------
#
# Cadence is a second axis, not a function of the zone. The same zone ridden
# at GRIND and at SPIN is two different workouts, and the library has to be
# able to say so.

GRIND = (50, 60)    # seated heavy torque; strength work, not aerobic work
CLIMB = (60, 70)    # a climb you stay seated for
STAND = (70, 80)    # out of the saddle, or the transition into it
EASY = (75, 85)     # recovery spin, low enough to be genuinely easy
STEADY = (80, 90)   # the default riding cadence
BRISK = (85, 95)    # endurance with intent
FAST = (95, 105)    # aerobic work on the fast side
SPIN = (105, 115)   # leg speed
SURGE = (110, 125)  # sprints

# --- Warmups (R2) ----------------------------------------------------------
#
# Progressive, never one flat block. Named by length in minutes.

WU5 = [(120, 1, EASY, None), (120, 2, STEADY, None), (60, 3, BRISK, None)]
WU6 = [(180, 1, EASY, None), (120, 2, STEADY, None), (60, 3, BRISK, None)]
WU7 = [(180, 1, EASY, None), (180, 2, STEADY, None), (60, 3, BRISK, None)]
WU8 = [(180, 1, EASY, None), (180, 2, STEADY, None), (120, 3, BRISK, None)]
WU10 = [(240, 1, EASY, None), (240, 2, STEADY, None), (120, 3, BRISK, None)]

# --- Cooldowns (R3) --------------------------------------------------------

CD3 = [(60, 2, EASY, None), (120, 1, EASY, None)]
CD4 = [(120, 2, EASY, None), (120, 1, EASY, None)]
CD5 = [(120, 2, EASY, None), (180, 1, EASY, None)]
CD6 = [(180, 2, EASY, None), (180, 1, EASY, None)]
CD7 = [(180, 2, EASY, None), (240, 1, EASY, None)]
CD8 = [(240, 2, EASY, None), (240, 1, EASY, None)]
CD9 = [(240, 2, EASY, None), (300, 1, EASY, None)]
CD10 = [(240, 2, EASY, None), (360, 1, EASY, None)]


def primers(n, zone, cadence, on, off, position=None):
    """Short efforts at the work zone with full recovery, before the work.

    Without these the first hard interval of a VO2 or sprint session *is* the
    warmup, and the rider pays for it in the second one.
    """
    return [
        b for _ in range(n)
        for b in ((on, zone, cadence, position), (off, 1, EASY, None))
    ]


def sets(n, on, zone, cadence, off, off_zone=1, off_cadence=EASY, trailing=False,
         position=None, off_position=None):
    """`n` efforts with recovery between them.

    The trailing recovery is omitted by default: a set ends on work and the
    cooldown is what follows it. Including it puts two easy blocks back to back,
    which reads on the ride screen as one long block of nothing.
    """
    out = []
    for i in range(n):
        out.append((on, zone, cadence, position))
        if trailing or i < n - 1:
            out.append((off, off_zone, off_cadence, off_position))
    return out


def alternate(n, a_sec, a_zone, a_cadence, b_sec, b_zone, b_cadence,
              a_position=None, b_position=None):
    """Over/under: two efforts either side of a boundary, no full recovery."""
    return [
        b for _ in range(n)
        for b in (
            (a_sec, a_zone, a_cadence, a_position),
            (b_sec, b_zone, b_cadence, b_position),
        )
    ]


def ladder(rungs, zone, cadence, rests, rest_zone=1, rest_cadence=EASY,
           position=None):
    """Efforts of differing length with a named rest after each but the last."""
    assert len(rests) == len(rungs) - 1, "a ladder needs one rest between rungs"
    out = []
    for i, rung in enumerate(rungs):
        out.append((rung, zone, cadence, position))
        if i < len(rests):
            out.append((rests[i], rest_zone, rest_cadence, None))
    return out


def hold(seconds, zone, cadence, position=None):
    return [(seconds, zone, cadence, position)]


class Session:
    def __init__(self, id, title, category, blocks):
        self.id = id
        self.title = title
        self.category = category
        self.blocks = blocks

    @property
    def duration_sec(self):
        return sum(b[0] for b in self.blocks)


CATALOGUE = []


def klass(id, title, category, *parts):
    """Register one class. `parts` are block lists, concatenated in order."""
    blocks = [b for part in parts for b in part]
    CATALOGUE.append(Session(id, title, category, blocks))
