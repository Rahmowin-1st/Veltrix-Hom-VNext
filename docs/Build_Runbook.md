# Build Runbook

Prerequisites: JDK 17; Gradle 9.5.1-compatible environment; Android SDK platform/build tools 37 for Android builds; PostgreSQL 17-compatible service with pgvector for server integration. CI may additionally use an S3-compatible test service.

Core/server verification:
`gradle --no-daemon :core:test :server:test`

Server runtime:
`gradle --no-daemon :server:run`
then verify `/health` and `/ready`.

Android verification build:
`gradle --no-daemon -PVELTRIX_API_BASE_URL=<test-base-url> :android:app:testDebugUnitTest :android:app:assembleDebug :android:app:assembleDebugAndroidTest`

Expected APK: `android/app/build/outputs/apk/debug/app-debug.apk`; instrumentation APK is under `android/app/build/outputs/apk/androidTest/`.

Final CI must test the exact branch HEAD, confirm a clean checkout, preserve target SDK 37, execute Android functional runtime gates, then package exact tested source, APK/test APK, evidence, contracts/docs, provenance and SHA-256 sums.