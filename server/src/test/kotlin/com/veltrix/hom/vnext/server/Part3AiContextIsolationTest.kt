package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.server.ai.AiContextOrchestrator
import com.veltrix.hom.vnext.server.rag.EmbeddingFactory
import com.veltrix.hom.vnext.server.rag.HybridRetrievalRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.UUID

class Part3AiContextIsolationTest {
    private fun config(): ServerConfig? {
        val url = System.getenv("VELTRIX_TEST_DATABASE_URL") ?: return null
        return ServerConfig(
            "test",
            url,
            System.getenv("VELTRIX_TEST_DATABASE_USER") ?: "postgres",
            System.getenv("VELTRIX_TEST_DATABASE_PASSWORD") ?: "postgres",
            8080,
            "disabled",
            null,
        )
    }

    @Test
    fun globalAndProjectBrainsReceiveOnlyAllowedStudentSignals() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val suffix = UUID.randomUUID().toString().take(8)
            val account = AuthRepository(db).register(
                RegisterRequest("part3-ai-$suffix@example.test", "testing-password-12345", "AI Context User")
            )
            val projects = ProjectRepository(db)
            val chats = ChatRepository(db)
            val memory = MemoryRepository(db)
            val instructions = ProjectInstructionRepository(db)
            val intelligence = ChatIntelligenceRepository(db)
            val part3 = Part3FinalRepository(db, projects, chats, memory, instructions)
            val rag = HybridRetrievalRepository(db, EmbeddingFactory.create(cfg))
            val router = AiContextOrchestrator(projects, chats, memory, instructions, intelligence, rag, part3)

            val physics = projects.create(account.accountId, CreateProjectRequest("Physics $suffix", "Mechanics"))
            val cefr = projects.create(account.accountId, CreateProjectRequest("CEFR $suffix", "English exam"))
            val globalChat = chats.create(account.accountId, CreateConversationRequest(scope = "GLOBAL", title = "Global"))
            val physicsChat = chats.create(account.accountId, CreateConversationRequest(scope = "PROJECT", projectId = physics.id, title = "Physics"))

            val globalSignal = part3.createSignal(
                account.accountId,
                StudentSignalCreateRequest(
                    type = "PREFERENCE",
                    valueJson = "{\"answerStyle\":\"concise\"}",
                    evidence = listOf(StudentSignalEvidenceDto("USER_STATEMENT", "global-$suffix")),
                ),
            )
            val physicsSignal = part3.createSignal(
                account.accountId,
                StudentSignalCreateRequest(
                    projectId = physics.id,
                    type = "PROJECT",
                    valueJson = "{\"focus\":\"kinematics\"}",
                    evidence = listOf(StudentSignalEvidenceDto("PROJECT_ACTIVITY", "physics-$suffix")),
                ),
            )
            val cefrSignal = part3.createSignal(
                account.accountId,
                StudentSignalCreateRequest(
                    projectId = cefr.id,
                    type = "PROJECT",
                    valueJson = "{\"focus\":\"vocabulary\"}",
                    evidence = listOf(StudentSignalEvidenceDto("PROJECT_ACTIVITY", "cefr-$suffix")),
                ),
            )

            val global = router.prepare(
                account.accountId,
                AiStreamRequest(
                    conversationId = globalChat.id,
                    text = "What should I study next?",
                    idempotencyKey = "global-ai-$suffix",
                ),
            )
            assertEquals("GLOBAL", global.diagnostics.scope)
            assertEquals(null, global.diagnostics.projectId)
            assertEquals(setOf(globalSignal.id), global.diagnostics.studentSignals.map { it.id }.toSet())
            assertFalse(global.request.systemPrompt.contains("kinematics"))
            assertFalse(global.request.systemPrompt.contains("vocabulary"))

            val project = router.prepare(
                account.accountId,
                AiStreamRequest(
                    conversationId = physicsChat.id,
                    projectId = physics.id,
                    text = "Continue my current topic",
                    idempotencyKey = "physics-ai-$suffix",
                ),
            )
            assertEquals(physics.id, project.diagnostics.projectId)
            assertTrue(project.diagnostics.studentSignals.any { it.id == globalSignal.id })
            assertTrue(project.diagnostics.studentSignals.any { it.id == physicsSignal.id })
            assertFalse(project.diagnostics.studentSignals.any { it.id == cefrSignal.id })
            assertTrue(project.request.systemPrompt.contains("kinematics"))
            assertFalse(project.request.systemPrompt.contains("vocabulary"))
            assertTrue(project.diagnostics.studentSignals.all { it.evidenceObjectIds.isNotEmpty() })
            assertTrue(project.diagnostics.selectionCaps["studentSignals"] == 6)

            assertFailsWith<DomainException> {
                router.prepare(
                    account.accountId,
                    AiStreamRequest(
                        conversationId = globalChat.id,
                        projectId = physics.id,
                        text = "Leak project context",
                        idempotencyKey = "mismatch-$suffix",
                    ),
                )
            }
        }
    }
}
