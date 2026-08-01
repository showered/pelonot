#!/usr/bin/env python3
"""Check the catalogue against the design rules, then write the assets.

    python3 classlibrary/build.py

Nothing is written unless every rule passes, so a session that breaks one is a
build failure here rather than a bad ride later. `ClassLibraryAssetsTest`
re-checks most of the same claims against the emitted JSON — the assets are what
ships, and a generator nobody runs cannot vouch for them.
"""

import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import catalogue  # noqa: F401  (importing it is what populates the catalogue)
from builder import CATALOGUE

ASSET_ROOT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "classes",
)

# R1 — the vocabulary of block lengths under two minutes. An explicit set
# rather than "a multiple of n": 20 s on / 10 s off is the Tabata protocol and
# has to be sayable, while 97 s — a percentage of a total — must not be.
SHORT_LENGTHS = {10, 15, 20, 30, 40, 45, 60, 75, 90, 105}

# R6 — a burst count only resets when the rider actually gets a break. This is
# what was wrong with the old TB-01: sixteen consecutive rounds, no set break.
BURST_RESET_SEC = 45

# R6 — the dose matches what the zone is for. Seconds.
SINGLE_BLOCK_CAP = {4: 20 * 60, 5: 8 * 60, 6: 90, 7: 30}
TOTAL_CAP = {4: 40 * 60, 5: 20 * 60, 6: 10 * 60, 7: 3 * 60}

# R6 — a category has to deliver what its name promises.
FLOOR_FRACTION = {
    "Sweet Spot": (4, 0.25),
    "Threshold": (4, 0.25),
    "Climbs": (4, 0.25),
    "VO2 Max": (5, 0.15),
}
FLOOR_ABSOLUTE = {"Sprints": (6, 3 * 60)}


def problems(session):
    """Every rule this session breaks, as human sentences."""
    out = []
    blocks = session.blocks
    total = session.duration_sec
    zones = [z for _, z, _ in blocks]
    top = max(zones)

    def note(rule, message):
        out.append(f"{session.id} [{rule}] {message}")

    if total % 60:
        note("length", f"is {total} s, not a whole number of minutes")

    # R1 — lengths a rider can hold.
    for seconds, zone, _ in blocks:
        if seconds <= 0:
            note("R1", "has a block with no length")
        elif seconds < 120 and seconds not in SHORT_LENGTHS:
            note("R1", f"has a {seconds} s block; under two minutes, use one of {sorted(SHORT_LENGTHS)}")
        elif seconds >= 120 and seconds % 60:
            note("R1", f"has a {seconds} s block; over two minutes, use whole minutes")

    for _, zone, (lo, hi) in blocks:
        if zone not in range(1, 8):
            note("zone", f"asks for zone {zone}")
        if not (30 <= lo <= hi <= 140):
            note("cadence", f"asks for {lo}-{hi} rpm")

    # R2 — the warmup warms up. A class that never leaves Z2 has no work to be
    # warm for, which is what makes Recovery exempt rather than special-cased.
    if top >= 3:
        if blocks[0][1] != 1 or blocks[0][0] < 120:
            note("R2", "does not open with at least two minutes at Z1")
        if len({z for _, z, _ in blocks[: _index_of_first(zones, 4)]}) < 3:
            note("R2", "has a warmup that is not progressive")
        before = sum(s for s, z, _ in blocks[: _index_of_first(zones, 4)])
        if before < 300:
            note("R2", f"gives {before} s before the first hard block; five minutes is the floor")
    if top >= 5:
        first_hard = next(i for i, z in enumerate(zones) if z >= 5)
        if blocks[first_hard][0] > 45:
            note(
                "R2",
                f"opens its Z5+ work with a {blocks[first_hard][0]} s effort; prime it first",
            )

    # R3 — the cooldown cools down.
    if blocks[-1][1] != 1:
        note("R3", "does not end at Z1")
    tail = 0
    for seconds, zone, _ in reversed(blocks):
        if zone > 2:
            break
        tail += seconds
    if tail < 180:
        note("R3", f"has {tail} s of cooldown; three minutes is the floor")

    # R4 — recovery is proportionate to the effort it follows. Only recovery
    # *between* efforts: the block after the last interval is the cooldown, and
    # holding it to this ratio would say nothing about the session.
    for i in range(1, len(blocks) - 1):
        seconds, zone, _ = blocks[i]
        prev_sec, prev_zone, _ = blocks[i - 1]
        next_zone = blocks[i + 1][1]
        if zone > 2 or prev_zone < 4 or next_zone < 3:
            continue
        if prev_sec < 60:
            continue  # 20 s on / 10 s off is a protocol, not a shortfall
        needed = prev_sec if prev_zone >= 5 else prev_sec // 2
        if seconds < needed:
            note(
                "R4",
                f"gives {seconds} s of recovery after a {prev_sec} s Z{prev_zone} effort; "
                f"needs {needed} s",
            )

    # R6 — the dose.
    by_zone = {}
    for seconds, zone, _ in blocks:
        by_zone[zone] = by_zone.get(zone, 0) + seconds
    for zone, cap in SINGLE_BLOCK_CAP.items():
        longest = max((s for s, z, _ in blocks if z == zone), default=0)
        if longest > cap:
            note("R6", f"has a {longest} s block at Z{zone}; the cap is {cap} s")
    for zone, cap in TOTAL_CAP.items():
        if by_zone.get(zone, 0) > cap:
            note("R6", f"spends {by_zone[zone]} s at Z{zone}; the cap is {cap} s")

    run = 0
    for seconds, zone, _ in blocks:
        if zone >= 6:
            run += 1
        elif zone <= 2 and seconds >= BURST_RESET_SEC:
            run = 0
        if run > 8:
            note("R6", "stacks more than eight bursts at Z6+ without a real break")
            break

    if session.category in FLOOR_FRACTION:
        floor_zone, fraction = FLOOR_FRACTION[session.category]
        got = sum(s for s, z, _ in blocks if z >= floor_zone)
        if got < total * fraction:
            note(
                "R6",
                f"is a {session.category} class with {got} s at Z{floor_zone}+; "
                f"needs {int(total * fraction)} s",
            )
    if session.category in FLOOR_ABSOLUTE:
        floor_zone, seconds_needed = FLOOR_ABSOLUTE[session.category]
        got = sum(s for s, z, _ in blocks if z >= floor_zone)
        if got < seconds_needed:
            note("R6", f"has {got} s at Z{floor_zone}+; needs {seconds_needed} s")

    # R7 — a recovery class recovers.
    if session.category == "Recovery":
        if top > 2:
            note("R7", f"is a Recovery class that reaches Z{top}")
        if by_zone.get(2, 0) * 2 >= total:
            note("R7", f"spends {by_zone.get(2, 0)} s of {total} s at Z2")

    return out


def _index_of_first(zones, threshold):
    for i, zone in enumerate(zones):
        if zone >= threshold:
            return i
    return len(zones)


def library_problems(sessions):
    """Rules about the library rather than about any one class."""
    out = []

    # R5 — cadence is a separate axis from zone, and the library has to use it.
    bands = {}
    for session in sessions:
        for _, zone, cadence in session.blocks:
            bands.setdefault(zone, set()).add(cadence)
    varied = [z for z, c in bands.items() if len(c) >= 3]
    if len(varied) < 4:
        out.append(
            f"[R5] only {len(varied)} zones are ridden at three or more cadences; "
            "cadence is behaving like a lookup from the zone"
        )

    # R9 — no two classes are the same class.
    seen = {}
    for session in sessions:
        signature = tuple(session.blocks)
        if signature in seen:
            out.append(f"[R9] {session.id} is the same class as {seen[signature]}")
        seen[signature] = session.id

    by_slot = {}
    for session in sessions:
        by_slot.setdefault((session.category, session.duration_sec), []).append(session)
    for (category, duration), group in by_slot.items():
        for i, a in enumerate(group):
            for b in group[i + 1:]:
                if len(a.blocks) == len(b.blocks):
                    differing = sum(1 for x, y in zip(a.blocks, b.blocks) if x != y)
                    if differing <= 1:
                        out.append(
                            f"[R9] {a.id} and {b.id} are both {category} "
                            f"{duration // 60} min and differ in {differing} block(s)"
                        )

    ids = [s.id for s in sessions]
    for id in set(ids):
        if ids.count(id) > 1:
            out.append(f"[ids] {id} is used {ids.count(id)} times")

    return out


def to_json(session):
    intervals = []
    at = 0
    for seconds, zone, (lo, hi) in session.blocks:
        intervals.append({
            "time_start_sec": at,
            "time_end_sec": at + seconds,
            "target_cadence_min": lo,
            "target_cadence_max": hi,
            "target_power_zone": zone,
        })
        at += seconds
    return {
        "id": session.id,
        "title": session.title,
        "category": session.category[0] if isinstance(session.category, tuple) else session.category,
        "duration_sec": at,
        "intervals_json": json.dumps(intervals, separators=(",", ":")),
    }


def main():
    sessions = CATALOGUE
    for session in sessions:
        # `klass` takes (display name, directory) so the author writes one thing.
        session.directory = session.category[1]
        session.category = session.category[0]

    failures = []
    for session in sessions:
        failures.extend(problems(session))
    failures.extend(library_problems(sessions))

    if failures:
        print(f"{len(failures)} problem(s); nothing written.\n")
        for failure in failures:
            print("  " + failure)
        return 1

    if os.path.isdir(ASSET_ROOT):
        shutil.rmtree(ASSET_ROOT)
    counts = {}
    for session in sessions:
        directory = os.path.join(ASSET_ROOT, session.directory)
        os.makedirs(directory, exist_ok=True)
        path = os.path.join(directory, f"{session.id.lower()}.json")
        with open(path, "w") as handle:
            json.dump(to_json(session), handle, indent=2)
            handle.write("\n")
        counts[session.category] = counts.get(session.category, 0) + 1

    total_minutes = sum(s.duration_sec for s in sessions) // 60
    print(f"{len(sessions)} classes, {total_minutes} minutes of riding, written to")
    print(f"  {ASSET_ROOT}\n")
    for category in sorted(counts):
        lengths = sorted(
            s.duration_sec // 60 for s in sessions if s.category == category
        )
        print(f"  {category:12} {counts[category]:2}   {lengths}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
