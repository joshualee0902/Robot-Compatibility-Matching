import math

import pandas as pd
import pytest

from analytics import data
from conftest import load


@pytest.fixture
def tables(fake_client):
    return data.build_tables(fake_client, [2020], log=lambda *_: None)


def test_event_type_filter_and_empty_events_skipped(tables):
    events, _, _, stats = tables
    # offseason (99) excluded by type; cancelled event has no matches
    assert list(events["event_key"]) == ["2020test"]
    assert stats["events_listed"] == 2


def test_unplayed_matches_skipped(tables):
    _, matches, _, stats = tables
    assert "2020test_qm3" not in set(matches["match_key"])
    assert stats["matches_skipped"] == 1


def test_qualification_and_playoff_are_separated(tables):
    _, matches, _, _ = tables
    qual = data.qual_matches(matches)
    playoff = data.playoff_matches(matches)
    assert set(qual["comp_level"]) == {"qm"}
    assert set(playoff["comp_level"]) <= set(data.PLAYOFF_LEVELS)
    assert set(playoff["comp_level"]) == {"sf", "f"}
    # disjoint, and together they are every played match
    assert set(qual["match_key"]).isdisjoint(set(playoff["match_key"]))
    assert len(qual) + len(playoff) == len(matches)


def test_per_robot_and_component_columns(tables):
    _, matches, _, _ = tables
    m = matches[matches["match_key"] == "2020test_qm1"].iloc[0]
    assert [m["red1"], m["red2"], m["red3"]] == [1, 2, 3]
    assert m["red_score"] == 98 and m["blue_score"] == 55
    assert m["red_init_line_1"] == "Exited" and m["red_endgame_3"] == "Park"
    assert m["red_auto_points"] == 20 and m["red_endgame_points"] == 25


def test_missing_breakdown_is_nan_not_guessed(tables):
    _, matches, _, _ = tables
    m = matches[matches["match_key"] == "2020test_qm2"].iloc[0]
    assert math.isnan(m["red_auto_points"])


def test_alliances_parsed_in_seed_order(tables):
    _, _, alliances, _ = tables
    a1 = alliances[alliances["seed"] == 1].iloc[0]
    assert (a1["captain"], a1["pick1"], a1["pick2"], a1["pick3"]) == (1, 2, 3, 9)
    assert a1["playoff_status"] == "won" and a1["playoff_wins"] == 2
    a2 = alliances[alliances["seed"] == 2].iloc[0]
    assert pd.isna(a2["pick3"])


def test_counts_derived_from_tables(tables):
    events, matches, alliances, stats = tables
    c = data.compute_counts(events, matches, alliances, stats)
    assert c["events_with_matches"] == 1
    assert c["qualification_matches"] == 2
    assert c["playoff_matches"] == 2
    assert c["alliances"] == 2


def test_bad_team_key_raises():
    with pytest.raises(ValueError):
        data.team_number("frc254B")


def test_write_outputs(tables, tmp_path):
    events, matches, alliances, stats = tables
    counts = data.compute_counts(events, matches, alliances, stats)
    data.write_outputs(events, matches, alliances, counts, tmp_path / "p", tmp_path / "r")
    assert (tmp_path / "p" / "matches.csv").exists()
    assert "qualification matches" in (tmp_path / "r" / "data_counts.md").read_text()
