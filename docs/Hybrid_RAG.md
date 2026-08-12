# Hybrid RAG

Hybrid retrieval combines PostgreSQL lexical ranking, exact-match bonus and pgvector similarity under account/source/Project/version filters. Deterministic embeddings make CI reproducible; production embedding remains an adapter. Unchanged text hashes avoid unnecessary re-embedding.
