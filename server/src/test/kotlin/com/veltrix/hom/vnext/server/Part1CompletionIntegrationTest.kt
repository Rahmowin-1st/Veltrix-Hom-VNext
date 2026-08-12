package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.server.ai.AiContextOrchestrator
import com.veltrix.hom.vnext.server.ai.MemoryAutomationService
import com.veltrix.hom.vnext.server.learning.DeepPracticeRepository
import kotlin.test.*
import java.io.File
import java.util.UUID

class Part1CompletionIntegrationTest {
    private fun config(): ServerConfig? {
        val url = System.getenv("VELTRIX_TEST_DATABASE_URL") ?: System.getenv("VELTRIX_DATABASE_URL") ?: return null
        val env = System.getenv().toMutableMap().apply {
            put("VELTRIX_ENV", "test")
            put("VELTRIX_DATABASE_URL", url)
            put("VELTRIX_DATABASE_USER", System.getenv("VELTRIX_TEST_DATABASE_USER") ?: System.getenv("VELTRIX_DATABASE_USER") ?: "postgres")
            put("VELTRIX_DATABASE_PASSWORD", System.getenv("VELTRIX_TEST_DATABASE_PASSWORD") ?: System.getenv("VELTRIX_DATABASE_PASSWORD") ?: "postgres")
            put("VELTRIX_AI_PROVIDER", "disabled")
            put("VELTRIX_TEST_AI_MOCK", "true")
            put("VELTRIX_EMBEDDING_PROVIDER", "disabled")
            put("VELTRIX_TEST_EMBEDDING_MOCK", "true")
            put("VELTRIX_WORKERS_ENABLED", "false")
            put("VELTRIX_STORAGE_PROVIDER", System.getenv("VELTRIX_STORAGE_PROVIDER") ?: "local")
        }
        return ServerConfig.fromEnv(env)
    }

    @Test fun sourceRagAiMemoryPracticeAndIsolationFlow() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val auth = AuthRepository(db)
            val projects = ProjectRepository(db)
            val instructions = ProjectInstructionRepository(db)
            val memory = MemoryRepository(db)
            val sources = SourceRepository(db)
            val sourceProcessing = SourceProcessingService(cfg, db, sources)
            val chats = ChatRepository(db)
            val chatIntel = ChatIntelligenceRepository(db)
            val ai = AiExecutionService(cfg)
            val context = AiContextOrchestrator(projects, chats, memory, instructions, chatIntel, sourceProcessing.rag)
            val memoryAutomation = MemoryAutomationService(db, workerEnabled = false)
            val practice = DeepPracticeRepository(db)
            try {
                val suffix = UUID.randomUUID().toString().take(8)
                val a = auth.register(RegisterRequest("part1-a-$suffix@example.test", "testing-password-12345", "Part1 A"))
                val b = auth.register(RegisterRequest("part1-b-$suffix@example.test", "testing-password-12345", "Part1 B"))
                val project = projects.create(a.accountId, CreateProjectRequest("CEFR C1", "Reach advanced English"))
                instructions.put(a.accountId, project.id, ProjectInstructionPutRequest("Use British English. Correct grammar. Prefer CEFR C1 vocabulary."))
                memory.create(a.accountId, MemoryCreateRequest("ACCOUNT", null, "PREFERENCE", "prefers concise explanations", "EXPLICIT_USER", 1.0, "TEST", "pref-$suffix"))
                memory.create(a.accountId, MemoryCreateRequest("PROJECT", project.id, "WEAKNESS", "struggles with phrasal verbs", "ASSESSMENT", 0.92, "TEST", "weak-$suffix"))

                // Production-style object-storage path: upload -> object store -> processing job -> chunks -> vectors -> READY.
                val fixture = File.createTempFile("veltrix-part1-", ".txt").apply {
                    writeText("CEFR C1 British English fixture. Phrasal verbs include carry on and put up with. Use colour, not color, in this source.")
                }
                val uploaded = sourceProcessing.enqueueUpload(a.accountId, "CEFR Fixture", "TEXT", "text/plain", "cefr-fixture.txt", fixture)
                assertEquals("PROCESSING", uploaded.state)
                assertEquals(1, sourceProcessing.processPendingNow(5))
                val ready = sources.get(a.accountId, uploaded.id)
                assertEquals("READY", ready.state)
                val head = sourceProcessing.storageHead(a.accountId, ready.id)
                assertTrue(head.size > 0)
                assertNotNull(head.sha256)
                assertFailsWith<DomainException> { sourceProcessing.storageHead(b.accountId, ready.id) }

                val hybrid = sourceProcessing.hybridSearch(a.accountId, HybridSearchRequest("British English colour phrasal verbs", listOf(ready.id), null, 5))
                assertTrue(hybrid.isNotEmpty())
                assertEquals(ready.id, hybrid.first().citation.sourceId)
                assertTrue(hybrid.first().semanticScore > 0.0)
                assertTrue(hybrid.first().citation.textHash.isNotBlank())
                assertTrue(sourceProcessing.hybridSearch(b.accountId, HybridSearchRequest("British English", listOf(ready.id), null, 5)).isEmpty())

                sources.linkProject(a.accountId, ready.id, project.id)
                val projectChat = chats.create(a.accountId, CreateConversationRequest(scope="PROJECT", projectId=project.id, title="CEFR Coach", learningMode="TUTOR"))
                val planned = context.prepare(a.accountId, AiStreamRequest(
                    conversationId=projectChat.id,
                    projectId=project.id,
                    sourceIds=listOf(ready.id),
                    text="Help me understand phrasal verbs from the selected source",
                    learningMode="TUTOR",
                    idempotencyKey="ctx-$suffix-12345678"
                ))
                assertEquals(project.id, planned.planned.carry.projectId)
                assertTrue(planned.planned.projectInstruction?.contains("British English") == true)
                assertTrue(planned.planned.memories.any { it.statement.contains("concise", true) })
                assertTrue(planned.planned.memories.any { it.statement.contains("phrasal", true) })
                assertTrue(planned.citations.isNotEmpty())
                assertTrue(planned.request.systemPrompt.contains("Source grounding is active"))
                assertTrue(planned.planned.toolIds.isNotEmpty())

                // Test-only provider exercises the same prepared provider request; it is never routed in production.
                assertTrue(ai.testProviderConfigured)
                assertFalse(ai.liveProviderConfigured)
                val streamed = ai.stream("integration-ai-$suffix", planned.request).joinToString("") { it.text }
                assertTrue(streamed.isNotBlank())

                val userMessage = chats.enqueueUserMessage(a.accountId, projectChat.id, SendMessageRequest(
                    text="I prefer concise explanations.", idempotencyKey="user-memory-$suffix-12345678"
                ))
                chats.markUserSending(a.accountId, userMessage.id)
                val assistant = chats.createAssistantStreaming(a.accountId, projectChat.id, userMessage.id, "assistant-memory-$suffix-12345678")
                chats.appendAssistantContent(a.accountId, assistant.id, "Understood. I will keep explanations concise.")
                chats.finishAssistant(a.accountId, assistant.id)
                chats.markUserCompleted(a.accountId, userMessage.id)
                chatIntel.persistCitations(a.accountId, assistant.id, planned.citations)
                assertEquals(planned.citations.size, chatIntel.citations(a.accountId, assistant.id).size)
                memoryAutomation.enqueuePostChat(a.accountId, projectChat.id, userMessage.id, assistant.id)
                assertTrue(memoryAutomation.processPendingNow(10) >= 1)
                val retrieved = memory.retrieveCore(a.accountId, project.id, "concise explanations", 20)
                assertTrue(retrieved.any { it.statement.contains("concise", true) })

                // Global chat must not inherit CEFR Project instruction or Project-scoped memory.
                val global = chats.create(a.accountId, CreateConversationRequest(scope="GLOBAL", title="Global"))
                val globalCtx = context.prepare(a.accountId, AiStreamRequest(
                    conversationId=global.id,
                    text="Explain a general topic concisely",
                    idempotencyKey="global-$suffix-12345678"
                ))
                assertNull(globalCtx.planned.carry.projectId)
                assertNull(globalCtx.planned.projectInstruction)
                assertFalse(globalCtx.planned.memories.any { it.scopeId == project.id })
                assertTrue(globalCtx.planned.memories.any { it.statement.contains("concise", true) })

                // Deep Practice: prompt -> attempt -> hint -> check -> Mistake + signal -> next -> complete.
                val session = practice.create(a.accountId, PracticeConfigRequest(projectId=project.id, sourceIds=listOf(ready.id), focusTopic="phrasal verbs", difficulty=3, targetItemCount=2, adaptive=true))
                val first = practice.addItem(a.accountId, session.session.id, PracticeSeedItemRequest("Meaning of carry on?", "continue", "carry on means continue", topic="phrasal verbs", difficulty=3))
                practice.addItem(a.accountId, session.session.id, PracticeSeedItemRequest("British spelling of color?", "colour", "British English uses colour", topic="British English", difficulty=3))
                practice.attempt(a.accountId, session.session.id, first.id, PracticeAttemptRequest("stop", "practice-attempt-$suffix-12345678"))
                val hint = practice.hint(a.accountId, session.session.id, first.id)
                assertTrue(hint.body.isNotBlank())
                val checked = practice.check(a.accountId, session.session.id, first.id)
                assertFalse(checked.correct)
                assertNotNull(checked.mistakeId)
                assertNotNull(checked.nextItemId)
                val afterFirst = practice.detail(a.accountId, session.session.id)
                val second = afterFirst.items.first { it.id == checked.nextItemId }
                practice.attempt(a.accountId, session.session.id, second.id, PracticeAttemptRequest("colour", "practice-second-$suffix-12345678"))
                assertTrue(practice.check(a.accountId, session.session.id, second.id).correct)
                val beforeComplete = practice.detail(a.accountId, session.session.id)
                val completed = practice.complete(a.accountId, session.session.id, beforeComplete.session.revision)
                assertEquals("COMPLETED", completed.session.state)
                assertEquals(2, completed.answered)
                assertEquals(1, completed.correct)

                val signalCount = db.tx { c -> c.prepareStatement("SELECT count(*) FROM learning_signal WHERE account_id=?::uuid AND project_id=?::uuid AND topic IN ('phrasal verbs','British English')").use { ps -> ps.setString(1,a.accountId);ps.setString(2,project.id);ps.executeQuery().use{rs->rs.next();rs.getInt(1)} } }
                assertTrue(signalCount >= 2)
                assertTrue(memoryAutomation.processPendingNow(20) >= 1)

                // Ownership boundary on Project remains enforced across integrated systems.
                assertFailsWith<DomainException> { projects.get(b.accountId, project.id) }
                assertFailsWith<DomainException> { chats.get(b.accountId, projectChat.id) }
            } finally {
                memoryAutomation.close()
                sourceProcessing.close()
            }
        }
    }
}
