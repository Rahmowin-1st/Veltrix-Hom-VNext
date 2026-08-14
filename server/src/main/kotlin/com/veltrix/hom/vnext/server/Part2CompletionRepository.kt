package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.core.LevelCurveV1
import java.sql.Connection
import java.time.OffsetDateTime

enum class LedgerAdjustmentType { ADJUSTMENT, REVERSAL }

data class XpReconciliationResponse(val projectedXp:Long,val ledgerXp:Long,val matches:Boolean,val level:Int,val revision:Long)
data class XpAdjustmentResult(val lifetimeXp:Long,val level:Int,val revision:Long,val replay:Boolean)
data class CoinAdjustmentResult(val balance:Long,val revision:Long,val replay:Boolean)
data class StoreRefundResult(val purchaseId:String,val refundId:String,val amount:Long,val balance:Long,val inventoryRemoved:Boolean,val replay:Boolean)

/**
 * Operational completion service for ledger reconciliation, controlled corrections and Store refunds.
 * It is intentionally server-only: callers never provide authoritative reward/price/balance values.
 */
class Part2CompletionRepository(private val db: Database) {
    fun reconcileXp(accountId:String):XpReconciliationResponse = db.tx { c ->
        ensure(c,accountId)
        val projected=c.prepareStatement("SELECT lifetime_xp,level,revision FROM progression_profile WHERE account_id=?::uuid").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->rs.next();Triple(rs.getLong(1),rs.getInt(2),rs.getLong(3))}}
        val ledger=c.prepareStatement("SELECT COALESCE(sum(amount),0) FROM xp_ledger WHERE account_id=?::uuid").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
        XpReconciliationResponse(projected.first,ledger,projected.first==ledger,projected.second,projected.third)
    }

    fun adjustXp(accountId:String,amount:Long,idempotencyKey:String,reason:String,type:LedgerAdjustmentType=LedgerAdjustmentType.ADJUSTMENT):XpAdjustmentResult = db.tx { c ->
        require(amount != 0L) { "amount must be non-zero" }
        require(idempotencyKey.length in 8..160) { "invalid idempotency key" }
        require(reason.length in 3..240) { "reason required" }
        if(type==LedgerAdjustmentType.REVERSAL) require(amount < 0) { "reversal must be negative" }
        ensure(c,accountId)
        val prior=c.prepareStatement("SELECT amount,entry_type,reason FROM xp_ledger WHERE account_id=?::uuid AND idempotency_key=?").use{ps->ps.setString(1,accountId);ps.setString(2,idempotencyKey);ps.executeQuery().use{rs->if(rs.next())Triple(rs.getLong(1),rs.getString(2),rs.getString(3)) else null}}
        if(prior!=null){
            if(prior.first!=amount || prior.second!=type.name || prior.third!=reason) throw conflict("IDEMPOTENCY_KEY_REUSED","Idempotency key was already used for a different XP correction")
            val p=lockedProgression(c,accountId)
            return@tx XpAdjustmentResult(p.first,p.second,p.third,true)
        }
        val before=lockedProgression(c,accountId)
        val total=before.first+amount
        if(total<0) throw conflict("XP_UNDERFLOW","XP correction cannot make lifetime XP negative")
        val level=LevelCurveV1.levelForXp(total)
        c.prepareStatement("INSERT INTO xp_ledger(account_id,amount,entry_type,reward_source,idempotency_key,policy_version,curve_version,reason) VALUES (?::uuid,?,?,'OPERATIONS',?,'reward-v1','level-curve-v1',?)").use{ps->ps.setString(1,accountId);ps.setLong(2,amount);ps.setString(3,type.name);ps.setString(4,idempotencyKey);ps.setString(5,reason);ps.executeUpdate()}
        val revision=c.prepareStatement("UPDATE progression_profile SET lifetime_xp=?,level=?,revision=revision+1,updated_at=now() WHERE account_id=?::uuid RETURNING revision").use{ps->ps.setLong(1,total);ps.setInt(2,level);ps.setString(3,accountId);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
        emit(c,accountId,if(type==LedgerAdjustmentType.REVERSAL)"XP_REVERSED" else "XP_ADJUSTED","xp-correction:$idempotencyKey",idempotencyKey,revision,"{\"amount\":$amount,\"lifetimeXp\":$total,\"level\":$level}")
        XpAdjustmentResult(total,level,revision,false)
    }

    fun adjustCoins(accountId:String,amount:Long,idempotencyKey:String,reason:String,type:LedgerAdjustmentType=LedgerAdjustmentType.ADJUSTMENT):CoinAdjustmentResult = db.tx { c ->
        require(amount != 0L) { "amount must be non-zero" }
        require(idempotencyKey.length in 8..160) { "invalid idempotency key" }
        require(reason.length in 3..240) { "reason required" }
        if(type==LedgerAdjustmentType.REVERSAL) require(amount < 0) { "reversal must be negative" }
        ensure(c,accountId)
        val prior=c.prepareStatement("SELECT amount,entry_type,reason FROM coin_ledger WHERE account_id=?::uuid AND idempotency_key=?").use{ps->ps.setString(1,accountId);ps.setString(2,idempotencyKey);ps.executeQuery().use{rs->if(rs.next())Triple(rs.getLong(1),rs.getString(2),rs.getString(3)) else null}}
        if(prior!=null){
            if(prior.first!=amount || prior.second!=type.name || prior.third!=reason) throw conflict("IDEMPOTENCY_KEY_REUSED","Idempotency key was already used for a different Coin correction")
            val p=lockedCoins(c,accountId)
            return@tx CoinAdjustmentResult(p.first,p.second,true)
        }
        val before=lockedCoins(c,accountId)
        val balance=before.first+amount
        if(balance<0) throw conflict("INSUFFICIENT_COINS","Coin correction cannot make balance negative")
        c.prepareStatement("INSERT INTO coin_ledger(account_id,amount,entry_type,reward_source,idempotency_key,policy_version,reason) VALUES (?::uuid,?,?,'OPERATIONS',?,'reward-v1',?)").use{ps->ps.setString(1,accountId);ps.setLong(2,amount);ps.setString(3,type.name);ps.setString(4,idempotencyKey);ps.setString(5,reason);ps.executeUpdate()}
        val revision=c.prepareStatement("UPDATE coin_account_projection SET balance=?,revision=revision+1,updated_at=now() WHERE account_id=?::uuid RETURNING revision").use{ps->ps.setLong(1,balance);ps.setString(2,accountId);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
        emit(c,accountId,if(type==LedgerAdjustmentType.REVERSAL)"COINS_REVERSED" else "COINS_ADJUSTED","coin-correction:$idempotencyKey",idempotencyKey,revision,"{\"amount\":$amount,\"balance\":$balance}")
        CoinAdjustmentResult(balance,revision,false)
    }

    fun refundPurchase(accountId:String,purchaseId:String,idempotencyKey:String):StoreRefundResult = db.tx { c ->
        require(idempotencyKey.length in 8..160) { "invalid idempotency key" }
        ensure(c,accountId)
        val byKey=refundByKey(c,accountId,idempotencyKey)
        if(byKey!=null){
            if(byKey.purchaseId!=purchaseId) throw conflict("IDEMPOTENCY_KEY_REUSED","Refund idempotency key belongs to another purchase")
            return@tx byKey.copy(balance=lockedCoins(c,accountId).first,replay=true)
        }
        val purchase=c.prepareStatement("SELECT item_id,authoritative_price,status FROM store_purchase WHERE id=?::uuid AND account_id=?::uuid FOR UPDATE").use{ps->ps.setString(1,purchaseId);ps.setString(2,accountId);ps.executeQuery().use{rs->if(rs.next())Triple(rs.getString(1),rs.getLong(2),rs.getString(3)) else null}}
            ?: throw DomainException(DomainError("PURCHASE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Purchase not found"))
        if(purchase.third=="REFUNDED"){
            val existing=refundByPurchase(c,accountId,purchaseId) ?: throw conflict("REFUND_STATE_INVALID","Purchase is refunded without refund provenance")
            return@tx existing.copy(balance=lockedCoins(c,accountId).first,replay=true)
        }
        if(purchase.third!="COMMITTED") throw conflict("PURCHASE_NOT_REFUNDABLE","Purchase is not in a refundable state")
        val coins=lockedCoins(c,accountId)
        val refundId=java.util.UUID.randomUUID().toString()
        val ledgerKey="refund:$idempotencyKey"
        val ledgerId=c.prepareStatement("INSERT INTO coin_ledger(account_id,amount,entry_type,reward_source,purchase_id,idempotency_key,policy_version,reason) VALUES (?::uuid,?,'REFUND','STORE',?::uuid,?,'reward-v1','Store purchase refund') RETURNING id").use{ps->ps.setString(1,accountId);ps.setLong(2,purchase.second);ps.setString(3,purchaseId);ps.setString(4,ledgerKey);ps.executeQuery().use{rs->rs.next();rs.getString(1)}}
        val balance=coins.first+purchase.second
        val revision=c.prepareStatement("UPDATE coin_account_projection SET balance=?,total_spent=GREATEST(0,total_spent-?),revision=revision+1,updated_at=now() WHERE account_id=?::uuid RETURNING revision").use{ps->ps.setLong(1,balance);ps.setLong(2,purchase.second);ps.setString(3,accountId);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
        c.prepareStatement("UPDATE gaming_statistics SET coins_spent=GREATEST(0,coins_spent-?),revision=revision+1,updated_at=now() WHERE account_id=?::uuid").use{ps->ps.setLong(1,purchase.second);ps.setString(2,accountId);ps.executeUpdate()}
        val equipped=c.prepareStatement("SELECT avatar_id FROM equipped_avatar WHERE account_id=?::uuid").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->if(rs.next())rs.getString(1) else null}}
        if(equipped==purchase.first){
            c.prepareStatement("UPDATE equipped_avatar SET avatar_id='avatar-noob-default',revision=revision+1,updated_at=now() WHERE account_id=?::uuid").use{ps->ps.setString(1,accountId);ps.executeUpdate()}
        }
        val removed=c.prepareStatement("DELETE FROM inventory_ownership WHERE account_id=?::uuid AND item_id=? AND ownership_source='STORE_PURCHASE'").use{ps->ps.setString(1,accountId);ps.setString(2,purchase.first);ps.executeUpdate()>0}
        c.prepareStatement("UPDATE store_purchase SET status='REFUNDED',updated_at=now() WHERE id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,purchaseId);ps.setString(2,accountId);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO store_refund(id,account_id,purchase_id,amount,idempotency_key,coin_ledger_id,inventory_removed) VALUES (?::uuid,?::uuid,?::uuid,?,?,?::uuid,?)").use{ps->ps.setString(1,refundId);ps.setString(2,accountId);ps.setString(3,purchaseId);ps.setLong(4,purchase.second);ps.setString(5,idempotencyKey);ps.setString(6,ledgerId);ps.setBoolean(7,removed);ps.executeUpdate()}
        emit(c,accountId,"STORE_PURCHASE_REFUNDED","store-refund:$purchaseId",idempotencyKey,revision,"{\"purchaseId\":\"${json(purchaseId)}\",\"itemId\":\"${json(purchase.first)}\",\"amount\":${purchase.second},\"balance\":$balance}")
        StoreRefundResult(purchaseId,refundId,purchase.second,balance,removed,false)
    }

    private data class RefundRow(val purchaseId:String,val refundId:String,val amount:Long,val inventoryRemoved:Boolean)
    private fun refundByKey(c:Connection,a:String,key:String):StoreRefundResult? = c.prepareStatement("SELECT purchase_id,id,amount,inventory_removed FROM store_refund WHERE account_id=?::uuid AND idempotency_key=?").use{ps->ps.setString(1,a);ps.setString(2,key);ps.executeQuery().use{rs->if(rs.next())StoreRefundResult(rs.getString(1),rs.getString(2),rs.getLong(3),0,rs.getBoolean(4),true) else null}}
    private fun refundByPurchase(c:Connection,a:String,p:String):StoreRefundResult? = c.prepareStatement("SELECT purchase_id,id,amount,inventory_removed FROM store_refund WHERE account_id=?::uuid AND purchase_id=?::uuid").use{ps->ps.setString(1,a);ps.setString(2,p);ps.executeQuery().use{rs->if(rs.next())StoreRefundResult(rs.getString(1),rs.getString(2),rs.getLong(3),0,rs.getBoolean(4),true) else null}}

    private fun ensure(c:Connection,a:String){
        c.prepareStatement("INSERT INTO progression_profile(account_id,level_curve_version,reward_policy_version) VALUES (?::uuid,'level-curve-v1','reward-v1') ON CONFLICT(account_id) DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO coin_account_projection(account_id) VALUES (?::uuid) ON CONFLICT(account_id) DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO gaming_statistics(account_id) VALUES (?::uuid) ON CONFLICT(account_id) DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO inventory_ownership(account_id,item_id,ownership_source) VALUES (?::uuid,'avatar-noob-default','DEFAULT') ON CONFLICT(account_id,item_id) DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO equipped_avatar(account_id,avatar_id) VALUES (?::uuid,'avatar-noob-default') ON CONFLICT(account_id) DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}
    }
    private fun lockedProgression(c:Connection,a:String):Triple<Long,Int,Long> = c.prepareStatement("SELECT lifetime_xp,level,revision FROM progression_profile WHERE account_id=?::uuid FOR UPDATE").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();Triple(rs.getLong(1),rs.getInt(2),rs.getLong(3))}}
    private fun lockedCoins(c:Connection,a:String):Pair<Long,Long> = c.prepareStatement("SELECT balance,revision FROM coin_account_projection WHERE account_id=?::uuid FOR UPDATE").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();rs.getLong(1) to rs.getLong(2)}}
    private fun emit(c:Connection,a:String,type:String,idempotency:String,causation:String,revision:Long,payload:String){
        c.prepareStatement("INSERT INTO game_state_event(account_id,event_type,causation_id,correlation_id,resulting_revision,payload,idempotency_key) VALUES (?::uuid,?,?,?,?,?::jsonb,?) ON CONFLICT(account_id,idempotency_key) DO NOTHING").use{ps->ps.setString(1,a);ps.setString(2,type);ps.setString(3,causation);ps.setString(4,causation);ps.setLong(5,revision);ps.setString(6,payload);ps.setString(7,idempotency);ps.executeUpdate()}
    }
    private fun conflict(code:String,message:String)=DomainException(DomainError(code,ErrorCategory.CONFLICT,message))
    private fun json(value:String)=value.replace("\\","\\\\").replace("\"","\\\"")
}
