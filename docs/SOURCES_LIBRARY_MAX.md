# Sources / Library — Final

Sources support upload/create, metadata update/delete, processing retry, text ingestion, storage lookup, Project link/unlink, annotations, relationships and hybrid search.

The pipeline validates size/MIME, persists durable metadata/job state, extracts/OCRs through adapters, normalizes/chunks, indexes lexical text and embeddings, and reaches READY only after required processing. Ownership/Project filters are applied before search return.

Part 3 source relationships are typed; AI-suggested relationships default unaccepted. ContextCarry can retain authorized Source IDs. External storage/OCR provider behavior remains a deployment boundary.