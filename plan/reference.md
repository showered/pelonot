> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## Quick Reference: Coggan 7-Zone Power Model

Bounds are **contiguous and half-open** (`lower ≤ pct < upper`). The published table quotes whole percentages, which leaves unassigned gaps between zones; modelling those literally means a rider at 55.5% of FTP matches no zone at all.

| Zone | Name | % of FTP | Training purpose |
|------|------|----------|------------------|
| Z1 | Active Recovery | < 56% | Warmup, cooldown |
| Z2 | Endurance | 56–76% | Aerobic base |
| Z3 | Tempo | 76–91% | Aerobic efficiency |
| Z4 | Lactate Threshold | 91–106% | Sustainable hard effort |
| Z5 | VO2 Max | 106–121% | Max oxygen uptake |
| Z6 | Anaerobic Capacity | 121–151% | Short power bursts |
| Z7 | Neuromuscular Power | ≥ 151% | Explosive sprints |

## Quick Reference: Ride Intent

`P_target = FTP × zone% × k`

| Intent | `k` | Effect |
|--------|-----|--------|
| Reach New Milestones | 1.05 | Targets 5% higher |
| Just Stay Fit | 0.95 | Targets 5% lower |
