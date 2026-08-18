# Google Auth Security Report

## Threats covered
- forged/tampered token → cryptographic signature verification;
- wrong OAuth client → audience validation;
- attacker issuer → issuer allowlist;
- stale token → expiry/time validation;
- ambiguous provider identity → required `sub`;
- intercepted/replayed exchange → request nonce + durable hash-only replay row;
- account takeover by email collision → explicit collision rejection unless identity is already linked;
- deleted-account resurrection → persistent provider-subject tombstone survives purge;
- refresh replay race → row lock plus compare-and-swap refresh hash rotation;
- bearer leakage → raw Google token/nonce not persisted or intentionally logged; Veltrix session secrets stored hashed.

## Executed backend-owned proof
The source-audit gate executed a real RSA signed Google-shaped fixture through `GoogleIdTokenVerifier` and rejected wrong nonce, audience, issuer, expiry, missing subject and tampered signature. Real PostgreSQL tests executed replay, concurrent exchange, session rotation, logout, deletion, purge, tombstone and cross-account isolation.

## External boundary
No report may call live Google sign-in fully verified unless a real Google-issued token from the configured production client is exchanged against a deployed backend. Absence of that external token does not weaken or bypass production verification code.