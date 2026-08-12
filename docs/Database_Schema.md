# Database Schema

Migrations V001–V004 define account/session, projects/goals, memory/evidence, sources/chunks, chats/messages, notes, assessments, flashcards, mistakes, sync/idempotency, activity, source processing, vector embeddings, post-response jobs, deep practice, attachments/citations, generated artifacts and related indexes. Ownership columns are account-scoped and hot paths receive indexes. Vector rows retain source/version/chunk/text-hash/model provenance.
