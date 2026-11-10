"""SYNTHETIC multi-event season with known robot strengths, for unit tests only."""
import numpy as np
import pandas as pd

COMPS = ("auto", "teleop", "endgame")


def make_season(n_events=8, seed=0, n_pool=70, n_teams=24, n_qm=60, noise=3.0):
    rng = np.random.default_rng(seed)
    pool = np.arange(100, 100 + n_pool)
    truth = {int(t): dict(auto=rng.uniform(2, 14), teleop=rng.uniform(8, 40), endgame=rng.uniform(0, 20)) for t in pool}
    tot = lambda t: sum(truth[int(t)].values())
    events, matches, alliances, rankings = [], [], [], []
    for i in range(n_events):
        key = f"2020ev{i}"
        start = f"2020-{1 + i // 4:02d}-{1 + 7 * (i % 4):02d}"
        events.append({"event_key": key, "start_date": start})
        teams = rng.choice(pool, n_teams, replace=False)
        got = {int(t): [] for t in teams}
        for m in range(1, n_qm + 1):
            six = rng.choice(teams, 6, replace=False)
            row = {"event_key": key, "comp_level": "qm", "match_number": m}
            for side, trio in (("red", six[:3]), ("blue", six[3:])):
                comps = {c: sum(truth[int(t)][c] for t in trio) + rng.normal(0, noise) for c in COMPS}
                for j, t in enumerate(trio, 1):
                    row[f"{side}{j}"] = int(t)
                for c in COMPS:
                    row[f"{side}_{c}_points"] = comps[c]
                row[f"{side}_score"] = sum(comps.values())
                for t in trio:
                    got[int(t)].append(row[f"{side}_score"])
            matches.append(row)
        order = sorted(got, key=lambda t: -np.mean(got[t]))
        rankings += [{"event_key": key, "team": t, "rank": r + 1} for r, t in enumerate(order)]
        captains, free = order[:8], list(order[8:])
        picks1, picks2 = {}, {}
        for s in range(8):
            b = max(free, key=lambda t: tot(t) + rng.normal(0, 2)); free.remove(b); picks1[s] = b
        for s in reversed(range(8)):
            b = max(free, key=lambda t: tot(t) + rng.normal(0, 2)); free.remove(b); picks2[s] = b
        members = [(captains[s], picks1[s], picks2[s]) for s in range(8)]
        scores = {s: [] for s in range(8)}
        for j in range(1, 17):
            a, b = rng.choice(8, 2, replace=False)
            row = {"event_key": key, "comp_level": "sf", "match_number": j}
            for side, s in (("red", a), ("blue", b)):
                for jj, t in enumerate(members[s], 1):
                    row[f"{side}{jj}"] = int(t)
                row[f"{side}_score"] = sum(tot(t) for t in members[s]) + rng.normal(0, noise)
                scores[s].append(row[f"{side}_score"])
            matches.append(row)
        best = max(scores, key=lambda s: np.mean(scores[s]) if scores[s] else -1)
        for s in range(8):
            alliances.append({"event_key": key, "seed": s + 1, "captain": members[s][0], "pick1": members[s][1],
                              "pick2": members[s][2], "pick3": np.nan,
                              "playoff_status": "won" if s == best else "eliminated"})
    return (pd.DataFrame(events), pd.DataFrame(matches), pd.DataFrame(alliances), pd.DataFrame(rankings))
