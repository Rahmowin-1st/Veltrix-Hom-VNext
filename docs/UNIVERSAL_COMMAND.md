# Universal Command

`POST /commands/resolve` exposes deterministic command classification from `UniversalCommandEngine`. The typed response includes kind, deterministic flag, confirmation requirement and optional target/deep-link/query/Project/Source diagnostics.

It is intentionally not an unbounded LLM router. Deterministic intents remain deterministic; sensitive/destructive operations still require normal confirmation/authorization. Android consumes `UniversalCommandResultModel`.