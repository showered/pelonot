#!/usr/bin/env python3
"""Check the catalogue against the design rules, then write the assets.

    python3 classlibrary/build.py

Nothing is written unless every rule passes, so a session that breaks one is a
build failure here rather than a bad ride later. `ClassLibraryAssetsTest`
re-checks most of the same claims against the emitted JSON — the assets are what
ships, and a generator nobody runs cannot vouch for them.
"""

import hashlib
import json
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import catalogue  # noqa: F401  (importing it is what populates the catalogue)
from builder import CADENCE_GOVERNS, CATALOGUE, POWER_GOVERNS
from descriptions import DESCRIPTIONS

ASSETS = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets",
)
ASSET_ROOT = os.path.join(ASSETS, "classes")

# One small file the seeder can read on every launch to find out whether the
# 72 have changed, without parsing the 72. PLAN 23.2.6c — and the local half of
# the diffing that 23.2.4 wants for the cloud channel.
MANIFEST = os.path.join(ASSETS, "class_library.json")

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

# R11 — standing (PLAN 25.1.3). Nobody rides out of the saddle for five
# minutes, and "stand up" at 120 rpm is an instruction with no way to follow it.
STANDING_CAP_SEC = 180
STANDING_CADENCE_CAP = 110

# R12 — governance (PLAN 11.7.2). The library's comfortable seated range: a band
# living entirely inside it is the *default*, and a block that claims to be
# governed by a default is claiming nothing.
NEUTRAL_CADENCE = (75, 95)

# R12 — the categories whose names promise the exercise is about the pedalling
# rather than about the watts. If a Climbs class never says cadence governs,
# it is an interval class at a low rpm.
CADENCE_CATEGORIES = {"Climbs", "Sprints"}

# R12 — cadence governing is the exception, across the whole library. A
# ceiling rather than a floor, because the failure mode is the field spreading
# until "one instruction at a time" means "always the cadence".
CADENCE_LIBRARY_CEILING = 1 / 3

# R13 — the description (PLAN 23.2.7). Two or three sentences: long enough to
# say what the ride is and what it trains, short enough that a rider standing
# over a bike reads it rather than skims past it.
DESCRIPTION_MIN = 80
DESCRIPTION_MAX = 320

# R13 — words that belong where a measurement is being *read*, not where a
# class is being *chosen*. CLAUDE.md's standing rule and Phase 26's argument:
# a rider picking tonight's ride is deciding, and "FTP" in that sentence is the
# app showing its working. The zone *names* are fine — "threshold effort"
# describes a feeling — so this bans the units and the acronyms, not the
# vocabulary of riding.
JARGON = ["FTP", "watts?", "kilojoules?", "kJ", "W/kg", "VO2", "rpm"]

# R10 and R13 — a position word is a promise the blocks have to keep (PLAN
# 25.4.2), and it is the *same* promise whichever surface says it. The four
# classes 25.4.2 renamed were found by hand and the rule it left behind was
# written into the README and checked nowhere; R10 is the one rule in this file
# with a history of being written down and broken by all 72 classes at once, so
# it does not get to be enforced by memory a second time.
#
# "big gear" is here because in cycling usage it means seated torque and reads
# as exactly that instruction — the README says so under R10, and three of the
# four classes 25.4.2 renamed said "Big Gear" rather than "Seated".
POSITION_WORDS = {
    "standing": r"out of the saddle|standing|stand up",
    "seated": r"seated|big gear",
}


def broken_position_promises(text, blocks):
    """The positions `text` claims that no block in `blocks` prescribes.

    One helper for both surfaces on purpose. The title and the description make
    the identical claim and it was previously checked on neither: the title not
    at all, and the description in the standing direction only — which is how
    `CLB-04` came to say "seated rises" with no position on any of its seventeen
    blocks, and `SPR-05` to promise a seated set in a class whose only
    positioned blocks ask the rider to stand up.
    """
    prescribed = {b[3] for b in blocks if b[3]}
    return [
        position
        for position, pattern in POSITION_WORDS.items()
        if re.search(pattern, text, re.IGNORECASE) and position not in prescribed
    ]


def governs(cadence):
    """Which metric this block's cadence intent says is the instruction.

    Plain tuples still appear in tests and in hand-written blocks, and they
    mean what an unmarked interval means on disk: power.
    """
    return getattr(cadence, "governs", POWER_GOVERNS)


def problems(session):
    """Every rule this session breaks, as human sentences."""
    out = []
    blocks = session.blocks
    total = session.duration_sec
    zones = [z for _, z, _, _ in blocks]
    top = max(zones)

    def note(rule, message):
        out.append(f"{session.id} [{rule}] {message}")

    if total % 60:
        note("length", f"is {total} s, not a whole number of minutes")

    # R1 — lengths a rider can hold.
    for seconds, zone, _, _ in blocks:
        if seconds <= 0:
            note("R1", "has a block with no length")
        elif seconds < 120 and seconds not in SHORT_LENGTHS:
            note("R1", f"has a {seconds} s block; under two minutes, use one of {sorted(SHORT_LENGTHS)}")
        elif seconds >= 120 and seconds % 60:
            note("R1", f"has a {seconds} s block; over two minutes, use whole minutes")

    for _, zone, (lo, hi), _ in blocks:
        if zone not in range(1, 8):
            note("zone", f"asks for zone {zone}")
        if not (30 <= lo <= hi <= 140):
            note("cadence", f"asks for {lo}-{hi} rpm")

    # R2 — the warmup warms up. A class that never leaves Z2 has no work to be
    # warm for, which is what makes Recovery exempt rather than special-cased.
    if top >= 3:
        if blocks[0][1] != 1 or blocks[0][0] < 120:
            note("R2", "does not open with at least two minutes at Z1")
        if len({z for _, z, _, _ in blocks[: _index_of_first(zones, 4)]}) < 3:
            note("R2", "has a warmup that is not progressive")
        before = sum(s for s, z, _, _ in blocks[: _index_of_first(zones, 4)])
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
    for seconds, zone, _, _ in reversed(blocks):
        if zone > 2:
            break
        tail += seconds
    if tail < 180:
        note("R3", f"has {tail} s of cooldown; three minutes is the floor")

    # R4 — recovery is proportionate to the effort it follows. Only recovery
    # *between* efforts: the block after the last interval is the cooldown, and
    # holding it to this ratio would say nothing about the session.
    for i in range(1, len(blocks) - 1):
        seconds, zone, _, _ = blocks[i]
        prev_sec, prev_zone, _, _ = blocks[i - 1]
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
    for seconds, zone, _, _ in blocks:
        by_zone[zone] = by_zone.get(zone, 0) + seconds
    for zone, cap in SINGLE_BLOCK_CAP.items():
        longest = max((s for s, z, _, _ in blocks if z == zone), default=0)
        if longest > cap:
            note("R6", f"has a {longest} s block at Z{zone}; the cap is {cap} s")
    for zone, cap in TOTAL_CAP.items():
        if by_zone.get(zone, 0) > cap:
            note("R6", f"spends {by_zone[zone]} s at Z{zone}; the cap is {cap} s")

    run = 0
    for seconds, zone, _, _ in blocks:
        if zone >= 6:
            run += 1
        elif zone <= 2 and seconds >= BURST_RESET_SEC:
            run = 0
        if run > 8:
            note("R6", "stacks more than eight bursts at Z6+ without a real break")
            break

    if session.category in FLOOR_FRACTION:
        floor_zone, fraction = FLOOR_FRACTION[session.category]
        got = sum(s for s, z, _, _ in blocks if z >= floor_zone)
        if got < total * fraction:
            note(
                "R6",
                f"is a {session.category} class with {got} s at Z{floor_zone}+; "
                f"needs {int(total * fraction)} s",
            )
    if session.category in FLOOR_ABSOLUTE:
        floor_zone, seconds_needed = FLOOR_ABSOLUTE[session.category]
        got = sum(s for s, z, _, _ in blocks if z >= floor_zone)
        if got < seconds_needed:
            note("R6", f"has {got} s at Z{floor_zone}+; needs {seconds_needed} s")

    # R11 — standing is an instruction, and it has to be a possible one.
    positioned = 0
    for seconds, _, (lo, hi), position in blocks:
        if position is None:
            continue
        positioned += seconds
        if position != "standing":
            continue
        if seconds > STANDING_CAP_SEC:
            note("R11", f"asks the rider to stand for {seconds} s; the cap is {STANDING_CAP_SEC} s")
        if hi > STANDING_CADENCE_CAP:
            note("R11", f"asks the rider to stand at {lo}-{hi} rpm")
    if positioned * 2 > total:
        note(
            "R11",
            f"prescribes a position for {positioned} s of {total} s; leave most of "
            "a class to the rider",
        )

    # R12 — one instruction at a time, and it has to be one worth giving.
    for _, _, cadence, _ in blocks:
        if governs(cadence) != CADENCE_GOVERNS:
            continue
        lo, hi = cadence
        if NEUTRAL_CADENCE[0] <= lo and hi <= NEUTRAL_CADENCE[1]:
            note(
                "R12",
                f"says cadence governs a {lo}-{hi} rpm block; that is the library's "
                "default seated range and governing it instructs nobody",
            )
    if session.category in CADENCE_CATEGORIES:
        if not any(governs(c) == CADENCE_GOVERNS for _, _, c, _ in blocks):
            note(
                "R12",
                f"is a {session.category} class where the cadence never governs; "
                "the category name promises the pedalling is the point",
            )

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


def _signature(session):
    """A class reduced to what makes it that class.

    `Cadence` is a tuple subclass so that every `(lo, hi)` unpacking keeps
    working, which also means two bands compare equal while asking the rider
    for different things. R9 has to see the difference.
    """
    return tuple(
        (sec, zone, tuple(cadence), governs(cadence), position)
        for sec, zone, cadence, position in session.blocks
    )


def library_problems(sessions):
    """Rules about the library rather than about any one class."""
    out = []

    # R5 — cadence is a separate axis from zone, and the library has to use it.
    bands = {}
    for session in sessions:
        for _, zone, cadence, _ in session.blocks:
            bands.setdefault(zone, set()).add(cadence)
    varied = [z for z, c in bands.items() if len(c) >= 3]
    if len(varied) < 4:
        out.append(
            f"[R5] only {len(varied)} zones are ridden at three or more cadences; "
            "cadence is behaving like a lookup from the zone"
        )

    # R12 — both governors are used, and cadence stays the exception. Same
    # shape of argument as R5's: a field only one value is ever written to is
    # decorative, and a field written to every block has stopped choosing.
    governors = [governs(c) for s in sessions for _, _, c, _ in s.blocks]
    for value in (POWER_GOVERNS, CADENCE_GOVERNS):
        if value not in governors:
            out.append(f"[R12] no block anywhere is governed by {value}")
    share = governors.count(CADENCE_GOVERNS) / max(len(governors), 1)
    if share > CADENCE_LIBRARY_CEILING:
        out.append(
            f"[R12] cadence governs {share:.0%} of the library; the ceiling is "
            f"{CADENCE_LIBRARY_CEILING:.0%} — it is meant to be the exception"
        )

    # R9 — no two classes are the same class. The signature carries the
    # governor as well as the band, because two classes alike in every number
    # but differing in what they ask the rider to *do* are two classes.
    seen = {}
    for session in sessions:
        signature = _signature(session)
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
                    differing = sum(
                        1 for x, y in zip(_signature(a), _signature(b)) if x != y
                    )
                    if differing <= 1:
                        out.append(
                            f"[R9] {a.id} and {b.id} are both {category} "
                            f"{duration // 60} min and differ in {differing} block(s)"
                        )

    # R10 — the title names the shape and the demand, not the length. The rule
    # was written down and marked "not tested", and all 72 titles broke it:
    # "The Long Climb 30" sits beside a chip already reading "30 min", on the
    # library, on the start screen, on the ride screen and in history. A shape
    # count ("4×2", "5×5") is the demand and stays.
    #
    # It is the class's *own length* that is banned, not any trailing number:
    # `SWT-01` is "Sweet Spot 5 + 4", where the 4 is a block length and the
    # class is 30 minutes. Matching against the duration rather than against
    # the shape of the string is what tells those apart — and it is not a
    # hypothetical, because the looser version flagged exactly that class on
    # its first run.
    for session in sessions:
        minutes = session.duration_sec // 60
        if re.search(rf"(^|\s){minutes}$", session.title):
            out.append(
                f"[R10] {session.id} \"{session.title}\" ends in its own length; "
                "the duration is already on every surface that shows the title"
            )

    # R10 — a position word in a title is a promise that the blocks say it too
    # (PLAN 25.4.2). This is the rule the owner's rename produced and it has
    # never been checked: `END-08`, `SWT-05`, `THR-06` and `END-12` were found
    # by reading the list, and nothing stood between the library and a fifth.
    # The failure it exists to prevent is the rider being told one thing by the
    # name and another by every surface that speaks during the ride.
    for session in sessions:
        for position in broken_position_promises(session.title, session.blocks):
            out.append(
                f"[R10] {session.id} \"{session.title}\" promises {position} "
                "riding and no block asks for it"
            )

    # R10 — and two classes may not share a name, which is the thing the
    # duration was quietly doing. Without it the check has to be real.
    titles = [s.title for s in sessions]
    for title in set(titles):
        if titles.count(title) > 1:
            out.append(f"[R10] \"{title}\" is the name of {titles.count(title)} classes")

    # R13 — the description says what the ride is for (PLAN 23.2.7).
    #
    # This is the first authored prose in the library, and a build rule cannot
    # check whether a sentence is *true*. What it can check is the shape, and
    # the shape is where R10's failure lived: a description that repeats the
    # duration, the category or the block count is spending the rider's
    # attention on three things already drawn beside it.
    #
    # The substantive half is the last one. A description promising a position
    # or a cadence character is making a claim the blocks have to keep — the
    # same rule as R11 and 25.4.2, one surface along, and the only way an
    # authored sentence can be *wrong* in a way arithmetic can catch.
    for session in sessions:
        text = session.description
        if not text.strip():
            out.append(f"[R13] {session.id} has no description")
            continue
        if len(text) < DESCRIPTION_MIN or len(text) > DESCRIPTION_MAX:
            out.append(
                f"[R13] {session.id} description is {len(text)} characters; "
                f"the band is {DESCRIPTION_MIN}-{DESCRIPTION_MAX}"
            )
        minutes = session.duration_sec // 60
        if re.search(rf"\b{minutes}\b", text):
            out.append(
                f"[R13] {session.id} description names its own length "
                f"({minutes}); the duration is already on the screen beside it"
            )
        if re.search(rf"\b{re.escape(session.category)}\b", text, re.IGNORECASE):
            out.append(
                f"[R13] {session.id} description names its own category "
                f"(\"{session.category}\"), which is drawn beside it"
            )
        for word in JARGON:
            if re.search(rf"\b{word}\b", text, re.IGNORECASE):
                out.append(
                    f"[R13] {session.id} description says \"{word}\"; a rider "
                    "choosing a class is not reading a measurement (Phase 26)"
                )
        # A position word is a promise the blocks have to keep — in both
        # directions. This clause used to read `standing` only, and the two
        # classes that broke it both broke it the other way.
        for position in broken_position_promises(text, session.blocks):
            out.append(
                f"[R13] {session.id} description promises {position} riding "
                "and no block asks for it"
            )

    ids = [s.id for s in sessions]
    for id in set(ids):
        if ids.count(id) > 1:
            out.append(f"[ids] {id} is used {ids.count(id)} times")

    return out


def to_json(session):
    intervals = []
    at = 0
    for seconds, zone, cadence, position in session.blocks:
        lo, hi = cadence
        interval = {
            "time_start_sec": at,
            "time_end_sec": at + seconds,
            "target_cadence_min": lo,
            "target_cadence_max": hi,
            "target_power_zone": zone,
        }
        # Omitted, not null: absent means the rider chooses (PLAN 25.1.1), and
        # a key present with a null in it invites a reader to treat it as a
        # third value.
        if position is not None:
            interval["target_position"] = position
        # PLAN 11.7.2. Optional and additive, exactly like `target_position`,
        # so every class written before it decodes unchanged — and **absent
        # means power**, which is why the common case writes nothing. Not the
        # same argument as position's omission: there is no third claim here,
        # the default is simply the ordinary one.
        if governs(cadence) != POWER_GOVERNS:
            interval["governed_by"] = governs(cadence)
        intervals.append(interval)
        at += seconds
    return {
        "id": session.id,
        "title": session.title,
        "category": session.category[0] if isinstance(session.category, tuple) else session.category,
        "duration_sec": at,
        "description": session.description,
        "intervals_json": json.dumps(intervals, separators=(",", ":")),
    }


def main():
    sessions = CATALOGUE
    # PLAN 23.2.7. The prose lives in its own module so the 72 sentences can be
    # read as a set — the failure mode is not a wrong sentence, it is 72 that
    # all sound alike. A class with no entry gets an empty string and R13
    # refuses to write, which is what makes a new class carry one.
    for session in sessions:
        session.description = DESCRIPTIONS.get(session.id, "")

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
    digest = hashlib.sha256()
    for session in sorted(sessions, key=lambda s: s.id):
        directory = os.path.join(ASSET_ROOT, session.directory)
        os.makedirs(directory, exist_ok=True)
        path = os.path.join(directory, f"{session.id.lower()}.json")
        body = json.dumps(to_json(session), indent=2) + "\n"
        with open(path, "w") as handle:
            handle.write(body)
        digest.update(body.encode())
        counts[session.category] = counts.get(session.category, 0) + 1

    fingerprint = digest.hexdigest()[:16]
    with open(MANIFEST, "w") as handle:
        json.dump({"fingerprint": fingerprint, "count": len(sessions)}, handle, indent=2)
        handle.write("\n")

    total_minutes = sum(s.duration_sec for s in sessions) // 60
    print(f"fingerprint {fingerprint}")
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
