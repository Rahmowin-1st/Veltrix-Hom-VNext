# Known Limitations — Backend Final Closure

- Live production AI/OCR/translation providers remain external deployment boundaries; CI uses deterministic test adapters where configured. AI is never permissions/economy authority.
- CI performance measurements are smoke/regression proof, not production-scale SLO certification.
- Final Android functional proof uses Android 16/API36 Google APIs x86_64 while compile/target SDK are 37; physical-device/OEM behavior is not claimed.
- Final frontend visual polish remains outside backend scope.
- Live Google-account sign-in requires real Google OAuth/Android credentials, configured public server client ID, a real Google-issued ID token and a reachable deployed backend. Backend-owned cryptographic verification, nonce, replay, account-link, session, deletion and isolation logic are independently executable in CI, but live Google-account E2E is not claimed without that external token.
- No claim is made that data can never be lost; durability depends on deployment backup/restore and provider availability policies.

These limitations do not turn implemented backend functionality into placeholders and do not permit bypassing security or authority invariants.