# Map Generation

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

Map content generation has a typed `MapContentDraft` shape. The deterministic provider emits five sequential Foundation units with exact completion event types/counts. AI is an optional content-shape provider only. `MapContentValidator` rejects wrong unit count/ordinals, unsupported event criteria, invalid required progress and dependency cycles; invalid AI output falls back to deterministic content.

Economic fields remain outside the AI content authority. XP/Coin/item rewards come from versioned server/database definitions. Executed proof: `Part2CompletionSystemsTest.invalidAiMapFallsBackAndAiHasNoEconomicAuthority`, `validAiMapSuppliesContentShapeOnly`, and deterministic structural test.
