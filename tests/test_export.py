import json
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

from analytics import export
from analytics.features import assemble
import parity_cases as P

PARITY = Path(__file__).parent / "fixtures" / "parity"


def test_committed_parity_fixtures_match_the_python_reference():
    """Fixtures must be regenerated (python tests/parity_cases.py --write) if the reference changes."""
    cases = P.all_cases()
    assert sorted(p.stem for p in PARITY.glob("*.json")) == sorted(c["name"] for c in cases)
    for c in cases:
        assert (PARITY / f"{c['name']}.json").read_text() == P.render(c), c["name"]


def test_params_roundtrip_and_validation(tmp_path):
    w = {"features": ["auto_opr", "teleop_opr", "endgame_opr"], "distance_weights": [0.2, 0.5, 0.3],
         "coefficients": [0.1, 0.4, 0.2], "winning_profile": [1.0, 2.0, 0.5]}
    p = export.build_params(w, {"n_events": 3})
    export.write_params(tmp_path / "a" / "model_params.json", p)
    back = json.loads((tmp_path / "a" / "model_params.json").read_text())
    assert back["schema_version"] == 1 and back["features"] == w["features"]
    assert back["normalization"]["method"] == "zscore" and back["scouting_prior_strength"] == 5.0
    for bad in ({**p, "schema_version": 2}, {**p, "weights": [1.0]}, {**p, "weights": [-0.1, 0.6, 0.5]},
                {**p, "features": ["nope", "teleop_opr", "endgame_opr"]}):
        with pytest.raises(ValueError):
            export.validate_params(bad)


def test_missing_scouting_features_are_neutral_zero():
    r = pd.DataFrame({"auto_opr": [1.0, 2.0, 3.0], "teleop_opr": [3.0, 1.0, 2.0], "endgame_opr": [0.0, 5.0, 1.0]},
                     index=pd.Index([1, 2, 3], name="team"))
    z = assemble(r, None, 5.0, names=["teleop_opr", "defense"])
    assert list(z.columns) == ["teleop_opr", "defense"] and (z["defense"] == 0).all()


def test_constant_column_with_float_noise_is_not_amplified():
    r = pd.DataFrame({"auto_opr": [0.1 + 0.2, 0.3, 0.3], "teleop_opr": [1.0, 2.0, 3.0], "endgame_opr": [1.0, 2.0, 4.0]},
                     index=pd.Index([1, 2, 3], name="team"))
    assert (assemble(r)["auto_opr"] == 0).all()


def test_snapshot_frame_has_raw_ratings():
    r = pd.DataFrame({"auto_opr": [1.5], "teleop_opr": [2.5], "endgame_opr": [3.5]}, index=pd.Index([254], name="team"))
    s = export.snapshot_frame(r, "2020xyz")
    assert list(s.columns) == ["team", "event_key", "auto_opr", "teleop_opr", "endgame_opr"] or \
        set(s.columns) == {"team", "event_key", "auto_opr", "teleop_opr", "endgame_opr"}
    assert s.loc[0, "team"] == 254 and s.loc[0, "teleop_opr"] == 2.5
