package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.server.ai.OpenAiResponsesProvider
import com.veltrix.hom.vnext.server.foundation.*
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class AiExecutionService(private val config: ServerConfig) {
    private val active = ConcurrentHashMap<String, RequestCancellation>()
    private val providers: List<ModelProviderAdapter> = buildList {
        if (config.aiProvider.equals("openai", true) && !config.aiApiKey.isNullOrBlank()) {
            add(OpenAiResponsesProvider(config.aiApiKey, config.aiBaseUrl, config.aiFastModel, ModelTier.FAST, Duration.ofSeconds(config.aiRequestTimeoutSeconds)))
            if (config.aiQualityModel != config.aiFastModel) {
                add(OpenAiResponsesProvider(config.aiApiKey, config.aiBaseUrl, config.aiQualityModel, ModelTier.HIGH_QUALITY, Duration.ofSeconds(config.aiRequestTimeoutSeconds)))
            }
        }
        if (config.testAiEnabled && config.environment == "test") add(DeterministicTestModelProvider())
    }

    val liveProviderConfigured: Boolean get() = providers.any { !it.testOnly && it.isConfigured() }
    val testProviderConfigured: Boolean get() = providers.any { it.testOnly && it.isConfigured() }
    val providerIds: List<String> get() = providers.filter { it.isConfigured() }.map { it.id + ":" + it.modelId }

    fun stream(requestId: String, request: AiProviderRequest, onAttempt: (ModelProviderAdapter, Int, String?) -> Unit = { _,_,_ -> }): Sequence<AiProviderChunk> {
        val cancellation = RequestCancellation()
        if (active.putIfAbsent(requestId, cancellation) != null) throw DomainException(DomainError("CONFLICT", ErrorCategory.CONFLICT, "AI request id is already active"))
        val routed = try {
            AIRequestRouter(providers, allowTestProviders = config.environment == "test" && config.testAiEnabled)
                .route(request.operation)
        } catch (t: Throwable) {
            active.remove(requestId)
            throw t
        }
        return sequence {
            var last: DomainException? = null
            try {
                for ((index, provider) in routed.withIndex()) {
                    cancellation.throwIfCancelled()
                    try {
                        onAttempt(provider, index + 1, null)
                        for (chunk in provider.stream(request, cancellation)) yield(chunk)
                        last = null
                        return@sequence
                    } catch (e: DomainException) {
                        onAttempt(provider, index + 1, e.error.code)
                        last = e
                        if (!e.error.retryable || index == routed.lastIndex) throw e
                    }
                }
                throw last ?: DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.AI_PROVIDER, "No provider completed the request", true))
            } finally {
                active.remove(requestId)
            }
        }
    }

    fun structured(requestId: String, request: AiProviderRequest, schemaName: String, schemaJson: String): String {
        val cancellation = RequestCancellation()
        if (active.putIfAbsent(requestId, cancellation) != null) throw DomainException(DomainError("CONFLICT", ErrorCategory.CONFLICT, "AI request id is already active"))
        try {
            val routed = AIRequestRouter(providers, allowTestProviders = config.environment == "test" && config.testAiEnabled)
                .route(request.operation, needsStreaming = false)
            var last: DomainException? = null
            for ((index, provider) in routed.withIndex()) {
                if (!provider.capabilities.structuredOutput) continue
                try { return provider.structuredGenerate(request, schemaName, schemaJson, cancellation) }
                catch (e: DomainException) { last=e; if(!e.error.retryable || index==routed.lastIndex) throw e }
            }
            throw last ?: DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "No configured provider supports structured output"))
        } finally { active.remove(requestId) }
    }

    fun cancel(requestId: String): Boolean = active[requestId]?.let { it.cancel(); true } ?: false
}
