# Migration Report — Final

Flyway V001–V009 is the only release schema path. Final CI runs PostgreSQL 17 + pgvector, applies the chain, then executes full core/server regressions.

V007 is broad Part 3 capability expansion; V008 adds final-depth/versioned behavior; V009 closes identity/session/deletion integrity. No manual production schema edits are part of the contract. `evidence/migrations.txt` must show V007/V008/V009 successful on the exact candidate.