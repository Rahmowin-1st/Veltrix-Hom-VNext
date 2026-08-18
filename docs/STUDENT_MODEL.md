# Student Model

`student_signal` is evidence-backed scoped state, not a transcript dump. Public DTOs expose type, value JSON, confidence, evidence refs, source, status, timestamps, review/supersession data and revision.

Signals support ACTIVE/CONFIRMED/REJECTED/ARCHIVED/SUPERSEDED. Correction/state/delete require revisions; user correction supplies explicit evidence and overrides inferred state. Personalization consumes eligible active/confirmed scoped signals, not raw chat history.

Global context excludes unrelated Project signals; Project context can combine global + same-project evidence. Snapshot maturity is based on durable evidence categories/counts, not simple message count.