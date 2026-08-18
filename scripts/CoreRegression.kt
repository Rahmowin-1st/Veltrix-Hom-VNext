import com.veltrix.hom.vnext.core.*
import java.time.Duration
import java.time.Instant

private var pass = 0
private var fail = 0
private fun test(name: String, block: () -> Boolean) {
    try {
        if (block()) { println("PASS | $name"); pass++ } else { println("FAIL | $name"); fail++ }
    } catch (t: Throwable) {
        println("FAIL | $name | ${t::class.simpleName}: ${t.message}"); fail++
    }
}
private fun expectDomain(code: String, block: () -> Unit): Boolean = try { block(); false } catch (e: DomainException) { e.error.code == code }

fun main() {
    val now = Instant.parse("2026-08-11T12:00:00Z")
    val accountA = Account(id="acct-A", createdAt=now.minus(Duration.ofDays(45)))
    val accountB = Account(id="acct-B", createdAt=now.minus(Duration.ofDays(10)))
    val projectA = Project(id="proj-A", accountId=accountA.id, title="CEFR C1", purpose="Reach C1 English", priority=10)
    val projectB = Project(id="proj-B", accountId=accountB.id, title="Private")

    test("ownership accepts owner") { Ownership.requireAccount(accountA.id, accountA.id); true }
    test("ownership rejects cross-account") { expectDomain("PERMISSION_DENIED") { Ownership.requireAccount(accountA.id, accountB.id) } }
    test("context rejects cross-project") { expectDomain("PROJECT_CONTEXT_MISMATCH") { Ownership.validateContext(ContextCarry(accountA.id, projectId="other"), projectA) } }

    val goal = Goal(id="goal-1", accountId=accountA.id, projectId=projectA.id, title="Learn 100 words")
    val completed = GoalEngine.transition(goal, GoalStatus.COMPLETED, now)
    test("goal completion persists timestamp") { completed.status == GoalStatus.COMPLETED && completed.completedAt == now }
    test("goal reopen clears completion timestamp") { GoalEngine.transition(completed, GoalStatus.ACTIVE, now).completedAt == null }
    test("goal invalid transition rejected") { expectDomain("INVALID_GOAL_TRANSITION") { GoalEngine.transition(goal.copy(status=GoalStatus.ARCHIVED), GoalStatus.COMPLETED) } }

    var msg = ConversationMessage(id="msg-1", accountId=accountA.id, conversationId="conv-1", role=MessageRole.USER, state=MessageState.DRAFT, content="hello", idempotencyKey="k1")
    msg = ChatStateMachine.transition(msg, MessageState.QUEUED)
    msg = ChatStateMachine.transition(msg, MessageState.SENDING)
    msg = ChatStateMachine.transition(msg, MessageState.STREAMING)
    msg = ChatStateMachine.transition(msg, MessageState.COMPLETED)
    test("chat full state path completes") { msg.state == MessageState.COMPLETED }
    test("completed chat cannot regress") { expectDomain("INVALID_MESSAGE_TRANSITION") { ChatStateMachine.transition(msg, MessageState.QUEUED) } }

    val guard = IdempotencyGuard(2)
    test("idempotency first accepted") { guard.first("A") }
    test("idempotency replay rejected") { !guard.first("A") }
    guard.first("B"); guard.first("C")
    test("idempotency bounded eviction") { guard.first("A") }

    val ev1 = MemoryEvidence(kind="CHAT", objectId="msg-pref", observedAt=now.minus(Duration.ofDays(2)))
    val mem1 = MemoryItem(id="mem-1", accountId=accountA.id, scope=MemoryScope.ACCOUNT, category=MemoryCategory.PREFERENCE, statement="Use British English", origin=MemoryOrigin.EXPLICIT_USER, confidence=1.0, evidence=listOf(ev1), updatedAt=now.minus(Duration.ofDays(2)))
    val memDup = MemoryItem(id="mem-2", accountId=accountA.id, scope=MemoryScope.ACCOUNT, category=MemoryCategory.PREFERENCE, statement="  use  British   English ", origin=MemoryOrigin.OBSERVED_BEHAVIOR, confidence=.7, evidence=listOf(MemoryEvidence(kind="PROJECT", objectId="p")))
    var memories = MemoryEngine.upsert(listOf(mem1), memDup)
    test("memory deduplicates canonical statement") { memories.size == 1 && memories[0].evidence.size == 2 }
    val replacement = MemoryItem(accountId=accountA.id, scope=MemoryScope.ACCOUNT, category=MemoryCategory.PREFERENCE, statement="Use American English", origin=MemoryOrigin.EXPLICIT_USER, confidence=.5, evidence=listOf(MemoryEvidence(kind="USER_CORRECTION", objectId="mem-1")))
    memories = MemoryEngine.correct(memories, "mem-1", replacement)
    test("memory correction keeps old auditable") { memories.any { it.id=="mem-1" && it.status==MemoryStatus.USER_CORRECTED } }
    test("memory correction wins with explicit confidence") { memories.any { it.statement=="Use American English" && it.status==MemoryStatus.ACTIVE && it.confidence==1.0 } }

    val projectMem = MemoryItem(id="pm", accountId=accountA.id, scope=MemoryScope.PROJECT, scopeId=projectA.id, category=MemoryCategory.GOAL, statement="Pass CEFR C1 exam", origin=MemoryOrigin.PROJECT_ACTIVITY, confidence=.9, evidence=listOf(MemoryEvidence(kind="GOAL", objectId="g")), updatedAt=now)
    val otherProjectMem = projectMem.copy(id="pm2", scopeId="proj-other", statement="Secret other project memory")
    val ranked = MemoryEngine.rank(memories + projectMem + otherProjectMem, accountA.id, projectA.id, "CEFR exam British English", now)
    test("memory retrieval includes matching project") { ranked.any { it.id=="pm" } }
    test("project memory isolation") { ranked.none { it.id=="pm2" } }

    val signalList = (1..8).map { LearningSignal(accountId=accountA.id, projectId=projectA.id, topic="English", kind="ASSESSMENT_ACCURACY", value=.8, confidence=.9, evidenceIds=listOf("e$it"), observedAt=now.minus(Duration.ofDays(it.toLong()))) }
    val richMem = memories + projectMem + (1..6).map { MemoryItem(accountId=accountA.id, scope=MemoryScope.ACCOUNT, category=if (it%2==0) MemoryCategory.INTEREST else MemoryCategory.LEARNING, statement="signal $it English learning", origin=MemoryOrigin.OBSERVED_BEHAVIOR, confidence=.8, evidence=listOf(MemoryEvidence(kind="EVENT", objectId="x$it")), updatedAt=now.minus(Duration.ofDays(it.toLong()))) }
    val maturity = MemoryEngine.maturity(accountA.createdAt, richMem, signalList, listOf(projectA, projectA.copy(id="p2"), projectA.copy(id="p3")), now)
    test("memory maturity is multifactor") { maturity.score >= 55 && maturity.factors.size == 6 }
    test("memory maturity never raw chat count") { maturity.factors.keys.containsAll(listOf("age","interests","learning","projects","evidence","confidence")) }

    val chunks = listOf(
        SourceChunk(id="c1", accountId=accountA.id, sourceId="s1", sourceVersion=1, page=3, section="Grammar", offsetStart=0, offsetEnd=40, text="British English uses present perfect in this example", textHash="h1"),
        SourceChunk(id="c2", accountId=accountA.id, sourceId="s1", sourceVersion=1, page=4, section="Vocabulary", offsetStart=41, offsetEnd=80, text="Advanced vocabulary supports CEFR C1 performance", textHash="h2"),
        SourceChunk(id="c3", accountId=accountB.id, sourceId="secret", sourceVersion=1, offsetStart=0, offsetEnd=10, text="CEFR secret", textHash="h3")
    )
    val retrieval = SourceRetrievalEngine.search(chunks, accountA.id, "CEFR C1 vocabulary")
    test("source retrieval returns provenance") { retrieval.first().second.sourceId=="s1" && retrieval.first().second.textHash.isNotBlank() }
    test("source retrieval blocks other account") { retrieval.none { it.first.accountId==accountB.id } }
    test("source retrieval source scope") { SourceRetrievalEngine.search(chunks, accountA.id, "British English", setOf("other")).isEmpty() }

    val assessment = Assessment(id="quiz-1", accountId=accountA.id, projectId=projectA.id, kind="QUIZ", title="C1 quiz", questions=listOf(
        Question(id="q1", prompt="2+2?", type="NUMERIC", expectedAnswers=listOf("4"), numericTolerance=0.0),
        Question(id="q2", prompt="Choose", type="MULTIPLE_CHOICE", expectedAnswers=listOf("A","C")),
        Question(id="q3", prompt="True?", type="TRUE_FALSE", expectedAnswers=listOf("true"))
    ))
    val score = AssessmentEngine.score(assessment, mapOf(
        "q1" to AttemptAnswer("q1", listOf("4.0")),
        "q2" to AttemptAnswer("q2", listOf("C","A")),
        "q3" to AttemptAnswer("q3", listOf("false"))
    ))
    test("deterministic assessment score") { kotlin.math.abs(score.score - 66.6666666667) < .001 }
    test("assessment per-question result") { score.results.size==3 && score.results.count { it.correct }==2 }

    var fs = FlashcardScheduleState(cardId="card1", dueAt=now)
    fs = FlashcardScheduler.review(fs, ReviewRating.GOOD, now)
    test("flashcard good schedules one day") { fs.intervalDays==1 && fs.repetitions==1 }
    fs = FlashcardScheduler.review(fs, ReviewRating.GOOD, now.plus(Duration.ofDays(1)))
    test("flashcard repetition expands interval") { fs.intervalDays==3 && fs.repetitions==2 }
    val lapse = FlashcardScheduler.review(fs, ReviewRating.AGAIN, now.plus(Duration.ofDays(4)))
    test("flashcard lapse deterministic") { lapse.intervalDays==1 && lapse.repetitions==0 && lapse.lapses==1 }

    var mistakes = listOf<Mistake>()
    val m1 = Mistake(id="m1", accountId=accountA.id, projectId=projectA.id, sourceId="s1", topic="Grammar", prompt="Fix tense", userAnswer="I went", expectedAnswer="I have gone")
    mistakes = MistakeEngine.record(mistakes, m1)
    mistakes = MistakeEngine.record(mistakes, m1.copy(id="m2", userAnswer="I gone", lastSeenAt=now))
    test("mistake aggregation increments occurrence") { mistakes.size==1 && mistakes[0].occurrenceCount==2 }
    val resolved = MistakeEngine.resolve(mistakes[0])
    mistakes = MistakeEngine.record(listOf(resolved), m1.copy(id="m3", lastSeenAt=now))
    test("resolved mistake recurrence tracked") { mistakes[0].status==MistakeStatus.RECURRED }

    val ls = LearningSignalEngine.fromAssessment(accountA.id, projectA.id, "English", score, "attempt-1")
    test("learning signal derived from raw result") { ls.value==score.accuracy && ls.evidenceIds==listOf("attempt-1") }
    val decayed = LearningSignalEngine.decay(ls.copy(observedAt=now.minus(Duration.ofDays(180))), now, 90.0)
    test("old weak evidence decays") { decayed.confidence < ls.confidence * .3 }

    val tools = ToolRegistry()
    test("tool registry deterministic flags") { tools.definitions().all { it.deterministic && !it.networkRequired } }
    test("calculator tool") { tools.invoke("calculator.basic", mapOf("expression" to "(2+3)*4/2")).values["result"]=="10" }
    test("calculator divide zero honest failure") { expectDomain("TOOL_FAILED") { tools.invoke("calculator.basic", mapOf("expression" to "1/0")) } }
    test("length conversion") { kotlin.math.abs(tools.invoke("unit.length", mapOf("value" to "1", "from" to "km", "to" to "m")).values["result"]!!.toDouble()-1000.0)<1e-9 }
    test("date arithmetic") { tools.invoke("date.days_between", mapOf("from" to "2026-08-01", "to" to "2026-08-11")).values["days"]=="10" }
    test("text count") { tools.invoke("text.count", mapOf("text" to "one two   three")).values["words"]=="3" }

    val note = Note(id="n1", accountId=accountA.id, projectId=projectA.id, title="Grammar note", body="present perfect British English")
    val conversation = Conversation(id="conv-1", accountId=accountA.id, projectId=projectA.id, scope=ConversationScope.PROJECT, title="C1 grammar")
    val source = Source(id="s1", accountId=accountA.id, title="C1 Handbook", type="PDF", mimeType="application/pdf", contentHash="hash", state=SourceState.READY, updatedAt=now)
    val search = SearchEngine.search("C1 grammar", accountA.id, listOf(projectA, projectB), listOf(conversation), listOf(source), listOf(note), mistakes, listOf(goal))
    test("global typed search returns multiple domains") { search.map { it.type }.toSet().size >= 2 }
    test("search account isolation") { search.none { it.id==projectB.id } }
    test("search stable deep links") { search.all { it.deepLink.startsWith("veltrix://") } }

    val event = ActivityEvent(accountId=accountA.id, type="QUIZ_COMPLETED", projectId=projectA.id, objectId="quiz-1", idempotencyKey="quiz-1-completed")
    test("meaningful activity accepts completed quiz") { MeaningfulActivityClassifier.isMeaningful(event) }
    test("meaningful activity rejects nav tap") { !MeaningfulActivityClassifier.isMeaningful(event.copy(type="NAV_TAP")) }

    val mutation = SyncMutation(accountId=accountA.id, entityType="NOTE", entityId="n1", operation="UPDATE", expectedRevision=1, idempotencyKey="note-n1-r2")
    var queue = SyncEngine.enqueue(emptyList(), mutation)
    queue = SyncEngine.enqueue(queue, mutation.copy(id="other"))
    test("sync queue idempotent") { queue.size==1 }
    test("sync conflict detected") { SyncEngine.resolveServerRevision(mutation, 2)==SyncState.CONFLICT }
    test("sync matching revision allowed") { SyncEngine.resolveServerRevision(mutation, 1)==SyncState.PENDING }

    val home = SnapshotEngine.home(UserProfile(accountId=accountA.id, displayName="User"), listOf(projectA), listOf(source), maturity, listOf(score.score), listOf("Grammar"), SyncState.SYNCED)
    test("home snapshot aggregates in one contract") { home.recentProjects.size==1 && home.recentSources.size==1 && home.mapState=="LOCKED_PART_2" }
    val ws = SnapshotEngine.project(projectA, listOf(goal, completed.copy(id="goal2")), listOf(event), listOf(conversation), listOf(source), listOf(note), mistakes, maturity.state, SyncState.SYNCED)
    test("project workspace is more than folder") { ws.activeGoals.size==1 && ws.completedGoalCount==1 && ws.recentConversations.size==1 && ws.noteCount==1 }

    test("Part2 XP not implemented in core tool registry") { tools.definitions().none { it.id.contains("xp", true) || it.id.contains("coin", true) } }
    test("Store state honest Part2 placeholder") { home.mapState=="LOCKED_PART_2" }

    println("SUMMARY | PASS=$pass FAIL=$fail TOTAL=${pass+fail}")
    if (fail != 0) error("Regression failures: $fail")
}
