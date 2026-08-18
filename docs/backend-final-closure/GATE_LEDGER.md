# Gate Ledger — Backend Final Closure

| Gate | Purpose | Decisive proof | Status before canonical final gate |
|---|---|---|---|
| B01–B06 | restore exact source/baselines/frontend blocker | Git ancestry, pack audit, issue #4 | VERIFIED |
| B07 | freeze `/v1/auth/google` DTO | OpenAPI + frontend DTO audit | VERIFIED |
| B08–B14 | production verifier + signature/aud/iss/exp/sub/nonce | GoogleIdTokenVerifier + signed RSA fixture matrix | VERIFIED backend-owned crypto/contract |
| B15–B21 | replay/link/collision/takeover/delete | real PostgreSQL integration tests | VERIFIED |
| B22–B30 | SessionResponse/hash/logout/refresh/delete/export/password/private auth | real PostgreSQL + Ktor tests | VERIFIED |
| B31 | cross-account isolation | adversarial integration tests | VERIFIED |
| B32–B48 | preserved Part1/2/3 product contracts | frozen regressions + existing runtime provenance | VERIFIED/preserved; final gate checks affected boundaries |
| B49–B50 | V009 upgrade + real PostgreSQL | pgvector/PostgreSQL 17 CI | VERIFIED |
| B51 | restart durability | persisted identity/session/tombstone state + final runtime | canonical final gate |
| B52 | OpenAPI drift | 148 route operations == 148 OpenAPI operations | VERIFIED |
| B53–B55 | security/redaction/rate limits | source invariants + tests + APK/source scan | final gate seals |
| B56 | performance sanity | real HTTP performance smoke | final gate |
| B57 | exact frontend compatibility | `{idToken,nonce}` → existing SessionResponse + Store DTO | VERIFIED; final gate seals |
| B58 | real HTTP runtime | exact Ktor socket smoke | final gate |
| B59 | provenance | exact source/APKs/evidence/SHA256SUMS | final gate |
| B60 | no known backend P0/P1 | self-red-team + clean required gates | final gate |

Live Google-account E2E is tracked separately from backend-owned cryptographic verification. It is not marked VERIFIED without a real Google-issued token.