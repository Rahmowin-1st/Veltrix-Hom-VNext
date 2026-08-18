# AI Context Router

The context pipeline composes bounded context from conversation scope, active Project instruction, authorized Sources, ContextCarry and eligible Student Model evidence.

Rules: Global Chat gets account-global eligible signals; Project Chat may get global + that Project; unrelated Projects are excluded; retrieval applies ownership before context assembly; diagnostics expose safe IDs/confidence/evidence refs rather than a raw memory dump.

The model cannot grant permissions, mutate economy, silently accept subjective goals or bypass server validation. Structured/tool output remains behind server authorization and validation.