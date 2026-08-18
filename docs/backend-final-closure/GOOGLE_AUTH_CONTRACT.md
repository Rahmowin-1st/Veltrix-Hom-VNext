# Google Auth Contract

## Android → Backend
`POST /v1/auth/google`

Request:
```json
{"idToken":"<Google ID token>","nonce":"<request-bound nonce>"}
```

Response: the existing Veltrix `SessionResponse`; no Google token becomes an application session token.

## Verification sequence
1. Reject malformed/oversized token or nonce.
2. Production `GoogleIdTokenVerifier` validates RSA signature, trusted audience, issuer and token time claims against Google's rotating public keys.
3. Require stable provider subject (`sub`).
4. Require and constant-time compare the typed OIDC nonce claim.
5. Enter database transaction only after external token verification.
6. Insert a durable hash-only replay record; duplicate exchange is rejected atomically.
7. Resolve/link the federated identity by `(provider, provider_subject)`, never by display name.
8. Apply email-collision/takeover/deletion-tombstone policy.
9. Mint normal Veltrix access/refresh session; only hashes are persisted.

## Deployment configuration
`VELTRIX_GOOGLE_SERVER_CLIENT_ID=<public Google Web OAuth client ID>`; optional additional allowed IDs use `VELTRIX_GOOGLE_ALLOWED_CLIENT_IDS` comma-separated. No client secret belongs in the APK or repository.

A live Google-account E2E additionally needs real Google OAuth/Android credentials, a real Google-issued token and a reachable deployed backend. The backend test matrix does not fake that external proof.