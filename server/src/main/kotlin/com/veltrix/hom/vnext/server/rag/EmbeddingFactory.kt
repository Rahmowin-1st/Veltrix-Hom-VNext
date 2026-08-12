package com.veltrix.hom.vnext.server.rag

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.server.ServerConfig

object EmbeddingFactory {
    fun create(config: ServerConfig): EmbeddingAdapter = when {
        config.environment == "test" && config.testEmbeddingEnabled -> DeterministicEmbeddingAdapter(config.embeddingDimensions)
        config.embeddingProvider.equals("openai", true) && !config.embeddingApiKey.isNullOrBlank() -> OpenAiEmbeddingAdapter(config.embeddingApiKey, config.embeddingBaseUrl, config.embeddingModel, config.embeddingDimensions)
        else -> DisabledEmbeddingAdapter(config.embeddingDimensions)
    }
}
