package com.veltrix.hom.vnext.core

import java.time.Instant
import java.util.UUID

fun newId(@Suppress("UNUSED_PARAMETER") prefix: String): String = UUID.randomUUID().toString()

enum class AppEnvironment { DEVELOPMENT, TEST, STAGING, PRODUCTION }
enum class PrimaryDestination { HOME, PERSONAL, STORE, PROJECTS }
enum class CapabilityRoute { CHAT, LIBRARY, TESTING, PRACTICE, QUIZZES, FLASHCARDS, MISTAKES, CALCULATOR, TRANSLATE, NOTIFICATIONS, SETTINGS }
enum class ProjectStatus { ACTIVE, PAUSED, COMPLETED, ARCHIVED }
enum class GoalStatus { ACTIVE, COMPLETED, PAUSED, CANCELLED, ARCHIVED }
enum class ConversationScope { GLOBAL, PROJECT, SOURCE, SPECIALIZED }
enum class MessageState { DRAFT, QUEUED, SENDING, STREAMING, COMPLETED, FAILED, CANCELLED }
enum class MessageRole { USER, ASSISTANT, SYSTEM_INTERNAL, TOOL }
enum class AttachmentState { UPLOADING, PROCESSING, READY, FAILED }
enum class SourceState { UPLOADING, PROCESSING, READY, PARTIAL, FAILED, UNSUPPORTED }
enum class AssessmentState { NOT_STARTED, IN_PROGRESS, SUBMITTED, GRADED, ABANDONED }
enum class ArtifactState { DRAFT, READY, ARCHIVED }
enum class MemoryScope { ACCOUNT, PROJECT, CONVERSATION, SOURCE, SESSION }
enum class MemoryOrigin { EXPLICIT_USER, OBSERVED_BEHAVIOR, ASSESSMENT, PROJECT_ACTIVITY, AI_INFERENCE, SYSTEM_DERIVED }
enum class MemoryCategory { IDENTITY, PREFERENCE, INTEREST, LEARNING, SKILL, WEAKNESS, FRICTION, GOAL, PROJECT, FORMAT_PREFERENCE, RECENT_CONTEXT }
enum class MemoryStatus { ACTIVE, UNCERTAIN, CONTRADICTED, USER_CORRECTED, ARCHIVED }
enum class MemoryMaturityState { COLD, LEARNING, SUFFICIENT, STRONG }
enum class LearningMode { DEFAULT, TUTOR, SOCRATIC, EXPLAIN_SIMPLE, DEEP_DIVE, PRACTICE_COACH, EXAM_PREP, RESEARCH, WRITING_HELP }
enum class ReviewRating { AGAIN, HARD, GOOD, EASY }
enum class MistakeStatus { ACTIVE, IMPROVING, RESOLVED, RECURRED, ARCHIVED }
enum class SyncState { SYNCED, PENDING, CONFLICT, FAILED }
enum class ErrorCategory { AUTH, PERMISSION, VALIDATION, NOT_FOUND, CONFLICT, RATE_LIMIT, NETWORK_UPSTREAM, AI_PROVIDER, SOURCE_PROCESSING, STORAGE, DATABASE, TEMPORARY_UNAVAILABLE, INTERNAL }

data class DomainError(
    val code: String,
    val category: ErrorCategory,
    val message: String,
    val retryable: Boolean = false,
    val requestId: String? = null,
)

class DomainException(val error: DomainError) : RuntimeException(error.message)

data class Account(
    val id: String = newId("acct"),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    val revision: Long = 1,
)

data class UserProfile(
    val accountId: String,
    val displayName: String,
    val username: String? = null,
    val preferredLanguage: String = "en",
    val educationLevel: String? = null,
    val subjects: Set<String> = emptySet(),
    val interests: Set<String> = emptySet(),
    val timezone: String = "UTC",
    val onboardingComplete: Boolean = false,
    val defaultAvatarId: String = "default",
    val memoryEnabled: Boolean = true,
    val updatedAt: Instant = Instant.now(),
    val revision: Long = 1,
)

data class Project(
    val id: String = newId("proj"),
    val accountId: String,
    val title: String,
    val purpose: String? = null,
    val template: String = "CUSTOM",
    val priority: Int = 0,
    val deadline: Instant? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val aiInstruction: String? = null,
    val defaultLearningMode: LearningMode = LearningMode.DEFAULT,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    val lastActiveAt: Instant = createdAt,
    val revision: Long = 1,
)

data class Goal(
    val id: String = newId("goal"),
    val accountId: String,
    val projectId: String,
    val title: String,
    val description: String? = null,
    val type: String = "GENERAL",
    val priority: Int = 0,
    val targetDate: Instant? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val progress: Double? = null,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val userCreated: Boolean = true,
    val revision: Long = 1,
)

data class Conversation(
    val id: String = newId("conv"),
    val accountId: String,
    val projectId: String? = null,
    val scope: ConversationScope = ConversationScope.GLOBAL,
    val title: String = "New chat",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    val revision: Long = 1,
)

data class ConversationMessage(
    val id: String = newId("msg"),
    val accountId: String,
    val conversationId: String,
    val parentMessageId: String? = null,
    val role: MessageRole,
    val state: MessageState,
    val content: String,
    val idempotencyKey: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    val revision: Long = 1,
)

data class Source(
    val id: String = newId("src"),
    val accountId: String,
    val title: String,
    val type: String,
    val mimeType: String,
    val contentHash: String,
    val state: SourceState = SourceState.UPLOADING,
    val language: String? = null,
    val sizeBytes: Long = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    val revision: Long = 1,
)

data class SourceChunk(
    val id: String = newId("chunk"),
    val accountId: String,
    val sourceId: String,
    val sourceVersion: Long,
    val page: Int? = null,
    val section: String? = null,
    val offsetStart: Int,
    val offsetEnd: Int,
    val text: String,
    val textHash: String,
)

data class Citation(
    val sourceId: String,
    val sourceVersion: Long,
    val chunkId: String,
    val page: Int?,
    val section: String?,
    val relevance: Double,
    val textHash: String,
)

data class Note(
    val id: String = newId("note"),
    val accountId: String,
    val projectId: String? = null,
    val sourceId: String? = null,
    val conversationId: String? = null,
    val title: String,
    val body: String,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val updatedAt: Instant = Instant.now(),
    val revision: Long = 1,
)

data class MemoryEvidence(
    val id: String = newId("evidence"),
    val kind: String,
    val objectId: String,
    val observedAt: Instant = Instant.now(),
)

data class MemoryItem(
    val id: String = newId("mem"),
    val accountId: String,
    val scope: MemoryScope,
    val scopeId: String? = null,
    val category: MemoryCategory,
    val statement: String,
    val attributes: Map<String, String> = emptyMap(),
    val origin: MemoryOrigin,
    val confidence: Double,
    val evidence: List<MemoryEvidence>,
    val status: MemoryStatus = MemoryStatus.ACTIVE,
    val sensitivity: String = "NORMAL",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    val lastConfirmedAt: Instant? = null,
    val revision: Long = 1,
)

data class MemoryMaturity(
    val score: Int,
    val state: MemoryMaturityState,
    val factors: Map<String, Double>,
)

data class ContextCarry(
    val accountId: String,
    val projectId: String? = null,
    val sourceIds: Set<String> = emptySet(),
    val conversationId: String? = null,
    val learningMode: LearningMode = LearningMode.DEFAULT,
    val topic: String? = null,
    val originFeature: String? = null,
    val returnDestination: String? = null,
    val memoryEnabled: Boolean = true,
    val projectMemoryEnabled: Boolean = true,
)

data class ContextSummary(
    val projectId: String?,
    val sourceIds: Set<String>,
    val memoryEnabled: Boolean,
    val projectMemoryEnabled: Boolean,
    val learningMode: LearningMode,
    val userInstruction: String?,
    val projectInstruction: String?,
    val toolIds: Set<String>,
)

data class Question(
    val id: String = newId("q"),
    val prompt: String,
    val type: String,
    val options: List<String> = emptyList(),
    val expectedAnswers: List<String> = emptyList(),
    val numericTolerance: Double? = null,
    val sourceEvidence: List<Citation> = emptyList(),
)

data class Assessment(
    val id: String = newId("assessment"),
    val accountId: String,
    val projectId: String? = null,
    val kind: String,
    val title: String,
    val questions: List<Question>,
    val state: ArtifactState = ArtifactState.READY,
)

data class AttemptAnswer(val questionId: String, val answers: List<String>, val answeredAt: Instant = Instant.now())

data class AssessmentAttempt(
    val id: String = newId("attempt"),
    val accountId: String,
    val assessmentId: String,
    val projectId: String? = null,
    val state: AssessmentState = AssessmentState.NOT_STARTED,
    val startedAt: Instant? = null,
    val lastActiveAt: Instant? = null,
    val submittedAt: Instant? = null,
    val answers: Map<String, AttemptAnswer> = emptyMap(),
    val revision: Long = 1,
)

data class QuestionResult(val questionId: String, val correct: Boolean, val score: Double, val explanation: String? = null)
data class AssessmentResult(val score: Double, val accuracy: Double, val results: List<QuestionResult>)

data class Flashcard(
    val id: String = newId("card"),
    val accountId: String,
    val deckId: String,
    val projectId: String? = null,
    val front: String,
    val back: String,
    val explanation: String? = null,
    val citation: Citation? = null,
    val tags: Set<String> = emptySet(),
    val suspended: Boolean = false,
)

data class FlashcardScheduleState(
    val cardId: String,
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val dueAt: Instant = Instant.now(),
    val lastReviewedAt: Instant? = null,
)

data class Mistake(
    val id: String = newId("mistake"),
    val accountId: String,
    val projectId: String? = null,
    val sourceId: String? = null,
    val topic: String,
    val skill: String? = null,
    val prompt: String,
    val userAnswer: String,
    val expectedAnswer: String,
    val explanation: String? = null,
    val occurrenceCount: Int = 1,
    val firstSeenAt: Instant = Instant.now(),
    val lastSeenAt: Instant = firstSeenAt,
    val status: MistakeStatus = MistakeStatus.ACTIVE,
    val confidence: Double = 1.0,
)

data class LearningSignal(
    val id: String = newId("signal"),
    val accountId: String,
    val projectId: String? = null,
    val topic: String,
    val kind: String,
    val value: Double,
    val confidence: Double,
    val evidenceIds: List<String>,
    val observedAt: Instant = Instant.now(),
)

data class ActivityEvent(
    val eventId: String = newId("event"),
    val accountId: String,
    val type: String,
    val timestamp: Instant = Instant.now(),
    val projectId: String? = null,
    val objectId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val idempotencyKey: String,
)

data class SyncMutation(
    val id: String = newId("mutation"),
    val accountId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val expectedRevision: Long?,
    val idempotencyKey: String,
    val createdAt: Instant = Instant.now(),
    val attemptCount: Int = 0,
    val state: SyncState = SyncState.PENDING,
)

data class TypedSearchResult(
    val type: String,
    val id: String,
    val title: String,
    val snippet: String,
    val projectId: String? = null,
    val score: Double,
    val deepLink: String,
)

data class HomeSnapshot(
    val accountId: String,
    val displayName: String,
    val defaultAvatarId: String,
    val recentProjects: List<Project>,
    val currentFocus: String?,
    val recentAssessmentScore: Double?,
    val reviewTopics: List<String>,
    val recentSources: List<Source>,
    val memoryMaturity: MemoryMaturityState,
    val mapState: String = "LOCKED_PART_2",
    val unreadNotificationCount: Int = 0,
    val syncState: SyncState = SyncState.SYNCED,
    val schemaVersion: Int = 1,
)

data class ProjectWorkspaceSnapshot(
    val project: Project,
    val activeGoals: List<Goal>,
    val completedGoalCount: Int,
    val recentActivity: List<ActivityEvent>,
    val recentConversations: List<Conversation>,
    val sourceCount: Int,
    val noteCount: Int,
    val mistakeCount: Int,
    val memoryMaturity: MemoryMaturityState,
    val syncState: SyncState,
)
