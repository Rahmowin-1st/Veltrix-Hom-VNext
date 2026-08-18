# ContextCarry

ContextCarry preserves optional Project, authorized Source IDs, conversation, assessment, topic, Learning Mode, origin and return destination. It is account-scoped and revisioned.

Server `PUT /context-carry` re-authorizes each referenced object and requires expected revision. Android persists ContextCarry, can update local state first, queues a stable sync mutation and uses WorkManager to drain. Server sync enforces ownership, revision and idempotency; ACK updates local revision/state.

Carried IDs are continuity metadata, never permission.