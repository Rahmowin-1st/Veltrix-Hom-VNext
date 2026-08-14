package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.LevelCurveV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.UUID

class Part3FinalIntegrationTest {
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
    fun studentCorrectionContextSnapshotsAndCommandsAreAccountScoped() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val suffix = UUID.randomUUID().toString().take(8)
            val auth = AuthRepository(db)
            val session = auth.register(RegisterRequest("part3-$suffix@example.test", "testing-password-12345", "Part3 User"))
            val other = auth.register(RegisterRequest("part3-other-$suffix@example.test", "testing-password-12345", "Other User"))
            val projects = ProjectRepository(db)
            val chats = ChatRepository(db)
            val memory = MemoryRepository(db)
            val instructions = ProjectInstructionRepository(db)
            val game = Part2GameRepository(db, memory)
            val part3 = Part3FinalRepository(db, projects, chats, memory, instructions)

            game.profile(session.accountId)
            game.profile(other.accountId)
            val project = projects.create(session.accountId, CreateProjectRequest("Physics $suffix", "Prepare for mechanics exam"))
            val otherProject = projects.create(other.accountId, CreateProjectRequest("Foreign $suffix", "Must remain isolated"))

            val explicit = part3.createSignal(
                session.accountId,
                StudentSignalCreateRequest(
                    projectId = project.id,
                    type = "INTEREST",
                    valueJson = "{\"topic\":\"mechanics\"}",
                    evidence = listOf(StudentSignalEvidenceDto("USER_STATEMENT", "message-$suffix")),
                ),
            )
            val corrected = part3.correctSignal(
                session.accountId,
                explicit.id,
                StudentSignalCorrectionRequest(
                    valueJson = "{\"topic\":\"electromagnetism\"}",
                    evidenceObjectId = "correction-$suffix",
                    expectedRevision = explicit.revision,
                ),
            )
            assertEquals("EXPLICIT_USER", corrected.source)
            assertEquals(explicit.id, corrected.supersedes)
            assertEquals(1.0, corrected.confidence)

            val model = part3.studentModel(session.accountId, project.id, 50)
            assertTrue(model.signals.any { it.id == corrected.id })
            assertFalse(model.signals.any { it.id == explicit.id && it.status == "ACTIVE" })

            val context = part3.putContextCarry(
                session.accountId,
                ContextCarryPutRequest(
                    projectId = project.id,
                    topic = "electromagnetism",
                    learningMode = "TUTOR",
                    origin = "PROJECT",
                    returnDestination = "veltrix://project/${project.id}",
                    expectedRevision = 0,
                ),
            )
            assertEquals(project.id, context.projectId)
            assertEquals("TUTOR", part3.getContextCarry(session.accountId)?.learningMode)

            val command = part3.resolveCommand(session.accountId, UniversalCommandRequest("Open my Physics $suffix project"))
            assertTrue(command.deterministic)
            assertEquals(project.id, command.targetId)
            assertEquals("veltrix://project/${project.id}", command.deepLink)

            val templates = part3.templates()
            assertEquals(setOf("LANGUAGE_EXAM", "SCHOOL_SUBJECT", "RESEARCH", "EXAM_PREPARATION", "PERSONAL_SKILL", "COMPETITION", "CUSTOM"), templates.map { it.templateId }.toSet())
            assertEquals(135, part3.avatarCatalog(session.accountId).size)

            val home = part3.homeSnapshot(session.accountId)
            assertEquals(session.accountId, home.accountId)
            assertEquals(3, home.schemaVersion)
            assertTrue(home.recentProjects.any { it.id == project.id })
            val personal = part3.personalSnapshot(session.accountId)
            assertEquals(3, personal.schemaVersion)
            assertEquals(session.accountId, personal.accountId)
            val workspace = part3.workspace(session.accountId, project.id)
            assertEquals(3, workspace.schemaVersion)
            assertEquals(project.id, workspace.project.id)
            assertNotNull(workspace.contextCarry)

            val foreignCommand = part3.resolveCommand(session.accountId, UniversalCommandRequest("Open my Foreign $suffix project"))
            assertEquals(null, foreignCommand.targetId)
            assertFalse(part3.studentModel(session.accountId, project.id, 50).signals.any { it.projectId == otherProject.id })
        }
    }

    @Test
    fun part2ProgressionRemainsUsableWhileLevel50RequiresNinetyQualifiedDays() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val suffix = UUID.randomUUID().toString().take(8)
            val session = AuthRepository(db).register(RegisterRequest("part3-level-$suffix@example.test", "testing-password-12345", "Level Gate User"))
            val memory = MemoryRepository(db)
            val game = Part2GameRepository(db, memory)
            val completion = Part2CompletionRepository(db)
            game.profile(session.accountId)

            completion.adjustXp(session.accountId, LevelCurveV1.thresholdForLevel(10), "part3-level-xp-$suffix", "Part3 level compatibility fixture")
            completion.adjustCoins(session.accountId, 400, "part3-level-coins-$suffix", "Part3 store compatibility fixture")
            val purchase = game.purchase(session.accountId, StorePurchaseRequest("avatar-pro-focus", "part3-buy-$suffix"))
            assertEquals(350, purchase.authoritativePrice)

            db.tx { c ->
                c.prepareStatement("UPDATE progression_profile SET lifetime_xp=?,level=50,qualified_active_days=89,effective_level=49 WHERE account_id=?::uuid").use { ps ->
                    ps.setLong(1, LevelCurveV1.thresholdForLevel(50)); ps.setString(2, session.accountId); ps.executeUpdate()
                }
            }
            val effective89 = db.tx { c -> c.prepareStatement("SELECT LEAST(level,part3_max_level_for_days(qualified_active_days)) FROM progression_profile WHERE account_id=?::uuid").use { ps -> ps.setString(1, session.accountId); ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) } } }
            assertEquals(49, effective89)
            db.tx { c -> c.prepareStatement("UPDATE progression_profile SET qualified_active_days=90 WHERE account_id=?::uuid").use { ps -> ps.setString(1, session.accountId); ps.executeUpdate() } }
            val effective90 = db.tx { c -> c.prepareStatement("SELECT LEAST(level,part3_max_level_for_days(qualified_active_days)) FROM progression_profile WHERE account_id=?::uuid").use { ps -> ps.setString(1, session.accountId); ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) } } }
            assertEquals(50, effective90)
        }
    }
}
