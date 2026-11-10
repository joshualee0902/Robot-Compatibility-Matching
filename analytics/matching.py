"""Complementary k-NN partner matching (FR6) and baselines.

Alliance model. An alliance is captain + 2 picks. The *winning profile* S is the average of
the SUMMED z-scored feature vectors of historical winning alliances (3 robots each).
Given a captain c and picks already made P, the remaining gap is S - c - sum(P), to be filled
by the remaining picks, so the per-robot TARGET is

    target = clip( (S - c - sum(P)) / picks_remaining , 0, None )

Candidates are ranked by weighted Euclidean distance to the target (smaller is better).
Plain k-NN on the captain's own vector would return robots similar to the captain; the
target is what makes the match complementary. After the first pick, the target is recomputed
with that pick added, so the second recommendation fills what is still missing.
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.linear_model import Ridge

ALLIANCE_SIZE = 3


def fit_weights(X: np.ndarray, y: np.ndarray, alpha: float = 1.0):
    """Regress playoff alliance performance y on the SUM of its members' feature vectors X.
    Returns (coefficients, intercept)."""
    model = Ridge(alpha=alpha).fit(X, y)
    return model.coef_.copy(), float(model.intercept_)


def distance_weights(coef: np.ndarray) -> np.ndarray:
    """Non-negative distance weights from regression coefficients: negatives -> 0, then sum to 1.
    If nothing is positive, fall back to equal weights."""
    w = np.clip(np.asarray(coef, dtype=float), 0, None)
    if w.sum() <= 0:
        return np.full(len(w), 1.0 / len(w))
    return w / w.sum()


def winning_profile(alliance_sums: np.ndarray) -> np.ndarray:
    """Mean of summed 3-robot feature vectors of winning alliances (rows)."""
    return np.asarray(alliance_sums, dtype=float).mean(axis=0)


def target_profile(S, captain, picked, picks_remaining: int) -> np.ndarray:
    gap = np.asarray(S, float) - np.asarray(captain, float)
    for p in picked:
        gap = gap - np.asarray(p, float)
    return np.clip(gap / max(picks_remaining, 1), 0.0, None)


def rank_candidates(target, features: pd.DataFrame, weights, pool) -> pd.DataFrame:
    """Rank `pool` teams by weighted Euclidean distance to `target`. The returned frame has
    `distance` and one `contrib_<feature>` column per feature (weight * squared gap), so
    distance == sqrt(sum of contributions) and each candidate's ranking can be explained."""
    pool = list(pool)
    X = features.loc[pool].to_numpy(dtype=float)
    w = np.asarray(weights, float)
    contrib = w * (X - np.asarray(target, float)) ** 2
    out = pd.DataFrame(contrib, index=pd.Index(pool, name="team"),
                       columns=[f"contrib_{c}" for c in features.columns])
    out.insert(0, "distance", np.sqrt(contrib.sum(axis=1)))
    # stable tie-break by team number so results are reproducible
    return out.reset_index().sort_values(["distance", "team"], kind="mergesort").set_index("team")


def rank_by_value(values: pd.Series, pool, higher_is_better: bool = True) -> list:
    """Baseline ranking (plain OPR, or seeding with higher_is_better=False). Ties by team number."""
    v = values.reindex(list(pool))
    key = (-v if higher_is_better else v).fillna(np.inf)  # unknown value sorts last
    order = pd.DataFrame({"k": key.to_numpy(), "team": list(pool)}).sort_values(["k", "team"], kind="mergesort")
    return list(order["team"])


class Draft:
    """Live draft state for one captain: mark teams picked (by us) or declined/taken (by
    others), and the recommendation list updates immediately."""

    def __init__(self, features: pd.DataFrame, weights, S, captain: int, pool):
        self.features, self.weights, self.S = features, np.asarray(weights, float), np.asarray(S, float)
        self.captain = captain
        self.pool = [t for t in pool if t != captain]
        self.picked: list[int] = []

    def pick(self, team: int):
        """Our alliance takes `team`."""
        self.pool.remove(team)
        self.picked.append(team)

    def decline(self, team: int):
        """`team` is gone (declined or taken by another alliance); it does not join our alliance."""
        self.pool.remove(team)

    def target(self) -> np.ndarray:
        remaining = ALLIANCE_SIZE - 1 - len(self.picked)
        return target_profile(self.S, self.features.loc[self.captain].to_numpy(float),
                              [self.features.loc[t].to_numpy(float) for t in self.picked], remaining)

    def recommend(self, n: int | None = None) -> pd.DataFrame:
        ranked = rank_candidates(self.target(), self.features, self.weights, self.pool)
        return ranked if n is None else ranked.head(n)
