# Android Domain Contracts

Android owns local state and presentation-facing repositories; server/domain logic remains replaceable behind typed contracts. Required states include loading, success, empty, error, offline/stale, processing, pagination, mutation progress, conflict and retry. Debug package is `com.veltrix.hom.vnext.dev`; release base package is `com.veltrix.hom.vnext`.
