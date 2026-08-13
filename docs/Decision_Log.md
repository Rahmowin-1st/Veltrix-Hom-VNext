# Decision Log

- PostgreSQL is the authoritative server-state store; object storage owns file bodies; deterministic domain rules do not depend on an LLM.
- Android is an untrusted client. Privileged provider credentials remain server-side.
- Room provides Android offline/local durability with explicit migrations and queued sync mutations.
- API/schema compatibility remains compile/target SDK 37. Final functional emulator acceptance uses stable Android 16/API36 normal services because the hosted API37 image repeatedly restarted core framework services; this does not lower the product SDK contract.
- Assessment read models must not expose answer keys before submission.
- Wrong-answer learning signals preserve source provenance when the source is known.
- Store/economy behavior is not implemented in Part 1; only an explicit placeholder is allowed.
- Final visual design/polish is deferred to frontend scope; Part 1 Android UI is a functional developer harness.
- A gate is VERIFIED only by an executed result tied to an exact commit; historical GREEN evidence is reused only when its inputs are unaffected.