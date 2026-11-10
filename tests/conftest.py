import json
from pathlib import Path

import pytest

FIXTURES = Path(__file__).parent / "fixtures"


def load(name):
    return json.loads((FIXTURES / f"{name}.json").read_text())


class FakeClient:
    """Stands in for TBAClient using the synthetic fixtures."""

    def events(self, year):
        return load("events_2020") if year == 2020 else []

    def event_matches(self, key):
        return load("matches_2020test") if key == "2020test" else load("matches_empty")

    def event_alliances(self, key):
        return load("alliances_2020test")

    def event_rankings(self, key):
        return load("rankings_2020test")


@pytest.fixture
def fake_client():
    return FakeClient()
