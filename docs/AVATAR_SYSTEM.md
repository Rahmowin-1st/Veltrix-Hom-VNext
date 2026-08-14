# Avatar System

> **Part 2 current-state document.** The exact acceptance SHA/run/job are bound in the canonical package `PROVENANCE.txt`. Preserved Android runtime evidence is from product SHA `2e70908f65bb25d23e87a9ddc690b8ea09f3040e`, run `31787591097`, job `94726891516`; Manager-acceptance deltas are reverified on the final handoff SHA.

The avatar catalog defines eight tiers: NOOB, PRO, ELITE, SUPER, ULTRA, MAX, HYPERPRO and LEGENDARY. Each avatar has a stable asset key, unlock rule, optional Store price and catalog version.

Equipping is account-authoritative and requires actual inventory ownership. `equipped_avatar` is locked `FOR UPDATE`; caller supplies an expected revision, and only one concurrent mutation can win. The resulting revision and `AVATAR_EQUIPPED` event are durable.

Executed Manager proofs: `multiDeviceConcurrentAvatarEquipHasSingleWinnerAndConverges` and `avatarOwnershipEquipSurvivesServerRestartAndRelogin`.
