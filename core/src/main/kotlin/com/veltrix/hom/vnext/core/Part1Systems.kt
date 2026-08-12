package com.veltrix.hom.vnext.core

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.min

/** Deterministic onboarding state; optional fields never block completion. */
data class OnboardingState(
    val accountId: String,
    val displayName: String? = null,
    val preferredLanguage: String = "en",
    val educationLevel: String? = null,
    val subjects: Set<String> = emptySet(),
    val interests: Set<String> = emptySet(),
    val helpGoal: String? = null,
    val memoryAcknowledged: Boolean = false,
    val notificationsEnabled: Boolean? = null,
    val completed: Boolean = false,
    val revision: Long = 1,
)

object OnboardingEngine {
    fun update(current: OnboardingState, patch: OnboardingState): OnboardingState {
        require(current.accountId == patch.accountId) { "Account mismatch" }
        return patch.copy(revision = current.revision + 1)
    }
    fun complete(current: OnboardingState): OnboardingState {
        if (current.displayName.isNullOrBlank()) throw DomainException(DomainError("VALIDATION", ErrorCategory.VALIDATION, "Display name is required"))
        if (!current.memoryAcknowledged) throw DomainException(DomainError("VALIDATION", ErrorCategory.VALIDATION, "Memory preference acknowledgement is required"))
        return current.copy(completed = true, revision = current.revision + 1)
    }
}

enum class SourceProcessStage { UPLOAD, SAFETY_VALIDATE, EXTRACT, OCR, NORMALIZE, CHUNK, METADATA, INDEX, READY, FAILED, UNSUPPORTED }
data class SourceProcessingRecord(
    val sourceId: String,
    val stage: SourceProcessStage = SourceProcessStage.UPLOAD,
    val progress: Int = 0,
    val retryable: Boolean = true,
    val errorCode: String? = null,
    val revision: Long = 1,
)

data class ExtractedDocument(val text: String, val language: String? = null, val pageBreakOffsets: List<Int> = emptyList())
fun interface OcrAdapter { fun recognize(bytes: ByteArray, mimeType: String): String }

object SourceIngestionEngine {
    private val textMimes = setOf("text/plain", "text/markdown")
    private val imageMimes = setOf("image/png", "image/jpeg", "image/webp")
    private val deferredBinaryMimes = setOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun extract(bytes: ByteArray, mimeType: String, ocr: OcrAdapter? = null): ExtractedDocument {
        if (bytes.isEmpty()) throw DomainException(DomainError("SOURCE_PROCESSING_FAILED", ErrorCategory.SOURCE_PROCESSING, "Source is empty"))
        return when {
            mimeType in textMimes -> ExtractedDocument(bytes.toString(Charsets.UTF_8).replace("\u0000", "").trim())
            mimeType in imageMimes -> {
                val adapter = ocr ?: throw DomainException(DomainError("OCR_FAILED", ErrorCategory.SOURCE_PROCESSING, "OCR adapter is not configured", retryable = true))
                val text = adapter.recognize(bytes, mimeType).trim()
                if (text.isBlank()) throw DomainException(DomainError("OCR_FAILED", ErrorCategory.SOURCE_PROCESSING, "OCR returned no text", retryable = false))
                ExtractedDocument(text)
            }
            mimeType in deferredBinaryMimes -> throw DomainException(DomainError("SOURCE_PROCESSING_FAILED", ErrorCategory.SOURCE_PROCESSING, "Binary extractor is not configured for $mimeType", retryable = true))
            else -> throw DomainException(DomainError("SOURCE_UNSUPPORTED", ErrorCategory.VALIDATION, "Unsupported source type: $mimeType"))
        }
    }

    fun advance(record: SourceProcessingRecord, needsOcr: Boolean = false): SourceProcessingRecord {
        val next = when (record.stage) {
            SourceProcessStage.UPLOAD -> SourceProcessStage.SAFETY_VALIDATE
            SourceProcessStage.SAFETY_VALIDATE -> SourceProcessStage.EXTRACT
            SourceProcessStage.EXTRACT -> if (needsOcr) SourceProcessStage.OCR else SourceProcessStage.NORMALIZE
            SourceProcessStage.OCR -> SourceProcessStage.NORMALIZE
            SourceProcessStage.NORMALIZE -> SourceProcessStage.CHUNK
            SourceProcessStage.CHUNK -> SourceProcessStage.METADATA
            SourceProcessStage.METADATA -> SourceProcessStage.INDEX
            SourceProcessStage.INDEX -> SourceProcessStage.READY
            SourceProcessStage.READY, SourceProcessStage.FAILED, SourceProcessStage.UNSUPPORTED -> record.stage
        }
        val p = when (next) {
            SourceProcessStage.UPLOAD -> 5; SourceProcessStage.SAFETY_VALIDATE -> 12; SourceProcessStage.EXTRACT -> 25
            SourceProcessStage.OCR -> 40; SourceProcessStage.NORMALIZE -> 55; SourceProcessStage.CHUNK -> 68
            SourceProcessStage.METADATA -> 78; SourceProcessStage.INDEX -> 90; SourceProcessStage.READY -> 100
            SourceProcessStage.FAILED, SourceProcessStage.UNSUPPORTED -> record.progress
        }
        return record.copy(stage = next, progress = p, revision = record.revision + 1)
    }
}

enum class SyncOutcome { APPLIED, DUPLICATE, CONFLICT, RETRY_LATER, REJECTED }
data class SyncDecision(val outcome: SyncOutcome, val nextAttemptAt: Instant? = null, val serverRevision: Long? = null)

object SyncRecoveryEngine {
    fun decide(mutation: SyncMutation, seenIdempotencyKeys: Set<String>, serverRevision: Long?, transientFailure: Boolean, now: Instant = Instant.now()): SyncDecision {
        if (mutation.idempotencyKey in seenIdempotencyKeys) return SyncDecision(SyncOutcome.DUPLICATE, serverRevision = serverRevision)
        if (mutation.expectedRevision != null && serverRevision != null && mutation.expectedRevision != serverRevision) return SyncDecision(SyncOutcome.CONFLICT, serverRevision = serverRevision)
        if (transientFailure) {
            val seconds = min(3600L, 2L shl min(10, mutation.attemptCount))
            return SyncDecision(SyncOutcome.RETRY_LATER, now.plusSeconds(seconds), serverRevision)
        }
        return SyncDecision(SyncOutcome.APPLIED, serverRevision = (serverRevision ?: 0L) + 1)
    }
}

data class NotificationPreferenceRule(
    val category: String,
    val enabled: Boolean = true,
    val quietStart: LocalTime? = null,
    val quietEnd: LocalTime? = null,
    val timezone: String = "UTC",
)

object NotificationPolicy {
    fun mayDeliver(rule: NotificationPreferenceRule, at: Instant): Boolean {
        if (!rule.enabled) return false
        val start = rule.quietStart ?: return true
        val end = rule.quietEnd ?: return true
        val local = at.atZone(ZoneId.of(rule.timezone)).toLocalTime()
        val quiet = if (start <= end) local >= start && local < end else local >= start || local < end
        return !quiet
    }
}

data class TranslationInput(val text: String, val sourceLanguage: String? = null, val targetLanguage: String)
data class TranslationOutput(val text: String, val providerId: String, val live: Boolean)
fun interface TranslationAdapter { fun translate(input: TranslationInput): TranslationOutput }

class TranslationRouter(private val adapters: List<Pair<String, TranslationAdapter>>, private val allowTestOnly: Boolean = false) {
    fun translate(input: TranslationInput): TranslationOutput {
        if (input.text.isBlank() || input.text.length > 100_000) throw DomainException(DomainError("TRANSLATION_FAILED", ErrorCategory.VALIDATION, "Translation text must be 1..100000 chars"))
        if (!input.targetLanguage.matches(Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?"))) throw DomainException(DomainError("TRANSLATION_FAILED", ErrorCategory.VALIDATION, "Invalid target language"))
        val adapter = adapters.firstOrNull { allowTestOnly || it.first != "MOCK_TEST_ONLY" }?.second
            ?: throw DomainException(DomainError("TRANSLATION_FAILED", ErrorCategory.AI_PROVIDER, "Translation provider unavailable", retryable = true))
        return adapter.translate(input)
    }
}

object DataControlPolicy {
    fun validateAccountDeletion(passwordReauthenticated: Boolean, confirmation: String) {
        if (!passwordReauthenticated) throw DomainException(DomainError("AUTH_EXPIRED", ErrorCategory.AUTH, "Re-authentication required"))
        if (confirmation != "DELETE MY ACCOUNT") throw DomainException(DomainError("VALIDATION", ErrorCategory.VALIDATION, "Explicit account deletion confirmation required"))
    }
}

object NoteConflictPolicy {
    fun canWrite(expectedRevision: Long, currentRevision: Long): Boolean = expectedRevision == currentRevision
}

object SourceLinkPolicy {
    /** Unlinking a Project never implies deleting the global Source. */
    fun unlink(source: Source, linkedProjectIds: Set<String>, projectId: String): Pair<Source, Set<String>> = source to (linkedProjectIds - projectId)
}

object PerformanceBudgets {
    const val HOME_CACHE_READ_MS = 50L
    const val PROJECT_LIST_QUERY_MS = 100L
    const val LOCAL_SEARCH_DEBOUNCE_MS = 200L
    const val MAX_PAGE_SIZE = 200
    const val MAX_MEMORY_RETRIEVAL = 50
    fun boundedPageSize(requested: Int): Int = requested.coerceIn(1, MAX_PAGE_SIZE)
}
