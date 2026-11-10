PY ?= .venv/bin/python

.PHONY: setup data opr-eval test

setup:
	python3 -m venv .venv
	.venv/bin/pip install -e ".[dev]"

# Pulls Infinite Recharge (2020, 2021) events from The Blue Alliance, caches raw JSON in
# data/cache, writes data/processed/*.csv and reports/data_counts.{md,json}.
# Requires TBA_API_KEY in the environment (never commit it).
data:
	$(PY) -m analytics.data --years 2020 2021

# Needs `make data` first. Writes reports/opr_mae_by_k.{png,csv,md}.
opr-eval:
	$(PY) -m analytics.opr

test:
	$(PY) -m pytest -q
