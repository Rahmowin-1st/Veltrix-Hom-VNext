# Tool Bridge

Deterministic tools are registered server-side and validated before execution. Chat receives only allowed tool IDs; tool results are treated as authoritative and persisted/returned as metadata. Tool loops are bounded by the server contract rather than letting a model invent calculator/unit/date results.
