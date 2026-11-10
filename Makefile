PY ?= .venv/bin/python

.PHONY: setup data test

setup:
	python3 -m venv .venv
	.venv/bin/pip install -e ".[dev]"

# Pulls Infinite Recharge (2020, 2021) events from The Blue Alliance, caches raw JSON in
# data/cache, writes data/processed/*.csv and reports/data_counts.{md,json}.
# Requires TBA_API_KEY in the environment (never commit it).
data:
	$(PY) -m analytics.data --years 2020 2021

test:
	$(PY) -m pytest -q
