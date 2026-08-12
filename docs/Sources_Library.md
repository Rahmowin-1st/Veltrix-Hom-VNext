# Sources / Library

Source ingestion validates MIME/size, stores the object through `StorageAdapter`, extracts/OCRs content, normalizes/chunks, indexes lexical text, creates semantic embeddings and marks READY only after required indexing. Processing jobs are durable and diagnosable; local storage is development/test only.
