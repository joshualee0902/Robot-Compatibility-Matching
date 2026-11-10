"""Export learned parameters for the Android app (FR8) and the Python reference session.

model_params.json (schema_version 1)
    features          ordered feature names the weights/profile refer to
    weights           non-negative distance weights (sum to 1)
    coefficients      raw regression coefficients (informational)
    winning_profile   mean summed 3-robot z-vector of winning alliances
    scouting_prior_strength   m in the shrinkage weight n / (n + m)
    normalization     z-score within the event, population std, constant column -> 0
    alliance_size     3
    source / trained_on       provenance; "fitted" only when written by `make export`

`reference_session` is the Python reference the Java engine is tested against (parity fixtures).
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd

from analytics.features import DEFAULT_PRIOR_STRENGTH, SCOUTING_FEATURES, TBA_FEATURES, assemble
from analytics.matching import Draft

SCHEMA_VERSION = 1
KNOWN_FEATURES = tuple(TBA_FEATURES) + tuple(SCOUTING_FEATURES)
NORMALIZATION = {"method": "zscore", "scope": "event", "std": "population",
                 "constant_column_value": 0.0, "constant_std_threshold": 1e-12}
APP_ASSET = "app/src/main/assets/model_params.json"


def build_params(weights: dict, trained_on: dict, source: str = "fitted",
                 m: float = DEFAULT_PRIOR_STRENGTH) -> dict:
    p = {
        "schema_version": SCHEMA_VERSION,
        "source": source,
        "features": list(weights["features"]),
        "weights": [float(x) for x in weights["distance_weights"]],
        "coefficients": [float(x) for x in weights["coefficients"]],
        "winning_profile": [float(x) for x in weights["winning_profile"]],
        "scouting_prior_strength": float(m),
        "normalization": NORMALIZATION,
        "alliance_size": 3,
        "trained_on": trained_on,
    }
    validate_params(p)
    return p


def validate_params(p: dict) -> None:
    if p.get("schema_version") != SCHEMA_VERSION:
        raise ValueError(f"unsupported schema_version {p.get('schema_version')}")
    n = len(p["features"])
    if any(f not in KNOWN_FEATURES for f in p["features"]):
        raise ValueError("unknown feature name")
    if len(p["weights"]) != n or len(p["winning_profile"]) != n:
        raise ValueError("weights / winning_profile length must match features")
    if min(p["weights"]) < 0:
        raise ValueError("weights must be non-negative")
    if p.get("alliance_size") != 3:
        raise ValueError("alliance_size must be 3")


def write_params(path, params: dict) -> None:
    validate_params(params)
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(params, indent=2, sort_keys=True) + "\n")


def snapshot_frame(ratings: pd.DataFrame, event_key: str) -> pd.DataFrame:
    """Raw (un-z-scored) per-team ratings for the app's TBA snapshot import."""
    out = ratings[list(TBA_FEATURES)].copy()
    out.insert(0, "event_key", event_key)
    return out.reset_index().rename(columns={"index": "team"})


# ---------------------------------------------------------------- reference session
def _num(x):
    return None if x is None or (isinstance(x, float) and np.isnan(x)) else float(x)


def reference_session(params: dict, ratings: pd.DataFrame, scouting: pd.DataFrame | None,
                      captain: int, pool, actions: list) -> dict:
    """Run a draft with the Python reference implementation.
    actions: {"op": "recommend", "n": k} | {"op": "pick", "team": t} | {"op": "decline", "team": t}.
    Returns the z-scored feature matrix and one result per 'recommend'."""
    validate_params(params)
    feats = assemble(ratings, scouting, params["scouting_prior_strength"], names=params["features"])
    draft = Draft(feats, np.array(params["weights"]), np.array(params["winning_profile"]), captain, list(pool))
    results = []
    for a in actions:
        if a["op"] == "pick":
            draft.pick(a["team"])
        elif a["op"] == "decline":
            draft.decline(a["team"])
        elif a["op"] == "recommend":
            r = draft.recommend(a.get("n"))
            contrib = [c for c in r.columns if c.startswith("contrib_")]
            results.append({
                "target": [float(x) for x in draft.target()],
                "ranking": [{"team": int(t), "distance": float(row["distance"]),
                             "contrib": [float(row[c]) for c in contrib]} for t, row in r.iterrows()],
            })
        else:
            raise ValueError(a["op"])
    return {"features": [{"team": int(t), "z": [float(x) for x in row]} for t, row in feats.iterrows()],
            "results": results}


# ------------------------------------------------------------------------- CLI
def main(argv=None):
    from analytics.backtest import final_weights, prepare_events

    p = argparse.ArgumentParser(description="Write model_params.json (and optionally an event snapshot CSV)")
    p.add_argument("--data", default="data/processed")
    p.add_argument("--out", default=APP_ASSET)
    p.add_argument("--snapshot", metavar="EVENT_KEY", help="also write reports/snapshot_<EVENT_KEY>.csv")
    p.add_argument("--report", default="reports")
    args = p.parse_args(argv)

    d = Path(args.data)
    needed = ["events", "matches", "alliances", "rankings"]
    missing = [n for n in needed if not (d / f"{n}.csv").exists()]
    if missing:
        raise SystemExit(f"Missing {', '.join(m + '.csv' for m in missing)} in {d}. Run `make data` first (needs TBA_API_KEY).")
    events, matches, alliances, rankings = (pd.read_csv(d / f"{n}.csv") for n in needed)
    bundles = prepare_events(matches, events, alliances, rankings)
    w = final_weights(bundles)
    if w is None:
        raise SystemExit("Not enough completed events with playoff results to fit weights.")
    trained_on = {"n_events": len(bundles), "n_alliances": w["n_alliances"],
                  "n_winning_alliances": w["n_winning_alliances"],
                  "first_event_date": min(b.start for b in bundles), "last_event_date": max(b.start for b in bundles)}
    write_params(args.out, build_params(w, trained_on))
    print(f"wrote {args.out}")

    if args.snapshot:
        match = [b for b in bundles if b.key == args.snapshot]
        if not match:
            raise SystemExit(f"event {args.snapshot} not found among usable events")
        out = Path(args.report) / f"snapshot_{args.snapshot}.csv"
        out.parent.mkdir(parents=True, exist_ok=True)
        snapshot_frame(match[0].ratings, args.snapshot).to_csv(out, index=False)
        print(f"wrote {out}")


if __name__ == "__main__":
    main()
