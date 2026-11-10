"""Backtest on historical events (FR7).

For every event E (in date order) everything is rebuilt using ONLY:
  * E's own qualification matches (features, plain OPR, seeding), and
  * events that started strictly BEFORE E (lambda, feature weights, winning profile).
E's playoff results are used only as the thing being predicted.

Simulation. Captains pick in seed order; captain i's pool is every team that is not one of
the event's alliance captains and was not already taken by an earlier captain's actual first
pick. Three methods rank that pool: complementary k-NN, plain OPR, and event seeding.

Metrics (per event, then averaged over events with bootstrap 95% CIs):
  spearman  Spearman correlation, across the event's alliances, between the method's
            percentile rating of the actual first pick (1 = top of the pool) and the
            alliance's actual playoff performance (mean playoff score per match).
  top3      fraction of captains whose actual first pick is in the method's top 3.
  gap       mean over captains of  strength(method's #1 recommendation) - strength(actual pick),
            where strength is the pick's centered ridge OPR. Neutral-ish but it favours the
            OPR baseline by construction (the OPR baseline's #1 maximises plain OPR).
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from scipy.stats import spearmanr

from analytics.features import TBA_FEATURES, feature_table
from analytics.matching import (distance_weights, fit_weights, rank_by_value, rank_candidates,
                                target_profile, winning_profile)
from analytics.opr import (DEFAULT_HORIZON, DEFAULT_LAMS, alliance_scores, design_matrix,
                           evaluate_event_k, event_teams, fit_opr, fit_ridge, sort_qual)

DEFAULT_LAM = 3.0          # fixed constant used only when there is no earlier event to tune on
METHODS = ("knn", "opr", "seeding")
BOOTSTRAP_SEED = 0
N_BOOT = 5000


# ------------------------------------------------------------------- statistics
def bootstrap_ci(values, n_boot: int = N_BOOT, seed: int = BOOTSTRAP_SEED, level: float = 0.95):
    """Mean and percentile bootstrap CI over events (resampling events with replacement)."""
    v = np.asarray([x for x in values if np.isfinite(x)], dtype=float)
    if len(v) == 0:
        return float("nan"), float("nan"), float("nan"), 0
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, len(v), size=(n_boot, len(v)))
    means = v[idx].mean(axis=1)
    lo, hi = np.percentile(means, [(1 - level) / 2 * 100, (1 + level) / 2 * 100])
    return float(v.mean()), float(lo), float(hi), len(v)


# ------------------------------------------------------------------ preparation
def opr_table(qm: pd.DataFrame, lam: float) -> pd.DataFrame:
    """plain_opr and centered ridge_opr per team from the given qualification matches."""
    qm = sort_qual(qm)
    teams = event_teams(qm)
    A, s = design_matrix(qm, teams), alliance_scores(qm)
    return pd.DataFrame({"plain_opr": fit_opr(A, s), "ridge_opr": fit_ridge(A, s, lam)},
                        index=pd.Index(teams, name="team"))


def alliance_members(row) -> list[int]:
    get = row.get if hasattr(row, "get") else (lambda c: getattr(row, c))
    return [int(get(c)) for c in ("captain", "pick1", "pick2", "pick3") if pd.notna(get(c))]


def alliance_performance(playoff: pd.DataFrame, alliances: pd.DataFrame) -> dict:
    """Seed -> mean playoff score per match, matching each playoff alliance side to the
    event alliance it shares at least 2 robots with."""
    sums, counts = {}, {}
    members = {int(r.seed): set(alliance_members(r)) for r in alliances.itertuples()}
    for r in playoff.itertuples():
        for side in ("red", "blue"):
            trio = {getattr(r, f"{side}{i}") for i in (1, 2, 3)}
            best = max(members, key=lambda sd: len(trio & members[sd]), default=None)
            if best is None or len(trio & members[best]) < 2:
                continue
            sums[best] = sums.get(best, 0.0) + float(getattr(r, f"{side}_score"))
            counts[best] = counts.get(best, 0) + 1
    return {sd: sums[sd] / counts[sd] for sd in sums}


class EventBundle:
    """Everything computed for one event from its qualification matches only."""

    def __init__(self, key, start, qm, lam, alliances, playoff, rankings):
        self.key, self.start, self.lam = key, start, lam
        self.feats = feature_table(qm, lam)
        self.oprs = opr_table(qm, lam)
        self.rank = rankings.set_index("team")["rank"] if len(rankings) else pd.Series(dtype=float)
        self.alliances = alliances.sort_values("seed")
        self.perf = alliance_performance(playoff, self.alliances)

    def sum_vector(self, row):
        """Summed feature vector of captain + two picks, or None if any is missing."""
        trio = [row["captain"], row["pick1"], row["pick2"]]
        if any(pd.isna(t) or int(t) not in self.feats.index for t in trio):
            return None
        return sum(self.feats.loc[int(t)].to_numpy(float) for t in trio)

    def training_rows(self):
        """(X, y, winner_sum_vectors) for fitting weights / the winning profile."""
        X, y, win = [], [], []
        for _, row in self.alliances.iterrows():
            v = self.sum_vector(row)
            if v is None or int(row["seed"]) not in self.perf:
                continue
            X.append(v)
            y.append(self.perf[int(row["seed"])])
            if row.get("playoff_status") == "won":
                win.append(v)
        return X, y, win


def prepare_events(matches, events, alliances, rankings, lams=DEFAULT_LAMS, horizon=DEFAULT_HORIZON):
    """Build an EventBundle per event, in date order. lambda for each event is chosen only from
    events that started strictly before it (ridge MAE predicting the last `horizon` matches)."""
    from analytics.data import playoff_matches, qual_matches

    qual, playoff = qual_matches(matches), playoff_matches(matches)
    meta = events.dropna(subset=["start_date"]).sort_values(["start_date", "event_key"])
    cache, bundles = {}, []
    for ev in meta.itertuples():
        qm = qual[qual["event_key"] == ev.event_key]
        a = alliances[alliances["event_key"] == ev.event_key]
        if len(qm) < horizon + 5 or a.empty:
            continue
        past = [v for (start, v) in cache.values() if start < ev.start_date]
        if past:
            mean_mae = pd.DataFrame(past).mean()
            lam = float(mean_mae.idxmin())
        else:
            lam = DEFAULT_LAM
        bundles.append(EventBundle(ev.event_key, ev.start_date, qm, lam, a,
                                   playoff[playoff["event_key"] == ev.event_key],
                                   rankings[rankings["event_key"] == ev.event_key]))
        n = len(qm)
        cache[ev.event_key] = (ev.start_date, evaluate_event_k(qm, n - horizon, horizon, lams)["ridge_opr"])
    return bundles


# -------------------------------------------------------------------- training
def fit_from_events(past: list, alpha: float = 1.0):
    """Feature weights, regression coefficients and winning profile from earlier events.
    Returns None if there is nothing to learn from."""
    X, y, win = [], [], []
    for b in past:
        bx, by, bw = b.training_rows()
        X += bx; y += by; win += bw
    if len(y) < 4 or not win:
        return None
    coef, intercept = fit_weights(np.array(X), np.array(y), alpha)
    return {"coef": coef, "intercept": intercept, "weights": distance_weights(coef),
            "S": winning_profile(np.array(win)), "n_alliances": len(y), "n_winners": len(win)}


# ------------------------------------------------------------------- simulation
def percentile_of(order: list, team) -> float:
    """1.0 = ranked first in the pool, 0.0 = ranked last."""
    if len(order) <= 1:
        return 1.0
    return 1.0 - order.index(team) / (len(order) - 1)


def simulate_event(b: EventBundle, model: dict) -> dict | None:
    """Metrics for one event given a model fit on earlier events only."""
    captains = {int(c) for c in b.alliances["captain"].dropna()}
    taken, rows = set(), []
    for _, a in b.alliances.iterrows():
        c, p = a["captain"], a["pick1"]
        if pd.isna(c) or pd.isna(p) or int(c) not in b.feats.index or int(p) not in b.feats.index:
            continue
        c, p = int(c), int(p)
        pool = [t for t in b.feats.index if t not in captains and t not in taken]
        taken.add(p)
        if p not in pool or len(pool) < 3:
            continue
        target = target_profile(model["S"], b.feats.loc[c].to_numpy(float), [], 2)
        orders = {
            "knn": list(rank_candidates(target, b.feats, model["weights"], pool).index),
            "opr": rank_by_value(b.oprs["plain_opr"], pool, True),
            "seeding": rank_by_value(b.rank, pool, False),
        }
        rec = {"seed": int(a["seed"])}
        strength = b.oprs["ridge_opr"]
        for m, order in orders.items():
            rec[f"pct_{m}"] = percentile_of(order, p)
            rec[f"top3_{m}"] = float(p in order[:3])
            rec[f"gap_{m}"] = float(strength[order[0]] - strength[p])
        rows.append(rec)
    if not rows:
        return None
    df = pd.DataFrame(rows)
    out = {"event_key": b.key, "start_date": b.start, "lam": b.lam, "n_captains": len(df)}
    perf = df["seed"].map(b.perf)
    for m in METHODS:
        out[f"top3_{m}"] = df[f"top3_{m}"].mean()
        out[f"gap_{m}"] = df[f"gap_{m}"].mean()
        ok = perf.notna()
        if ok.sum() >= 4 and df.loc[ok, f"pct_{m}"].nunique() > 1 and perf[ok].nunique() > 1:
            out[f"spearman_{m}"] = float(spearmanr(df.loc[ok, f"pct_{m}"], perf[ok])[0])
        else:
            out[f"spearman_{m}"] = np.nan
    return out


def run_backtest(bundles: list, min_train_events: int = 3):
    """Walk events in date order; each event is modeled from earlier events only."""
    rows = []
    for i, b in enumerate(bundles):
        past = [p for p in bundles[:i] if p.start < b.start]
        if len(past) < min_train_events:
            continue
        model = fit_from_events(past)
        if model is None:
            continue
        res = simulate_event(b, model)
        if res is not None:
            res.update({f"w_{f}": w for f, w in zip(TBA_FEATURES, model["weights"])})
            rows.append(res)
    return pd.DataFrame(rows)


def summarize(detail: pd.DataFrame) -> pd.DataFrame:
    """Mean and bootstrap 95% CI over events for each metric and method, plus paired
    knn - opr and knn - seeding differences."""
    out = []
    for metric in ("spearman", "top3", "gap"):
        for m in METHODS:
            mean, lo, hi, n = bootstrap_ci(detail[f"{metric}_{m}"])
            out.append({"metric": metric, "method": m, "mean": mean, "ci_low": lo, "ci_high": hi, "n_events": n})
        for other in ("opr", "seeding"):
            d = detail[f"{metric}_knn"] - detail[f"{metric}_{other}"]
            mean, lo, hi, n = bootstrap_ci(d)
            out.append({"metric": metric, "method": f"knn - {other}", "mean": mean, "ci_low": lo,
                        "ci_high": hi, "n_events": n})
    return pd.DataFrame(out)


def final_weights(bundles: list) -> dict | None:
    """Weights fitted on ALL events. Descriptive (and what the app will use); the backtest itself
    never uses this, since it would include each event's own playoffs."""
    model = fit_from_events(bundles)
    if model is None:
        return None
    return {"features": list(TBA_FEATURES), "coefficients": model["coef"].tolist(),
            "intercept": model["intercept"], "distance_weights": model["weights"].tolist(),
            "winning_profile": model["S"].tolist(), "n_alliances": model["n_alliances"],
            "n_winning_alliances": model["n_winners"]}


def write_report(detail, summary, weights, report_dir="reports"):
    rep = Path(report_dir)
    rep.mkdir(parents=True, exist_ok=True)
    detail.to_csv(rep / "backtest_detail.csv", index=False)
    summary.to_csv(rep / "backtest_results.csv", index=False)
    lines = ["| metric | method | mean | 95% CI | events |", "|---|---|---|---|---|"]
    for r in summary.itertuples():
        lines.append(f"| {r.metric} | {r.method} | {r.mean:.3f} | [{r.ci_low:.3f}, {r.ci_high:.3f}] | {r.n_events} |")
    (rep / "backtest_results.md").write_text("\n".join(lines) + "\n")
    if weights is not None:
        (rep / "fitted_weights.json").write_text(json.dumps(weights, indent=2) + "\n")
        wl = ["| feature | regression coefficient | distance weight | winning-profile component |", "|---|---|---|---|"]
        for f, c, w, s in zip(weights["features"], weights["coefficients"], weights["distance_weights"], weights["winning_profile"]):
            wl.append(f"| {f} | {c:.4f} | {w:.4f} | {s:.4f} |")
        (rep / "fitted_weights.md").write_text("\n".join(wl) + "\n")


def main(argv=None):
    p = argparse.ArgumentParser(description="Backtest complementary k-NN vs plain OPR vs seeding")
    p.add_argument("--data", default="data/processed")
    p.add_argument("--report", default="reports")
    p.add_argument("--min-train-events", type=int, default=3)
    args = p.parse_args(argv)
    d = Path(args.data)
    needed = ["events", "matches", "alliances", "rankings"]
    missing = [n for n in needed if not (d / f"{n}.csv").exists()]
    if missing:
        raise SystemExit(f"Missing {', '.join(m + '.csv' for m in missing)} in {d}. Run `make data` first (needs TBA_API_KEY).")
    events, matches, alliances, rankings = (pd.read_csv(d / f"{n}.csv") for n in needed)
    bundles = prepare_events(matches, events, alliances, rankings)
    detail = run_backtest(bundles, args.min_train_events)
    if detail.empty:
        raise SystemExit("No event had enough earlier events and complete alliances to backtest.")
    write_report(detail, summarize(detail), final_weights(bundles), args.report)
    print((Path(args.report) / "backtest_results.md").read_text())


if __name__ == "__main__":
    main()
