# Final Product Constitution — Backend/App Foundation

1. Permissions, account ownership, XP, Coins, Store price/ownership, avatar equip, Map eligibility, seasons and deletion are server-authoritative.
2. Account/project scope is enforced before DB/search/memory/AI-context return.
3. Student Model memory is evidence-backed, scoped, confidence-bearing and user-correctable/deletable.
4. AI is advisory: it may propose/explain/draft, never bypass deterministic/user authority.
5. Offline-capable Android state is persisted; mutation queues survive process death and use stable IDs/revisions/idempotency.
6. Conflicts are explicit; silent last-write-wins is not a universal rule.
7. API/schema/definitions are versioned.
8. Offline/provider success is never fabricated.
9. Export/delete/session revocation/federated identity deletion are backend-owned.
10. VERIFIED requires executed PostgreSQL, HTTP, Android runtime/offline/process-death, security/performance and provenance proof.
11. Frontend owns final visuals but must consume backend contracts without reimplementing authority.
12. No privileged server/provider secret belongs in the APK.

Canonical release truth is the exact commit packaged by the final GREEN workflows plus `PROVENANCE.txt` and `SHA256SUMS.txt`.