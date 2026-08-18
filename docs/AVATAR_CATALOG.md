# Avatar Catalog

`AvatarCatalogPolicy` validates the final catalog exposed by `/avatars/catalog`: NOOB 40, PRO 30, ELITE 20, SUPER 15, ULTRA 12, MAX 10, HYPERPRO 5, LEGENDARY 3 — 135 total.

Entries expose permanent name, asset key, tier/rarity, identity metadata, animation/behavior/preview capabilities, ownership/equip, optional Store price and catalog version. Ownership/equip truth is server-authoritative; frontend cannot fabricate it.