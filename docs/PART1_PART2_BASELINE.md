# Part 1 + Part 2 Baseline

Part 3 expands rather than restarts the accepted baseline.

Part 1 preserves Chat, Projects, Sources/Library, notes, learning modes, assessments/practice/quizzes, flashcards, mistakes, memory foundations, tools/translate, typed errors, idempotency and Android persistence. The final gate executes frozen local regressions and requires `LOCAL_REGRESSION_GATE=PASS total=105`.

Part 2 preserves server-authoritative game profile, XP/Coin ledgers, Store, inventory, achievements, avatars/equip, Personal Map, seasons, statistics, notifications and account export/delete compatibility. Final CI reruns Part 2 tests and HTTP smoke.

Historical GREEN gates reopen only when affected inputs change, plus explicit final clean proof. Exact release truth comes from the final package provenance.