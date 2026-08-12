package com.veltrix.hom.vnext.server.ai

import com.veltrix.hom.vnext.core.*
import com.veltrix.hom.vnext.server.*
import com.veltrix.hom.vnext.server.foundation.*
import com.veltrix.hom.vnext.server.rag.HybridRetrievalRepository

/** Stable, auditable input assembled before any model is called. */
data class PreparedAiContext(
    val conversation: ConversationResponse,
    val planned: PlannedContext,
    val citations: List<CitationResponse>,
    val request: AiProviderRequest,
)

object PromptPolicyComposer {
    private const val PRODUCT_POLICY = """You are the Veltrix Hom assistant. Be accurate, student-appropriate, concise by default, and explicit about uncertainty. Never invent citations or tool results. Do not reveal private chain-of-thought. If source evidence is insufficient, say so. Follow the current explicit user instruction over stored preferences."""

    fun compose(planned: PlannedContext, sourceEvidence: List<CitationResponse>, userText: String): Pair<String, String> {
        val system = buildString {
            append(PRODUCT_POLICY)
            append("\nLearning mode: ").append(planned.learningMode.name)
            planned.projectInstruction?.takeIf { it.isNotBlank() }?.let { append("\nProject instruction:\n").append(it.take(8_000)) }
            if (planned.memories.isNotEmpty()) {
                append("\nRelevant memory (use only when useful; user-corrected facts outrank inferred facts):")
                planned.memories.take(10).forEach { append("\n- [").append(it.scope.name).append('/').append(it.category.name).append("] ").append(it.statement.take(600)) }
            }
            if (sourceEvidence.isNotEmpty()) append("\nSource grounding is active. Cite only evidence IDs supplied below; never invent citation markers.")
            if (planned.toolIds.isNotEmpty()) append("\nAllowed deterministic tools: ").append(planned.toolIds.sorted().joinToString(", "))
        }
        val input = buildString {
            append("USER REQUEST:\n").append(userText.trim())
            if (sourceEvidence.isNotEmpty()) {
                append("\n\nRETRIEVED EVIDENCE:\n")
                sourceEvidence.take(8).forEachIndexed { index, c ->
                    append("EVIDENCE_").append(index + 1)
                        .append(" sourceId=").append(c.sourceId)
                        .append(" sourceVersion=").append(c.sourceVersion)
                        .append(" chunkId=").append(c.chunkId)
                        .append(" textHash=").append(c.textHash)
                    c.page?.let { append(" page=").append(it) }
                    c.section?.let { append(" section=").append(it.take(160)) }
                    append(" score=").append("%.4f".format(java.util.Locale.ROOT, c.relevance))
                    append("\n").append(c.excerpt.take(1_200)).append("\n")
                }
            }
        }
        return system to input
    }
}

class AiContextOrchestrator(
    private val projects: ProjectRepository,
    private val chats: ChatRepository,
    private val memory: MemoryRepository,
    private val projectInstructions: ProjectInstructionRepository,
    private val chatIntelligence: ChatIntelligenceRepository,
    private val rag: HybridRetrievalRepository,
) {
    private val tools = ToolRegistry()

    fun prepare(accountId: String, req: AiStreamRequest): PreparedAiContext {
        val conversation = chats.get(accountId, req.conversationId)
        val explicitProjectId = req.projectId
        val projectId = explicitProjectId ?: conversation.projectId
        if (explicitProjectId != null && conversation.projectId != explicitProjectId) {
            throw DomainException(DomainError("PROJECT_CONTEXT_MISMATCH", ErrorCategory.PERMISSION, "Conversation does not belong to selected Project"))
        }
        if (conversation.scope == ConversationScope.PROJECT.name && projectId == null) {
            throw DomainException(DomainError("PROJECT_CONTEXT_MISMATCH", ErrorCategory.PERMISSION, "Project conversation is missing its Project"))
        }
        if (conversation.scope != ConversationScope.PROJECT.name && explicitProjectId != null && conversation.projectId == null) {
            throw DomainException(DomainError("PROJECT_CONTEXT_MISMATCH", ErrorCategory.PERMISSION, "Global conversation cannot inherit Project context"))
        }
        val projectResponse = projectId?.let { projects.get(accountId, it) }
        val activeProjectInstruction = projectId?.let { projectInstructions.active(accountId, it)?.body ?: projectResponse?.aiInstruction }
        val project = projectResponse?.let { p ->
            Project(id=p.id, accountId=accountId, title=p.title, purpose=p.purpose, template=p.template, priority=p.priority,
                status=ProjectStatus.valueOf(p.status), aiInstruction=activeProjectInstruction, updatedAt=java.time.Instant.parse(p.updatedAt), lastActiveAt=java.time.Instant.parse(p.lastActiveAt), revision=p.revision)
        }
        val mode = runCatching { LearningMode.valueOf(req.learningMode.uppercase()) }
            .getOrElse { throw DomainException(DomainError("VALIDATION", ErrorCategory.VALIDATION, "Unknown learning mode")) }

        val attachmentContext = chatIntelligence.resolveAttachmentContext(accountId, req.conversationId, req.attachmentIds)
        val effectiveSourceIds = (req.sourceIds + attachmentContext.sourceIds).distinct()
        val retrievalQuery = if (attachmentContext.noteContext.isBlank()) req.text else req.text + "\n" + attachmentContext.noteContext.take(2500)
        val accountMemories = if (req.memoryEnabled) memory.retrieveCore(accountId, null, req.text, 8) else emptyList()
        val projectMemories = if (projectId != null && req.projectMemoryEnabled) memory.retrieveCore(accountId, projectId, req.text, 10)
            .filter { it.scope != MemoryScope.PROJECT || it.scopeId == projectId } else emptyList()

        // Global chats must never silently search the account-wide Source Library.
        // RAG is enabled only by explicit selected sources or by an active Project brain.
        val hits = if (effectiveSourceIds.isEmpty() && projectId == null) emptyList() else rag.search(
            accountId = accountId,
            query = retrievalQuery,
            sourceIds = effectiveSourceIds,
            projectId = if (projectId != null && effectiveSourceIds.isEmpty()) projectId else null,
            limit = 8,
        )
        val sourcePairs = hits.map { h ->
            val c = h.citation
            SourceChunk(id=c.chunkId, accountId=accountId, sourceId=c.sourceId, sourceVersion=c.sourceVersion, page=c.page,
                section=c.section, offsetStart=0, offsetEnd=c.excerpt.length, text=c.excerpt, textHash=c.textHash) to
                Citation(c.sourceId,c.sourceVersion,c.chunkId,c.page,c.section,c.relevance,c.textHash)
        }
        val carry = ContextCarry(
            accountId = accountId,
            projectId = projectId,
            sourceIds = effectiveSourceIds.toSet(),
            conversationId = req.conversationId,
            learningMode = mode,
            originFeature = "CHAT",
            memoryEnabled = req.memoryEnabled && conversation.memoryEnabled,
            projectMemoryEnabled = req.projectMemoryEnabled && conversation.projectMemoryEnabled,
        )
        val registeredTools = tools.definitions().map { it.id }.toSet()
        val allowedTools = if (req.toolIds.isEmpty()) registeredTools else req.toolIds.toSet().intersect(registeredTools)
        val planned = ContextPlanner.plan(carry, project, accountMemories, projectMemories, sourcePairs, allowedTools, req.text)
        val citations = hits.map { it.citation }
        val userWithAttachments = if (attachmentContext.noteContext.isBlank()) req.text else req.text + "\n\nREADY NOTE ATTACHMENTS:\n" + attachmentContext.noteContext
        val (systemPrompt,input) = PromptPolicyComposer.compose(planned,citations,userWithAttachments)
        val operation = when {
            citations.isNotEmpty() -> AiOperation.SOURCE_REASONING
            mode == LearningMode.TUTOR || mode == LearningMode.SOCRATIC || mode == LearningMode.PRACTICE_COACH -> AiOperation.TUTOR
            else -> AiOperation.CHAT
        }
        return PreparedAiContext(
            conversation = conversation,
            planned = planned,
            citations = citations,
            request = AiProviderRequest(
                operation = operation,
                input = input,
                context = planned,
                systemPrompt = systemPrompt,
                metadata = mapOf("conversationId" to req.conversationId, "projectId" to (projectId ?: "none"), "learningMode" to mode.name),
            ),
        )
    }
}
