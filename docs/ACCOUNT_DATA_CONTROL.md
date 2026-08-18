# Account Data Control

`GET /account/export` returns schema-versioned account-owned JSON data with counts and payload SHA-256; auth/session secrets are excluded and export auditing is durable.

Deletion requires re-authentication + explicit DELETE intent, deactivates account/revokes sessions, enters durable deletion lifecycle and purges owned product rows. V009 covers federated identity deletion/tombstone/session integrity.

Google identity exchange verifies signed claims, nonce/replay and account linking server-side. Live Google-account E2E requires real external OAuth credentials/token and is not claimed when unavailable in CI.