package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.LevelCurveV1
import kotlin.test.*
import java.util.UUID

class Part2CompletionIntegrationTest {
    private fun config(): ServerConfig? {
        val url=System.getenv("VELTRIX_TEST_DATABASE_URL") ?: return null
        return ServerConfig("test",url,System.getenv("VELTRIX_TEST_DATABASE_USER") ?: "postgres",System.getenv("VELTRIX_TEST_DATABASE_PASSWORD") ?: "postgres",8080,"disabled",null)
    }

    @Test fun ledgersStoreRefundSeasonAndNotificationsAreDurableAndIdempotent() {
        val cfg=config() ?: return
        Database(cfg).use { db ->
            val auth=AuthRepository(db)
            val memory=MemoryRepository(db)
            val game=Part2GameRepository(db,memory)
            val completion=Part2CompletionRepository(db)
            val suffix=UUID.randomUUID().toString().take(8)
            val account=auth.register(RegisterRequest("part2-completion-$suffix@example.test","testing-password-12345","Part2 Completion"))
            game.profile(account.accountId)

            val xpKey="ops-xp-$suffix-12345678"
            val target=LevelCurveV1.thresholdForLevel(5)
            val xp=completion.adjustXp(account.accountId,target,xpKey,"integration fixture progression")
            assertEquals(5,xp.level)
            assertFalse(xp.replay)
            assertTrue(completion.adjustXp(account.accountId,target,xpKey,"integration fixture progression").replay)
            assertTrue(completion.reconcileXp(account.accountId).matches)

            val coinKey="ops-coin-$suffix-12345678"
            val coins=completion.adjustCoins(account.accountId,1_000,coinKey,"integration fixture coins")
            assertEquals(1_000,coins.balance)
            assertTrue(completion.adjustCoins(account.accountId,1_000,coinKey,"integration fixture coins").replay)
            assertTrue(game.reconcileCoins(account.accountId).matches)

            val purchaseKey="purchase-$suffix-12345678"
            val purchase=game.purchase(account.accountId,StorePurchaseRequest("avatar-pro-focus",purchaseKey))
            assertEquals(350,purchase.authoritativePrice)
            assertEquals(650,purchase.coinBalance)
            val replay=game.purchase(account.accountId,StorePurchaseRequest("avatar-pro-focus",purchaseKey))
            assertTrue(replay.idempotentReplay)
            assertEquals(purchase.purchaseId,replay.purchaseId)
            assertEquals(650,replay.coinBalance)

            val itemNoticeCount=db.tx { c -> c.prepareStatement("SELECT count(*) FROM notification_intent WHERE account_id=?::uuid AND category='SYSTEM_NOTICE' AND payload->>'eventType'='ITEM_ACQUIRED'").use{ps->ps.setString(1,account.accountId);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}} }
            assertEquals(1,itemNoticeCount)

            val refundKey="refund-$suffix-12345678"
            val refund=completion.refundPurchase(account.accountId,purchase.purchaseId,refundKey)
            assertEquals(350,refund.amount)
            assertEquals(1_000,refund.balance)
            assertTrue(refund.inventoryRemoved)
            assertFalse(refund.replay)
            val refundReplay=completion.refundPurchase(account.accountId,purchase.purchaseId,refundKey)
            assertTrue(refundReplay.replay)
            assertEquals(1_000,refundReplay.balance)
            assertTrue(game.reconcileCoins(account.accountId).matches)
            assertFalse(game.inventory(account.accountId,200,0).any { it.itemId=="avatar-pro-focus" })

            assertFails { completion.adjustCoins(account.accountId,-2_000,"ops-underflow-$suffix","must not underflow") }
            assertTrue(game.reconcileCoins(account.accountId).matches)

            val projects=ProjectRepository(db)
            projects.create(account.accountId,CreateProjectRequest("Season project $suffix","Meaningful seasonal activity"))
            assertTrue(game.processPending(50)>=1)
            val season=game.currentSeason(account.accountId).second
            assertNotNull(season)
            assertTrue(season.xpEarned>0)
            assertTrue(season.coinsEarned>0)
            assertEquals(1,game.gamingStats(account.accountId).seasonsParticipated)
            val seasonAchievement=game.achievements(account.accountId).single { it.achievementId=="season-first" }
            assertEquals("CLAIMED",seasonAchievement.state)
            assertTrue(completion.reconcileXp(account.accountId).matches)
            assertTrue(game.reconcileCoins(account.accountId).matches)
        }
    }
}
