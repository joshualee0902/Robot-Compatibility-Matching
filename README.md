# Robot Compatibility Matching

An Android scouting app for the FRC *Infinite Recharge* game, plus an analytics layer for
choosing alliance partners: robot contribution estimates (OPR), complementary k-NN
matching, and a backtest on real historical events.

> **Status: work in progress.** Milestones 1 and 2 are implemented. Milestones 3 to 6
> (contribution model, matching, backtest, on-device engine) are **not built yet**.
> This README describes them as planned and reports **no results**, because none exist.
> Every number reported here will be generated from code in this repo and written to
> `reports/`, never typed by hand.

| Milestone | What | Status |
|---|---|---|
| 1 | Scouting app fixes (single `MatchRecord`, defense, validation, CSV, migration) | Code done. JVM unit tests pass; the Android build and Espresso test have not been run |
| 2 | Historical data pipeline (The Blue Alliance) | Code done. Offline tests pass; `make data` **not yet run** (needs an API key) |
| 3 | Contribution model (OPR, ridge OPR) | Not started |
| 4 | Complementary k-NN matching and backtest | Not started |
| 5 | On-device Alliance Selection screen | Not started |
| 6 | CI, generated results, screenshots | Not started |

PySpark (optional in the PRD) was deliberately skipped.

## Problem

At alliance selection, a captain picks partners from about 40 teams with little time and
noisy information. Ranking by raw score rewards robots that duplicate what the captain
already does. The goal is to rank partners by how well they **fill the captain's gaps**,
using scouting data and public match data, and to check honestly whether that beats simpler
baselines on past events.

## Repo layout

```
app/                    Android app (Java, SQLite)
analytics/
  tba.py                TBA API v3 client with on-disk cache
  data.py               normalize matches and alliances
tests/                  pytest tests; fixtures/ are SYNTHETIC (see tests/fixtures/README.md)
reports/                generated tables (data_counts.md / .json after `make data`)
Makefile                setup, data, test
pyproject.toml          pinned Python dependencies
```

Planned, not yet present: `opr.py`, `features.py`, `matching.py`, `backtest.py`,
`export.py`, and the Android Alliance Selection screen.

## Android app (Milestone 1)

Scouts step through five screens per match: pre-match (team, match), autonomous,
teleop, post-match, then save/export.

- One `MatchRecord` object is carried through the whole flow. Back-navigation keeps entered
  data, and the record survives rotation and process death. This replaces the old public
  static fields.
- Every wheel checkbox combination maps to a defined value (bitmask: none 0, spin 1,
  color 2, both 3).
- New fields: **defense rating** (0 none, 1 light, 2 medium, 3 heavy, scout-judged) and
  **defended against**.
- Team and match number are required, counts must be non-negative, outcome is chosen from
  W/D/L, and **total points are calculated** from components (scoring constants are in
  `MatchRecord.java` and should be checked against the game manual).
- SQLite schema v2 migrates v1 data in place instead of dropping the table.
- No runtime permissions. **Export** goes through the Android share sheet, **import** uses
  the system file picker. Importing skips exact duplicate rows, so several scouting tablets
  can be merged on one device.
- Toolchain: AGP 8.5.2, Gradle 8.7, compileSdk/targetSdk 34, min SDK 24, Java 17.

Run the tests (needs Android Studio / the Android SDK):

```
./gradlew testDebugUnitTest            # MatchRecord and CSV codec
./gradlew connectedDebugAndroidTest    # Espresso scouting-flow test (device or emulator)
```

Known limitations: going Back from the data screen and submitting again saves a second
copy of the match; the v1 to v2 database migration has not been tested on a real v1 file.

## Data (Milestone 2)

Source: [The Blue Alliance API v3](https://www.thebluealliance.com/apidocs/v3). Target
seasons are 2020 and 2021 (*Infinite Recharge*). 2021 was mostly the at-home challenge, so
few or no usable events are expected from it.

```
export TBA_API_KEY=your_free_key     # never commit this
make setup
make data
```

`make data` caches raw JSON in `data/cache/` (git-ignored), normalizes it into
`data/processed/{events,matches,alliances}.csv`, and writes event, match and alliance counts
to `reports/data_counts.md` and `.json`. Event types pulled: regional, district, district
championship, and championship division and finals; offseason, preseason and festival
events are excluded.

Qualification (`qm`) and playoff (`ef`, `qf`, `sf`, `f`) matches are separated by
`qual_matches()` and `playoff_matches()`. Models may only be fit on qualification data;
playoff matches are outcomes for the backtest. A test checks the two sets are disjoint and
cover every played match.

Counts: **not yet available.** They will appear here from `reports/data_counts.md` after the
first real run. The score-breakdown field names (`autoPoints`, `initLineRobot1`, ...) follow
the documented TBA schema and have not yet been verified against a real pull.

## Methods (planned, Milestones 3 to 5)

**OPR.** For one event, each row of the design matrix `A` marks the three robots on one
alliance in one qualification match, and `s` holds that alliance's score. Solve by least
squares:

```
A x ≈ s          x = argmin ||A x − s||²
```

Component OPRs (auto, teleop, endgame points) use the same `A`.

**Ridge OPR.** Early in an event there are few matches per robot and plain OPR is noisy.
Shrink it:

```
x_ridge = (AᵀA + λI)⁻¹ Aᵀ s
```

with λ chosen by time-ordered cross-validation inside past events, so there is no
look-ahead. Evaluation: fit on the first *k* qualification matches, predict the next
matches' alliance scores, and compare mean-score average, OPR and ridge OPR by mean
absolute error at several *k*.

**Small-sample shrinkage for scouting rates.** A rate observed over *n* matches is pulled
toward the event mean with weight `n / (n + m)`, where *m* is a tunable prior strength.
Every feature is z-scored within the event before distances are computed.

**Complementary k-NN.** Feature weights are learned by regressing playoff alliance
performance on the sum of its members' feature vectors across historical events. The target
partner profile is the average feature vector of historical winning alliances minus the
captain's own vector, clipped at zero. Candidates are ranked by weighted Euclidean distance
to that target (smaller is better). Plain k-NN on the captain's vector would return similar
robots; the target vector is what makes the match complementary. The second pick recomputes
the target after the first pick is added. Baselines: rank by plain OPR, and rank by seeding.

**Backtest.** For each historical event, rebuild everything from qualification data only
and simulate each captain's first pick. Metrics: Spearman correlation with actual playoff
performance, top-3 hit rate, and predicted strength gap versus the real choice, each with
bootstrap 95% confidence intervals over events. The result will be reported honestly even
if complementary k-NN does not beat plain OPR.

## Results

None yet. This section will be filled with tables generated from `reports/` once
Milestones 3 and 4 are implemented and run on real data.

## Screenshots

Not yet available. The Alliance Selection screen does not exist yet.

## Limitations

- Milestones 3 to 6 are not implemented; nothing above under "Methods" has been run.
- The Android build and Espresso test have not been run on a device or emulator.
- Total-point scoring constants in the app are assumptions pending a check against the
  game manual.
- OPR assumes alliance scores are the sum of independent robot contributions, which
  ignores synergy and defense; defense enters only through scouting data.
- Scouting features exist only for events scouted with this app, so the historical
  backtest can use TBA-derived features only.

## Honest provenance

The app was built in high school and used at competition. The modeling layer was added
afterwards and is validated only on historical data, so it cannot be credited for any past
event result.
