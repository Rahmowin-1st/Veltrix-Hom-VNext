# Known Limitations — Part 2

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

- Live production AI/OCR/translation providers are external deployment boundaries; CI uses deterministic test adapters where configured. AI is not economic authority.
- Performance numbers are CI smoke measurements, not production load/SLO certification.
- Final Android functional proof is Android 16/API36 Google APIs x86_64 while the APK targets/compiles SDK 37. Physical-device camera/OS-vendor behavior is not claimed.
- Room compiler warned in the preserved Android build that `room.schemaLocation` is not configured. Room schema JSON export is therefore not a committed artifact. Android database/migration behavior itself was runtime-tested; this correction deliberately preserves unchanged Android inputs instead of changing release bytes only to silence a tooling warning.
- Account deletion is implemented as authenticated soft deletion plus session revocation. Account-owned Part 2 tables remain linked for the product's deletion-retention lifecycle; the public API cannot log into a deleted account. A separate destructive purge scheduler is not claimed.
- Part 3 final visual/UI composition is not backend scope.
