# Feature Inventory

| Capability | vNext Part 1 system | Persistence/API | Project/Memory/AI | Runtime gate |
|---|---|---|---|---|
| Chat | scoped streaming brain | messages/attachments/citations | Project + Memory + RAG + tools | Ktor smoke + server tests |
| Projects | operating workspace | projects/goals/instructions/snapshot | primary scope | server tests + HTTP |
| Sources | durable ingestion/RAG | S3 + chunks + vectors | Project links + citations | MinIO/pgvector HTTP flow |
| Tutor/Modes | context policy | typed mode contract | AI planner | AI fingerprint smoke |
| Practice | deep session engine | PostgreSQL state | Mistakes/signals | server integration |
| Tests/Quizzes | assessment engine | durable attempts | Mistakes/signals | server integration |
| Flashcards | deterministic SRS | durable schedule | Project/source/mistake | server + Android persistence |
| Mistakes | recurrence/mastery | durable engine | Practice/Memory signals | server integration |
| Translate | adapter service | API contract | optional Project | deterministic CI only unless live provider |
| Calculator/tools | deterministic bridge | tool invocation | Chat allowed tools | server integration |
| Notifications/Settings | typed preferences | PostgreSQL/API | account/Project defaults | server tests |
| Account/Profile | durable auth/profile | PostgreSQL/API | root ownership | HTTP runtime |
| History/Timeline | meaningful events | activity_event | Part 2 hook | server integration |
