"""The 72 sessions.

One `klass(...)` per class, written in blocks of real time. Read
`README.md` first — the rules the sessions are designed to are there, and
`build.py` enforces most of them, so a session that breaks one will refuse to
write rather than reaching a rider.

Ids are a new series (END, REC, SWT, THR, VMX, CLB, SPR). The old series
(PZE, HC, RC, SS, TH, TB, VO2) is *not* reused, because `workouts.class_id`
points at it and a ride already recorded on the bike must keep meaning what it
meant. See README, "Three constraints that are not negotiable".
"""

from builder import (
    BRISK, CLIMB, EASY, FAST, GRIND, SPIN, STAND, STEADY, SURGE,
    WU5, WU6, WU7, WU8, WU10,
    CD3, CD4, CD5, CD6, CD7, CD8, CD9, CD10,
    SEATED, STANDING,
    alternate, hold, klass, ladder, primers, sets,
)

ENDURANCE = ("Endurance", "endurance")
RECOVERY = ("Recovery", "recovery")
SWEET_SPOT = ("Sweet Spot", "sweet_spot")
THRESHOLD = ("Threshold", "threshold")
VO2 = ("VO2 Max", "vo2_max")
CLIMBS = ("Climbs", "climbs")
SPRINTS = ("Sprints", "sprints")


# ---------------------------------------------------------------------------
# Endurance — Z2 with Z3 accents. The category most riders spend most of their
# time in, so it carries most of the cadence variety.
# ---------------------------------------------------------------------------

klass("END-01", "Zone 2 Steady 20", ENDURANCE,
      WU5, hold(600, 2, BRISK), CD5)

klass("END-02", "Cadence Ladder 20", ENDURANCE,
      WU5,
      hold(120, 2, STEADY), hold(120, 2, BRISK),
      hold(120, 2, FAST), hold(120, 2, SPIN),
      hold(120, 2, STEADY),
      CD5)

klass("END-03", "Endurance Build 30", ENDURANCE,
      WU8,
      hold(360, 2, STEADY), hold(360, 2, BRISK), hold(360, 3, BRISK),
      CD4)

klass("END-04", "Rolling Roads 30", ENDURANCE,
      WU8,
      sets(6, on=120, zone=3, cadence=CLIMB, off=60, off_zone=2, off_cadence=BRISK,
           trailing=True, position=SEATED),
      CD4)

klass("END-05", "The Long Steady 30", ENDURANCE,
      WU5, hold(1260, 2, STEADY), CD4)

klass("END-06", "Tempo Touches 30", ENDURANCE,
      WU8,
      alternate(4, 180, 2, BRISK, 60, 3, FAST),
      hold(120, 2, STEADY),
      CD4)

klass("END-07", "Base Ride 45", ENDURANCE,
      WU8,
      hold(480, 2, STEADY), hold(480, 2, BRISK),
      hold(480, 2, FAST), hold(480, 2, STEADY),
      CD5)

klass("END-08", "Tempo Climbs 5×5 45", ENDURANCE,
      WU8,
      sets(5, on=300, zone=3, cadence=CLIMB, off=60, off_zone=2, off_cadence=EASY,
           trailing=True),
      hold(120, 2, STEADY),
      CD5)

klass("END-09", "Progression 45", ENDURANCE,
      WU8,
      hold(600, 2, STEADY), hold(600, 2, BRISK),
      hold(480, 3, BRISK), hold(240, 3, FAST),
      CD5)

klass("END-10", "Long Base 60", ENDURANCE,
      WU8,
      hold(900, 2, STEADY), hold(900, 2, BRISK), hold(900, 2, STEADY),
      CD7)

klass("END-11", "Endurance with Surges 60", ENDURANCE,
      WU8,
      alternate(9, 240, 2, BRISK, 60, 3, FAST),
      CD7)

klass("END-12", "Climb and Spin 60", ENDURANCE,
      WU8,
      alternate(6, 360, 2, CLIMB, 60, 2, SPIN),
      hold(180, 2, STEADY),
      CD7)


# ---------------------------------------------------------------------------
# Recovery — nothing above Z2, and Z2 is a minority of the ride. A recovery
# class that spends half its time at Z2 is an endurance class with a soothing
# name, which is how a rider ends up never actually recovering.
# ---------------------------------------------------------------------------

klass("REC-01", "Easy Spin 15", RECOVERY,
      hold(180, 1, EASY), hold(120, 1, STEADY),
      hold(240, 2, STEADY),
      hold(120, 1, STEADY), hold(240, 1, EASY))

klass("REC-02", "Loosen the Legs 15", RECOVERY,
      hold(240, 1, EASY),
      sets(2, on=60, zone=1, cadence=SPIN, off=60, trailing=True),
      hold(180, 2, STEADY),
      hold(240, 1, EASY))

klass("REC-03", "Recovery Ride 20", RECOVERY,
      hold(300, 1, EASY), hold(300, 2, STEADY),
      hold(300, 1, STEADY), hold(300, 1, EASY))

klass("REC-04", "Spin Out 20", RECOVERY,
      hold(240, 1, EASY),
      sets(4, on=60, zone=1, cadence=SPIN, off=120, trailing=True),
      hold(240, 1, EASY))

klass("REC-05", "Flush 20", RECOVERY,
      hold(180, 1, EASY), hold(240, 2, BRISK),
      hold(180, 1, EASY), hold(180, 2, STEADY),
      hold(420, 1, EASY))

klass("REC-06", "Recovery Flow 30", RECOVERY,
      hold(300, 1, EASY), hold(360, 2, STEADY),
      hold(300, 1, STEADY), hold(360, 2, BRISK),
      hold(480, 1, EASY))

klass("REC-07", "Easy Miles 30", RECOVERY,
      hold(300, 1, EASY), hold(600, 1, STEADY),
      hold(300, 2, BRISK), hold(300, 1, STEADY),
      hold(300, 1, EASY))

klass("REC-08", "Cadence Play 30", RECOVERY,
      hold(300, 1, EASY),
      sets(6, on=60, zone=1, cadence=SPIN, off=120, off_cadence=STEADY, trailing=True),
      hold(420, 1, EASY))

klass("REC-09", "Long Recovery 45", RECOVERY,
      hold(300, 1, EASY), hold(900, 1, STEADY),
      hold(600, 2, STEADY), hold(600, 1, STEADY),
      hold(300, 1, EASY))

klass("REC-10", "Active Recovery 45", RECOVERY,
      hold(300, 1, EASY),
      sets(4, on=300, zone=1, cadence=STEADY, off=180, off_zone=2, off_cadence=BRISK,
           trailing=True),
      hold(480, 1, EASY))


# ---------------------------------------------------------------------------
# Sweet Spot — long blocks at the Z3/Z4 boundary with short recoveries. What
# separates it from Threshold below is the work-to-rest ratio and the cadence:
# sweet spot is ridden at a cadence you could hold all day, threshold is not.
# ---------------------------------------------------------------------------

klass("SWT-01", "Sweet Spot 5 + 4 20", SWEET_SPOT,
      WU5,
      hold(300, 4, BRISK), hold(180, 1, EASY), hold(240, 4, BRISK),
      CD3)

klass("SWT-02", "Sweet Spot Over/Under 20", SWEET_SPOT,
      WU5,
      alternate(4, 120, 4, BRISK, 60, 3, BRISK),
      CD3)

klass("SWT-03", "Sweet Spot 3×5 30", SWEET_SPOT,
      WU5,
      sets(3, on=300, zone=4, cadence=STEADY, off=180),
      CD4)

klass("SWT-04", "Sweet Spot Build 30", SWEET_SPOT,
      WU5,
      hold(240, 3, BRISK), hold(120, 1, EASY),
      hold(300, 4, BRISK), hold(180, 1, EASY),
      hold(360, 4, BRISK),
      CD5)

# SWT-05 is gone from here on purpose: it was 4×4 at Z4/CLIMB, which is
# `THR-06` block for block, and it is replaced by `SWT-13` below rather than
# edited in place. See that class, and PLAN 25.4.3.

klass("SWT-06", "Sweet Spot Descending 30", SWEET_SPOT,
      WU5,
      ladder([360, 300, 240], zone=4, cadence=FAST, rests=[180, 180]),
      CD4)

klass("SWT-07", "Sweet Spot 3×8 45", SWEET_SPOT,
      WU8,
      sets(3, on=480, zone=4, cadence=STEADY, off=300),
      CD3)

klass("SWT-08", "Over/Under, Two Sets 45", SWEET_SPOT,
      WU5,
      alternate(4, 120, 4, BRISK, 120, 3, BRISK),
      hold(180, 1, EASY),
      alternate(4, 120, 4, BRISK, 120, 3, BRISK),
      CD5)

klass("SWT-09", "Big Gear / Fast Legs 45", SWEET_SPOT,
      WU8,
      hold(300, 4, CLIMB, SEATED), hold(180, 1, EASY),
      hold(300, 4, FAST), hold(180, 1, EASY),
      hold(300, 4, CLIMB, SEATED), hold(180, 1, EASY),
      hold(300, 4, FAST),
      CD8)

klass("SWT-10", "Sweet Spot Ladder 45", SWEET_SPOT,
      WU8,
      ladder([240, 360, 480, 360], zone=4, cadence=BRISK, rests=[180, 180, 240]),
      CD3)

klass("SWT-11", "Sweet Spot 3×12 60", SWEET_SPOT,
      WU8,
      sets(3, on=720, zone=4, cadence=STEADY, off=360),
      CD4)

klass("SWT-12", "Sweet Spot Endurance 60", SWEET_SPOT,
      WU8,
      hold(600, 4, BRISK), hold(300, 2, STEADY),
      hold(600, 4, BRISK), hold(300, 2, STEADY),
      hold(900, 4, CLIMB),
      CD7)

# What `SWT-05` should have been. It was 4×4 at Z4 over the gear, which is
# `THR-06` block for block — the two differed only in the recovery, which is
# defensible on paper and is the same complaint 23.2.6 made about the old
# library at a smaller scale (PLAN 25.4.3). Rungs that grow are what make this
# the sweet-spot half of the pair, and the title says so without the rider
# opening either class. The spin-out recovery stays: it was never the wrong
# idea, only never the whole of the difference.
#
# **A new id rather than an edit**, and that is the constraint rather than a
# preference: `workouts.class_id` is a foreign key, and rewriting what `SWT-05`
# *is* while a ride still points at it changes what that ride was. `SWT-05`
# leaves the bundle and `ClassTemplateSeeder` retires it if anyone rode it
# (23.2.6c), so the ride keeps the class it actually rode.
klass("SWT-13", "Low Cadence Sweet Spot 4-5-6 30", SWEET_SPOT,
      WU5,
      ladder([240, 300, 360], zone=4, cadence=CLIMB, rests=[180, 240],
             rest_zone=2, rest_cadence=SPIN),
      CD3)


# ---------------------------------------------------------------------------
# Threshold — sustained Z4 with recovery that is actually recovery, and the
# occasional trip above it. Shorter blocks, longer rests, faster cadence than
# Sweet Spot.
# ---------------------------------------------------------------------------

klass("THR-01", "Threshold 2×4 20", THRESHOLD,
      WU5,
      sets(2, on=240, zone=4, cadence=FAST, off=240),
      CD3)

klass("THR-02", "Threshold to VO2 20", THRESHOLD,
      WU5, primers(2, 5, FAST, on=30, off=60),
      hold(240, 4, FAST), hold(180, 1, EASY), hold(120, 5, FAST),
      CD3)

klass("THR-03", "Threshold 2×8 30", THRESHOLD,
      WU6,
      sets(2, on=480, zone=4, cadence=FAST, off=300),
      CD3)

klass("THR-04", "Threshold Over/Under 30", THRESHOLD,
      WU5, primers(2, 5, FAST, on=30, off=60),
      alternate(3, 120, 5, FAST, 180, 4, FAST),
      CD7)

klass("THR-05", "Threshold Descending 30", THRESHOLD,
      WU6,
      ladder([360, 300, 180], zone=4, cadence=FAST, rests=[180, 180]),
      CD4)

klass("THR-06", "Low Cadence Threshold 4×4 30", THRESHOLD,
      WU5,
      sets(4, on=240, zone=4, cadence=CLIMB, off=120),
      CD3)

klass("THR-07", "FTP Prep 2×12 45", THRESHOLD,
      WU8,
      sets(2, on=720, zone=4, cadence=BRISK, off=480),
      CD5)

klass("THR-08", "Threshold 4×6 45", THRESHOLD,
      WU8,
      sets(4, on=360, zone=4, cadence=FAST, off=180),
      CD4)

klass("THR-09", "Threshold with a VO2 Top 45", THRESHOLD,
      WU5, primers(2, 5, FAST, on=30, off=60),
      hold(180, 4, FAST), hold(120, 1, EASY),
      hold(360, 4, FAST), hold(180, 1, EASY),
      hold(180, 5, FAST), hold(180, 1, EASY),
      hold(360, 4, FAST), hold(180, 1, EASY),
      hold(180, 4, FAST),
      CD5)

klass("THR-10", "Threshold 5×4, Shrinking Rest 45", THRESHOLD,
      WU8,
      hold(240, 4, BRISK), hold(240, 1, EASY),
      hold(240, 4, BRISK), hold(180, 1, EASY),
      hold(240, 4, BRISK), hold(180, 1, EASY),
      hold(240, 4, BRISK), hold(120, 1, EASY),
      hold(240, 4, BRISK),
      CD5)

klass("THR-11", "Threshold 4×9 60", THRESHOLD,
      WU5,
      sets(4, on=540, zone=4, cadence=FAST, off=300),
      CD4)

klass("THR-12", "Threshold Mixed 60", THRESHOLD,
      WU5, primers(2, 5, FAST, on=30, off=60),
      hold(600, 4, BRISK), hold(300, 1, EASY),
      hold(180, 5, FAST), hold(180, 1, EASY),
      hold(180, 5, FAST), hold(180, 1, EASY),
      hold(600, 4, CLIMB), hold(300, 1, EASY),
      hold(300, 4, FAST),
      CD5)


# ---------------------------------------------------------------------------
# VO2 Max — Z5, and therefore primed warmups throughout: the first effort of a
# VO2 session should not be the one that gets the rider ready for the second.
# ---------------------------------------------------------------------------

klass("VMX-01", "VO2 2×3 20", VO2,
      WU5, primers(2, 5, FAST, on=30, off=60),
      sets(2, on=180, zone=5, cadence=FAST, off=180),
      CD3)

klass("VMX-02", "VO2 30/30 20", VO2,
      WU5, primers(2, 5, SPIN, on=30, off=60),
      sets(4, on=30, zone=5, cadence=SPIN, off=30),
      hold(120, 1, EASY),
      sets(4, on=30, zone=5, cadence=SPIN, off=30),
      CD3)

klass("VMX-03", "VO2 Build 1-2-3 20", VO2,
      WU5, primers(2, 5, FAST, on=30, off=60),
      ladder([60, 120, 180], zone=5, cadence=FAST, rests=[60, 120]),
      CD3)

klass("VMX-04", "VO2 3×3 30", VO2,
      WU5, primers(2, 5, FAST, on=30, off=60),
      sets(3, on=180, zone=5, cadence=FAST, off=240),
      CD5)

klass("VMX-05", "VO2 3×4 30", VO2,
      WU5, primers(2, 5, FAST, on=30, off=30),
      sets(3, on=240, zone=5, cadence=FAST, off=240),
      CD3)

klass("VMX-06", "VO2 45/45 30", VO2,
      WU5, primers(2, 5, SPIN, on=30, off=45),
      sets(6, on=45, zone=5, cadence=SPIN, off=45),
      hold(120, 1, EASY),
      sets(6, on=45, zone=5, cadence=SPIN, off=45),
      CD4)

klass("VMX-07", "VO2 Pyramid 30", VO2,
      WU5, primers(2, 5, FAST, on=30, off=60),
      ladder([60, 120, 180, 120, 60], zone=5, cadence=FAST,
             rests=[60, 120, 180, 120]),
      CD5)

klass("VMX-08", "VO2 4×4 45", VO2,
      WU5, primers(2, 5, FAST, on=30, off=60),
      sets(4, on=240, zone=5, cadence=FAST, off=240),
      CD9)

klass("VMX-09", "VO2 Climbing 45", VO2,
      WU5, primers(2, 5, CLIMB, on=30, off=60),
      sets(6, on=180, zone=5, cadence=CLIMB, off=180, position=SEATED),
      CD4)

klass("VMX-10", "VO2 Mixed 45", VO2,
      WU5, primers(2, 5, FAST, on=30, off=60),
      hold(240, 5, FAST), hold(240, 1, EASY),
      hold(180, 5, SPIN), hold(180, 1, EASY),
      hold(120, 5, CLIMB), hold(120, 1, EASY),
      hold(240, 5, FAST), hold(240, 1, EASY),
      hold(180, 5, SPIN),
      CD8)


# ---------------------------------------------------------------------------
# Climbs — heavy torque at GRIND and CLIMB cadence. This is the category where
# the cadence *is* the workout, and where the old library had one lookup table.
# ---------------------------------------------------------------------------

klass("CLB-01", "Torque Repeats 4×2 20", CLIMBS,
      WU5,
      sets(4, on=120, zone=4, cadence=GRIND, off=60, off_zone=2, off_cadence=STEADY,
           trailing=True, position=SEATED),
      CD3)

klass("CLB-02", "Standing Attacks 20", CLIMBS,
      WU5,
      hold(240, 4, CLIMB, SEATED), hold(120, 1, EASY),
      sets(4, on=45, zone=6, cadence=STAND, off=45, trailing=True, position=STANDING),
      CD3)

klass("CLB-03", "The Long Climb 30", CLIMBS,
      WU8,
      hold(180, 3, CLIMB), hold(900, 4, CLIMB),
      CD4)

klass("CLB-04", "Rolling Climbs 30", CLIMBS,
      WU8,
      sets(6, on=120, zone=4, cadence=CLIMB, off=60, off_zone=2, off_cadence=BRISK,
           trailing=True),
      CD4)

klass("CLB-05", "Torque Ladder 30", CLIMBS,
      WU8,
      # Seated, like every other GRIND block in the library (25.4.1). At 50-60
      # rpm the position is not a flourish: standing at that cadence is a
      # different exercise, and torque work done out of the saddle is not
      # torque work. The rests are two and three minutes, so the call at the
      # top of each rung is an instruction rather than a nag.
      ladder([120, 180, 240, 120], zone=4, cadence=GRIND, rests=[120, 180, 120],
             position=SEATED),
      CD4)

klass("CLB-06", "Climb and Attack 30", CLIMBS,
      WU5, primers(2, 6, SURGE, on=30, off=60),
      hold(240, 4, CLIMB, SEATED), hold(30, 6, STAND, STANDING), hold(90, 1, EASY),
      hold(240, 4, CLIMB, SEATED), hold(30, 6, STAND, STANDING), hold(90, 1, EASY),
      hold(240, 4, CLIMB, SEATED), hold(30, 6, STAND, STANDING), hold(90, 1, EASY),
      CD4)

klass("CLB-07", "Climb 4×5 45", CLIMBS,
      WU8,
      sets(4, on=300, zone=4, cadence=CLIMB, off=180),
      CD8)

klass("CLB-08", "Grinding Sets 45", CLIMBS,
      WU8,
      sets(6, on=180, zone=4, cadence=GRIND, off=120, off_zone=2, off_cadence=BRISK,
           trailing=True, position=SEATED),
      CD7)

klass("CLB-09", "Mountain Stage 45", CLIMBS,
      WU5, primers(2, 5, CLIMB, on=30, off=60),
      hold(420, 4, CLIMB), hold(300, 2, BRISK),
      hold(240, 5, CLIMB), hold(240, 1, EASY),
      hold(480, 4, GRIND, SEATED), hold(240, 2, BRISK),
      hold(120, 5, STAND, STANDING),
      CD3)

klass("CLB-10", "Big Mountain 60", CLIMBS,
      WU5, primers(2, 5, CLIMB, on=30, off=60),
      hold(720, 4, CLIMB), hold(360, 2, BRISK),
      hold(600, 4, GRIND, SEATED), hold(300, 1, EASY),
      hold(480, 4, CLIMB), hold(300, 2, BRISK),
      hold(120, 5, STAND, STANDING),
      CD4)


# ---------------------------------------------------------------------------
# Sprints — Tabata and its relatives. Eight rounds is a set; the sixteen
# consecutive rounds the old TB-01 prescribed are a rider soft-pedalling while
# the class claims Z6 (README, "What was wrong with the first 72").
# ---------------------------------------------------------------------------

klass("SPR-01", "Tabata 2×8 20", SPRINTS,
      WU5, primers(1, 6, SURGE, on=20, off=60),
      sets(8, on=20, zone=6, cadence=SURGE, off=10),
      hold(180, 1, EASY),
      sets(8, on=20, zone=6, cadence=SURGE, off=10),
      CD3)

klass("SPR-02", "Sprint 30/30 20", SPRINTS,
      WU5, primers(1, 6, SURGE, on=20, off=40),
      sets(5, on=30, zone=6, cadence=SURGE, off=30),
      hold(120, 1, EASY),
      sets(5, on=30, zone=6, cadence=SURGE, off=30),
      CD3)

klass("SPR-03", "Sprint Ladder 20", SPRINTS,
      WU5, primers(2, 6, SURGE, on=20, off=40),
      sets(4, on=15, zone=7, cadence=SURGE, off=45, trailing=True),
      sets(4, on=30, zone=6, cadence=SURGE, off=30, trailing=True),
      sets(2, on=15, zone=7, cadence=SURGE, off=45, trailing=True),
      CD3)

klass("SPR-04", "Tabata 3×8 30", SPRINTS,
      WU5, primers(3, 6, SURGE, on=20, off=60),
      sets(8, on=20, zone=6, cadence=SURGE, off=10, trailing=True),
      hold(180, 1, EASY),
      sets(8, on=20, zone=6, cadence=SURGE, off=10, trailing=True),
      hold(180, 1, EASY),
      sets(8, on=20, zone=6, cadence=SURGE, off=10, trailing=True),
      CD3)

klass("SPR-05", "Sprints, Three Ways 30", SPRINTS,
      WU5, primers(3, 6, SURGE, on=20, off=60),
      sets(8, on=20, zone=6, cadence=SURGE, off=10),
      hold(180, 1, EASY),
      sets(3, on=40, zone=6, cadence=STAND, off=40, position=STANDING),
      hold(240, 1, EASY),
      sets(8, on=20, zone=6, cadence=SPIN, off=10),
      CD3)

klass("SPR-06", "Sprint Endurance 30", SPRINTS,
      WU5, primers(3, 6, SURGE, on=20, off=60),
      hold(180, 2, BRISK),
      sets(8, on=20, zone=6, cadence=SURGE, off=10, trailing=True),
      hold(240, 2, BRISK),
      sets(8, on=20, zone=6, cadence=SURGE, off=10, trailing=True),
      hold(180, 2, BRISK),
      CD3)
