import numpy as np
import pandas as pd
import pytest

from analytics import features as F
from test_opr import make_event


def test_shrink_weight_is_n_over_n_plus_m():
    # n=5, m=5 -> w=0.5: halfway between team value and event mean
    assert F.shrink(1.0, 5, 0.0, 5) == pytest.approx(0.5)
    assert F.shrink(1.0, 0, 0.3, 5) == pytest.approx(0.3)       # no data -> event mean
    assert F.shrink(1.0, 1000, 0.0, 5) == pytest.approx(1.0, abs=0.01)


def test_zscore_mean_zero_std_one_and_constant_column_zero():
    df = pd.DataFrame({"a": [1.0, 2.0, 3.0, 4.0], "b": [5.0] * 4})
    z = F.zscore(df)
    assert z["a"].mean() == pytest.approx(0) and z["a"].std(ddof=0) == pytest.approx(1)
    assert (z["b"] == 0).all()


def _scouting():
    return pd.DataFrame({
        "team_num": [1] * 10 + [2] * 2, "defense_rating": [3] * 10 + [0] * 2,
        "climbed": [1] * 10 + [0] * 2, "bricked": [0] * 12, "fouls": [0] * 10 + [4] * 2})


def test_scouting_features_shrink_small_samples_more():
    f = F.scouting_features(_scouting(), [1, 2, 3], m=5.0)
    mean_def = _scouting()["defense_rating"].mean()
    assert f.loc[3, "defense"] == pytest.approx(mean_def)            # unscouted team = event mean
    # team 2 has only 2 matches: pulled much closer to the mean than its raw value 0
    assert abs(f.loc[2, "defense"] - mean_def) < abs(0 - mean_def)
    assert f.loc[2, "defense"] > 0
    # team 1 (10 matches) stays closer to its raw 3 than team 2 does to its raw 0
    assert abs(f.loc[1, "defense"] - 3) < abs(f.loc[2, "defense"] - 0)


def test_feature_table_is_zscored_and_tba_only_without_scouting():
    qm, _ = make_event("e", n_matches=40, noise=2, seed=1)
    t = F.feature_table(qm, lam=1.0)
    assert list(t.columns) == list(F.TBA_FEATURES)
    assert np.allclose(t.mean(), 0, atol=1e-9) and np.allclose(t.std(ddof=0), 1, atol=1e-9)


def test_feature_table_adds_scouting_columns_when_available():
    qm, _ = make_event("e", n_matches=40, noise=2, seed=1)
    teams = sorted(set(qm["red1"]))[:5]
    s = pd.DataFrame({"team_num": teams * 3, "defense_rating": [1, 2, 3, 0, 1] * 3,
                      "climbed": [1, 0, 1, 0, 1] * 3, "bricked": [0] * 15, "fouls": [0, 1, 2, 0, 1] * 3})
    t = F.feature_table(qm, 1.0, scouting=s)
    assert set(F.SCOUTING_FEATURES) <= set(t.columns)


def test_feature_table_uses_only_first_upto_matches():
    qm, _ = make_event("e", n_matches=40, noise=2, seed=2)
    altered = qm.copy()
    altered.loc[altered.match_number > 20, ["red_teleop_points", "blue_teleop_points"]] += 300
    a = F.feature_table(qm, 1.0, upto=20)
    b = F.feature_table(altered, 1.0, upto=20)
    assert np.allclose(a, b)
