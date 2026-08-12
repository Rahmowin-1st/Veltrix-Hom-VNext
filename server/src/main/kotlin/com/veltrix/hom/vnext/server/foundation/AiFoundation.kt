package com.veltrix.hom.vnext.server.foundation

import com.veltrix.hom.vnext.core.*
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

enum class AiOperation { CHAT, TUTOR, SOURCE_REASONING, QUIZ_DRAFT, TEST_DRAFT, FLASHCARD_DRAFT, PRACTICE_DRAFT, MEMORY_SYNTHESIS, CLASSIFICATION, TRANSLATION, PROJECT_PLANNING }
enum class ModelTier { FAST, HIGH_QUALITY, LOCAL_OPTIONAL }

data class ModelCapability(
    val streaming: Boolean,
    val structuredOutput: Boolean,
    val vision: Boolean,
    val maxContextUnits: Int,
)

data class ModelPolicy(
    val operation: AiOperation,
    val preferredTier: ModelTier,
    val allowFallback: Boolean,
    val timeout: Duration,
    val maxOutputUnits: Int,
)

object ModelPolicyRegistry {
    private val policies = mapOf(
        AiOperation.CLASSIFICATION to ModelPolicy(AiOperation.CLASSIFICATION, ModelTier.FAST, true, Duration.ofSeconds(12), 300),
        AiOperation.TRANSLATION to ModelPolicy(AiOperation.TRANSLATION, ModelTier.FAST, true, Duration.ofSeconds(20), 2_000),
        AiOperation.MEMORY_SYNTHESIS to ModelPolicy(AiOperation.MEMORY_SYNTHESIS, ModelTier.FAST, true, Duration.ofSeconds(18), 800),
        AiOperation.CHAT to ModelPolicy(AiOperation.CHAT, ModelTier.HIGH_QUALITY, true, Duration.ofSeconds(75), 4_000),
        AiOperation.TUTOR to ModelPolicy(AiOperation.TUTOR, ModelTier.HIGH_QUALITY, true, Duration.ofSeconds(75), 4_000),
        AiOperation.SOURCE_REASONING to ModelPolicy(AiOperation.SOURCE_REASONING, ModelTier.HIGH_QUALITY, true, Duration.ofSeconds(90), 4_000),
        AiOperation.QUIZ_DRAFT to ModelPolicy(AiOperation.QUIZ_DRAFT, ModelTier.FAST, true, Duration.ofSeconds(45), 3_000),
        AiOperation.TEST_DRAFT to ModelPolicy(AiOperation.TEST_DRAFT, ModelTier.HIGH_QUALITY, true, Duration.ofSeconds(60), 5_000),
        AiOperation.FLASHCARD_DRAFT to ModelPolicy(AiOperation.FLASHCARD_DRAFT, ModelTier.FAST, true, Duration.ofSeconds(45), 4_000),
        AiOperation.PRACTICE_DRAFT to ModelPolicy(AiOperation.PRACTICE_DRAFT, ModelTier.FAST, true, Duration.ofSeconds(45), 3_000),
        AiOperation.PROJECT_PLANNING to ModelPolicy(AiOperation.PROJECT_PLANNING, ModelTier.HIGH_QUALITY, true, Duration.ofSeconds(60), 4_000),
    )
    fun forOperation(operation: AiOperation): ModelPolicy = policies.getValue(operation)
}

data class AiProviderRequest(
    val operation: AiOperation,
    val input: String,
    val context: PlannedContext? = null,
    val systemPrompt: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val maxOutputUnits: Int = ModelPolicyRegistry.forOperation(operation).maxOutputUnits,
)

data class AiProviderChunk(val text: String, val final: Boolean = false)

interface ModelProviderAdapter {
    val id: String
    val tier: ModelTier
    val modelId: String get() = id
    val capabilities: ModelCapability
    val testOnly: Boolean get() = false
    fun isConfigured(): Boolean
    fun stream(request: AiProviderRequest, cancellation: RequestCancellation = RequestCancellation()): Sequence<AiProviderChunk> =
        throw DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.AI_PROVIDER, "Provider execution is not configured", retryable = true))
    fun structuredGenerate(request: AiProviderRequest, schemaName: String, schemaJson: String, cancellation: RequestCancellation = RequestCancellation()): String =
        throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "Provider does not support structured generation", retryable = false))
}

class DeterministicTestModelProvider : ModelProviderAdapter {
    override val id: String = "MOCK_TEST_ONLY"
    override val tier: ModelTier = ModelTier.FAST
    override val capabilities = ModelCapability(streaming = true, structuredOutput = true, vision = false, maxContextUnits = 8_000)
    override val testOnly: Boolean = true
    override fun isConfigured(): Boolean = true
    override fun stream(request: AiProviderRequest, cancellation: RequestCancellation): Sequence<AiProviderChunk> = sequence {
        val normalized = request.input.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotEmpty()) { "AI test request input must not be blank" }
        val context = request.context
        val fingerprint = if (context == null) "ctx:none" else buildString {
            append("ctx:project=").append(context.projectId ?: "none")
            append(";instruction=").append(context.projectInstruction?.hashCode()?.toUInt()?.toString(16) ?: "none")
            append(";mem=").append(context.memories.size)
            append(";citations=").append(context.sourceCitations.size)
            append(";mode=").append(context.learningMode.name)
            append(";tools=").append(context.toolIds.sorted().joinToString(","))
        }
        val segments = listOf("TEST_ONLY: ", fingerprint, " | ", normalized.take(160))
        for ((index, segment) in segments.withIndex()) {
            cancellation.throwIfCancelled()
            yield(AiProviderChunk(segment, final = index == segments.lastIndex))
        }
    }
    override fun structuredGenerate(request: AiProviderRequest, schemaName: String, schemaJson: String, cancellation: RequestCancellation): String {
        cancellation.throwIfCancelled()
        val normalized = request.input.trim().replace(Regex("\\s+"), " ")
        return "{\"schema\":\"${schemaName.replace("\"","_")}\",\"testOnly\":true,\"summary\":\"${normalized.take(80).replace("\\","\\\\").replace("\"","\\\"")}\"}"
    }
}

class AIRequestRouter(private val providers: List<ModelProviderAdapter>, private val allowTestProviders: Boolean = false) {
    fun route(operation: AiOperation, needsVision: Boolean = false, needsStreaming: Boolean = true): List<ModelProviderAdapter> {
        val policy = ModelPolicyRegistry.forOperation(operation)
        val eligible = providers.filter { it.isConfigured() }
            .filter { allowTestProviders || !it.testOnly }
            .filter { !needsVision || it.capabilities.vision }
            .filter { !needsStreaming || it.capabilities.streaming }
        val preferred = eligible.filter { it.tier == policy.preferredTier }
        val fallback = if (policy.allowFallback) eligible.filterNot { it in preferred } else emptyList()
        val ordered = preferred + fallback
        if (ordered.isEmpty()) throw DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.AI_PROVIDER, "No configured provider supports this operation", retryable = true))
        return ordered
    }
}

class RequestCancellation {
    private val cancelled = AtomicBoolean(false)
    fun cancel() { cancelled.set(true) }
    fun isCancelled(): Boolean = cancelled.get()
    fun throwIfCancelled() {
        if (cancelled.get()) throw DomainException(DomainError("AI_CANCELLED", ErrorCategory.AI_PROVIDER, "AI request cancelled", retryable = false))
    }
}

data class PlannedContext(
    val projectId: String?,
    val projectInstruction: String?,
    val memories: List<MemoryItem>,
    val sourceCitations: List<Citation>,
    val toolIds: Set<String>,
    val learningMode: LearningMode,
)

object ContextPlanner {
    fun plan(
        context: ContextCarry,
        project: Project?,
        accountMemories: List<MemoryItem>,
        projectMemories: List<MemoryItem>,
        sourceResults: List<Pair<SourceChunk, Citation>>,
        availableTools: Set<String>,
        query: String,
    ): PlannedContext {
        Ownership.validateContext(context, project)
        val account = if (context.memoryEnabled) MemoryEngine.rank(accountMemories, context.accountId, null, query, limit = 6) else emptyList()
        val scoped = if (context.projectMemoryEnabled && context.projectId != null) {
            MemoryEngine.rank(projectMemories, context.accountId, context.projectId, query, limit = 6)
                .filter { it.scope != MemoryScope.PROJECT || it.scopeId == context.projectId }
        } else emptyList()
        val sources = sourceResults.asSequence()
            .filter { context.sourceIds.isEmpty() || it.first.sourceId in context.sourceIds }
            .take(8)
            .map { it.second }
            .toList()
        return PlannedContext(
            projectId = context.projectId,
            projectInstruction = project?.takeIf { it.id == context.projectId }?.aiInstruction,
            memories = (account + scoped).distinctBy { it.id }.take(10),
            sourceCitations = sources,
            toolIds = availableTools,
            learningMode = context.learningMode,
        )
    }
}
