import json

import pytest

from analytics.tba import TBAClient, TBAError


class Resp:
    def __init__(self, status, body=None, etag=None):
        self.status_code = status
        self.text = json.dumps(body) if body is not None else ""
        self.headers = {"ETag": etag} if etag else {}
        self._body = body

    def json(self):
        return self._body


class FakeSession:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def get(self, url, headers=None, timeout=None):
        self.calls.append((url, dict(headers or {})))
        return self.responses.pop(0)


def client(tmp_path, responses, key="k", **kw):
    s = FakeSession(responses)
    return TBAClient(api_key=key, cache_dir=tmp_path, session=s, sleep=lambda _: None, **kw), s


def test_sends_auth_header_and_caches_raw_json(tmp_path):
    c, s = client(tmp_path, [Resp(200, [{"key": "2020x"}], etag='"abc"')])
    assert c.events(2020) == [{"key": "2020x"}]
    assert s.calls[0][1]["X-TBA-Auth-Key"] == "k"
    assert json.loads((tmp_path / "events__2020.json").read_text()) == [{"key": "2020x"}]


def test_revalidates_with_etag_and_uses_cache_on_304(tmp_path):
    c, s = client(tmp_path, [Resp(200, [1], etag='"v1"'), Resp(304)])
    c.events(2020)
    assert c.events(2020) == [1]
    assert s.calls[1][1]["If-None-Match"] == '"v1"'


def test_no_key_but_cached_works_offline(tmp_path):
    c, _ = client(tmp_path, [Resp(200, [1])])
    c.events(2020)
    c2, s2 = client(tmp_path, [], key="")
    assert c2.events(2020) == [1]
    assert s2.calls == []


def test_no_key_and_no_cache_is_a_clear_error(tmp_path):
    c, _ = client(tmp_path, [], key="")
    with pytest.raises(TBAError, match="TBA_API_KEY"):
        c.events(2020)


def test_offline_mode_never_touches_network(tmp_path):
    c, s = client(tmp_path, [], offline=True)
    with pytest.raises(TBAError):
        c.events(2020)
    assert s.calls == []


def test_retries_on_server_error_then_succeeds(tmp_path):
    c, s = client(tmp_path, [Resp(503), Resp(200, [2])])
    assert c.events(2020) == [2]
    assert len(s.calls) == 2


def test_http_error_raises(tmp_path):
    c, _ = client(tmp_path, [Resp(404)])
    with pytest.raises(TBAError, match="404"):
        c.events(2020)
