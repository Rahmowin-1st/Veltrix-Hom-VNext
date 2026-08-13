# Data Model

Server persistence is PostgreSQL with migrations V001–V004. Major account-scoped aggregates include accounts/sessions, projects/goals, notes, sources/source versions/chunks/embeddings, conversations/messages/attachments/citations, assessments/attempts/answers, practice sessions, flashcard schedules/reviews, mistakes/learning signals, activity events, sync/idempotency records and generated/post-response work.

Source retrieval rows retain source/version/chunk/text-hash/model provenance. Ownership is account-scoped and hot paths are indexed.

Android Room is a local/offline projection containing profile, project, goal, note, source, conversation/message, assessment attempt/answer, practice, mistake, flashcard schedule, cached snapshot and queued sync mutation entities. Room is version 2 with explicit migration 1→2; local state is not an authorization boundary.

Canonical schema details: `Database_Schema.md`, server migration files, and `android/app/src/main/kotlin/com/veltrix/hom/vnext/LocalDatabase.kt`.