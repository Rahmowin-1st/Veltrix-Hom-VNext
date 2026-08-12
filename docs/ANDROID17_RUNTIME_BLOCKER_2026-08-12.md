# Android 17 Runtime Final Gate Blocker — 2026-08-12

## Status

**PART 1 NOT COMPLETE**

This file records the exact remaining blocker after the backend/core/intelligence foundation, real server/storage/database gates, Android build gates, and API 37 install work were exercised.

## VERIFIED

- Product baseline commit: `dd6defbab6165a1cf3942e7032160509884f8ee3` (V10 product APK source baseline).
- Exact production APK SHA256: `4a70f76e971ed11a58cd125da050bef23b61223aa148e353994b4912d69342da`.
- Exact instrumentation APK SHA256: `a6afe3978740c081edc8b1b1a4de785150cb1e16d8017a3a4e54ff32fe8fcd49`.
- APK badging reports package `com.veltrix.hom.vnext.dev`, compile/target SDK 37, and launchable activity `com.veltrix.hom.vnext.MainActivity`.
- Source manifest declares `MainActivity` exported with `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`.
- Debug application ID is intentionally `com.veltrix.hom.vnext.dev` via `applicationIdSuffix = ".dev"`.
- Real PostgreSQL 17 + pgvector, MinIO/S3-compatible storage, Ktor health/readiness/runtime, deterministic regressions, structure/OpenAPI/security gates, Android APK build, Android instrumentation APK build, API 37 boot/install evidence exist in prior accepted evidence chain.
- V13/V14 evidence showed the Android 17 Google APIs emulator graphics stack can restart/crash SurfaceFlinger/SystemUI around RegionSampling/`mapper.ranchu`; this is not an APK manifest omission.
- A targeted SystemUI-isolation probe was added at `.github/workflows/android17-systemui-isolation-probe.yml` to separate emulator SystemUI graphics failure from app/runtime failure.

## CURRENT EXTERNAL BLOCKER

GitHub Actions is not starting new hosted-runner jobs for this account/repository.

GitHub check-run annotation on V16 and on the targeted SystemUI isolation probe states:

> The job was not started because recent account payments have failed or your spending limit needs to be increased. Please check the 'Billing & plans' section in your settings

The affected jobs have `runner_id=0` and `steps=[]`, proving they failed before any workflow/product command executed.

Examples:

- V16 run: `31603887088`, job `94137778636`, commit `ff973ff167bea5cce60f4821d5793ec7005fcf56`.
- Targeted isolation probe run: `31604098366`, job `94138479548`, commit `d7cf86a9232ac6d05bd90553705b5774f1f4f10c`.

## WHY NO MORE BLIND VERSIONS

The next run must not repeat full backend/server gates until the Android blocker is isolated. The focused probe does only:

1. Import the exact V10 APKs and verify SHA256.
2. Boot Android 17 / API 37 Google APIs x86_64.
3. Record framework/SystemUI/SurfaceFlinger process identity.
4. Disable `com.android.systemui` for the probe only, to isolate the known SystemUI/RegionSampling graphics path.
5. Verify PackageManager sees the app.
6. Verify `dumpsys package` contains `MainActivity`.
7. Resolve the MAIN/LAUNCHER activity device-side.
8. Cold-launch the resolved component and require stable `system_server` + `surfaceflinger` plus a live app process/activity state.
9. Run `ShellInstrumentedTest` and require `OK (1 test)` with stable framework processes.

If this focused probe fails with stable framework state, treat it as a real product/runtime failure and fix that exact failure before any finalizer run.

If this focused probe passes, run one final full Android gate with the same isolation and require, in order:

- API 37 boot
- exact APK install
- launcher resolve
- cold launch/activity state
- shell instrumentation
- real server integration on device
- process-death persistence
- offline persistence
- full connected 6-test instrumentation suite
- final cold launch
- artifact/source/evidence/handoff packaging
- SHA256/provenance verification

Only after those hard markers are GREEN may the status be:

`VELTRIX HOM vNEXT PART 1 BACKEND / CORE / INTELLIGENCE FOUNDATION: ACCEPTANCE CANDIDATE — FINAL GATE GREEN`

## LOCAL ENVIRONMENT LIMITATION

The current assistant container was checked as a fallback. It has no `/dev/kvm`, no Android SDK/emulator installation, and therefore cannot honestly substitute for the required real Android 17 emulator/device gate. Static verification of the exact V10 source/APKs was performed locally instead; device-only claims remain NOT VERIFIED until an actual Android runtime executes them.

## NEXT EXECUTION PATH

Once a usable Android-capable runner is available, execute the targeted isolation probe first. Do not create another numbered finalizer unless that probe produces a new, evidence-backed root cause. If the probe is GREEN, promote that exact configuration into one final full gate and package the acceptance-candidate artifacts only after all hard markers pass.
