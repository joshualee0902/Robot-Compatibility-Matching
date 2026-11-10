"""Generates the SYNTHETIC parity fixtures shared by pytest and the Java JUnit tests.

Each fixture holds: model params, a TBA snapshot (raw ratings), scouting rows, a draft script,
and the expected output of the Python reference. The Java engine must reproduce the expected
output. Inputs are random but seeded; none of this is real data.

    python tests/parity_cases.py --write      # regenerate tests/fixtures/parity/*.json
"""
import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from analytics import export  # noqa: E402

OUT = Path(__file__).parent / "fixtures" / "parity"
TBA = ["auto_opr", "teleop_opr", "endgame_opr"]
SCOUT = ["defense", "climb_rate", "bricked_rate", "foul_rate"]


def _params(features, rng):
    w = rng.uniform(0.1, 1, len(features))
    return {"schema_version": 1, "source": "synthetic-test-fixture", "features": features,
            "weights": [round(float(x), 6) for x in w / w.sum()],
            "coefficients": [round(float(x), 6) for x in rng.normal(size=len(features))],
            "winning_profile": [round(float(x), 6) for x in rng.normal(1.0, 0.6, len(features))],
            "scouting_prior_strength": 5.0, "normalization": export.NORMALIZATION,
            "alliance_size": 3, "trained_on": {"synthetic": True}}


def _ratings(rng, teams, nan_auto_for=()):
    df = pd.DataFrame({"auto_opr": rng.uniform(2, 15, len(teams)).round(4),
                       "teleop_opr": rng.uniform(10, 40, len(teams)).round(4),
                       "endgame_opr": rng.uniform(0, 20, len(teams)).round(4)},
                      index=pd.Index(teams, name="team"))
    for t in nan_auto_for:
        df.loc[t, "auto_opr"] = np.nan
    return df


def _scouting(rng, teams, n_rows):
    rows = []
    for _ in range(n_rows):
        rows.append({"team_num": int(rng.choice(teams)), "defense_rating": int(rng.integers(0, 4)),
                     "climbed": int(rng.integers(0, 2)), "bricked": int(rng.random() < 0.1),
                     "fouls": int(rng.integers(0, 4))})
    return pd.DataFrame(rows)


def _case(name, seed, features, n_teams=14, scouting_rows=0, nan_auto_for=()):
    rng = np.random.default_rng(seed)
    teams = sorted(int(t) for t in rng.choice(np.arange(100, 400), n_teams, replace=False))
    params = _params(features, rng)
    ratings = _ratings(rng, teams, [teams[3]] if nan_auto_for else [])
    scouting = _scouting(rng, teams[:-3], scouting_rows) if scouting_rows else None   # last 3 teams unscouted
    captain = teams[0]
    pool = [t for t in teams if t != captain] + [999]      # 999: not in snapshot? removed below
    pool = [t for t in teams if t != captain]
    first, second, third = teams[5], teams[2], teams[7]
    actions = [{"op": "recommend", "n": 5}, {"op": "decline", "team": first}, {"op": "recommend", "n": 5},
               {"op": "pick", "team": second}, {"op": "recommend"}, {"op": "decline", "team": third},
               {"op": "recommend", "n": 3}]
    ref = export.reference_session(params, ratings, scouting, captain, pool, actions)
    return {
        "name": name, "params": params,
        "snapshot": [{"team": int(t), **{c: export._num(ratings.loc[t, c]) for c in TBA}} for t in teams],
        "scouting": [] if scouting is None else scouting.to_dict("records"),
        "captain": captain, "pool": pool, "actions": actions,
        "expected_features": ref["features"], "expected_results": ref["results"],
    }


def all_cases():
    return [
        _case("tba_only", 1, TBA),
        _case("with_scouting", 2, TBA + SCOUT, scouting_rows=60),
        _case("scouting_requested_but_absent", 3, TBA + ["defense", "climb_rate"], scouting_rows=0),
        _case("nan_rating", 4, TBA, nan_auto_for=(True,)),
        _case("subset_and_order", 5, ["endgame_opr", "defense", "auto_opr"], scouting_rows=40),
    ]


def render(case) -> str:
    return json.dumps(case, indent=1, sort_keys=True) + "\n"


if __name__ == "__main__":
    if "--write" in sys.argv:
        OUT.mkdir(parents=True, exist_ok=True)
        for c in all_cases():
            (OUT / f"{c['name']}.json").write_text(render(c))
            print("wrote", c["name"])
    else:
        print(__doc__)
