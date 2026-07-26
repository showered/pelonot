#!/usr/bin/env python3
"""
Pelonot Class Template Seed Generator

Generates the INSERT SQL for 72 structured cycling workouts across 7 categories.
Output can be pasted directly into supabase/migration.sql.

Usage:
    python3 seed_generator.py > seed_output.sql
    
    Or run with --preview to see a summary table of all classes:
    python3 seed_generator.py --preview
"""

import json
import sys

# ── Category definitions ──────────────────────────────────────────────
# Each category has: prefix, count, [durations], title_templates
# Durations are in seconds: 1200=20min, 1800=30min, 2700=45min, 3600=60min

CATEGORIES = {
    "Power Zone Endurance": {
        "prefix": "PZE",
        "count": 12,
        "durations": [1200, 1200, 1200, 1800, 1800, 1800, 1800, 2700, 2700, 2700, 3600, 3600],
        "titles": [
            "Base Builder 20",
            "Endurance Flow 20",
            "Zone 2 Cruise 20",
            "Steady Cadence 30",
            "Endurance Cruise 30",
            "Base Distance 30",
            "Tempo Builder 30",
            "Long Endurance 45",
            "Progression Ride 45",
            "Tempo Cruise 45",
            "Classic PZE 60",
            "Distance Builder 60",
        ],
        "zone_profile": ["easy_z2_z3"] * 12,  # Z1/Z2 warmup, Z2/Z3 blocks, Z1 cooldown
    },
    "Sweet Spot": {
        "prefix": "SS",
        "count": 12,
        "durations": [1200, 1200, 1200, 1800, 1800, 1800, 1800, 2700, 2700, 2700, 3600, 3600],
        "titles": [
            "Sweet Start 20",
            "SS Repeats 20",
            "Tempo Push 20",
            "Sweet Spot 3x5 30",
            "Tempo Ladder 30",
            "SS Builder 30",
            "Controlled Burn 30",
            "Sweet Spot Pyramid 45",
            "Over/Under Lite 45",
            "Tempo Chase 45",
            "Long Sweet Spot 60",
            "SS Endurance 60",
        ],
        "zone_profile": ["sweet_spot"] * 12,  # Z1 warmup, Z3/Z4 blocks, Z1 cooldown
    },
    "Threshold": {
        "prefix": "TH",
        "count": 12,
        "durations": [1200, 1200, 1200, 1800, 1800, 1800, 1800, 2700, 2700, 2700, 3600, 3600],
        "titles": [
            "Threshold 2x5 20",
            "Controlled Threshold 20",
            "Build to Threshold 20",
            "Threshold Ladder 30",
            "Threshold Repeats 30",
            "Tempo Threshold 30",
            "FTP Prep 30",
            "Threshold Pyramid 45",
            "Strong Threshold 45",
            "FTP Builder 45",
            "Threshold Base 60",
            "Long Threshold 60",
        ],
        "zone_profile": ["threshold"] * 12,  # Z3 warmup, Z4 threshold blocks, Z1 cooldown
    },
    "VO2 Max": {
        "prefix": "VO2",
        "count": 10,
        "durations": [1200, 1200, 1200, 1200, 1800, 1800, 1800, 1800, 2700, 2700],
        "titles": [
            "VO2 Spark 20",
            "VO2 Repeats 20",
            "Speed Play 20",
            "Fast Finish 20",
            "VO2 Builder 30",
            "Aerobic Burn 30",
            "Top End 30",
            "VO2 Ladder 30",
            "VO2 Pyramid 45",
            "Top End Climb 45",
        ],
        "zone_profile": ["vo2"] * 10,  # Z2 prep, Z5 intervals with recovery, Z1 cooldown
    },
    "HIIT & Heavy Climbs": {
        "prefix": "HC",
        "count": 10,
        "durations": [1200, 1200, 1200, 1200, 1800, 1800, 1800, 1800, 2700, 2700],
        "titles": [
            "Hill Grind 20",
            "Torque Repeats 20",
            "Climb Sprint 20",
            "Hill Attack 20",
            "Climb Blocks 30",
            "Heavy Hitter 30",
            "Mountain Repeats 30",
            "Hill Tempo 30",
            "Mountain Stage 45",
            "Torque Builder 45",
        ],
        "zone_profile": ["hiit_climb"] * 10,  # Z1/2 warmup, Z4-6 low-cadence blocks, Z1 cooldown
    },
    "Tabata Bursts": {
        "prefix": "TB",
        "count": 6,
        "durations": [1200, 1200, 1200, 1200, 1800, 1800],
        "titles": [
            "Tabata Sprint 20",
            "Tabata Power 20",
            "Tabata Max 20",
            "Tabata Finish 20",
            "Tabata Endurance 30",
            "Tabata Repeat 30",
        ],
        "zone_profile": ["tabata"] * 6,  # Strict 20s ON / 10s OFF
    },
    "Recovery": {
        "prefix": "RC",
        "count": 10,
        "durations": [1200, 1200, 1200, 1800, 1800, 1800, 2700, 2700, 3600, 3600],
        "titles": [
            "Recovery Ride 20",
            "Easy Spin 20",
            "Reset Ride 20",
            "Recovery Flow 30",
            "Easy Endurance 30",
            "Spin Out 30",
            "Recovery Base 45",
            "Steady Reset 45",
            "Long Recovery 60",
            "Base Recovery 60",
        ],
        "zone_profile": ["recovery"] * 10,  # Z1 mostly, occasional Z2
    },
}


def generate_intervals(duration_sec: int, profile: str) -> list[dict]:
    """
    Generate interval arrays for a given duration and profile type.
    
    All profiles follow: warmup → work blocks → cooldown
    Cadence ranges vary by profile (low for climbs, high for tabata, moderate for endurance).
    """
    warmup_sec = 240 if duration_sec >= 1200 else 180
    cooldown_sec = 180 if duration_sec >= 1200 else 120
    if duration_sec >= 2700:
        warmup_sec = 300
        cooldown_sec = 300
    if duration_sec == 3600:
        warmup_sec = 300
        cooldown_sec = 300

    work_window = duration_sec - warmup_sec - cooldown_sec
    intervals = []

    # Warmup
    intervals.append({
        "time_start_sec": 0,
        "time_end_sec": warmup_sec,
        "target_cadence_min": 80,
        "target_cadence_max": 90,
        "target_power_zone": 1,
    })

    if profile == "easy_z2_z3":
        block = work_window // 2
        intervals.append({
            "time_start_sec": warmup_sec,
            "time_end_sec": warmup_sec + block,
            "target_cadence_min": 85,
            "target_cadence_max": 95,
            "target_power_zone": 2,
        })
        intervals.append({
            "time_start_sec": warmup_sec + block,
            "time_end_sec": warmup_sec + work_window,
            "target_cadence_min": 90,
            "target_cadence_max": 100,
            "target_power_zone": 3,
        })

    elif profile == "sweet_spot":
        # Alternating Z3/Z4 blocks
        n_blocks = max(2, work_window // 300)
        block_sec = work_window // n_blocks
        for i in range(n_blocks):
            zone = 4 if i % 2 == 0 else 3
            cad_min = 90 if zone == 4 else 88
            cad_max = 100 if zone == 4 else 98
            intervals.append({
                "time_start_sec": warmup_sec + i * block_sec,
                "time_end_sec": warmup_sec + (i + 1) * block_sec,
                "target_cadence_min": 85,
                "target_cadence_max": 95,
                "target_power_zone": 2,
            })
            intervals.append({
                "time_start_sec": warmup_sec + i * block_sec,
                "time_end_sec": warmup_sec + (i + 1) * block_sec,
                "target_cadence_min": cad_min,
                "target_cadence_max": cad_max,
                "target_power_zone": zone,
            })

    elif profile == "threshold":
        # Predominantly Z4 with Z3 prep/transition
        block = work_window // 2
        intervals.append({
            "time_start_sec": warmup_sec,
            "time_end_sec": warmup_sec + block // 2,
            "target_cadence_min": 88,
            "target_cadence_max": 98,
            "target_power_zone": 3,
        })
        intervals.append({
            "time_start_sec": warmup_sec + block // 2,
            "time_end_sec": warmup_sec + work_window,
            "target_cadence_min": 90,
            "target_cadence_max": 100,
            "target_power_zone": 4,
        })

    elif profile == "vo2":
        # Z5 intervals with Z2 recovery
        n_intervals = max(4, work_window // 180)
        interval_sec = work_window // n_intervals
        work_sec = interval_sec * 2 // 3
        rest_sec = interval_sec - work_sec
        current = warmup_sec
        intervals.append({
            "time_start_sec": warmup_sec,
            "time_end_sec": current + rest_sec,
            "target_cadence_min": 85,
            "target_cadence_max": 95,
            "target_power_zone": 2,
        })
        current += rest_sec
        for i in range(n_intervals):
            intervals.append({
                "time_start_sec": current,
                "time_end_sec": current + work_sec,
                "target_cadence_min": 95,
                "target_cadence_max": 110,
                "target_power_zone": 5,
            })
            current += work_sec
            if i < n_intervals - 1:
                intervals.append({
                    "time_start_sec": current,
                    "time_end_sec": current + rest_sec,
                    "target_cadence_min": 85,
                    "target_cadence_max": 95,
                    "target_power_zone": 2,
                })
                current += rest_sec

    elif profile == "hiit_climb":
        # Low cadence Z4-Z6 blocks with Z2 recovery
        n_blocks = max(3, work_window // 360)
        block_sec = work_window // n_blocks
        current = warmup_sec
        for i in range(n_blocks):
            zones = [4, 5, 6]
            cad_mins = [60, 55, 50]
            cad_maxs = [70, 65, 60]
            zi = i % 3
            intervals.append({
                "time_start_sec": current,
                "time_end_sec": current + block_sec // 2,
                "target_cadence_min": 85,
                "target_cadence_max": 95,
                "target_power_zone": 2,
            })
            current += block_sec // 2
            intervals.append({
                "time_start_sec": current,
                "time_end_sec": current + block_sec // 2,
                "target_cadence_min": cad_mins[zi],
                "target_cadence_max": cad_maxs[zi],
                "target_power_zone": zones[zi],
            })
            current += block_sec // 2

    elif profile == "tabata":
        # Strict 20s ON / 10s OFF, then extended Z2 block
        n_work = work_window // 30  # number of tabata cycles
        current = warmup_sec
        for i in range(n_work):
            if current + 30 > warmup_sec + work_window:
                break
            cad_min = 100 if i % 2 == 0 else 105
            cad_max = 120 if i % 2 == 0 else 125
            zone = 6 if i % 2 == 0 else 5
            intervals.append({
                "time_start_sec": current,
                "time_end_sec": current + 20,
                "target_cadence_min": cad_min,
                "target_cadence_max": cad_max,
                "target_power_zone": zone,
            })
            current += 20
            intervals.append({
                "time_start_sec": current,
                "time_end_sec": current + 10,
                "target_cadence_min": 80,
                "target_cadence_max": 90,
                "target_power_zone": 1,
            })
            current += 10
        # Fill remaining time with Z2
        if current < warmup_sec + work_window:
            intervals.append({
                "time_start_sec": current,
                "time_end_sec": warmup_sec + work_window,
                "target_cadence_min": 85,
                "target_cadence_max": 95,
                "target_power_zone": 2,
            })

    elif profile == "recovery":
        # Mostly Z1, occasional Z2
        block = work_window // 3
        intervals.append({
            "time_start_sec": warmup_sec,
            "time_end_sec": warmup_sec + block,
            "target_cadence_min": 80,
            "target_cadence_max": 90,
            "target_power_zone": 1,
        })
        intervals.append({
            "time_start_sec": warmup_sec + block,
            "time_end_sec": warmup_sec + block * 2,
            "target_cadence_min": 85,
            "target_cadence_max": 95,
            "target_power_zone": 2,
        })
        intervals.append({
            "time_start_sec": warmup_sec + block * 2,
            "time_end_sec": warmup_sec + work_window,
            "target_cadence_min": 80,
            "target_cadence_max": 90,
            "target_power_zone": 1,
        })

    # Cooldown
    intervals.append({
        "time_start_sec": warmup_sec + work_window,
        "time_end_sec": warmup_sec + work_window + cooldown_sec,
        "target_cadence_min": 80,
        "target_cadence_max": 90,
        "target_power_zone": 1,
    })

    return intervals


def duration_label(sec: int) -> str:
    return f"{sec // 60}min"


def main():
    if "--preview" in sys.argv:
        print(f"{'ID':<10} {'Title':<30} {'Category':<25} {'Duration':<8} {'Intervals':<8}")
        print("-" * 85)
        for cat_name, cat in CATEGORIES.items():
            for i in range(cat["count"]):
                cid = f"{cat['prefix']}-{i+1:02d}"
                title = cat["titles"][i]
                dur = duration_label(cat["durations"][i])
                intervals = generate_intervals(cat["durations"][i], cat["zone_profile"][i])
                print(f"{cid:<10} {title:<30} {cat_name:<25} {dur:<8} {len(intervals)}")
        sys.exit(0)

    # Generate INSERT SQL
    print("-- Seed data: 72 structured cycling classes (7 categories)")
    print("INSERT INTO class_templates (id, title, category, duration_sec, intervals_json) VALUES")
    rows = []
    for cat_name, cat in CATEGORIES.items():
        for i in range(cat["count"]):
            cid = f"{cat['prefix']}-{i+1:02d}"
            title = cat["titles"][i]
            dur = cat["durations"][i]
            intervals = generate_intervals(dur, cat["zone_profile"][i])
            intervals_json = json.dumps(intervals, separators=(",", ":"))
            rows.append(f"('{cid}', '{title}', '{cat_name}', {dur}, '{intervals_json}')")

    print(",\n".join(rows) + ";")
    print()
    print(f"-- Total: {len(rows)} classes")


if __name__ == "__main__":
    main()