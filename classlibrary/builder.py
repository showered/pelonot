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

# --- Which metric governs a block (PLAN 11.7.2 / README R12) ---------------
#
# The rider's own complaint: *"what do i do? do i focus on zone, cadence, or
# resistance?"* It is not three instructions, it is one outcome and two
# controls — power is what happens when you turn the pedals at some cadence
# against some resistance. So exactly one of the two is the instruction and the
# rest of the screen is context, and **the block says which**, because 25.4.2
# already settled the general form of this: a class states what it means rather
# than letting the reader infer it from a number band.

POWER_GOVERNS = "power"
CADENCE_GOVERNS = "cadence"


class Cadence(tuple):
    """A cadence band that also knows whether it is *the* instruction.

    A tuple subclass rather than a new type, so every `(lo, hi)` unpacking in
    `build.py` and every comparison in R9's signature check keeps working
    untouched.

    Governance rides on the intent rather than on the block because that is
    where the meaning already lives: an author who writes `GRIND` has said
    "this block is about turning a big gear slowly", and an author who writes
    `STEADY` has said "ride at a normal cadence and let the watts be the
    point". The number band is a consequence of the intent, not the source of
    it — which is why this is not 11.7.2's rejected route (a).
    """

    def __new__(cls, lo, hi, governs=POWER_GOVERNS):
        self = super().__new__(cls, (lo, hi))
        self.governs = governs
        return self


def POWER(cadence):
    """Override: this block wants the watts, whatever its cadence intent.

    For a threshold effort that happens to sit at a climbing cadence — the
    cadence is the terrain, the zone is the work.
    """
    return Cadence(cadence[0], cadence[1], POWER_GOVERNS)


def CADENCE(cadence):
    """Override: this block wants the legs, whatever its cadence intent.

    The owner's *"perhaps there's a way we can use both"* case, from the other
    side: a sweet-spot block that genuinely wants 60 rpm as well as the zone.
    """
    return Cadence(cadence[0], cadence[1], CADENCE_GOVERNS)


# --- Cadence intents (PLAN 23.2.6 / README R5) -----------------------------
#
# Cadence is a second axis, not a function of the zone. The same zone ridden
# at GRIND and at SPIN is two different workouts, and the library has to be
# able to say so.
#
# The tails govern and the middle does not. A 50-60 rpm grind and a 110-125 rpm
# sprint are exercises *about* the pedalling; 75-85 and 80-90 are the library's
# comfortable seated defaults and prescribing them is how a rider spinning a
# perfectly good 92 rpm during a threshold block came to be shown amber
# (11.7.1a).

GRIND = Cadence(50, 60, CADENCE_GOVERNS)    # seated heavy torque; strength work, not aerobic work
CLIMB = Cadence(60, 70, CADENCE_GOVERNS)    # a climb you stay seated for
STAND = Cadence(70, 80)     # out of the saddle, or the transition into it
EASY = Cadence(75, 85)      # recovery spin, low enough to be genuinely easy
STEADY = Cadence(80, 90)    # the default riding cadence
BRISK = Cadence(85, 95)     # endurance with intent
FAST = Cadence(95, 105)     # aerobic work on the fast side
SPIN = Cadence(105, 115, CADENCE_GOVERNS)   # leg speed
SURGE = Cadence(110, 125, CADENCE_GOVERNS)  # sprints

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
    def __init__(self, id, title, category, blocks, description=""):
        self.id = id
        self.title = title
        self.category = category
        self.blocks = blocks
        self.description = description

    @property
    def duration_sec(self):
        return sum(b[0] for b in self.blocks)


CATALOGUE = []


def klass(id, title, category, *parts, description=""):
    """Register one class. `parts` are block lists, concatenated in order.

    `description` is the one thing here a rule cannot check for truth (PLAN
    23.2.7): every other fact about a class is derived from its blocks, so the
    library can say a ride is "20 min · Climbs · four hard efforts" and cannot
    say why anybody would ride it. Keyword-only, so it never gets mistaken for
    a block list in the middle of a `*parts` run.
    """
    blocks = [b for part in parts for b in part]
    CATALOGUE.append(Session(id, title, category, blocks, description))
