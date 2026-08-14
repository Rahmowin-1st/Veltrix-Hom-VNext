package com.veltrix.hom.vnext.server

import kotlin.test.*
import java.util.UUID

class Part2PostgresIntegrationTest {
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

    @Test fun meaningfulEventRewardsExactlyOnceAndSemanticDuplicateIsRejected() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val auth = AuthRepository(db)
            val projects = ProjectRepository(db)
            val memory = MemoryRepository(db)
            val game = Part2GameRepository(db, memory)
            val suffix = UUID.randomUUID().toString().take(8)
            val account = auth.register(RegisterRequest("part2-reward-$suffix@example.test", "testing-password-12345", "Reward Test"))

            val before = game.profile(account.accountId)
            assertEquals(1, before.level)
            assertEquals(0, before.lifetimeXp)
            assertEquals(0, before.coinBalance)

            val project = projects.create(account.accountId, CreateProjectRequest("Meaningful project", "A durable learning purpose"))
            assertTrue(game.processPending(50) >= 1)
            val afterFirst = game.profile(account.accountId)
            assertTrue(afterFirst.lifetimeXp > 0)
            assertTrue(afterFirst.coinBalance > 0)
            assertTrue(afterFirst.gamingStatsSummary.meaningfulActivities >= 1)

            // Queue processing is replay-safe after the original event is DONE.
            game.processPending(50)
            val afterReplay = game.profile(account.accountId)
            assertEquals(afterFirst.lifetimeXp, afterReplay.lifetimeXp)
            assertEquals(afterFirst.coinBalance, afterReplay.coinBalance)

            // A new event with a different delivery idempotency key but the same semantic object
            // must not create a second reward grant.
            db.tx { c ->
                c.prepareStatement("""
                    INSERT INTO activity_event(account_id,event_type,project_id,object_id,metadata,idempotency_key,meaningful,evidence)
                    VALUES (?::uuid,'PROJECT_CREATED',?::uuid,?,'{}'::jsonb,?,true,'{\"semanticEvidence\":true}'::jsonb)
                """.trimIndent()).use { ps ->
                    ps.setString(1, account.accountId)
                    ps.setString(2, project.id)
                    ps.setString(3, project.id)
                    ps.setString(4, "semantic-duplicate:${project.id}")
                    ps.executeUpdate()
                }
            }
            game.processPending(50)
            val afterSemanticDuplicate = game.profile(account.accountId)
            assertEquals(afterFirst.lifetimeXp, afterSemanticDuplicate.lifetimeXp)
            assertEquals(afterFirst.coinBalance, afterSemanticDuplicate.coinBalance)

            val duplicateRejected = db.tx { c ->
                c.prepareStatement("""
                    SELECT count(*) FROM reward_decision_log r
                    JOIN activity_event a ON a.event_id=r.source_event_id
                    WHERE r.account_id=?::uuid AND a.object_id=? AND r.decision_code='SEMANTIC_DUPLICATE'
                """.trimIndent()).use { ps ->
                    ps.setString(1, account.accountId)
                    ps.setString(2, project.id)
                    ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
                }
            }
            assertEquals(1, duplicateRejected)

            // Non-meaningful navigation noise is never enqueued for progression.
            db.tx { c ->
                c.prepareStatement("""
                    INSERT INTO activity_event(account_id,event_type,object_id,metadata,idempotency_key,meaningful,evidence)
                    VALUES (?::uuid,'NAV_TAP','home','{}'::jsonb,?,false,'{}'::jsonb)
                """.trimIndent()).use { ps ->
                    ps.setString(1, account.accountId)
                    ps.setString(2, "nav-tap:${UUID.randomUUID()}")
                    ps.executeUpdate()
                }
            }
            assertEquals(0, game.processPending(50))
            val afterNoise = game.profile(account.accountId)
            assertEquals(afterFirst.lifetimeXp, afterNoise.lifetimeXp)
            assertEquals(afterFirst.coinBalance, afterNoise.coinBalance)
            assertTrue(game.reconcileCoins(account.accountId).matches)
        }
    }

    @Test fun accountGameStateIsIsolated() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val auth = AuthRepository(db)
            val projects = ProjectRepository(db)
            val memory = MemoryRepository(db)
            val game = Part2GameRepository(db, memory)
            val suffix = UUID.randomUUID().toString().take(8)
            val a = auth.register(RegisterRequest("part2-a-$suffix@example.test", "testing-password-12345", "A"))
            val b = auth.register(RegisterRequest("part2-b-$suffix@example.test", "testing-password-12345", "B"))

            projects.create(a.accountId, CreateProjectRequest("A project", "Only account A should earn this"))
            game.processPending(50)
            val aProfile = game.profile(a.accountId)
            val bProfile = game.profile(b.accountId)
            assertTrue(aProfile.lifetimeXp > 0)
            assertEquals(0, bProfile.lifetimeXp)
            assertEquals(0, bProfile.coinBalance)
            assertTrue(game.xpHistory(b.accountId, 50, 0).isEmpty())
            assertTrue(game.coinHistory(b.accountId, 50, 0).isEmpty())
        }
    }
}
