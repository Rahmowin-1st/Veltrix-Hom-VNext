# Android Part 2 Contracts

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Android uses `Part2GameClient` for `/v1/game/profile`, `/v1/personal/map`, `/v1/store`, `/v1/avatars`, Store purchase and avatar equip. `Part2GameCache` stores server payloads with server revision and rejects stale lower-revision writes after database reopen.

The app never computes authoritative XP, Coins, Store prices or ownership. Expected revision and idempotency keys are mutation guards. The final Manager workflow is allowed to reuse the exact runtime-tested APK/androidTest APK only after `git diff --quiet` proves Android, core and Android build inputs are unchanged from SHA `2e70908...`; server-source changes are independently tested against real PostgreSQL/Ktor.

Preserved runtime evidence: Android 16/API36 Google APIs x86_64, target/compile SDK 37, install+cold launch, full 15 connected tests, offline/process-death and targeted Part2 tests in run 31787591097.
