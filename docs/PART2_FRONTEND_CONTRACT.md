# Part 2 → Part 3 Frontend Contract

Backend owner: Veltrix Back-end Agent. Frontend may replace every pixel without changing these semantics.

## Authority

- Server is authoritative for XP, Level, Coins, rewards, purchases, ownership, equipped avatar validation, achievements, Map eligibility/completion and season state.
- Android caches Part 2 snapshots for read continuity only. It never invents or queues offline Coin spends, rewards, purchases or entitlements.
- All public mutations require the authenticated account inferred from the server session; account IDs in client payloads are not authority.

## Primary read contracts

- `GET /v1/game/profile` — compact account game snapshot: Level/XP progress, Coins, consistency, avatar, achievement/inventory summaries, Map eligibility/state, current season and lifetime statistics.
- `GET /v1/game/xp` — authoritative XP ledger history.
- `GET /v1/game/coins` — authoritative Coin ledger history.
- `GET /v1/game/coins/reconciliation` — projection-vs-ledger diagnostic.
- `GET /v1/game/stats` — lifetime server-derived game statistics.
- `GET /v1/game/events` — observable state transitions for refresh/invalidation.
- `GET /v1/achievements`, `/v1/inventory`, `/v1/avatars` — semantic collection contracts.
- `GET /v1/store` — server-authoritative catalog, prices, ownership/availability and current balance.
- `GET /v1/personal/map` — semantic map/unit states; no visual coordinates.
- `GET /v1/seasons/current` — active season and account seasonal progress.
- `GET /v1/notifications/intents` — backend notification intents. Part 2 does not generate streak-pressure notifications.

## Mutation contracts

- `POST /v1/store/purchase` body `{itemId,idempotencyKey}`. Server selects authoritative price and commits spend+purchase+ownership atomically. Retry with the same key returns the same logical purchase.
- `POST /v1/avatars/equip` body `{avatarId,expectedRevision}`. Only owned/allowed avatars can be equipped.
- `POST /v1/personal/map/unlock` has no client-provided Level/maturity; server evaluates both gates.
- `POST /v1/personal/map/units/{unitId}/start` body `{expectedRevision}`. Completion itself is evidence-driven by meaningful Part 1 events, not a client completion button.

Controlled XP/Coin adjustments, reversals and Store refunds exist as server-side operational services in Part 2 (`Part2CompletionRepository`). They are deliberately not exposed as arbitrary client-value endpoints; a future product-approved refund UI can receive a narrow purchase-ID/idempotency endpoint without ever accepting a client refund amount.

## Map semantics

Unlock requires both:

1. Profile Level >= 5; and
2. Memory Maturity `SUFFICIENT` or `STRONG`.

Frontend may display `levelRequirement`, `memoryRequirement`, satisfaction booleans and `unlockState`; it must not infer unlock truth itself.

Unit state vocabulary: `HIDDEN`, `LOCKED`, `AVAILABLE`, `IN_PROGRESS`, `COMPLETED`, `REWARD_GRANTED`. Future units stay hidden until prerequisites are satisfied. Rendering position/shape/animation is Part 3-owned.

Map content provider architecture accepts validated AI content proposals only. AI cannot provide XP/Coin values or decide completion/unlock/entitlement truth; deterministic templates are the fallback.

## Avatar semantics

Canonical tiers: `NOOB`, `PRO`, `ELITE`, `SUPER`, `ULTRA`, `MAX`, `HYPERPRO`, `LEGENDARY`.

Use `avatarId`, `assetKey`, `tier`, `owned`, `equipped`, `unlockState`, `requirements` and Store price as semantic inputs. No final artwork/layout contract is defined here.

## Offline/process-death

`Part2GameCacheDatabase` stores server snapshots with server revision and refuses an older revision overwriting a newer snapshot. Reopening the file-backed Room database is an acceptance test. Economy mutations are online-only; the server idempotency key handles network retry ambiguity.

## Refresh strategy

Prefer game-state events/revisions to decide what to refresh. Do not poll or refetch every screen open. Home/Personal consume their Part 1 aggregators, which already include Part 2 semantic game summaries.
