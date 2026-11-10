"""The Blue Alliance API v3 client with an on-disk cache of raw JSON responses.

The API key is read from the TBA_API_KEY environment variable (never from code or git).
Each endpoint is cached as <cache_dir>/<path>.json (raw body) plus a .etag sidecar, so
re-runs revalidate with If-None-Match instead of re-downloading.
"""
from __future__ import annotations

import json
import os
import time
from pathlib import Path

import requests

BASE_URL = "https://www.thebluealliance.com/api/v3"
KEY_ENV = "TBA_API_KEY"


class TBAError(RuntimeError):
    pass


class TBAClient:
    def __init__(self, api_key=None, cache_dir="data/cache", session=None,
                 offline=False, max_retries=3, sleep=time.sleep):
        self.api_key = api_key if api_key is not None else os.environ.get(KEY_ENV)
        self.cache_dir = Path(cache_dir)
        self.session = session or requests.Session()
        self.offline = offline
        self.max_retries = max_retries
        self._sleep = sleep

    # ---- cache -------------------------------------------------------------
    def _paths(self, path: str):
        name = path.strip("/").replace("/", "__")
        return self.cache_dir / f"{name}.json", self.cache_dir / f"{name}.etag"

    def _read_cache(self, path: str):
        body, _ = self._paths(path)
        return json.loads(body.read_text()) if body.exists() else None

    def _write_cache(self, path: str, text: str, etag):
        body, etag_file = self._paths(path)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        body.write_text(text)
        if etag:
            etag_file.write_text(etag)
        elif etag_file.exists():
            etag_file.unlink()

    # ---- requests ----------------------------------------------------------
    def get(self, path: str):
        """Return parsed JSON for an API path such as '/events/2020'."""
        cached = self._read_cache(path)
        # Offline, or no key but we already have the data: serve from cache only.
        if cached is not None and (self.offline or not self.api_key):
            return cached
        if self.offline:
            raise TBAError(f"{path} is not in the cache and offline mode is on")
        if not self.api_key:
            raise TBAError(f"{KEY_ENV} is not set and {path} is not cached. "
                           "Get a free key at https://www.thebluealliance.com/account")

        headers = {"X-TBA-Auth-Key": self.api_key}
        _, etag_file = self._paths(path)
        if cached is not None and etag_file.exists():
            headers["If-None-Match"] = etag_file.read_text().strip()

        for attempt in range(self.max_retries + 1):
            resp = self.session.get(BASE_URL + path, headers=headers, timeout=30)
            if resp.status_code == 304 and cached is not None:
                return cached
            if resp.status_code == 200:
                self._write_cache(path, resp.text, resp.headers.get("ETag"))
                return resp.json()
            if resp.status_code in (429, 500, 502, 503, 504) and attempt < self.max_retries:
                self._sleep(2 ** attempt)
                continue
            raise TBAError(f"GET {path} failed with HTTP {resp.status_code}")
        raise TBAError(f"GET {path} failed")  # pragma: no cover

    # ---- endpoints ---------------------------------------------------------
    def events(self, year: int):
        return self.get(f"/events/{year}")

    def event_matches(self, event_key: str):
        return self.get(f"/event/{event_key}/matches")

    def event_alliances(self, event_key: str):
        return self.get(f"/event/{event_key}/alliances")

    def event_rankings(self, event_key: str):
        return self.get(f"/event/{event_key}/rankings")
