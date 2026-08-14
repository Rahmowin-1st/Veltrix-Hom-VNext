# Level Curve V1

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Version: `level-curve-v1`. Max level: 50. Cumulative threshold is `round(100*(level-1)^1.85 + 150*(level-1))`, with level 1 fixed at 0 XP. Level lookup is monotonic binary search over thresholds. At level 50, progress fraction is 1.0 and next-level requirement is 0.

The exact 50 thresholds are exported machine-readably in `level-curve-v1.json` and also embedded in `progression-policy-v1.json`. `Part2GameSystemsTest.levelCurveIsMonotonicAndHasExactBoundaries` checks every boundary; `progressFractionIsBounded` checks projection behavior.
