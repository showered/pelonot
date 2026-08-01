"""Block vocabulary and structures for authoring a class.

A session is a flat list of `(seconds, zone, cadence)` blocks. Everything here
returns such a list, so a class is written by concatenation and the author
never touches a timestamp. `build.py` does the arithmetic and the checking.
"""

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

WU5 = [(120, 1, EASY), (120, 2, STEADY), (60, 3, BRISK)]
WU6 = [(180, 1, EASY), (120, 2, STEADY), (60, 3, BRISK)]
WU7 = [(180, 1, EASY), (180, 2, STEADY), (60, 3, BRISK)]
WU8 = [(180, 1, EASY), (180, 2, STEADY), (120, 3, BRISK)]
WU10 = [(240, 1, EASY), (240, 2, STEADY), (120, 3, BRISK)]

# --- Cooldowns (R3) --------------------------------------------------------

CD3 = [(60, 2, EASY), (120, 1, EASY)]
CD4 = [(120, 2, EASY), (120, 1, EASY)]
CD5 = [(120, 2, EASY), (180, 1, EASY)]
CD6 = [(180, 2, EASY), (180, 1, EASY)]
CD7 = [(180, 2, EASY), (240, 1, EASY)]
CD8 = [(240, 2, EASY), (240, 1, EASY)]
CD9 = [(240, 2, EASY), (300, 1, EASY)]
CD10 = [(240, 2, EASY), (360, 1, EASY)]


def primers(n, zone, cadence, on, off):
    """Short efforts at the work zone with full recovery, before the work.

    Without these the first hard interval of a VO2 or sprint session *is* the
    warmup, and the rider pays for it in the second one.
    """
    return [b for _ in range(n) for b in ((on, zone, cadence), (off, 1, EASY))]


def sets(n, on, zone, cadence, off, off_zone=1, off_cadence=EASY, trailing=False):
    """`n` efforts with recovery between them.

    The trailing recovery is omitted by default: a set ends on work and the
    cooldown is what follows it. Including it puts two easy blocks back to back,
    which reads on the ride screen as one long block of nothing.
    """
    out = []
    for i in range(n):
        out.append((on, zone, cadence))
        if trailing or i < n - 1:
            out.append((off, off_zone, off_cadence))
    return out


def alternate(n, a_sec, a_zone, a_cadence, b_sec, b_zone, b_cadence):
    """Over/under: two efforts either side of a boundary, no full recovery."""
    return [
        b for _ in range(n)
        for b in ((a_sec, a_zone, a_cadence), (b_sec, b_zone, b_cadence))
    ]


def ladder(rungs, zone, cadence, rests, rest_zone=1, rest_cadence=EASY):
    """Efforts of differing length with a named rest after each but the last."""
    assert len(rests) == len(rungs) - 1, "a ladder needs one rest between rungs"
    out = []
    for i, rung in enumerate(rungs):
        out.append((rung, zone, cadence))
        if i < len(rests):
            out.append((rests[i], rest_zone, rest_cadence))
    return out


def hold(seconds, zone, cadence):
    return [(seconds, zone, cadence)]


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
