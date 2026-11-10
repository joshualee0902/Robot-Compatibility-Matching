import pandas as pd
import pytest

from analytics import report as R

README = f"intro\n\n## Results\n\n{R.START}\nold\n{R.END}\n\n## Limitations\n"


def summary(mean, lo, hi, metric="spearman", method="knn - opr"):
    return pd.DataFrame([{"metric": metric, "method": method, "mean": mean, "ci_low": lo, "ci_high": hi, "n_events": 12}])


def test_empty_reports_say_not_generated(tmp_path):
    out = R.render(tmp_path / "reports", tmp_path / "shots")
    assert out.count(R.NOT_YET) == 5
    assert "make data" in out and "make opr-eval" in out and "make backtest" in out


def test_tables_are_copied_from_reports(tmp_path):
    rep = tmp_path / "reports"
    rep.mkdir()
    (rep / "data_counts.md").write_text("| a | 1 |\n")
    (rep / "opr_mae_by_k.md").write_text("| k | 10 |\n")
    (rep / "opr_mae_by_k.png").write_bytes(b"png")
    (rep / "backtest_results.md").write_text("| spearman | 0.5 |\n")
    (rep / "fitted_weights.md").write_text("| auto | 0.2 |\n")
    out = R.render(rep, tmp_path / "shots")
    for text in ("| a | 1 |", "| k | 10 |", "| spearman | 0.5 |", "| auto | 0.2 |", "opr_mae_by_k.png"):
        assert text in out
    assert out.count(R.NOT_YET) == 1                  # only screenshots missing


def test_screenshots_listed_from_directory(tmp_path):
    shots = tmp_path / "shots"
    shots.mkdir()
    (shots / "ranking.png").write_bytes(b"x")
    out = R.render(tmp_path / "r", shots)
    assert "ranking.png" in out and "Add PNGs" not in out


@pytest.mark.parametrize("mean,lo,hi,word", [
    (0.1, 0.02, 0.2, "k-NN is better"),
    (-0.1, -0.2, -0.02, "k-NN is worse"),
    (0.01, -0.05, 0.07, "no clear difference"),
])
def test_verdict_follows_the_confidence_interval(mean, lo, hi, word):
    lines = R.verdicts(summary(mean, lo, hi))
    assert len(lines) == 1 and word in lines[0]
    assert f"{mean:+.3f}" in lines[0]


def test_verdict_ignores_gap_and_nan():
    assert R.verdicts(summary(0.5, 0.1, 0.9, metric="gap")) == []
    assert R.verdicts(summary(float("nan"), float("nan"), float("nan"))) == []


def test_update_replaces_only_the_block_and_is_idempotent():
    block = "### New\n\nbody\n"
    once = R.update_readme(README, block)
    assert once.startswith("intro") and once.endswith("## Limitations\n") and "old" not in once
    assert R.update_readme(once, block) == once


def test_update_requires_markers():
    with pytest.raises(ValueError):
        R.update_readme("no markers", "x")


def test_check_mode(tmp_path):
    readme = tmp_path / "README.md"
    readme.write_text(README)
    args = ["--readme", str(readme), "--reports", str(tmp_path / "r"), "--screenshots", str(tmp_path / "s")]
    assert R.main(args + ["--check"]) == 1             # stale
    assert R.main(args) == 0                           # rewrite
    assert R.main(args + ["--check"]) == 0             # now current
    (tmp_path / "r").mkdir()
    (tmp_path / "r" / "data_counts.md").write_text("| events | 3 |\n")
    assert R.main(args + ["--check"]) == 1             # a new report makes the README stale
