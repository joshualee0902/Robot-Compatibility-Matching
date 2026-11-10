"""Robot feature vectors (FR5).

Per team, at one event, using only the qualification matches given:
  * TBA-derived: component ridge OPRs (autonomy, scoring, endgame)
  * scouting-derived, only when scouting data exists: defense rating, climb rate,
    bricked rate, foul rate. Each is shrunk toward the event mean with weight
    n / (n + m):   shrunk = w * team_value + (1 - w) * event_mean,  w = n / (n + m)
    where n = matches scouted for that team and m is the prior strength.
Every feature is then z-scored within the event, so distances are unit-free.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from analytics.opr import team_ratings

TBA_FEATURES = ("auto_opr", "teleop_opr", "endgame_opr")
SCOUTING_FEATURES = ("defense", "climb_rate", "bricked_rate", "foul_rate")
DEFAULT_PRIOR_STRENGTH = 5.0


def shrink(team_value, n, event_mean, m):
    """Pull a per-team value toward the event mean. n = 0 returns the event mean exactly."""
    w = n / (n + m)
    return w * team_value + (1.0 - w) * event_mean


def zscore(df: pd.DataFrame) -> pd.DataFrame:
    """Z-score each column within the table (population std). Constant or all-NaN columns -> 0."""
    mean = df.mean()
    std = df.std(ddof=0).replace(0, np.nan)
    return ((df - mean) / std).fillna(0.0)


def scouting_features(scouting: pd.DataFrame, teams, m: float = DEFAULT_PRIOR_STRENGTH) -> pd.DataFrame:
    """Shrunk scouting features for `teams`. `scouting` has one row per scouted robot-match with
    columns team_num, defense_rating, climbed, bricked, fouls (the app's CSV export)."""
    s = scouting.copy()
    s["climbed"] = s["climbed"].astype(float)
    s["bricked"] = s["bricked"].astype(float)
    cols = {"defense": "defense_rating", "climb_rate": "climbed", "bricked_rate": "bricked", "foul_rate": "fouls"}
    s = s.rename(columns={v: k for k, v in cols.items()})
    event_mean = s[list(cols)].mean()                       # match-weighted event mean
    grouped = s.groupby("team_num")[list(cols)]
    means, n = grouped.mean(), grouped.size()
    out = pd.DataFrame(index=pd.Index(teams, name="team"), columns=list(cols), dtype=float)
    for t in out.index:
        nt = float(n.get(t, 0))
        for f in cols:
            raw = means.loc[t, f] if nt > 0 else event_mean[f]
            out.loc[t, f] = shrink(raw, nt, event_mean[f], m)
    return out


def feature_table(qm: pd.DataFrame, lam: float, scouting: pd.DataFrame | None = None,
                  m: float = DEFAULT_PRIOR_STRENGTH, upto: int | None = None) -> pd.DataFrame:
    """Z-scored feature table (index = team) from the first `upto` qualification matches."""
    ratings = team_ratings(qm, lam, upto)[list(TBA_FEATURES)]
    if scouting is not None and len(scouting):
        ratings = ratings.join(scouting_features(scouting, ratings.index, m))
    return zscore(ratings)
