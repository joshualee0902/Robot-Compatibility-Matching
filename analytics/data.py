"""Normalize raw TBA JSON into `matches` and `alliances` tables (pandas DataFrames).

Qualification matches (comp_level 'qm') feed the models. Playoff matches
(ef/qf/sf/f) are used only as outcomes in the backtest. Use `qual_matches` and
`playoff_matches` so the two never get mixed.

NOTE: score-breakdown field names (autoPoints, initLineRobot1, ...) follow the TBA v3
2020 schema as documented; verify them against a real pull. Missing fields become NaN
rather than guesses.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import pandas as pd

QUAL_LEVEL = "qm"
PLAYOFF_LEVELS = ("ef", "qf", "sf", "f")
LEVEL_ORDER = {"qm": 0, "ef": 1, "qf": 2, "sf": 3, "f": 4}

# TBA event_type: 0 regional, 1 district, 2 district championship, 3 championship
# division, 4 championship finals, 5 district championship division.
# Offseason (99), preseason (100) and festival (6) events are excluded by default.
DEFAULT_EVENT_TYPES = (0, 1, 2, 3, 4, 5)

# our column suffix -> TBA score_breakdown field (alliance level)
COMPONENT_FIELDS = {
    "auto_points": "autoPoints",
    "teleop_points": "teleopPoints",
    "endgame_points": "endgamePoints",
    "foul_points": "foulPoints",
    "control_panel_points": "controlPanelPoints",
    "auto_cell_points": "autoCellPoints",
    "teleop_cell_points": "teleopCellPoints",
    "total_points": "totalPoints",
}

_TEAM_RE = re.compile(r"^frc(\d+)$")


def team_number(team_key: str) -> int:
    m = _TEAM_RE.match(team_key)
    if not m:
        raise ValueError(f"unexpected team key: {team_key!r}")
    return int(m.group(1))


def _match_time(m: dict):
    for field in ("actual_time", "time", "predicted_time"):
        if m.get(field):
            return m[field]
    return None


def normalize_matches(event_key: str, raw_matches: list) -> tuple[list[dict], int]:
    """Return (rows, n_skipped). Unplayed matches (score -1) and non-3-team rows are skipped."""
    rows, skipped = [], 0
    for m in raw_matches:
        red = m["alliances"]["red"]
        blue = m["alliances"]["blue"]
        if red["score"] is None or blue["score"] is None or red["score"] < 0 or blue["score"] < 0:
            skipped += 1
            continue
        if len(red["team_keys"]) != 3 or len(blue["team_keys"]) != 3:
            skipped += 1
            continue
        row = {
            "event_key": event_key,
            "year": int(event_key[:4]),
            "match_key": m["key"],
            "comp_level": m["comp_level"],
            "set_number": m.get("set_number"),
            "match_number": m["match_number"],
            "time": _match_time(m),
            "winning_alliance": m.get("winning_alliance") or "",
            "red_score": red["score"],
            "blue_score": blue["score"],
        }
        breakdown = m.get("score_breakdown") or {}
        for side, alliance in (("red", red), ("blue", blue)):
            sb = breakdown.get(side) or {}
            for i, key in enumerate(alliance["team_keys"], start=1):
                row[f"{side}{i}"] = team_number(key)
                row[f"{side}_init_line_{i}"] = sb.get(f"initLineRobot{i}")
                row[f"{side}_endgame_{i}"] = sb.get(f"endgameRobot{i}")
            for ours, theirs in COMPONENT_FIELDS.items():
                row[f"{side}_{ours}"] = sb.get(theirs)
        rows.append(row)
    return rows, skipped


def normalize_alliances(event_key: str, raw_alliances: list | None) -> list[dict]:
    """One row per playoff alliance; seed is the 1-based list position (TBA lists them in seed order)."""
    rows = []
    for seed, a in enumerate(raw_alliances or [], start=1):
        picks = [team_number(k) for k in a.get("picks", [])]
        status = a.get("status") or {}
        record = status.get("record") or {}
        rows.append({
            "event_key": event_key,
            "seed": seed,
            "captain": picks[0] if len(picks) > 0 else None,
            "pick1": picks[1] if len(picks) > 1 else None,
            "pick2": picks[2] if len(picks) > 2 else None,
            "pick3": picks[3] if len(picks) > 3 else None,
            "playoff_level": status.get("level"),
            "playoff_status": status.get("status"),
            "playoff_wins": record.get("wins"),
            "playoff_losses": record.get("losses"),
            "playoff_average": status.get("playoff_average"),
        })
    return rows


def qual_matches(matches: pd.DataFrame) -> pd.DataFrame:
    """Qualification matches only: the only matches models may be fit on."""
    return matches[matches["comp_level"] == QUAL_LEVEL].copy()


def playoff_matches(matches: pd.DataFrame) -> pd.DataFrame:
    """Playoff matches only: outcomes for the backtest, never model inputs."""
    return matches[matches["comp_level"].isin(PLAYOFF_LEVELS)].copy()


def build_tables(client, years, event_types=DEFAULT_EVENT_TYPES, log=print):
    """Pull and normalize every event in `years`. Returns (events, matches, alliances, stats)."""
    events, matches, alliances = [], [], []
    stats = {"events_listed": 0, "events_with_matches": 0, "matches_skipped": 0}
    for year in years:
        for ev in sorted(client.events(year), key=lambda e: e["key"]):
            if ev.get("event_type") not in event_types:
                continue
            stats["events_listed"] += 1
            key = ev["key"]
            rows, skipped = normalize_matches(key, client.event_matches(key))
            stats["matches_skipped"] += skipped
            if not rows:
                log(f"skip {key}: no played matches")
                continue
            stats["events_with_matches"] += 1
            events.append({"event_key": key, "year": year, "name": ev.get("name"),
                           "event_type": ev.get("event_type"),
                           "start_date": ev.get("start_date")})
            matches.extend(rows)
            alliances.extend(normalize_alliances(key, client.event_alliances(key)))
            log(f"ok   {key}: {len(rows)} matches")
    return (pd.DataFrame(events), pd.DataFrame(matches), pd.DataFrame(alliances), stats)


def compute_counts(events: pd.DataFrame, matches: pd.DataFrame, alliances: pd.DataFrame, stats: dict) -> dict:
    """Counts reported in the README. Everything here is derived from the tables."""
    return {
        "events_listed": stats["events_listed"],
        "events_with_matches": int(len(events)),
        "events_per_year": {str(k): int(v) for k, v in events.groupby("year").size().items()} if len(events) else {},
        "qualification_matches": int(len(qual_matches(matches))) if len(matches) else 0,
        "playoff_matches": int(len(playoff_matches(matches))) if len(matches) else 0,
        "alliances": int(len(alliances)),
        "unplayed_or_malformed_matches_skipped": stats["matches_skipped"],
    }


def write_outputs(events, matches, alliances, counts, out_dir="data/processed", report_dir="reports"):
    out, rep = Path(out_dir), Path(report_dir)
    out.mkdir(parents=True, exist_ok=True)
    rep.mkdir(parents=True, exist_ok=True)
    events.to_csv(out / "events.csv", index=False)
    matches.to_csv(out / "matches.csv", index=False)
    alliances.to_csv(out / "alliances.csv", index=False)
    (rep / "data_counts.json").write_text(json.dumps(counts, indent=2, sort_keys=True) + "\n")
    lines = ["| Quantity | Count |", "|---|---|"]
    for k, v in counts.items():
        lines.append(f"| {k.replace('_', ' ')} | {v} |")
    (rep / "data_counts.md").write_text("\n".join(lines) + "\n")


def main(argv=None):
    from analytics.tba import TBAClient

    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--years", type=int, nargs="+", default=[2020, 2021])
    p.add_argument("--cache", default="data/cache")
    p.add_argument("--out", default="data/processed")
    p.add_argument("--report", default="reports")
    p.add_argument("--offline", action="store_true", help="use the cache only; no network")
    args = p.parse_args(argv)

    client = TBAClient(cache_dir=args.cache, offline=args.offline)
    events, matches, alliances, stats = build_tables(client, args.years)
    counts = compute_counts(events, matches, alliances, stats)
    write_outputs(events, matches, alliances, counts, args.out, args.report)
    print(json.dumps(counts, indent=2))


if __name__ == "__main__":
    main()
