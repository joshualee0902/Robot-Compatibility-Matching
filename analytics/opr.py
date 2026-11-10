"""Contribution model: OPR, component OPR, ridge OPR, and the MAE-versus-k evaluation.

Setup (one event). Each qualification match gives two rows of the design matrix A, one per
alliance. A[row, team] = 1 if the team was on that alliance (so every row sums to 3), and
s[row] is that alliance's score.

    OPR            x = argmin ||A x - s||^2          (minimum-norm solution if rank-deficient)
    ridge OPR      x = (A'A + lam I)^-1 A' s          (the PRD formula; "zero prior")
    centered ridge x = mu + (A'A + lam I)^-1 A' (s - mu*A1),  mu = mean(s)/3

The centered variant shrinks each robot toward the average robot instead of toward 0, so a
robot with little data is predicted as average rather than contributing nothing. mu is
computed from the fitting rows only (no look-ahead). Both variants are evaluated.

Evaluation: for each event and each k, fit on the first k qualification matches and predict
the alliance scores of the next `horizon` matches. lam is chosen using ONLY events that
started strictly before the event being evaluated, at the same k.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd

COMPONENTS = {
    "auto_opr": "auto_points",
    "teleop_opr": "teleop_points",
    "endgame_opr": "endgame_points",
}
DEFAULT_KS = (10, 20, 30, 40, 50, 60)
DEFAULT_LAMS = (0.1, 0.3, 1.0, 3.0, 10.0, 30.0, 100.0, 300.0)
DEFAULT_HORIZON = 10

METHODS = ("mean_avg", "opr", "ridge_opr", "ridge_opr_zero_prior")


# ---------------------------------------------------------------- design matrix
def sort_qual(qm: pd.DataFrame) -> pd.DataFrame:
    """Qualification matches in the order they were played."""
    return qm.sort_values("match_number").reset_index(drop=True)


def event_teams(qm: pd.DataFrame) -> np.ndarray:
    cols = ["red1", "red2", "red3", "blue1", "blue2", "blue3"]
    return np.unique(qm[cols].to_numpy())


def design_matrix(qm: pd.DataFrame, teams: np.ndarray) -> np.ndarray:
    """2 rows per match, [m0 red, m0 blue, m1 red, ...]; columns follow `teams` (sorted)."""
    n = len(qm)
    A = np.zeros((2 * n, len(teams)))
    rows = np.arange(n)
    for side, offset in (("red", 0), ("blue", 1)):
        for i in (1, 2, 3):
            cols = np.searchsorted(teams, qm[f"{side}{i}"].to_numpy())
            A[2 * rows + offset, cols] = 1.0
    return A


def alliance_scores(qm: pd.DataFrame, suffix: str = "score") -> np.ndarray:
    """Alliance scores (or a component such as 'auto_points') in design-matrix row order."""
    s = np.empty(2 * len(qm))
    s[0::2] = qm[f"red_{suffix}"].to_numpy(dtype=float)
    s[1::2] = qm[f"blue_{suffix}"].to_numpy(dtype=float)
    return s


# ---------------------------------------------------------------------- models
def fit_opr(A: np.ndarray, s: np.ndarray) -> np.ndarray:
    return np.linalg.lstsq(A, s, rcond=None)[0]


def fit_ridge(A: np.ndarray, s: np.ndarray, lam: float, center: bool = True) -> np.ndarray:
    if len(s) == 0:
        raise ValueError("cannot fit on zero rows")
    mu = float(s.mean() / 3.0) if center else 0.0
    resid = s - mu * A.sum(axis=1)
    if lam == 0:
        dev = np.linalg.lstsq(A, resid, rcond=None)[0]
    else:
        dev = np.linalg.solve(A.T @ A + lam * np.eye(A.shape[1]), A.T @ resid)
    return mu + dev


def predict_mean_avg(A_fit: np.ndarray, s_fit: np.ndarray, A_new: np.ndarray) -> np.ndarray:
    """Baseline: each team's average alliance score so far; an alliance is predicted as the mean
    of its robots' averages. Teams not yet seen use the overall mean alliance score."""
    counts = A_fit.sum(axis=0)
    avg = np.where(counts > 0, (A_fit.T @ s_fit) / np.maximum(counts, 1), s_fit.mean())
    return (A_new @ avg) / A_new.sum(axis=1)


def team_ratings(qm: pd.DataFrame, lam: float, upto: int | None = None) -> pd.DataFrame:
    """Centered ridge OPR and component OPRs per team, using only the first `upto` qualification
    matches (all if None). These feed the feature vectors in Milestone 4/5."""
    qm = sort_qual(qm)
    teams = event_teams(qm)
    fit = qm if upto is None else qm.iloc[:upto]
    A = design_matrix(fit, teams)
    out = pd.DataFrame(index=pd.Index(teams, name="team"))
    out["opr"] = fit_ridge(A, alliance_scores(fit), lam)
    for col, comp in COMPONENTS.items():
        s = alliance_scores(fit, comp)
        ok = np.isfinite(s)
        out[col] = fit_ridge(A[ok], s[ok], lam) if ok.any() else np.nan
    return out


# ------------------------------------------------------------------ evaluation
def evaluate_event_k(qm: pd.DataFrame, k: int, horizon: int, lams) -> dict:
    """MAE of each method predicting matches k..k+horizon-1 from the first k. Nothing after
    match k is used for fitting."""
    qm = sort_qual(qm)
    teams = event_teams(qm)
    A = design_matrix(qm, teams)
    s = alliance_scores(qm)
    A_fit, s_fit = A[: 2 * k], s[: 2 * k]
    A_new, s_new = A[2 * k: 2 * (k + horizon)], s[2 * k: 2 * (k + horizon)]

    def mae(pred):
        return float(np.mean(np.abs(pred - s_new)))

    return {
        "mean_avg": mae(predict_mean_avg(A_fit, s_fit, A_new)),
        "opr": mae(A_new @ fit_opr(A_fit, s_fit)),
        "ridge_opr": {lam: mae(A_new @ fit_ridge(A_fit, s_fit, lam, True)) for lam in lams},
        "ridge_opr_zero_prior": {lam: mae(A_new @ fit_ridge(A_fit, s_fit, lam, False)) for lam in lams},
    }


def choose_lambda(grid: pd.DataFrame, event_start: str, k: int, variant: str, lams) -> float | None:
    """Pick lam by lowest mean MAE over events that started STRICTLY BEFORE `event_start`.
    `grid` has columns event_key, start_date, k, variant, lam, mae. Returns None if there is
    no earlier event."""
    past = grid[(grid["start_date"] < event_start) & (grid["k"] == k) & (grid["variant"] == variant)]
    if past.empty:
        return None
    means = past.groupby("lam")["mae"].mean()
    means = means.reindex(sorted(lams)).dropna()
    return float(means.idxmin())


def run_eval(matches: pd.DataFrame, events: pd.DataFrame, ks=DEFAULT_KS,
             horizon: int = DEFAULT_HORIZON, lams=DEFAULT_LAMS):
    """Returns (detail, summary). detail has one row per (event, k, method)."""
    start = dict(zip(events["event_key"], events["start_date"]))
    records, grid_rows = [], []
    for key, qm in matches.groupby("event_key"):
        if not isinstance(start.get(key), str):
            continue  # no start date: cannot order in time without look-ahead risk
        qm = sort_qual(qm)
        for k in ks:
            if k + horizon > len(qm):
                continue
            r = evaluate_event_k(qm, k, horizon, lams)
            for method in ("mean_avg", "opr"):
                records.append((key, start[key], k, method, r[method], np.nan))
            for variant in ("ridge_opr", "ridge_opr_zero_prior"):
                for lam, m in r[variant].items():
                    grid_rows.append((key, start[key], k, variant, lam, m))
    grid = pd.DataFrame(grid_rows, columns=["event_key", "start_date", "k", "variant", "lam", "mae"])
    base = pd.DataFrame(records, columns=["event_key", "start_date", "k", "method", "mae", "lam"])

    chosen = []
    for (key, sd, k), _ in grid.groupby(["event_key", "start_date", "k"]):
        for variant in ("ridge_opr", "ridge_opr_zero_prior"):
            lam = choose_lambda(grid, sd, k, variant, lams)
            if lam is None:
                continue  # earliest events have no past to tune on; excluded from every method
            m = grid[(grid.event_key == key) & (grid.k == k) & (grid.variant == variant) & (grid.lam == lam)]["mae"].iloc[0]
            chosen.append((key, sd, k, variant, m, lam))
    ridge = pd.DataFrame(chosen, columns=["event_key", "start_date", "k", "method", "mae", "lam"])

    # Compare all methods on exactly the same (event, k) cells.
    cells = ridge.groupby(["event_key", "k"]).size()
    cells = cells[cells == 2].index
    keep = pd.MultiIndex.from_frame(base[["event_key", "k"]]).isin(cells)
    detail = pd.concat([base[keep], ridge[pd.MultiIndex.from_frame(ridge[["event_key", "k"]]).isin(cells)]],
                       ignore_index=True)
    if detail.empty:
        return detail, pd.DataFrame(columns=["k", "method", "mean_mae", "se", "n_events"])
    g = detail.groupby(["k", "method"])["mae"]
    summary = pd.DataFrame({"mean_mae": g.mean(), "se": g.sem(ddof=1), "n_events": g.size()}).reset_index()
    summary["method"] = pd.Categorical(summary["method"], METHODS, ordered=True)
    return detail, summary.sort_values(["k", "method"]).reset_index(drop=True)


# ---------------------------------------------------------------------- output
def write_report(detail, summary, report_dir="reports"):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    rep = Path(report_dir)
    rep.mkdir(parents=True, exist_ok=True)
    detail.to_csv(rep / "opr_eval_detail.csv", index=False)
    summary.to_csv(rep / "opr_mae_by_k.csv", index=False)
    lines = ["| k | method | mean MAE | SE | events |", "|---|---|---|---|---|"]
    for r in summary.itertuples():
        lines.append(f"| {r.k} | {r.method} | {r.mean_mae:.2f} | {r.se:.2f} | {r.n_events} |")
    (rep / "opr_mae_by_k.md").write_text("\n".join(lines) + "\n")

    fig, ax = plt.subplots(figsize=(7, 4.5))
    for method in METHODS:
        d = summary[summary["method"] == method]
        ax.errorbar(d["k"], d["mean_mae"], yerr=d["se"], marker="o", capsize=3, label=method)
    ax.set_xlabel("k = qualification matches used to fit")
    ax.set_ylabel("MAE of next-match alliance score")
    ax.set_title("Alliance-score prediction error vs. matches observed")
    ax.legend()
    fig.tight_layout()
    fig.savefig(rep / "opr_mae_by_k.png", dpi=150)
    plt.close(fig)


def main(argv=None):
    from analytics.data import qual_matches

    p = argparse.ArgumentParser(description="OPR vs ridge OPR vs mean-average, MAE by k")
    p.add_argument("--data", default="data/processed")
    p.add_argument("--report", default="reports")
    p.add_argument("--horizon", type=int, default=DEFAULT_HORIZON)
    args = p.parse_args(argv)

    d = Path(args.data)
    if not (d / "matches.csv").exists():
        raise SystemExit(f"{d}/matches.csv not found. Run `make data` first (needs TBA_API_KEY).")
    matches = qual_matches(pd.read_csv(d / "matches.csv"))  # qualification only: no playoff data
    events = pd.read_csv(d / "events.csv")
    detail, summary = run_eval(matches, events, horizon=args.horizon)
    if summary.empty:
        raise SystemExit("No event had enough qualification matches and prior events to evaluate.")
    write_report(detail, summary, args.report)
    print(summary.to_string(index=False))


if __name__ == "__main__":
    main()
