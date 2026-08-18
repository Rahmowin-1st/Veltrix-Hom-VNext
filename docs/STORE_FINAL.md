# Store — Final

Catalog/purchase are server-authoritative. Price, balance, ownership and purchase result use backend/database idempotency/concurrency protection; client-supplied price/balance is not trusted.

`StorePolicy` accepts explicit categories and rejects random-purchase/loot-box metadata. The semantic red-team gate permits only the defensive no-loot-box policy reference when the code proves random purchase tokens are rejected. AI cannot price/grant/randomly award ownership.