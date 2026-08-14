# Part 2 Progression Simulation — Reward V1

Status: initial backend tuning policy for Part 2. This is product tuning, not an immutable schema rule.
Authoritative exports: `docs/progression-policy-v1.json` and `core/.../Part2GameSystems.kt`.

## Invariants

- Level is 1–50 and derives only from authoritative lifetime XP.
- Level curve is monotonic and versioned as `level-curve-v1`.
- Reward policy is versioned as `reward-v1`.
- Client never chooses XP/Coins.
- Meaningful evidence, semantic-object deduplication, per-category soft/hard limits and the 450 XP / 90 Coin daily hard caps are applied before a grant.
- The first qualified local day may receive the configured daily bonus; app-open/navigation/retry activity receives zero.
- Policy rebalance does not recalculate historical lifetime XP, so tuning changes cannot silently remove earned levels.

## V1 curve checkpoints

| Level | Cumulative XP |
|---:|---:|
| 1 | 0 |
| 5 | 1,900 |
| 10 | 7,176 |
| 20 | 26,061 |
| 30 | 55,100 |
| 40 | 93,645 |
| 50 | 141,275 |

## Representative profiles

These are deterministic capacity/tuning models, not promises about a real user. Effective daily XP is an averaged mixed-feature outcome after eligibility and anti-farming. Representative normal mixes intentionally spread work across projects/goals, assessments, practice/mistake-resolution, knowledge/flashcards and notes/chat; no normal profile assumes one event type supplies most rewards.

- **LIGHT** — ~55 effective XP/day; a small number of meaningful activities.
- **REGULAR** — ~145 effective XP/day; mixed learning/project activity.
- **HIGH_ACTIVITY** — ~260 effective XP/day; sustained mixed high-quality work.
- **ABUSIVE/FARMING** — models 1,800 raw attempted XP/day but only 450 can survive the absolute daily cap even before semantic duplicate/category rejection. Thus at least 75% of modeled raw farming attempts are suppressed.

## Results

| Profile | 7d | 30d | 90d | 180d | 365d | Min days to L50 |
|---|---|---|---|---|---|---:|
| LIGHT | L2 / 385 XP | L4 / 1,650 | L8 / 4,950 | L11 / 9,900 | L17 / 20,075 | 2,569 |
| REGULAR | L3 / 1,015 | L7 / 4,350 | L13 / 13,050 | L20 / 26,100 | L29 / 52,925 | 975 |
| HIGH_ACTIVITY | L4 / 1,820 | L10 / 7,800 | L18 / 23,400 | L27 / 46,800 | L40 / 94,900 | 544 |
| ABUSIVE* | L6 / 3,150 | L14 / 13,500 | L25 / 40,500 | L36 / 81,000 | L50 / 164,250 | 314 |

`*` ABUSIVE is a hard upper bound that assumes the attacker somehow supplies enough genuinely eligible evidence to saturate the global cap every day. Real duplicate/semantic/category checks reduce it further. Even this upper bound requires 314 capped days to reach Level 50, so raw tapping/retry loops cannot rush the curve.

## Interpretation

Early progress is visible: LIGHT reaches Level 2 in the first week and REGULAR reaches Level 7 around 30 days. Long-term progression slows: REGULAR remains around Level 29 after a year and HIGH_ACTIVITY around Level 40. Level 50 therefore represents sustained long-horizon accomplishment rather than a short burst.

Normal users are not penalized for taking breaks: there is no XP decay or level loss. Consistency history is separate from lifetime progression. The model also does not require any single feature to dominate: category caps and mixed representative workloads keep ordinary progression distributed across meaningful product domains.

## Reproducibility gate

Run:

```bash
python3 scripts/part2_progression_simulation.py --check docs/progression-policy-v1.json
```

CI must fail if the checked-in machine-readable policy/simulation export drifts from the deterministic generator.
