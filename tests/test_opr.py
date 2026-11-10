"""Tests use SYNTHETIC events generated here with known robot contributions."""
import numpy as np
import pandas as pd
import pytest

from analytics import opr


def make_event(key, n_teams=24, n_matches=60, noise=0.0, seed=0, truth=None):
    rng = np.random.default_rng(seed)
    teams = np.arange(1000, 1000 + n_teams)
    if truth is None:
        truth = {t: dict(auto=rng.uniform(2, 15), teleop=rng.uniform(10, 40), endgame=rng.uniform(0, 20))
                 for t in teams}
    rows = []
    for m in range(1, n_matches + 1):
        six = rng.choice(teams, 6, replace=False)
        row = {"event_key": key, "match_number": m, "comp_level": "qm"}
        for side, trio in (("red", six[:3]), ("blue", six[3:])):
            for i, t in enumerate(trio, 1):
                row[f"{side}{i}"] = int(t)
            comps = {c: sum(truth[t][c] for t in trio) + rng.normal(0, noise) for c in ("auto", "teleop", "endgame")}
            row[f"{side}_auto_points"] = comps["auto"]
            row[f"{side}_teleop_points"] = comps["teleop"]
            row[f"{side}_endgame_points"] = comps["endgame"]
            row[f"{side}_score"] = comps["auto"] + comps["teleop"] + comps["endgame"]
        rows.append(row)
    return pd.DataFrame(rows), truth


def test_design_matrix_rows_sum_to_three_and_follow_order():
    qm, _ = make_event("e", n_matches=5)
    teams = opr.event_teams(qm)
    A = opr.design_matrix(qm, teams)
    assert A.shape == (10, len(teams))
    assert (A.sum(axis=1) == 3).all()
    red1 = qm.loc[0, "red1"]
    assert A[0, np.searchsorted(teams, red1)] == 1          # row 0 = match 1 red
    blue1 = qm.loc[0, "blue1"]
    assert A[1, np.searchsorted(teams, blue1)] == 1         # row 1 = match 1 blue
    s = opr.alliance_scores(qm)
    assert s[0] == qm.loc[0, "red_score"] and s[1] == qm.loc[0, "blue_score"]


def test_opr_recovers_noise_free_contributions():
    qm, truth = make_event("e", n_matches=80)
    teams = opr.event_teams(qm)
    x = opr.fit_opr(opr.design_matrix(qm, teams), opr.alliance_scores(qm))
    expected = np.array([sum(truth[t].values()) for t in teams])
    assert np.allclose(x, expected, atol=1e-6)


def test_zero_prior_ridge_matches_prd_formula():
    qm, _ = make_event("e", n_matches=30, noise=3, seed=1)
    teams = opr.event_teams(qm)
    A, s = opr.design_matrix(qm, teams), opr.alliance_scores(qm)
    lam = 2.5
    prd = np.linalg.inv(A.T @ A + lam * np.eye(A.shape[1])) @ A.T @ s
    assert np.allclose(opr.fit_ridge(A, s, lam, center=False), prd)


def test_ridge_limits():
    qm, _ = make_event("e", n_matches=30, noise=3, seed=2)
    teams = opr.event_teams(qm)
    A, s = opr.design_matrix(qm, teams), opr.alliance_scores(qm)
    # lam -> 0 approaches OPR (full rank here)
    assert np.allclose(opr.fit_ridge(A, s, 1e-9, center=False), opr.fit_opr(A, s), atol=1e-4)
    # lam -> infinity: centered shrinks to the mean robot, zero-prior shrinks to 0
    assert np.allclose(opr.fit_ridge(A, s, 1e12, center=True), s.mean() / 3, atol=1e-3)
    assert np.allclose(opr.fit_ridge(A, s, 1e12, center=False), 0, atol=1e-3)


def test_unseen_team_predicted_as_average_when_centered():
    qm, _ = make_event("e", n_matches=60, seed=3)
    teams = opr.event_teams(qm)
    A, s = opr.design_matrix(qm, teams), opr.alliance_scores(qm)
    A_fit, s_fit = A[:6], s[:6]                       # only 3 matches seen
    unseen = np.where(A_fit.sum(axis=0) == 0)[0]
    assert len(unseen) > 0
    x = opr.fit_ridge(A_fit, s_fit, 5.0, center=True)
    assert np.allclose(x[unseen], s_fit.mean() / 3)
    assert np.allclose(opr.fit_opr(A_fit, s_fit)[unseen], 0)


def test_component_oprs_recover_components_and_skip_nan_rows():
    qm, truth = make_event("e", n_matches=80, seed=4)
    qm.loc[[2, 3], "red_auto_points"] = np.nan        # missing breakdown rows are dropped
    r = opr.team_ratings(qm, lam=1e-6)
    for t in list(r.index)[:5]:
        assert r.loc[t, "teleop_opr"] == pytest.approx(truth[t]["teleop"], abs=1e-2)
        assert r.loc[t, "endgame_opr"] == pytest.approx(truth[t]["endgame"], abs=1e-2)
        assert r.loc[t, "auto_opr"] == pytest.approx(truth[t]["auto"], abs=0.5)


def test_team_ratings_upto_uses_only_early_matches():
    qm, _ = make_event("e", n_matches=40, seed=5)
    altered = qm.copy()
    altered.loc[altered.match_number > 20, ["red_score", "blue_score"]] += 500
    a = opr.team_ratings(qm, lam=1.0, upto=20)["opr"]
    b = opr.team_ratings(altered, lam=1.0, upto=20)["opr"]
    assert np.allclose(a, b)


def test_evaluate_event_uses_nothing_after_k_for_fitting():
    qm, _ = make_event("e", n_matches=60, noise=2, seed=6)
    k, h, lams = 20, 10, (1.0, 10.0)
    base = opr.evaluate_event_k(qm, k, h, lams)
    altered = qm.copy()
    altered.loc[altered.match_number > k + h, ["red_score", "blue_score"]] += 999  # beyond the window
    assert opr.evaluate_event_k(altered, k, h, lams) == base
    in_window = qm.copy()
    in_window.loc[in_window.match_number == k + 1, "red_score"] += 999             # a target changes
    assert opr.evaluate_event_k(in_window, k, h, lams) != base


def test_ridge_beats_plain_opr_when_samples_are_small():
    maes = {"opr": [], "ridge": []}
    for seed in range(8):
        qm, _ = make_event(f"e{seed}", noise=4, seed=100 + seed)
        r = opr.evaluate_event_k(qm, 10, 10, (1.0, 3.0, 10.0))
        maes["opr"].append(r["opr"])
        maes["ridge"].append(min(r["ridge_opr"].values()))
    assert np.mean(maes["ridge"]) < np.mean(maes["opr"])


def _tables(seeds_and_dates, n_matches=45, noise=3):
    matches, events = [], []
    for key, seed, date in seeds_and_dates:
        qm, _ = make_event(key, n_matches=n_matches, noise=noise, seed=seed)
        matches.append(qm)
        events.append({"event_key": key, "start_date": date})
    return pd.concat(matches, ignore_index=True), pd.DataFrame(events)


def test_lambda_selection_has_no_look_ahead():
    spec = [("a", 1, "2020-02-01"), ("b", 2, "2020-02-08"), ("c", 3, "2020-02-15"), ("d", 4, "2020-02-22")]
    matches, events = _tables(spec)
    d1, _ = opr.run_eval(matches, events, ks=(10,), horizon=10, lams=(0.3, 3.0, 30.0))
    # Rewrite the LAST event's data: lambda chosen for earlier events must not change.
    m2 = matches.copy()
    last = m2["event_key"] == "d"
    m2.loc[last, ["red_score", "blue_score"]] = np.random.default_rng(9).uniform(0, 200, (last.sum(), 2))
    d2, _ = opr.run_eval(m2, events, ks=(10,), horizon=10, lams=(0.3, 3.0, 30.0))
    pick = lambda d, e: d[(d.event_key == e) & (d.method == "ridge_opr")]["lam"].iloc[0]
    for e in ("b", "c"):
        assert pick(d1, e) == pick(d2, e)


def test_choose_lambda_ignores_same_day_and_later_events():
    grid = pd.DataFrame({
        "event_key": ["a", "a", "b", "b"], "start_date": ["2020-01-01"] * 2 + ["2020-02-01"] * 2,
        "k": 10, "variant": "ridge_opr", "lam": [1.0, 10.0, 1.0, 10.0], "mae": [5.0, 9.0, 99.0, 1.0]})
    assert opr.choose_lambda(grid, "2020-01-15", 10, "ridge_opr", (1.0, 10.0)) == 1.0  # only 'a' is past
    assert opr.choose_lambda(grid, "2020-01-01", 10, "ridge_opr", (1.0, 10.0)) is None


def test_run_eval_compares_methods_on_same_cells_and_writes_report(tmp_path):
    spec = [("a", 1, "2020-02-01"), ("b", 2, "2020-02-08"), ("c", 3, "2020-02-15")]
    matches, events = _tables(spec)
    detail, summary = opr.run_eval(matches, events, ks=(10, 20), horizon=10, lams=(0.3, 3.0, 30.0))
    assert set(detail["method"]) == set(opr.METHODS)
    assert "a" not in set(detail["event_key"])           # earliest event has no past to tune on
    assert summary.groupby("k")["n_events"].nunique().max() == 1   # same events for every method
    opr.write_report(detail, summary, tmp_path)
    for f in ("opr_mae_by_k.png", "opr_mae_by_k.csv", "opr_mae_by_k.md"):
        assert (tmp_path / f).exists()
