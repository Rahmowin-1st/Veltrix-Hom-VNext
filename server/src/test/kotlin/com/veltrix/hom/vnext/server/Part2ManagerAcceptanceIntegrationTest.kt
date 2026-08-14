package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Narrow Manager-acceptance coverage for Part 2 boundaries that were not explicitly proven by the
 * original final run. All tests use the real PostgreSQL service configured by CI.
 */
class Part2ManagerAcceptanceIntegrationTest {
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
            put("VELTRIX_STORAGE_PROVIDER", "local")
        }
        return ServerConfig.fromEnv(env)
    }

    @Test
    fun concurrentPurchaseRaceAllowsNoOverspendAndReconciles() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val suffix = UUID.randomUUID().toString().take(8)
            val auth = AuthRepository(db)
            val session = auth.register(RegisterRequest("race-$suffix@example.test", PASSWORD, "Race User"))
            val completion = Part2CompletionRepository(db)
            val memory = MemoryRepository(db)
            val deviceA = Part2GameRepository(db, memory)
            val deviceB = Part2GameRepository(db, memory)
            completion.adjustXp(session.accountId, 10_000, "race-xp-$suffix", "Manager acceptance level fixture")
            completion.adjustCoins(session.accountId, 700, "race-coins-$suffix", "Manager acceptance coin fixture")

            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val a = pool.submit<Result<StorePurchaseResponse>> {
                    start.await()
                    runCatching { deviceA.purchase(session.accountId, StorePurchaseRequest("avatar-pro-focus", "race-pro-$suffix")) }
                }
                val b = pool.submit<Result<StorePurchaseResponse>> {
                    start.await()
                    runCatching { deviceB.purchase(session.accountId, StorePurchaseRequest("avatar-elite-scholar", "race-elite-$suffix")) }
                }
                start.countDown()
                val results = listOf(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))
                assertEquals(1, results.count { it.isSuccess }, "Exactly one competing purchase may commit with 700 coins")
                val profileA = deviceA.profile(session.accountId)
                val profileB = deviceB.profile(session.accountId)
                assertTrue(profileA.coinBalance >= 0)
                assertEquals(profileA.coinBalance, profileB.coinBalance)
                assertTrue(deviceA.reconcileCoins(session.accountId).matches)
                val acquired = deviceA.inventory(session.accountId, 200, 0).count { it.itemId in setOf("avatar-pro-focus", "avatar-elite-scholar") }
                assertEquals(1, acquired)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun multiDeviceConcurrentAvatarEquipHasSingleWinnerAndConverges() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val suffix = UUID.randomUUID().toString().take(8)
            val session = AuthRepository(db).register(RegisterRequest("multi-$suffix@example.test", PASSWORD, "Multi User"))
            val completion = Part2CompletionRepository(db)
            val memory = MemoryRepository(db)
            val deviceA = Part2GameRepository(db, memory)
            val deviceB = Part2GameRepository(db, memory)
            completion.adjustXp(session.accountId, 10_000, "multi-xp-$suffix", "Manager acceptance level fixture")
            completion.adjustCoins(session.accountId, 1_050, "multi-coins-$suffix", "Manager acceptance coin fixture")
            deviceA.purchase(session.accountId, StorePurchaseRequest("avatar-pro-focus", "multi-pro-$suffix"))
            deviceA.purchase(session.accountId, StorePurchaseRequest("avatar-elite-scholar", "multi-elite-$suffix"))
            val expectedRevision = deviceA.profile(session.accountId).equippedAvatar.revision

            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val a = pool.submit<Result<AvatarStateResponse>> {
                    start.await()
                    runCatching { deviceA.equipAvatar(session.accountId, EquipAvatarRequest("avatar-pro-focus", expectedRevision)) }
                }
                val b = pool.submit<Result<AvatarStateResponse>> {
                    start.await()
                    runCatching { deviceB.equipAvatar(session.accountId, EquipAvatarRequest("avatar-elite-scholar", expectedRevision)) }
                }
                start.countDown()
                val results = listOf(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))
                assertEquals(1, results.count { it.isSuccess }, "Optimistic revision must allow exactly one equip winner")
                val profileA = deviceA.profile(session.accountId)
                val profileB = deviceB.profile(session.accountId)
                assertEquals(profileA.equippedAvatar.avatarId, profileB.equippedAvatar.avatarId)
                assertEquals(profileA.equippedAvatar.revision, profileB.equippedAvatar.revision)
                assertEquals(1, deviceA.avatars(session.accountId).count { it.equipped })
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun seasonRolloverPreservesLifetimeProgressionCoinsInventoryAndClosesSeasonProgress() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val suffix = UUID.randomUUID().toString().take(8)
            val session = AuthRepository(db).register(RegisterRequest("season-$suffix@example.test", PASSWORD, "Season User"))
            val completion = Part2CompletionRepository(db)
            val game = Part2GameRepository(db, MemoryRepository(db))
            completion.adjustXp(session.accountId, 8_000, "season-xp-$suffix", "Manager acceptance lifetime XP fixture")
            completion.adjustCoins(session.accountId, 900, "season-coins-$suffix", "Manager acceptance lifetime coin fixture")
            game.purchase(session.accountId, StorePurchaseRequest("avatar-pro-focus", "season-buy-$suffix"))
            val before = game.profile(session.accountId)
            val beforeInventory = game.inventory(session.accountId, 200, 0).map { it.itemId }.toSet()
            val oldId = "manager-old-$suffix"
            val newId = "manager-new-$suffix"
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            db.tx { c ->
                c.prepareStatement("INSERT INTO season_definition(season_id,version,start_at,end_at,state,identity_metadata) VALUES (?,1,?,?,'ACTIVE','{}'::jsonb)").use { ps ->
                    ps.setString(1, oldId); ps.setObject(2, now.minusDays(10)); ps.setObject(3, now.minusDays(1)); ps.executeUpdate()
                }
                c.prepareStatement("INSERT INTO season_progress(account_id,season_id,season_version,participated,xp_earned,coins_earned,state) VALUES (?::uuid,?,1,true,111,22,'ACTIVE')").use { ps -> ps.setString(1, session.accountId); ps.setString(2, oldId); ps.executeUpdate() }
                c.prepareStatement("INSERT INTO season_definition(season_id,version,start_at,end_at,state,identity_metadata) VALUES (?,1,?,?,'PLANNED','{}'::jsonb)").use { ps ->
                    ps.setString(1, newId); ps.setObject(2, now.minusHours(1)); ps.setObject(3, now.plusDays(2)); ps.executeUpdate()
                }
            }
            try {
                assertTrue(game.reconcileSeasons() >= 2)
                val oldState = db.tx { c -> c.prepareStatement("SELECT state FROM season_progress WHERE account_id=?::uuid AND season_id=? AND season_version=1").use { ps -> ps.setString(1, session.accountId); ps.setString(2, oldId); ps.executeQuery().use { rs -> rs.next(); rs.getString(1) } } }
                assertEquals("CLOSED", oldState)
                val current = game.currentSeason(session.accountId).first
                assertNotNull(current)
                assertEquals(newId, current.seasonId)
                assertEquals("ACTIVE", current.state)
                val after = game.profile(session.accountId)
                assertEquals(before.lifetimeXp, after.lifetimeXp)
                assertEquals(before.level, after.level)
                assertEquals(before.coinBalance, after.coinBalance)
                assertEquals(beforeInventory, game.inventory(session.accountId, 200, 0).map { it.itemId }.toSet())
                assertTrue(game.reconcileCoins(session.accountId).matches)
                assertTrue(completion.reconcileXp(session.accountId).matches)
            } finally {
                db.tx { c ->
                    c.prepareStatement("DELETE FROM season_progress WHERE account_id=?::uuid AND season_id IN (?,?)").use { ps -> ps.setString(1, session.accountId); ps.setString(2, oldId); ps.setString(3, newId); ps.executeUpdate() }
                    c.prepareStatement("DELETE FROM season_rollover_execution WHERE season_id IN (?,?)").use { ps -> ps.setString(1, oldId); ps.setString(2, newId); ps.executeUpdate() }
                    c.prepareStatement("DELETE FROM season_definition WHERE season_id IN (?,?)").use { ps -> ps.setString(1, oldId); ps.setString(2, newId); ps.executeUpdate() }
                }
            }
        }
    }

    @Test
    fun part2AccountExportIncludesOwnedGameStateAndDeleteRevokesRelogin() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val suffix = UUID.randomUUID().toString().take(8)
            val login = "delete-$suffix@example.test"
            val auth = AuthRepository(db)
            val session = auth.register(RegisterRequest(login, PASSWORD, "Delete User"))
            val game = Part2GameRepository(db, MemoryRepository(db))
            game.profile(session.accountId) // materializes all mandatory account-owned Part 2 projections.
            Part2CompletionRepository(db).adjustCoins(session.accountId, 25, "delete-coins-$suffix", "Manager acceptance export fixture")
            val export = AccountDataRepository(db).export(session.accountId)
            val requiredKeys = setOf(
                "progressionProfiles", "xpLedger", "coinAccounts", "coinLedger", "rewardGrants", "rewardDecisions", "rewardQueue",
                "dailyActivity", "consistencyState", "consistencyHistory", "achievementProgress", "inventoryOwnership", "equippedAvatars",
                "storePurchases", "storeRefunds", "personalMaps", "mapGenerations", "mapUnitProgress", "seasonProgress", "gamingStatistics", "gameStateEvents",
            )
            assertTrue(requiredKeys.all { export.entityCounts.containsKey(it) })
            assertEquals(1L, export.entityCounts.getValue("progressionProfiles"))
            assertEquals(1L, export.entityCounts.getValue("coinAccounts"))
            assertTrue(export.entityCounts.getValue("inventoryOwnership") >= 1L)
            assertEquals(1L, export.entityCounts.getValue("equippedAvatars"))
            assertEquals(1L, export.entityCounts.getValue("gamingStatistics"))

            AccountDataRepository(db).requestDeletion(session.accountId, AccountDeletionRequest(PASSWORD, "DELETE"))
            assertEquals(null, auth.resolve(session.sessionToken))
            assertFailsWith<DomainException> { auth.login(LoginRequest(login, PASSWORD, "manager-relogin")) }
            val deleted = db.tx { c -> c.prepareStatement("SELECT deleted_at IS NOT NULL FROM account WHERE id=?::uuid").use { ps -> ps.setString(1, session.accountId); ps.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) } } }
            assertTrue(deleted)
        }
    }

    @Test
    fun avatarOwnershipEquipSurvivesServerRestartAndRelogin() {
        val cfg = config() ?: return
        val suffix = UUID.randomUUID().toString().take(8)
        val login = "avatar-$suffix@example.test"
        var accountId = ""
        Database(cfg).use { db ->
            val auth = AuthRepository(db)
            val session = auth.register(RegisterRequest(login, PASSWORD, "Avatar User"))
            accountId = session.accountId
            val completion = Part2CompletionRepository(db)
            val game = Part2GameRepository(db, MemoryRepository(db))
            completion.adjustXp(accountId, 8_000, "avatar-xp-$suffix", "Manager acceptance avatar level fixture")
            completion.adjustCoins(accountId, 350, "avatar-coins-$suffix", "Manager acceptance avatar coin fixture")
            game.purchase(accountId, StorePurchaseRequest("avatar-pro-focus", "avatar-buy-$suffix"))
            val revision = game.profile(accountId).equippedAvatar.revision
            val equipped = game.equipAvatar(accountId, EquipAvatarRequest("avatar-pro-focus", revision))
            assertEquals("avatar-pro-focus", equipped.avatarId)
        }
        Database(cfg).use { restartedDb ->
            val relogin = AuthRepository(restartedDb).login(LoginRequest(login, PASSWORD, "post-restart-device"))
            assertEquals(accountId, relogin.accountId)
            val restartedGame = Part2GameRepository(restartedDb, MemoryRepository(restartedDb))
            val profile = restartedGame.profile(relogin.accountId)
            assertEquals("avatar-pro-focus", profile.equippedAvatar.avatarId)
            assertTrue(restartedGame.avatars(relogin.accountId).single { it.avatarId == "avatar-pro-focus" }.owned)
            assertTrue(restartedGame.avatars(relogin.accountId).single { it.avatarId == "avatar-pro-focus" }.equipped)
        }
    }

    private companion object {
        const val PASSWORD = "testing-password-12345"
    }
}
