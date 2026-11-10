import numpy as np
import pandas as pd
import pytest

from analytics import backtest as B
from synth import make_season


@pytest.fixture(scope="module")
def season():
    return make_season(n_events=8, seed=1)


def run(season, min_train=3):
    events, matches, alliances, rankings = season
    return B.run_backtest(B.prepare_events(matches, events, alliances, rankings), min_train)


def test_bootstrap_ci_deterministic_and_sane():
    a = B.bootstrap_ci([1.0, 2.0, 3.0, 4.0, np.nan])
    assert a == B.bootstrap_ci([1.0, 2.0, 3.0, 4.0, np.nan])
    mean, lo, hi, n = a
    assert n == 4 and mean == pytest.approx(2.5) and lo <= mean <= hi
    assert B.bootstrap_ci([7.0, 7.0, 7.0])[1:3] == (7.0, 7.0)
    assert np.isnan(B.bootstrap_ci([])[0])


def test_percentile():
    assert B.percentile_of([5, 6, 7], 5) == 1.0
    assert B.percentile_of([5, 6, 7], 7) == 0.0
    assert B.percentile_of([5], 5) == 1.0


def test_alliance_performance_maps_sides_to_alliances():
    alliances = pd.DataFrame([{"seed": 1, "captain": 1, "pick1": 2, "pick2": 3, "pick3": 9},
                              {"seed": 2, "captain": 4, "pick1": 5, "pick2": 6, "pick3": np.nan}])
    playoff = pd.DataFrame([{"red1": 1, "red2": 2, "red3": 9, "blue1": 4, "blue2": 5, "blue3": 6,
                             "red_score": 100, "blue_score": 60},     # backup 9 subbed in for 3
                            {"red1": 1, "red2": 2, "red3": 3, "blue1": 4, "blue2": 5, "blue3": 6,
                             "red_score": 80, "blue_score": 70}])
    perf = B.alliance_performance(playoff, alliances)
    assert perf == {1: 90.0, 2: 65.0}


def test_backtest_runs_and_baseline_is_sensible(season):
    d = run(season)
    assert len(d) >= 3
    assert {"spearman_knn", "top3_knn", "gap_opr", "top3_seeding", "lam"} <= set(d.columns)
    # picks in the synthetic season are made by true strength, so plain OPR should beat chance
    # (random top-3 from a pool of ~16 would be ~0.19). No claim is made about k-NN here.
    assert d["top3_opr"].mean() > 0.3
    assert (d["gap_opr"] >= -1e-9).all()        # OPR's #1 maximises plain OPR; gap uses ridge OPR, ~nonneg


def test_early_events_without_enough_history_are_skipped(season):
    d = run(season, min_train=3)
    events = season[0].sort_values("start_date")["event_key"].tolist()
    assert not set(events[:3]) & set(d["event_key"])


def test_no_look_ahead_future_event_changes_nothing_earlier(season):
    events, matches, alliances, rankings = season
    base = run(season)
    last = events.sort_values("start_date")["event_key"].iloc[-1]
    m2 = matches.copy()
    mask = m2["event_key"] == last
    m2.loc[mask, ["red_score", "blue_score"]] = np.random.default_rng(5).uniform(0, 300, (mask.sum(), 2))
    a2 = alliances.copy()
    a2.loc[a2["event_key"] == last, "playoff_status"] = "won"
    altered = B.run_backtest(B.prepare_events(m2, events, a2, rankings), 3)
    a = base[base["event_key"] != last].reset_index(drop=True)
    b = altered[altered["event_key"] != last].reset_index(drop=True)
    pd.testing.assert_frame_equal(a, b)


def test_own_playoff_results_do_not_affect_recommendations(season):
    events, matches, alliances, rankings = season
    base = run(season)
    target_event = base["event_key"].iloc[0]
    m2 = matches.copy()
    mask = (m2["event_key"] == target_event) & (m2["comp_level"] == "sf")
    m2.loc[mask, ["red_score", "blue_score"]] = np.random.default_rng(6).uniform(0, 300, (mask.sum(), 2))
    a2 = alliances.copy()
    a2.loc[a2["event_key"] == target_event, "playoff_status"] = "eliminated"
    altered = B.run_backtest(B.prepare_events(m2, events, a2, rankings), 3)
    row = lambda d: d[d["event_key"] == target_event].iloc[0]
    for col in ("lam", "top3_knn", "top3_opr", "top3_seeding", "gap_knn", "gap_opr", "gap_seeding"):
        assert row(base)[col] == row(altered)[col]          # only the evaluation target (spearman) may change


def test_same_day_events_are_not_training_data(season):
    events, matches, alliances, rankings = season
    e2 = events.copy()
    e2["start_date"] = "2020-03-01"                           # every event on the same day
    d = B.run_backtest(B.prepare_events(matches, e2, alliances, rankings), 1)
    assert d.empty


def test_summary_and_report(season, tmp_path):
    d = run(season)
    s = B.summarize(d)
    assert {"knn - opr", "knn - seeding"} <= set(s["method"])
    assert (s["ci_low"] <= s["mean"] + 1e-12).all() and (s["mean"] <= s["ci_high"] + 1e-12).all()
    events, matches, alliances, rankings = season
    w = B.final_weights(B.prepare_events(matches, events, alliances, rankings))
    assert w is not None and abs(sum(w["distance_weights"]) - 1) < 1e-9
    B.write_report(d, s, w, tmp_path)
    for f in ("backtest_results.md", "backtest_results.csv", "backtest_detail.csv", "fitted_weights.json", "fitted_weights.md"):
        assert (tmp_path / f).exists()
