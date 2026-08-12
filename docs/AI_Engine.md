# AI Engine

AI execution is provider-independent. A deterministic provider exists only for test environment; production routing excludes test adapters. The OpenAI Responses adapter implements server-side streaming, timeout/cancellation, error mapping, retry/fallback support and structured generation. Chat calls the context orchestrator before provider routing. Private chain-of-thought is never a persistence target.
