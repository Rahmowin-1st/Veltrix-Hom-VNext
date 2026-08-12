# Offline and Sync

Android persists profile/Project snapshots, notes, flashcard state, assessment progress and a sync queue. Server mutations use local mutation IDs, idempotency keys, revision conflict handling and durable idempotency records. Duplicate delivery produces one server effect; content conflicts are explicit rather than silent overwrite. Offline AI is not faked.
