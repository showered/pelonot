> Part of the Pelonot plan — the index is [PLAN.md](../PLAN.md).

## What a workout costs, and whether 500 MB runs out

Asked by the owner, and answered by measuring rather than by estimating.
The method and the exact figures are reproducible; where a number is modelled
rather than measured it says so.

### The measurements

**Local, exact.** The real Room schema was built in SQLite and a 45-minute
ride's worth of samples inserted:

| | |
|---|---|
| One 45-minute ride (2,700 samples) | **292 KB** |
| Per `workout_metrics` row, on disk | **~111 bytes** |

About a third of that row is the 36-character UUID `workout_id` carried on
every sample, plus the same UUID again in its index. Worth knowing, not worth
fixing — an integer foreign key would save ~30% of the local database and
costs the property that makes crash recovery and export straightforward.

**Cloud, modelled from a measured payload.** The same ride through the current
`WorkoutDto`:

| Stage | Size |
|-------|------|
| JSON on the wire (the `metrics_payload` array) | **228 KB** |
| As uncompressed `JSONB` in the row | ~372 KB (modelled) |
| **Stored, after TOAST compression** | **~25–30 KB** (modelled) |

The compression ratio is where the modelling is: `pglz` finds the repeated key
names in every one of the 2,700 objects easily, and the figure above is a gzip
measurement adjusted for `pglz` being the weaker algorithm. **One query settles
it exactly** and should be run the next time anyone is at the Management API —
it is the same trip 14.1.6 needs:

```sql
select pg_column_size(metrics_payload), jsonb_array_length(metrics_payload)
from workouts order by recorded_at desc limit 5;
```

### The budget

| Scenario | Rides/year | Cloud/year | Local/year |
|----------|-----------|-----------|-----------|
| **The question as asked** — 4 riders, one ride a week | 208 | **~6 MB** | ~61 MB |
| 4 riders, four rides a week each | 832 | ~25 MB | ~243 MB |

**500 MB is not the constraint for a household.** At ~30 KB a ride it is
somewhere around **13,000 rides**. Four riders at one ride a week would take
roughly sixty years to fill it, and at four rides a week each, about fifteen.
**No purging feature is needed for the reason it was proposed**, and building
one now would be building the wrong thing.

### Where it *does* run out — three findings, all more interesting than the question

1. **The local tablet fills seven to ten times faster than the cloud does**,
   because SQLite stores the series raw and Postgres compresses it. Five years
   of household riding is ~300 MB of database on a bike tablet — and the
   12.4.4 backup file is a full copy of it, so the export gets that big too.
   **If a trimming feature is ever built, it is a local feature first and a
   cloud feature second.** That is 23.4.
2. **A published community endpoint fills the free tier inside its first
   year.** 14.10 contemplates checking in a default endpoint and key so a
   fresh clone has a cloud. At ~30 KB a ride, 400 MB of usable budget is about
   **250 riders riding once a week for one year** — or sixty riders riding
   properly. This is a second and more concrete reason for 14.10.4's caution
   than the RLS one, and it belongs in that decision.
3. ~~**A float-to-double widening could triple every payload, and nobody has
   checked.**~~ **Checked, 2 August 2026 — the fear was justified, the damage
   is already repaired, and one useful fact came out of it.** 1,661 rows across
   the bike's four rides:
   - **The board reports fractional power and those digits are data**, not
     noise. Power arrives in tenths of a watt on the `0x44` frame and
     `PelotonFrameParser` divides by ten in doubles, so `29.7` means 29.7 W.
     1,360 of the 1,661 rows are fractional
   - **The widening noise is real and is confined to the pre-2.7c rides.**
     `29.2000007629395` is `29.2f` widened, and it appears only in the three
     rides recorded before the frame parser took over from
     `data.getFloat(KEY_DATA).toDouble()`. The ride recorded after it carries
     clean tenths. So the fix for the corruption defect quietly fixed this too,
     and nothing needs rewriting — those three rides are already marked suspect
     by 2.7.5
   - **Cadence and resistance are integral in every row**, which turned out to
     be worth 11 KB a ride: `80.0` costs two characters more than `80` across
     three columns and 2,700 samples, and that difference is the whole gap
     between the first columnar draft (64 KB) and the 49 KB predicted below.
     See 14.4.1a

   ```bash
   sqlite3 db.sqlite "SELECT cadence, resistance, power FROM workout_metrics LIMIT 20;"
   ```

### What to do about it

- **Change the cloud wire format now, while the cloud holds one row.** An array
  of 2,700 five-key objects repeats the key names 13,500 times. Columnar arrays
  — `{"t":[…],"c":[…],"r":[…],"p":[…],"hr":[…]}` — carry exactly the same data:
  **228 KB → 49 KB** on the wire, ~30 KB → ~19 KB stored. The storage saving is
  the smaller half; **the request body is the point**, and a 90-minute ride
  currently posts 457 KB in a single insert, which is precisely what 14.2.7 was
  worried about. It is free to change today and expensive to change once rides
  are up there. See **14.4**. ***Done, 2 August 2026*** — and the two figures
  above are no longer estimates: the round-trip test builds both shapes from
  the same 2,700 samples and measures `49 KB against 228 KB`.
- **Keep the timestamp array explicit. Do not imply time from the index.** It
  would save another 12 KB and it is exactly the wrong 12 KB: a stalled board
  leaves a genuine gap in the series (2.4.4), the charts draw those gaps
  deliberately (16.1.2, 16.2.2), and an implied index closes them silently.
  A ride that stopped for two minutes would come back from the cloud looking
  continuous.
- **Trim locally, opt-in, and never silently** — 23.4. A trimmed ride keeps its
  aggregates, its time-in-zone and a downsampled trace, and it is **marked** as
  trimmed so a chart says "10-second detail" rather than drawing a coarse line
  as if it were the record. That marking is the whole discipline: it is the
  same family as 7.8 and 16.1.6, a derived number whose provenance was thrown
  away.
- **Offer the export before the first trim ever runs.** 12.4.3 and 12.4.4 both
  exist already, which is why this can be an honest offer rather than a warning.
