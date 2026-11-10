import numpy as np
import pandas as pd
import pytest

from analytics import matching as M

COLS = ["scoring", "auto", "endgame"]


def feats(rows):
    return pd.DataFrame(rows, columns=COLS, index=pd.Index(range(1, len(rows) + 1), name="team"), dtype=float)


def test_fit_weights_recovers_linear_relationship():
    rng = np.random.default_rng(0)
    X = rng.normal(size=(200, 3))
    y = X @ np.array([2.0, 0.0, 1.0]) + 5
    coef, b = M.fit_weights(X, y, alpha=1e-6)
    assert np.allclose(coef, [2, 0, 1], atol=1e-3) and b == pytest.approx(5, abs=1e-3)


def test_distance_weights_clip_negatives_and_normalize():
    assert np.allclose(M.distance_weights(np.array([3.0, -1.0, 1.0])), [0.75, 0.0, 0.25])
    assert np.allclose(M.distance_weights(np.array([-1.0, -2.0])), [0.5, 0.5])


def test_target_profile_fills_the_gap_and_clips_at_zero():
    S = np.array([3.0, 3.0, 3.0])
    cap = np.array([1.0, 1.0, 5.0])
    t = M.target_profile(S, cap, [], 2)
    assert np.allclose(t, [1.0, 1.0, 0.0])         # endgame already exceeded -> clipped to 0
    t2 = M.target_profile(S, cap, [np.array([1.0, 0.0, 0.0])], 1)
    assert np.allclose(t2, [1.0, 2.0, 0.0])        # second pick fills what is STILL missing


def test_complementary_matching_prefers_filling_gaps_over_cloning_captain():
    # captain is a scorer; winners look like (scoring 3, auto 3, endgame 3) summed
    F = feats([[2, 0, 0],      # 1 = captain
               [2, 0, 0],      # 2 = clone of captain
               [0, 1.5, 1.5],  # 3 = fills the gap (auto + endgame)
               [0, 0, 0]])
    S = np.array([3.0, 3.0, 3.0])
    w = np.array([1 / 3] * 3)
    comp = M.rank_candidates(M.target_profile(S, F.loc[1].to_numpy(), [], 2), F, w, [2, 3, 4])
    assert comp.index[0] == 3 and comp.index[-1] != 3
    # plain k-NN on the captain's own vector would pick the clone:
    similar = M.rank_candidates(F.loc[1].to_numpy(), F, w, [2, 3, 4])
    assert similar.index[0] == 2


def test_contributions_explain_distance():
    F = feats([[1, 2, 3], [0, 1, 0], [2, 2, 2]])
    r = M.rank_candidates(np.array([1.0, 1.0, 1.0]), F, np.array([0.5, 0.3, 0.2]), [1, 2, 3])
    c = r[[x for x in r.columns if x.startswith("contrib_")]]
    assert np.allclose(np.sqrt(c.sum(axis=1)), r["distance"])


def test_zero_weight_feature_is_ignored():
    F = feats([[0, 0, 0], [0, 9, 0], [1, 0, 0]])
    r = M.rank_candidates(np.zeros(3), F, np.array([1.0, 0.0, 0.0]), [2, 3])
    assert r.index[0] == 2            # team 2 differs only in a zero-weight feature


def test_ranking_is_deterministic_on_ties():
    F = feats([[1, 1, 1], [1, 1, 1], [1, 1, 1]])
    r = M.rank_candidates(np.zeros(3), F, np.ones(3) / 3, [3, 1, 2])
    assert list(r.index) == [1, 2, 3]


def test_baseline_rankings():
    s = pd.Series({1: 5.0, 2: 9.0, 3: 1.0})
    assert M.rank_by_value(s, [1, 2, 3], True) == [2, 1, 3]
    assert M.rank_by_value(s, [1, 2, 3], False) == [3, 1, 2]
    assert M.rank_by_value(s, [1, 2, 99], False)[-1] == 99      # unknown rank sorts last


def test_draft_pick_decline_and_second_pick_recomputes_target():
    F = feats([[2, 0, 0], [0, 3, 0], [0, 0, 3], [0, 0, 0], [1, 1, 1]])
    S = np.array([3.0, 3.0, 3.0])
    d = M.Draft(F, np.ones(3) / 3, S, captain=1, pool=[1, 2, 3, 4, 5])
    assert 1 not in d.pool
    first = d.recommend()
    t0 = d.target().copy()
    d.decline(first.index[0])                       # someone else took/declined the top team
    assert first.index[0] not in d.recommend().index
    assert np.allclose(d.target(), t0)              # declining does not change the alliance
    d.pick(2)                                       # we pick team 2 (auto specialist)
    assert 2 not in d.recommend().index
    assert not np.allclose(d.target(), t0)          # target recomputed after the pick
    assert d.target()[1] == 0.0                     # auto gap is now filled -> clipped to 0
    assert d.recommend(1).shape[0] == 1
