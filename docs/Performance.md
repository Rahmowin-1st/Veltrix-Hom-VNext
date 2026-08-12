# Performance

Architecture avoids loading full histories at startup: Home/Project snapshots are bounded, lists paginate, source chunks remain server-side, and Android renders cached state first. Hot PostgreSQL paths are indexed in migrations. Production-scale latency claims are not made from tiny deterministic fixtures; final CI reports foundation timing only.
