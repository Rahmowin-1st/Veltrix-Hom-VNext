# Season Identity

Season state is versioned durable game-account data with lifecycle window/state and identity metadata. History exposes season ID/version/state/start/end/identity metadata plus units completed, XP earned and Coins earned.

Rollover closes seasonal progress while preserving lifetime progression, balances and ownership. `WorldContinuityResponse` carries stable account/avatar/progression/project/map/season IDs across surfaces.