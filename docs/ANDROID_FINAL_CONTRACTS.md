# Android Final Backend Contracts

Typed models: `HomeFinalModel`, `PersonalFinalModel`, `ProjectWorkspaceFinalModel`, `ContextCarryModel`, `UniversalCommandResultModel`, `FrontendSemanticEventModel`, `SearchResultModel`, `RepositoryState<T>`. Freshness is FRESH/STALE/OFFLINE.

`Part3LocalDatabase` stores snapshot payload/revision/freshness, ContextCarry revision/sync state and frontend-event consumption. Existing core Room DB remains mutation queue/sync foundation.

Network JSON parsing is contained in data source/repository code; higher layers receive typed models. WorkManager persists unique sync. Final artifacts compile/target SDK 37 while final functional CI runs Android 16/API36 Google APIs x86_64 including offline/process-death.