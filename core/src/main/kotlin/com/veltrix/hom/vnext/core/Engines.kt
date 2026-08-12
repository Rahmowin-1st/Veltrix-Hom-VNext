package com.veltrix.hom.vnext.core

import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object Ownership {
    fun requireAccount(ownerAccountId: String, requesterAccountId: String) {
        if (ownerAccountId != requesterAccountId) throw DomainException(
            DomainError("PERMISSION_DENIED", ErrorCategory.PERMISSION, "Object belongs to another account")
        )
    }

    fun validateContext(context: ContextCarry, project: Project?) {
        if (project != null) {
            requireAccount(project.accountId, context.accountId)
            if (context.projectId != null && context.projectId != project.id) throw DomainException(
                DomainError("PROJECT_CONTEXT_MISMATCH", ErrorCategory.PERMISSION, "Project context mismatch")
            )
        }
    }
}

object GoalEngine {
    fun transition(goal: Goal, target: GoalStatus, now: Instant = Instant.now()): Goal {
        val allowed = when (goal.status) {
            GoalStatus.ACTIVE -> setOf(GoalStatus.COMPLETED, GoalStatus.PAUSED, GoalStatus.CANCELLED, GoalStatus.ARCHIVED)
            GoalStatus.PAUSED -> setOf(GoalStatus.ACTIVE, GoalStatus.CANCELLED, GoalStatus.ARCHIVED)
            GoalStatus.COMPLETED -> setOf(GoalStatus.ACTIVE, GoalStatus.ARCHIVED)
            GoalStatus.CANCELLED -> setOf(GoalStatus.ACTIVE, GoalStatus.ARCHIVED)
            GoalStatus.ARCHIVED -> setOf(GoalStatus.ACTIVE)
        }
        if (target != goal.status && target !in allowed) throw DomainException(
            DomainError("INVALID_GOAL_TRANSITION", ErrorCategory.CONFLICT, "${goal.status} -> $target is not allowed")
        )
        return goal.copy(
            status = target,
            completedAt = if (target == GoalStatus.COMPLETED) now else if (goal.status == GoalStatus.COMPLETED) null else goal.completedAt,
            revision = goal.revision + 1,
        )
    }
}

object ChatStateMachine {
    fun transition(message: ConversationMessage, target: MessageState): ConversationMessage {
        val allowed = when (message.state) {
            MessageState.DRAFT -> setOf(MessageState.QUEUED, MessageState.CANCELLED)
            MessageState.QUEUED -> setOf(MessageState.SENDING, MessageState.CANCELLED, MessageState.FAILED)
            MessageState.SENDING -> setOf(MessageState.STREAMING, MessageState.COMPLETED, MessageState.CANCELLED, MessageState.FAILED)
            MessageState.STREAMING -> setOf(MessageState.COMPLETED, MessageState.CANCELLED, MessageState.FAILED)
            MessageState.FAILED -> setOf(MessageState.QUEUED, MessageState.CANCELLED)
            MessageState.CANCELLED -> setOf(MessageState.QUEUED)
            MessageState.COMPLETED -> emptySet()
        }
        if (target != message.state && target !in allowed) throw DomainException(
            DomainError("INVALID_MESSAGE_TRANSITION", ErrorCategory.CONFLICT, "${message.state} -> $target is not allowed")
        )
        return message.copy(state = target, updatedAt = Instant.now(), revision = message.revision + 1)
    }
}

class IdempotencyGuard(private val capacity: Int = 10_000) {
    private val order = ArrayDeque<String>()
    private val seen = HashSet<String>()

    @Synchronized
    fun first(key: String): Boolean {
        if (!seen.add(key)) return false
        order.addLast(key)
        while (order.size > capacity) seen.remove(order.removeFirst())
        return true
    }
}

object MemoryEngine {
    private fun canonical(s: String): String = s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun upsert(existing: List<MemoryItem>, candidate: MemoryItem): List<MemoryItem> {
        require(candidate.confidence in 0.0..1.0)
        val same = existing.firstOrNull {
            it.accountId == candidate.accountId && it.scope == candidate.scope && it.scopeId == candidate.scopeId &&
                it.category == candidate.category && canonical(it.statement) == canonical(candidate.statement) &&
                it.status != MemoryStatus.ARCHIVED
        }
        if (same != null) {
            val mergedEvidence = (same.evidence + candidate.evidence).distinctBy { it.kind to it.objectId }
            val merged = same.copy(
                confidence = max(same.confidence, candidate.confidence),
                evidence = mergedEvidence,
                updatedAt = Instant.now(),
                lastConfirmedAt = if (candidate.origin == MemoryOrigin.EXPLICIT_USER) Instant.now() else same.lastConfirmedAt,
                revision = same.revision + 1,
            )
            return existing.map { if (it.id == same.id) merged else it }
        }
        return existing + candidate
    }

    fun correct(existing: List<MemoryItem>, oldId: String, replacement: MemoryItem): List<MemoryItem> {
        val old = existing.firstOrNull { it.id == oldId } ?: throw DomainException(
            DomainError("MEMORY_NOT_FOUND", ErrorCategory.NOT_FOUND, "Memory not found")
        )
        Ownership.requireAccount(old.accountId, replacement.accountId)
        val corrected = old.copy(status = MemoryStatus.USER_CORRECTED, updatedAt = Instant.now(), revision = old.revision + 1)
        val explicit = replacement.copy(origin = MemoryOrigin.EXPLICIT_USER, confidence = 1.0, status = MemoryStatus.ACTIVE, lastConfirmedAt = Instant.now())
        return existing.map { if (it.id == oldId) corrected else it } + explicit
    }

    fun rank(
        memories: List<MemoryItem>,
        accountId: String,
        projectId: String?,
        query: String,
        now: Instant = Instant.now(),
        limit: Int = 12,
    ): List<MemoryItem> {
        val terms = tokenize(query)
        return memories.asSequence()
            .filter { it.accountId == accountId && it.status == MemoryStatus.ACTIVE }
            .filter { it.scope != MemoryScope.PROJECT || it.scopeId == projectId }
            .map { m ->
                val overlap = overlapScore(terms, tokenize(m.statement))
                val ageDays = max(0.0, Duration.between(m.updatedAt, now).toHours() / 24.0)
                val recency = 1.0 / (1.0 + ageDays / 30.0)
                val scopeBoost = when (m.scope) {
                    MemoryScope.PROJECT -> if (projectId != null && m.scopeId == projectId) 1.15 else 0.0
                    MemoryScope.ACCOUNT -> 1.0
                    MemoryScope.CONVERSATION -> 0.92
                    MemoryScope.SOURCE -> 0.88
                    MemoryScope.SESSION -> 0.8
                }
                m to (0.52 * overlap + 0.28 * m.confidence + 0.20 * recency) * scopeBoost
            }
            .filter { it.second > 0.12 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    fun maturity(
        accountCreatedAt: Instant,
        memories: List<MemoryItem>,
        signals: List<LearningSignal>,
        projects: List<Project>,
        now: Instant = Instant.now(),
    ): MemoryMaturity {
        val active = memories.filter { it.status == MemoryStatus.ACTIVE }
        val ageDays = max(0.0, Duration.between(accountCreatedAt, now).toHours() / 24.0)
        val age = min(1.0, ageDays / 30.0)
        val interests = min(1.0, active.count { it.category in setOf(MemoryCategory.INTEREST, MemoryCategory.PREFERENCE, MemoryCategory.GOAL) } / 6.0)
        val learning = min(1.0, (signals.size + active.count { it.category in setOf(MemoryCategory.LEARNING, MemoryCategory.SKILL, MemoryCategory.WEAKNESS) }) / 12.0)
        val project = min(1.0, projects.count { it.status != ProjectStatus.ARCHIVED } / 3.0)
        val evidence = min(1.0, active.sumOf { it.evidence.size } / 15.0)
        val confidence = if (active.isEmpty()) 0.0 else active.map { it.confidence }.average().coerceIn(0.0, 1.0)
        val score = ((age * 0.15 + interests * 0.18 + learning * 0.25 + project * 0.14 + evidence * 0.16 + confidence * 0.12) * 100).toInt().coerceIn(0, 100)
        val state = when {
            score < 25 -> MemoryMaturityState.COLD
            score < 55 -> MemoryMaturityState.LEARNING
            score < 78 -> MemoryMaturityState.SUFFICIENT
            else -> MemoryMaturityState.STRONG
        }
        return MemoryMaturity(score, state, mapOf("age" to age, "interests" to interests, "learning" to learning, "projects" to project, "evidence" to evidence, "confidence" to confidence))
    }
}

object SourceRetrievalEngine {
    fun search(chunks: List<SourceChunk>, accountId: String, query: String, sourceIds: Set<String> = emptySet(), limit: Int = 8): List<Pair<SourceChunk, Citation>> {
        val q = tokenize(query)
        return chunks.asSequence()
            .filter { it.accountId == accountId }
            .filter { sourceIds.isEmpty() || it.sourceId in sourceIds }
            .map { chunk ->
                val t = tokenize(chunk.text)
                val lexical = overlapScore(q, t)
                val exact = if (query.isNotBlank() && chunk.text.contains(query, ignoreCase = true)) 0.35 else 0.0
                val score = (lexical + exact).coerceAtMost(1.0)
                chunk to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (chunk, score) ->
                chunk to Citation(chunk.sourceId, chunk.sourceVersion, chunk.id, chunk.page, chunk.section, score, chunk.textHash)
            }.toList()
    }
}

object AssessmentEngine {
    fun score(assessment: Assessment, answers: Map<String, AttemptAnswer>): AssessmentResult {
        if (assessment.questions.isEmpty()) return AssessmentResult(0.0, 0.0, emptyList())
        val results = assessment.questions.map { q ->
            val actual = answers[q.id]?.answers ?: emptyList()
            val ok = when (q.type.uppercase(Locale.ROOT)) {
                "SINGLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER", "FILL_BLANK" -> normalized(actual.firstOrNull()) == normalized(q.expectedAnswers.firstOrNull())
                "MULTIPLE_CHOICE" -> actual.map(::normalized).toSet() == q.expectedAnswers.map(::normalized).toSet()
                "NUMERIC" -> {
                    val a = actual.firstOrNull()?.toDoubleOrNull()
                    val e = q.expectedAnswers.firstOrNull()?.toDoubleOrNull()
                    a != null && e != null && abs(a - e) <= (q.numericTolerance ?: 0.0)
                }
                "ORDERING" -> actual.map(::normalized) == q.expectedAnswers.map(::normalized)
                else -> false
            }
            QuestionResult(q.id, ok, if (ok) 1.0 else 0.0)
        }
        val correct = results.count { it.correct }
        val accuracy = correct.toDouble() / results.size
        return AssessmentResult(score = accuracy * 100.0, accuracy = accuracy, results = results)
    }

    private fun normalized(v: String?): String = v?.trim()?.lowercase(Locale.ROOT)?.replace(Regex("\\s+"), " ") ?: ""
}

object FlashcardScheduler {
    fun review(state: FlashcardScheduleState, rating: ReviewRating, now: Instant = Instant.now()): FlashcardScheduleState {
        val next = when (rating) {
            ReviewRating.AGAIN -> state.copy(intervalDays = 1, ease = max(1.3, state.ease - 0.20), repetitions = 0, lapses = state.lapses + 1)
            ReviewRating.HARD -> state.copy(intervalDays = max(1, if (state.intervalDays == 0) 1 else (state.intervalDays * 1.2).toInt()), ease = max(1.3, state.ease - 0.15), repetitions = state.repetitions + 1)
            ReviewRating.GOOD -> state.copy(intervalDays = when (state.repetitions) { 0 -> 1; 1 -> 3; else -> max(4, (state.intervalDays * state.ease).toInt()) }, repetitions = state.repetitions + 1)
            ReviewRating.EASY -> state.copy(intervalDays = when (state.repetitions) { 0 -> 4; else -> max(5, (state.intervalDays * state.ease * 1.3).toInt()) }, ease = min(3.2, state.ease + 0.15), repetitions = state.repetitions + 1)
        }
        return next.copy(lastReviewedAt = now, dueAt = now.plus(Duration.ofDays(next.intervalDays.toLong())))
    }
}

object MistakeEngine {
    fun record(existing: List<Mistake>, candidate: Mistake): List<Mistake> {
        val match = existing.firstOrNull {
            it.accountId == candidate.accountId && it.projectId == candidate.projectId &&
                it.topic.equals(candidate.topic, ignoreCase = true) &&
                it.prompt.trim().equals(candidate.prompt.trim(), ignoreCase = true) && it.status != MistakeStatus.ARCHIVED
        }
        return if (match == null) existing + candidate else existing.map {
            if (it.id == match.id) match.copy(
                userAnswer = candidate.userAnswer,
                expectedAnswer = candidate.expectedAnswer,
                explanation = candidate.explanation ?: match.explanation,
                occurrenceCount = match.occurrenceCount + 1,
                lastSeenAt = candidate.lastSeenAt,
                status = if (match.status == MistakeStatus.RESOLVED) MistakeStatus.RECURRED else match.status,
                confidence = max(match.confidence, candidate.confidence),
            ) else it
        }
    }

    fun markUnderstood(mistake: Mistake): Mistake = mistake.copy(status = MistakeStatus.IMPROVING)
    fun resolve(mistake: Mistake): Mistake = mistake.copy(status = MistakeStatus.RESOLVED)
}

object LearningSignalEngine {
    fun fromAssessment(accountId: String, projectId: String?, topic: String, result: AssessmentResult, evidenceId: String): LearningSignal =
        LearningSignal(accountId = accountId, projectId = projectId, topic = topic, kind = "ASSESSMENT_ACCURACY", value = result.accuracy, confidence = min(1.0, 0.55 + result.results.size * 0.03), evidenceIds = listOf(evidenceId))

    fun decay(signal: LearningSignal, now: Instant = Instant.now(), halfLifeDays: Double = 90.0): LearningSignal {
        val days = max(0.0, Duration.between(signal.observedAt, now).toHours() / 24.0)
        val factor = 0.5.pow(days / halfLifeDays)
        return signal.copy(confidence = (signal.confidence * factor).coerceIn(0.0, 1.0))
    }
}

data class ToolDefinition(
    val id: String,
    val name: String,
    val networkRequired: Boolean,
    val deterministic: Boolean,
    val timeoutMs: Long,
    val inputKeys: Set<String>,
)

data class ToolResult(val toolId: String, val summary: String, val values: Map<String, String>)

class ToolRegistry {
    private val tools = linkedMapOf<String, Pair<ToolDefinition, (Map<String, String>) -> ToolResult>>()

    init {
        register(ToolDefinition("calculator.basic", "Calculator", false, true, 1_000, setOf("expression"))) { input ->
            val result = ArithmeticParser(input["expression"] ?: "").parse()
            ToolResult("calculator.basic", result.stripTrailingZeros().toPlainString(), mapOf("result" to result.stripTrailingZeros().toPlainString()))
        }
        register(ToolDefinition("text.count", "Text counter", false, true, 1_000, setOf("text"))) { input ->
            val text = input["text"].orEmpty()
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
            ToolResult("text.count", "$words words", mapOf("characters" to text.length.toString(), "words" to words.toString()))
        }
        register(ToolDefinition("date.days_between", "Date difference", false, true, 1_000, setOf("from", "to"))) { input ->
            val a = LocalDate.parse(input["from"])
            val b = LocalDate.parse(input["to"])
            val days = java.time.temporal.ChronoUnit.DAYS.between(a, b)
            ToolResult("date.days_between", "$days days", mapOf("days" to days.toString()))
        }
        register(ToolDefinition("unit.length", "Length conversion", false, true, 1_000, setOf("value", "from", "to"))) { input ->
            val value = input["value"]!!.toDouble()
            val meters = mapOf("m" to 1.0, "km" to 1000.0, "cm" to .01, "mm" to .001, "mi" to 1609.344, "ft" to .3048, "in" to .0254)
            val from = meters[input["from"]?.lowercase()] ?: error("Unsupported unit")
            val to = meters[input["to"]?.lowercase()] ?: error("Unsupported unit")
            val out = value * from / to
            ToolResult("unit.length", out.toString(), mapOf("result" to out.toString()))
        }
    }

    fun register(definition: ToolDefinition, handler: (Map<String, String>) -> ToolResult) { tools[definition.id] = definition to handler }
    fun definitions(): List<ToolDefinition> = tools.values.map { it.first }
    fun invoke(id: String, input: Map<String, String>): ToolResult {
        val (definition, handler) = tools[id] ?: throw DomainException(DomainError("TOOL_NOT_FOUND", ErrorCategory.NOT_FOUND, "Unknown tool: $id"))
        val missing = definition.inputKeys - input.keys
        if (missing.isNotEmpty()) throw DomainException(DomainError("TOOL_INVALID_INPUT", ErrorCategory.VALIDATION, "Missing: ${missing.joinToString()}"))
        return try { handler(input) } catch (e: DomainException) { throw e } catch (e: Throwable) {
            throw DomainException(DomainError("TOOL_FAILED", ErrorCategory.VALIDATION, e.message ?: "Tool failed"))
        }
    }
}

private class ArithmeticParser(private val raw: String) {
    private var i = 0
    private val mc = MathContext.DECIMAL128
    fun parse(): BigDecimal {
        val v = expression()
        skip()
        if (i != raw.length) throw IllegalArgumentException("Unexpected input at $i")
        return v
    }
    private fun expression(): BigDecimal {
        var v = term()
        while (true) {
            skip(); v = when {
                take('+') -> v.add(term(), mc)
                take('-') -> v.subtract(term(), mc)
                else -> return v
            }
        }
    }
    private fun term(): BigDecimal {
        var v = factor()
        while (true) {
            skip(); v = when {
                take('*') -> v.multiply(factor(), mc)
                take('/') -> { val d = factor(); if (d.compareTo(BigDecimal.ZERO) == 0) throw IllegalArgumentException("Division by zero"); v.divide(d, mc) }
                else -> return v
            }
        }
    }
    private fun factor(): BigDecimal {
        skip()
        if (take('(')) { val v = expression(); if (!take(')')) throw IllegalArgumentException("Missing )"); return v }
        if (take('-')) return factor().negate(mc)
        val start = i
        while (i < raw.length && (raw[i].isDigit() || raw[i] == '.')) i++
        if (start == i) throw IllegalArgumentException("Number expected at $i")
        return raw.substring(start, i).toBigDecimal(mc)
    }
    private fun skip() { while (i < raw.length && raw[i].isWhitespace()) i++ }
    private fun take(c: Char): Boolean { skip(); if (i < raw.length && raw[i] == c) { i++; return true }; return false }
}

object SearchEngine {
    fun search(query: String, accountId: String, projects: List<Project>, conversations: List<Conversation>, sources: List<Source>, notes: List<Note>, mistakes: List<Mistake>, goals: List<Goal>, limit: Int = 30): List<TypedSearchResult> {
        val q = tokenize(query)
        val rows = mutableListOf<TypedSearchResult>()
        projects.filter { it.accountId == accountId }.forEach { rows += result("PROJECT", it.id, it.title, it.purpose.orEmpty(), it.id, q, "veltrix://project/${it.id}") }
        conversations.filter { it.accountId == accountId }.forEach { rows += result("CHAT", it.id, it.title, it.title, it.projectId, q, "veltrix://chat/${it.id}") }
        sources.filter { it.accountId == accountId }.forEach { rows += result("SOURCE", it.id, it.title, it.type, null, q, "veltrix://source/${it.id}") }
        notes.filter { it.accountId == accountId }.forEach { rows += result("NOTE", it.id, it.title, it.body, it.projectId, q, "veltrix://note/${it.id}") }
        mistakes.filter { it.accountId == accountId }.forEach { rows += result("MISTAKE", it.id, it.topic, it.prompt, it.projectId, q, "veltrix://mistake/${it.id}") }
        goals.filter { it.accountId == accountId }.forEach { rows += result("GOAL", it.id, it.title, it.description.orEmpty(), it.projectId, q, "veltrix://goal/${it.id}") }
        return rows.filter { it.score > 0.0 }.sortedByDescending { it.score }.take(limit)
    }

    private fun result(type: String, id: String, title: String, snippet: String, projectId: String?, q: Set<String>, link: String): TypedSearchResult {
        val titleScore = overlapScore(q, tokenize(title))
        val bodyScore = overlapScore(q, tokenize(snippet))
        return TypedSearchResult(type, id, title, snippet.take(180), projectId, min(1.0, titleScore * 0.7 + bodyScore * 0.3), link)
    }
}

object MeaningfulActivityClassifier {
    private val meaningful = setOf("GOAL_COMPLETED", "TEST_COMPLETED", "QUIZ_COMPLETED", "PRACTICE_COMPLETED", "FLASHCARD_REVIEW_COMPLETED", "MISTAKE_RESOLVED", "SOURCE_STUDY_QUALIFIED", "MEANINGFUL_CHAT_SESSION", "PROJECT_CREATED", "SOURCE_ADDED", "NOTE_CREATED")
    fun isMeaningful(event: ActivityEvent): Boolean = event.type in meaningful
}

object SnapshotEngine {
    fun home(profile: UserProfile, projects: List<Project>, sources: List<Source>, maturity: MemoryMaturity, scores: List<Double>, reviewTopics: List<String>, syncState: SyncState): HomeSnapshot =
        HomeSnapshot(
            accountId = profile.accountId,
            displayName = profile.displayName,
            defaultAvatarId = profile.defaultAvatarId,
            recentProjects = projects.filter { it.accountId == profile.accountId && it.status != ProjectStatus.ARCHIVED }.sortedByDescending { it.lastActiveAt }.take(5),
            currentFocus = projects.filter { it.accountId == profile.accountId && it.status == ProjectStatus.ACTIVE }.maxByOrNull { it.priority }?.title,
            recentAssessmentScore = scores.lastOrNull(),
            reviewTopics = reviewTopics.take(5),
            recentSources = sources.filter { it.accountId == profile.accountId && it.state == SourceState.READY }.sortedByDescending { it.updatedAt }.take(5),
            memoryMaturity = maturity.state,
            syncState = syncState,
        )

    fun project(project: Project, goals: List<Goal>, activity: List<ActivityEvent>, conversations: List<Conversation>, sources: List<Source>, notes: List<Note>, mistakes: List<Mistake>, maturity: MemoryMaturityState, syncState: SyncState): ProjectWorkspaceSnapshot {
        val accountId = project.accountId
        return ProjectWorkspaceSnapshot(
            project = project,
            activeGoals = goals.filter { it.accountId == accountId && it.projectId == project.id && it.status == GoalStatus.ACTIVE }.sortedByDescending { it.priority },
            completedGoalCount = goals.count { it.accountId == accountId && it.projectId == project.id && it.status == GoalStatus.COMPLETED },
            recentActivity = activity.filter { it.accountId == accountId && it.projectId == project.id }.sortedByDescending { it.timestamp }.take(20),
            recentConversations = conversations.filter { it.accountId == accountId && it.projectId == project.id }.sortedByDescending { it.updatedAt }.take(10),
            sourceCount = sources.count { it.accountId == accountId },
            noteCount = notes.count { it.accountId == accountId && it.projectId == project.id && !it.archived },
            mistakeCount = mistakes.count { it.accountId == accountId && it.projectId == project.id && it.status != MistakeStatus.ARCHIVED },
            memoryMaturity = maturity,
            syncState = syncState,
        )
    }
}

object SyncEngine {
    fun enqueue(existing: List<SyncMutation>, mutation: SyncMutation): List<SyncMutation> =
        if (existing.any { it.accountId == mutation.accountId && it.idempotencyKey == mutation.idempotencyKey && it.state != SyncState.FAILED }) existing else existing + mutation

    fun resolveServerRevision(mutation: SyncMutation, currentServerRevision: Long?): SyncState =
        when {
            mutation.expectedRevision == null -> SyncState.PENDING
            currentServerRevision == null -> SyncState.PENDING
            mutation.expectedRevision == currentServerRevision -> SyncState.PENDING
            else -> SyncState.CONFLICT
        }
}

fun tokenize(s: String): Set<String> = s.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+".trim())).filter { it.length > 1 }.toSet()
fun overlapScore(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val hit = a.count { it in b }
    return hit.toDouble() / a.size
}
