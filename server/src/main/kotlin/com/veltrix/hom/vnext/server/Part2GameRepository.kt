package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.*
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

private data class StoredActivity(
    val eventId:String,val accountId:String,val type:String,val occurredAt:Instant,val projectId:String?,val objectId:String?,val idempotencyKey:String,val meaningful:Boolean,val evidence:String,val revision:Long
)

class Part2GameRepository(private val db:Database, private val memory:MemoryRepository) {
    fun profile(accountId:String):GameProfileSnapshotResponse {
        val maturity=memory.maturity(accountId).state
        return db.tx { c -> ensureAccount(c,accountId); profileInTx(c,accountId,maturity) }
    }

    fun xpHistory(accountId:String,limit:Int,offset:Int)=db.tx { c ->
        ensureAccount(c,accountId)
        c.prepareStatement("SELECT id,amount,entry_type,reward_source,source_event_id,policy_version,reason,created_at FROM xp_ledger WHERE account_id=?::uuid ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?").use{ps->ps.setString(1,accountId);ps.setInt(2,limit.coerceIn(1,200));ps.setInt(3,offset.coerceAtLeast(0));ps.executeQuery().use{rs->buildList{while(rs.next())add(ledger(rs))}}}
    }
    fun coinHistory(accountId:String,limit:Int,offset:Int)=db.tx { c ->
        ensureAccount(c,accountId)
        c.prepareStatement("SELECT id,amount,entry_type,reward_source,source_event_id,policy_version,reason,created_at FROM coin_ledger WHERE account_id=?::uuid ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?").use{ps->ps.setString(1,accountId);ps.setInt(2,limit.coerceIn(1,200));ps.setInt(3,offset.coerceAtLeast(0));ps.executeQuery().use{rs->buildList{while(rs.next())add(ledger(rs))}}}
    }

    fun achievements(accountId:String):List<AchievementResponse> = db.tx { c ->
        ensureAccount(c,accountId); ensureAchievementRows(c,accountId)
        c.prepareStatement("""SELECT d.achievement_id,d.version,d.category,d.hidden,p.progress,p.state,p.unlocked_at,p.revision FROM achievement_definition d JOIN achievement_progress p ON p.achievement_id=d.achievement_id AND p.definition_version=d.version WHERE p.account_id=?::uuid AND d.active=true ORDER BY d.category,d.achievement_id""").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->buildList{while(rs.next())add(AchievementResponse(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getBoolean(4),rs.getLong(5),rs.getString(6),rs.getObject(7,OffsetDateTime::class.java)?.toInstant()?.toString(),rs.getLong(8)))}}}
    }

    fun inventory(accountId:String,limit:Int,offset:Int):List<InventoryItemResponse> = db.tx { c ->
        ensureAccount(c,accountId)
        c.prepareStatement("""SELECT o.item_id,i.item_type,i.catalog_version,o.ownership_source,o.acquired_at,o.season_scope,o.quantity,o.metadata::text,o.revision FROM inventory_ownership o JOIN inventory_catalog i ON i.item_id=o.item_id WHERE o.account_id=?::uuid ORDER BY o.acquired_at DESC,o.item_id LIMIT ? OFFSET ?""").use{ps->ps.setString(1,accountId);ps.setInt(2,limit.coerceIn(1,200));ps.setInt(3,offset.coerceAtLeast(0));ps.executeQuery().use{rs->buildList{while(rs.next())add(InventoryItemResponse(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getObject(5,OffsetDateTime::class.java).toInstant().toString(),rs.getString(6),rs.getLong(7),rs.getString(8),rs.getLong(9)))}}}
    }

    fun avatars(accountId:String):List<AvatarCatalogResponse> = db.tx { c ->
        ensureAccount(c,accountId)
        c.prepareStatement("""SELECT a.avatar_id,a.asset_key,a.tier,a.store_price,a.catalog_version,a.unlock_rule::text,(o.item_id IS NOT NULL) owned,(e.avatar_id=a.avatar_id) equipped FROM avatar_catalog a LEFT JOIN inventory_ownership o ON o.account_id=?::uuid AND o.item_id=a.avatar_id LEFT JOIN equipped_avatar e ON e.account_id=?::uuid WHERE a.active=true ORDER BY CASE a.tier WHEN 'NOOB' THEN 1 WHEN 'PRO' THEN 2 WHEN 'ELITE' THEN 3 WHEN 'SUPER' THEN 4 WHEN 'ULTRA' THEN 5 WHEN 'MAX' THEN 6 WHEN 'HYPERPRO' THEN 7 ELSE 8 END,a.avatar_id""").use{ps->ps.setString(1,accountId);ps.setString(2,accountId);ps.executeQuery().use{rs->buildList{while(rs.next()){val owned=rs.getBoolean(7);add(AvatarCatalogResponse(rs.getString(1),rs.getString(2),rs.getString(3),owned,rs.getBoolean(8),if(owned)"OWNED" else "LOCKED",rs.getObject(4)?.let{(it as Number).toLong()},rs.getString(5),rs.getString(6)))}}}}
    }

    fun equipAvatar(accountId:String,req:EquipAvatarRequest):AvatarStateResponse = db.tx { c ->
        ensureAccount(c,accountId)
        val row=c.prepareStatement("SELECT e.avatar_id,e.revision FROM equipped_avatar e WHERE e.account_id=?::uuid FOR UPDATE").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->rs.next();rs.getString(1) to rs.getLong(2)}}
        if(row.second!=req.expectedRevision) throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Avatar equip revision conflict"))
        val avatar=c.prepareStatement("""SELECT a.asset_key,a.tier FROM avatar_catalog a JOIN inventory_ownership o ON o.item_id=a.avatar_id AND o.account_id=?::uuid WHERE a.avatar_id=? AND a.active=true""").use{ps->ps.setString(1,accountId);ps.setString(2,req.avatarId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("AVATAR_NOT_OWNED",ErrorCategory.PERMISSION,"Avatar is not owned"));rs.getString(1) to rs.getString(2)}}
        val revision=c.prepareStatement("UPDATE equipped_avatar SET avatar_id=?,revision=revision+1,updated_at=now() WHERE account_id=?::uuid AND revision=? RETURNING revision").use{ps->ps.setString(1,req.avatarId);ps.setString(2,accountId);ps.setLong(3,req.expectedRevision);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Avatar equip conflict"));rs.getLong(1)}}
        emit(c,accountId,"AVATAR_EQUIPPED","avatar:${req.avatarId}:$revision",null,null,revision,"{\"avatarId\":\"${json(req.avatarId)}\"}")
        AvatarStateResponse(req.avatarId,avatar.first,avatar.second,"OWNED","OWNED",equipped=true,revision=revision)
    }

    fun store(accountId:String):StoreCatalogResponse = db.tx { c ->
        ensureAccount(c,accountId)
        val balance=coinBalance(c,accountId)
        val catalog=c.prepareStatement("SELECT catalog_version FROM store_catalog WHERE active=true LIMIT 1").use{it.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("STORE_UNAVAILABLE",ErrorCategory.TEMPORARY_UNAVAILABLE,"No active Store catalog",true));rs.getString(1)}}
        val level=effectiveLevel(c,accountId)
        val items=c.prepareStatement("""SELECT s.item_id,i.item_type,s.price_coins,s.requirements::text,i.metadata::text,(o.item_id IS NOT NULL),COALESCE((s.requirements->>'minLevel')::int,1) FROM store_item s JOIN inventory_catalog i ON i.item_id=s.item_id LEFT JOIN inventory_ownership o ON o.account_id=?::uuid AND o.item_id=s.item_id WHERE s.catalog_version=? AND s.active=true AND i.active=true ORDER BY s.price_coins,s.item_id""").use{ps->ps.setString(1,accountId);ps.setString(2,catalog);ps.executeQuery().use{rs->buildList{while(rs.next()){val owned=rs.getBoolean(6);val min=rs.getInt(7);add(StoreItemResponse(rs.getString(1),rs.getString(2),catalog,rs.getLong(3),owned,!owned&&level>=min,rs.getString(4),rs.getString(5)))}}}}
        StoreCatalogResponse(catalog,balance,items)
    }

    fun purchase(accountId:String,req:StorePurchaseRequest):StorePurchaseResponse = db.tx { c ->
        require(req.idempotencyKey.length in 8..180) { "Invalid idempotency key" }
        ensureAccount(c,accountId)
        c.prepareStatement("SELECT balance FROM coin_account_projection WHERE account_id=?::uuid FOR UPDATE").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->rs.next()}}
        val replay=existingPurchase(c,accountId,req.idempotencyKey)
        if(replay!=null){ if(replay.itemId!=req.itemId)throw DomainException(DomainError("IDEMPOTENCY_CONFLICT",ErrorCategory.CONFLICT,"Purchase idempotency key reused for another item")); return@tx replay.copy(idempotentReplay=true,coinBalance=coinBalance(c,accountId)) }
        val item=c.prepareStatement("""SELECT s.catalog_version,s.price_coins,COALESCE((s.requirements->>'minLevel')::int,1) min_level FROM store_item s JOIN store_catalog c ON c.catalog_version=s.catalog_version AND c.active=true JOIN inventory_catalog i ON i.item_id=s.item_id AND i.active=true WHERE s.item_id=? AND s.active=true""").use{ps->ps.setString(1,req.itemId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("STORE_ITEM_UNAVAILABLE",ErrorCategory.NOT_FOUND,"Store item unavailable"));Triple(rs.getString(1),rs.getLong(2),rs.getInt(3))}}
        val level=effectiveLevel(c,accountId)
        if(level<item.third)throw DomainException(DomainError("STORE_REQUIREMENT",ErrorCategory.PERMISSION,"Store requirement not met"))
        if(c.prepareStatement("SELECT 1 FROM inventory_ownership WHERE account_id=?::uuid AND item_id=?").use{ps->ps.setString(1,accountId);ps.setString(2,req.itemId);ps.executeQuery().use{it.next()}}) throw DomainException(DomainError("ALREADY_OWNED",ErrorCategory.CONFLICT,"Unique item already owned"))
        val balance=coinBalance(c,accountId);if(balance<item.second)throw DomainException(DomainError("INSUFFICIENT_COINS",ErrorCategory.CONFLICT,"Insufficient Coin balance"))
        val purchaseId=c.prepareStatement("INSERT INTO store_purchase(account_id,item_id,catalog_version,authoritative_price,idempotency_key,status) VALUES (?::uuid,?,?,?,?,'COMMITTED') RETURNING id").use{ps->ps.setString(1,accountId);ps.setString(2,req.itemId);ps.setString(3,item.first);ps.setLong(4,item.second);ps.setString(5,req.idempotencyKey);ps.executeQuery().use{rs->rs.next();rs.getObject(1,UUID::class.java).toString()}}
        val ledgerId=c.prepareStatement("INSERT INTO coin_ledger(account_id,amount,entry_type,reward_source,purchase_id,idempotency_key,policy_version,reason) VALUES (?::uuid,?,'SPEND','STORE_PURCHASE',?::uuid,?,'reward-v1','Store purchase') RETURNING id").use{ps->ps.setString(1,accountId);ps.setLong(2,-item.second);ps.setString(3,purchaseId);ps.setString(4,"purchase:$purchaseId");ps.executeQuery().use{rs->rs.next();rs.getObject(1,UUID::class.java).toString()}}
        val newBalance=c.prepareStatement("UPDATE coin_account_projection SET balance=balance-?,total_spent=total_spent+?,revision=revision+1,updated_at=now() WHERE account_id=?::uuid AND balance>=? RETURNING balance,revision").use{ps->ps.setLong(1,item.second);ps.setLong(2,item.second);ps.setString(3,accountId);ps.setLong(4,item.second);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("INSUFFICIENT_COINS",ErrorCategory.CONFLICT,"Concurrent spend exhausted balance"));rs.getLong(1) to rs.getLong(2)}}
        val invRev=c.prepareStatement("INSERT INTO inventory_ownership(account_id,item_id,ownership_source) VALUES (?::uuid,?,'STORE_PURCHASE') RETURNING revision").use{ps->ps.setString(1,accountId);ps.setString(2,req.itemId);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
        c.prepareStatement("UPDATE store_purchase SET coin_ledger_id=?::uuid,entitlement_revision=?,updated_at=now() WHERE id=?::uuid").use{ps->ps.setString(1,ledgerId);ps.setLong(2,invRev);ps.setString(3,purchaseId);ps.executeUpdate()}
        c.prepareStatement("UPDATE gaming_statistics SET coins_spent=coins_spent+?,revision=revision+1,updated_at=now() WHERE account_id=?::uuid").use{ps->ps.setLong(1,item.second);ps.setString(2,accountId);ps.executeUpdate()}
        emit(c,accountId,"COINS_SPENT","purchase:$purchaseId",purchaseId,null,newBalance.second,"{\"amount\":${item.second},\"balance\":${newBalance.first}}")
        emit(c,accountId,"ITEM_ACQUIRED","purchase-item:$purchaseId",purchaseId,null,invRev,"{\"itemId\":\"${json(req.itemId)}\"}")
        StorePurchaseResponse(purchaseId,req.itemId,item.first,item.second,"COINS","COMMITTED",newBalance.first,invRev)
    }

    fun reconcileCoins(accountId:String):CoinReconciliationResponse=db.tx{c->ensureAccount(c,accountId);val projected=coinBalance(c,accountId);val ledger=c.prepareStatement("SELECT COALESCE(sum(amount),0) FROM coin_ledger WHERE account_id=?::uuid").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}};CoinReconciliationResponse(projected,ledger,projected==ledger)}

    fun map(accountId:String,createIfEligible:Boolean):PersonalMapResponse {
        val maturity=memory.maturity(accountId).state
        return db.tx{c->ensureAccount(c,accountId);mapInTx(c,accountId,maturity,createIfEligible)}
    }

    fun startUnit(accountId:String,unitId:String,req:StartMapUnitRequest):MapUnitResponse=db.tx{c->
        ensureAccount(c,accountId)
        val row=c.prepareStatement("""SELECT p.personal_map_id,p.state,p.revision,u.ordinal,u.semantic_key,u.title_key,p.progress,p.required_progress,u.reward_definition::text FROM map_unit_progress p JOIN personal_map m ON m.id=p.personal_map_id AND m.account_id=p.account_id JOIN map_unit u ON u.unit_id=p.unit_id AND u.map_definition_id=m.map_definition_id AND u.map_version=m.map_version WHERE p.account_id=?::uuid AND p.unit_id=? FOR UPDATE""").use{ps->ps.setString(1,accountId);ps.setString(2,unitId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("MAP_UNIT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Map Unit not found"));arrayOf(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getInt(4),rs.getString(5),rs.getString(6),rs.getLong(7),rs.getLong(8),rs.getString(9))}}
        if(row[2] as Long != req.expectedRevision)throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Map Unit revision conflict"))
        if(row[1] != "AVAILABLE")throw DomainException(DomainError("MAP_UNIT_LOCKED",ErrorCategory.PERMISSION,"Map Unit is not available"))
        val revision=c.prepareStatement("UPDATE map_unit_progress SET state='IN_PROGRESS',started_at=COALESCE(started_at,now()),revision=revision+1,updated_at=now() WHERE account_id=?::uuid AND personal_map_id=?::uuid AND unit_id=? AND revision=? RETURNING revision").use{ps->ps.setString(1,accountId);ps.setString(2,row[0] as String);ps.setString(3,unitId);ps.setLong(4,req.expectedRevision);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
        emit(c,accountId,"UNIT_STARTED","unit-start:$unitId:$revision",unitId,null,revision,"{\"unitId\":\"${json(unitId)}\"}")
        MapUnitResponse(unitId,row[3] as Int,row[4] as String,row[5] as String,"IN_PROGRESS",row[6] as Long,row[7] as Long,row[8] as String,revision)
    }

    fun currentSeason(accountId:String):Pair<SeasonResponse?,SeasonProgressResponse?> = db.tx{c->ensureAccount(c,accountId);seasonInTx(c,accountId)}
    fun gamingStats(accountId:String):GamingStatisticsResponse=db.tx{c->ensureAccount(c,accountId);stats(c,accountId)}
    fun stateEvents(accountId:String,limit:Int,offset:Int):List<GameStateEventResponse> = db.tx{c->ensureAccount(c,accountId);c.prepareStatement("SELECT event_id,event_type,occurred_at,causation_id,correlation_id,source_event_id,resulting_revision,payload_schema_version,payload::text FROM game_state_event WHERE account_id=?::uuid ORDER BY occurred_at DESC,event_id DESC LIMIT ? OFFSET ?").use{ps->ps.setString(1,accountId);ps.setInt(2,limit.coerceIn(1,200));ps.setInt(3,offset.coerceAtLeast(0));ps.executeQuery().use{rs->buildList{while(rs.next())add(GameStateEventResponse(rs.uuid("event_id"),rs.getString(2),rs.getObject(3,OffsetDateTime::class.java).toInstant().toString(),rs.getString(4),rs.getString(5),rs.getObject(6,UUID::class.java)?.toString(),rs.getLong(7),rs.getInt(8),rs.getString(9)))}}}}

    fun processPending(max:Int=50):Int { var processed=0;repeat(max.coerceIn(1,200)){if(!processOne())return processed;processed++};return processed }
    private fun processOne():Boolean = db.tx { c ->
        val event=c.prepareStatement("""SELECT ae.event_id,ae.account_id,ae.event_type,ae.occurred_at,ae.project_id,ae.object_id,ae.idempotency_key,ae.meaningful,ae.evidence::text,ae.revision FROM activity_reward_queue q JOIN activity_event ae ON ae.event_id=q.activity_event_id WHERE q.status IN ('PENDING','RETRY') AND q.available_at<=now() ORDER BY q.created_at,q.activity_event_id LIMIT 1 FOR UPDATE OF q SKIP LOCKED""").use{ps->ps.executeQuery().use{rs->if(!rs.next())null else StoredActivity(rs.uuid("event_id"),rs.uuid("account_id"),rs.getString("event_type"),rs.getObject("occurred_at",OffsetDateTime::class.java).toInstant(),rs.uuidOrNull("project_id"),rs.getString("object_id"),rs.getString("idempotency_key"),rs.getBoolean("meaningful"),rs.getString("evidence"),rs.getLong("revision"))}} ?: return@tx false
        val qDone=c.prepareStatement("SELECT 1 FROM reward_decision_log WHERE account_id=?::uuid AND source_event_id=?::uuid AND policy_version='reward-v1'").use{ps->ps.setString(1,event.accountId);ps.setString(2,event.eventId);ps.executeQuery().use{it.next()}}
        if(qDone){markQueue(c,event.eventId,"DONE");return@tx true}
        ensureAccount(c,event.accountId)
        processActivity(c,event)
        true
    }

    private fun processActivity(c:Connection,e:StoredActivity){
        val zone=profileTimezone(c,e.accountId)
        val localDate=e.occurredAt.atZone(ZoneId.of(zone)).toLocalDate()
        ensureDaily(c,e.accountId,localDate,zone)
        val daily=c.prepareStatement("SELECT xp_granted,coins_granted,daily_bonus_granted FROM daily_activity_state WHERE account_id=?::uuid AND local_date=? FOR UPDATE").use{ps->ps.setString(1,e.accountId);ps.setObject(2,localDate);ps.executeQuery().use{rs->rs.next();Triple(rs.getLong(1),rs.getLong(2),rs.getBoolean(3))}}
        val sameType=c.prepareStatement("""SELECT count(*) FROM reward_decision_log r JOIN activity_event a ON a.event_id=r.source_event_id WHERE r.account_id=?::uuid AND r.eligible=true AND a.event_type=? AND (a.occurred_at AT TIME ZONE ?)::date=?""").use{ps->ps.setString(1,e.accountId);ps.setString(2,e.type);ps.setString(3,zone);ps.setObject(4,localDate);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}
        val semanticOk=semanticEvidence(c,e)
        val duplicate=semanticDuplicate(c,e)
        val core=ActivityEvent(eventId=e.eventId,accountId=e.accountId,type=e.type,timestamp=e.occurredAt,projectId=e.projectId,objectId=e.objectId,idempotencyKey=e.idempotencyKey)
        val decision=RewardPolicyV1.decide(RewardEligibilityContext(core,semanticOk,duplicate,sameType,daily.first,daily.second))
        if(!decision.eligible){insertDecision(c,e,decision);markQueue(c,e.eventId,"REJECTED");return}
        val bonusAllowed=!daily.third && dailyBonusTimeSafe(c,e.accountId,e.occurredAt)
        val xp=decision.xp+(if(bonusAllowed)RewardPolicyV1.config.dailyBonusXp else 0)
        val coins=decision.coins+(if(bonusAllowed)RewardPolicyV1.config.dailyBonusCoins else 0)
        val before=progression(c,e.accountId)
        c.prepareStatement("INSERT INTO reward_grant(account_id,source_event_id,reward_key,xp_amount,coin_amount,policy_version,decision_reason) VALUES (?::uuid,?::uuid,'ACTIVITY',?,?,'reward-v1',?) ON CONFLICT(account_id,source_event_id,reward_key) DO NOTHING").use{ps->ps.setString(1,e.accountId);ps.setString(2,e.eventId);ps.setLong(3,xp);ps.setLong(4,coins);ps.setString(5,if(bonusAllowed)"ELIGIBLE_WITH_DAILY_BONUS" else decision.code.name);ps.executeUpdate()}
        applyXpCoins(c,e.accountId,e.eventId,"ACTIVITY",xp,coins,"activity:${e.eventId}","Meaningful ${e.type}")
        c.prepareStatement("UPDATE daily_activity_state SET eligible_event_count=eligible_event_count+1,xp_granted=xp_granted+?,coins_granted=coins_granted+?,daily_bonus_granted=daily_bonus_granted OR ?,first_eligible_at=COALESCE(first_eligible_at,?),last_eligible_at=GREATEST(COALESCE(last_eligible_at,?),?),revision=revision+1 WHERE account_id=?::uuid AND local_date=?").use{ps->ps.setLong(1,xp);ps.setLong(2,coins);ps.setBoolean(3,bonusAllowed);ps.setObject(4,odt(e.occurredAt));ps.setObject(5,odt(e.occurredAt));ps.setObject(6,odt(e.occurredAt));ps.setString(7,e.accountId);ps.setObject(8,localDate);ps.executeUpdate()}
        updateConsistency(c,e.accountId,e.eventId,e.occurredAt,zone,localDate)
        c.prepareStatement("UPDATE gaming_statistics SET meaningful_activities=meaningful_activities+1,revision=revision+1,updated_at=now() WHERE account_id=?::uuid").use{ps->ps.setString(1,e.accountId);ps.executeUpdate()}
        progressAchievements(c,e)
        progressMapUnits(c,e)
        val after=progression(c,e.accountId)
        if(after.second>before.second)emit(c,e.accountId,"LEVEL_UP","level:${e.eventId}:${after.second}",e.eventId,e.eventId,after.third,"{\"from\":${before.second},\"to\":${after.second}}")
        insertDecision(c,e,decision.copy(xp=xp,coins=coins))
        markQueue(c,e.eventId,"DONE")
    }

    private fun applyXpCoins(c:Connection,a:String,sourceEvent:String?,source:String,xp:Long,coins:Long,key:String,reason:String){
        if(xp>0){
            c.prepareStatement("INSERT INTO xp_ledger(account_id,amount,entry_type,reward_source,source_event_id,idempotency_key,policy_version,curve_version,reason) VALUES (?::uuid,?,'GRANT',?,?::uuid,?,'reward-v1','level-curve-v1',?) ON CONFLICT(account_id,idempotency_key) DO NOTHING").use{ps->ps.setString(1,a);ps.setLong(2,xp);ps.setString(3,source);ps.setString(4,sourceEvent);ps.setString(5,"xp:$key");ps.setString(6,reason);val inserted=ps.executeUpdate();if(inserted==1){val current=c.prepareStatement("SELECT lifetime_xp FROM progression_profile WHERE account_id=?::uuid FOR UPDATE").use{q->q.setString(1,a);q.executeQuery().use{rs->rs.next();rs.getLong(1)}};val total=current+xp;val lvl=LevelCurveV1.levelForXp(total);val rev=c.prepareStatement("UPDATE progression_profile SET lifetime_xp=?,level=?,revision=revision+1,updated_at=now() WHERE account_id=?::uuid RETURNING revision").use{q->q.setLong(1,total);q.setInt(2,lvl);q.setString(3,a);q.executeQuery().use{rs->rs.next();rs.getLong(1)}};c.prepareStatement("UPDATE gaming_statistics SET xp_earned=xp_earned+?,revision=revision+1,updated_at=now() WHERE account_id=?::uuid").use{q->q.setLong(1,xp);q.setString(2,a);q.executeUpdate()};emit(c,a,"XP_GRANTED","xp:$key",key,sourceEvent,rev,"{\"amount\":$xp,\"lifetimeXp\":$total}")}}}
        if(coins>0){
            c.prepareStatement("INSERT INTO coin_ledger(account_id,amount,entry_type,reward_source,source_event_id,idempotency_key,policy_version,reason) VALUES (?::uuid,?,'GRANT',?,?::uuid,?,'reward-v1',?) ON CONFLICT(account_id,idempotency_key) DO NOTHING").use{ps->ps.setString(1,a);ps.setLong(2,coins);ps.setString(3,source);ps.setString(4,sourceEvent);ps.setString(5,"coin:$key");ps.setString(6,reason);val inserted=ps.executeUpdate();if(inserted==1){val row=c.prepareStatement("UPDATE coin_account_projection SET balance=balance+?,total_earned=total_earned+?,revision=revision+1,updated_at=now() WHERE account_id=?::uuid RETURNING balance,revision").use{q->q.setLong(1,coins);q.setLong(2,coins);q.setString(3,a);q.executeQuery().use{rs->rs.next();rs.getLong(1) to rs.getLong(2)}};c.prepareStatement("UPDATE gaming_statistics SET coins_earned=coins_earned+?,revision=revision+1,updated_at=now() WHERE account_id=?::uuid").use{q->q.setLong(1,coins);q.setString(2,a);q.executeUpdate()};emit(c,a,"COINS_GRANTED","coin:$key",key,sourceEvent,row.second,"{\"amount\":$coins,\"balance\":${row.first}}")}}}
    }

    private fun progressAchievements(c:Connection,e:StoredActivity){
        ensureAchievementRows(c,e.accountId)
        val ids=c.prepareStatement("""SELECT d.achievement_id,d.version,COALESCE((d.criteria->>'count')::bigint,COALESCE((d.criteria->'eventCount'->>'meaningful')::bigint,1)) target FROM achievement_definition d WHERE d.active=true AND (d.criteria->>'eventType'=? OR d.criteria->'eventCount' IS NOT NULL)""").use{ps->ps.setString(1,e.type);ps.executeQuery().use{rs->buildList{while(rs.next())add(Triple(rs.getString(1),rs.getInt(2),rs.getLong(3)))}}}
        for((id,version,target) in ids){
            val row=c.prepareStatement("UPDATE achievement_progress SET progress=progress+1,state=CASE WHEN progress+1>=? THEN 'UNLOCKED' ELSE 'IN_PROGRESS' END,unlocked_at=CASE WHEN progress+1>=? THEN COALESCE(unlocked_at,now()) ELSE unlocked_at END,revision=revision+1,updated_at=now() WHERE account_id=?::uuid AND achievement_id=? AND definition_version=? AND state NOT IN ('UNLOCKED','CLAIMED') RETURNING progress,state,revision").use{ps->ps.setLong(1,target);ps.setLong(2,target);ps.setString(3,e.accountId);ps.setString(4,id);ps.setInt(5,version);ps.executeQuery().use{rs->if(rs.next())Triple(rs.getLong(1),rs.getString(2),rs.getLong(3)) else null}} ?: continue
            emit(c,e.accountId,"ACHIEVEMENT_PROGRESS","achievement-progress:$id:${row.third}",id,e.eventId,row.third,"{\"achievementId\":\"${json(id)}\",\"progress\":${row.first}}")
            if(row.second=="UNLOCKED"){
                val reward=c.prepareStatement("SELECT COALESCE((reward_definition->>'xp')::bigint,0),COALESCE((reward_definition->>'coins')::bigint,0),reward_definition->>'itemId' FROM achievement_definition WHERE achievement_id=? AND version=?").use{ps->ps.setString(1,id);ps.setInt(2,version);ps.executeQuery().use{rs->rs.next();Triple(rs.getLong(1),rs.getLong(2),rs.getString(3))}}
                c.prepareStatement("INSERT INTO reward_grant(account_id,source_event_id,reward_key,xp_amount,coin_amount,policy_version,decision_reason) VALUES (?::uuid,?::uuid,?,?,?,'reward-v1','ACHIEVEMENT_UNLOCK') ON CONFLICT(account_id,source_event_id,reward_key) DO NOTHING").use{ps->ps.setString(1,e.accountId);ps.setString(2,e.eventId);ps.setString(3,"ACHIEVEMENT:$id");ps.setLong(4,reward.first);ps.setLong(5,reward.second);ps.executeUpdate()}
                applyXpCoins(c,e.accountId,e.eventId,"ACHIEVEMENT",reward.first,reward.second,"achievement:$id","Achievement $id")
                if(!reward.third.isNullOrBlank())grantInventory(c,e.accountId,reward.third,"ACHIEVEMENT",e.eventId,"achievement:$id")
                c.prepareStatement("UPDATE achievement_progress SET state='CLAIMED' WHERE account_id=?::uuid AND achievement_id=? AND definition_version=?").use{ps->ps.setString(1,e.accountId);ps.setString(2,id);ps.setInt(3,version);ps.executeUpdate()}
                c.prepareStatement("UPDATE gaming_statistics SET achievements_unlocked=achievements_unlocked+1,revision=revision+1,updated_at=now() WHERE account_id=?::uuid").use{ps->ps.setString(1,e.accountId);ps.executeUpdate()}
                emit(c,e.accountId,"ACHIEVEMENT_UNLOCKED","achievement-unlock:$id",id,e.eventId,row.third,"{\"achievementId\":\"${json(id)}\"}")
            }
        }
    }

    private fun progressMapUnits(c:Connection,e:StoredActivity){
        val units=c.prepareStatement("""SELECT p.personal_map_id,p.unit_id,p.progress,p.required_progress,p.revision,u.reward_definition::text,COALESCE((u.completion_criteria->>'count')::bigint,1) criterion_count FROM map_unit_progress p JOIN personal_map m ON m.id=p.personal_map_id AND m.account_id=p.account_id JOIN map_unit u ON u.unit_id=p.unit_id AND u.map_definition_id=m.map_definition_id AND u.map_version=m.map_version WHERE p.account_id=?::uuid AND m.state='ACTIVE' AND p.state IN ('AVAILABLE','IN_PROGRESS') AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(u.completion_criteria->'eventTypes') jt(value) WHERE jt.value=?) ORDER BY u.ordinal FOR UPDATE OF p""").use{ps->ps.setString(1,e.accountId);ps.setString(2,e.type);ps.executeQuery().use{rs->buildList{while(rs.next())add(arrayOf(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getString(6),rs.getLong(7)))}}}
        for(row in units){
            val mapId=row[0] as String;val unitId=row[1] as String;val progress=row[2] as Long;val required=row[6] as Long;val next=progress+1;val complete=next>=required
            val rev=c.prepareStatement("UPDATE map_unit_progress SET progress=?,required_progress=?,state=?,started_at=COALESCE(started_at,now()),completed_at=CASE WHEN ? THEN COALESCE(completed_at,now()) ELSE completed_at END,revision=revision+1,updated_at=now() WHERE account_id=?::uuid AND personal_map_id=?::uuid AND unit_id=? RETURNING revision").use{ps->ps.setLong(1,next);ps.setLong(2,required);ps.setString(3,if(complete)"COMPLETED" else "IN_PROGRESS");ps.setBoolean(4,complete);ps.setString(5,e.accountId);ps.setString(6,mapId);ps.setString(7,unitId);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
            if(!complete)continue
            val reward=c.prepareStatement("SELECT COALESCE((reward_definition->>'xp')::bigint,0),COALESCE((reward_definition->>'coins')::bigint,0),reward_definition->>'itemId' FROM map_unit u JOIN personal_map m ON m.map_definition_id=u.map_definition_id AND m.map_version=u.map_version WHERE m.id=?::uuid AND u.unit_id=?").use{ps->ps.setString(1,mapId);ps.setString(2,unitId);ps.executeQuery().use{rs->rs.next();Triple(rs.getLong(1),rs.getLong(2),rs.getString(3))}}
            c.prepareStatement("INSERT INTO reward_grant(account_id,source_event_id,reward_key,xp_amount,coin_amount,policy_version,decision_reason) VALUES (?::uuid,?::uuid,?,?,?,'reward-v1','MAP_UNIT_COMPLETION') ON CONFLICT(account_id,source_event_id,reward_key) DO NOTHING").use{ps->ps.setString(1,e.accountId);ps.setString(2,e.eventId);ps.setString(3,"MAP_UNIT:$mapId:$unitId");ps.setLong(4,reward.first);ps.setLong(5,reward.second);ps.executeUpdate()}
            applyXpCoins(c,e.accountId,e.eventId,"MAP_UNIT",reward.first,reward.second,"map-unit:$mapId:$unitId","Map Unit $unitId")
            if(!reward.third.isNullOrBlank())grantInventory(c,e.accountId,reward.third,"MAP_UNIT",e.eventId,"map-unit:$mapId:$unitId")
            c.prepareStatement("UPDATE map_unit_progress SET state='REWARD_GRANTED',revision=revision+1,updated_at=now() WHERE account_id=?::uuid AND personal_map_id=?::uuid AND unit_id=?").use{ps->ps.setString(1,e.accountId);ps.setString(2,mapId);ps.setString(3,unitId);ps.executeUpdate()}
            c.prepareStatement("UPDATE gaming_statistics SET units_completed=units_completed+1,revision=revision+1,updated_at=now() WHERE account_id=?::uuid").use{ps->ps.setString(1,e.accountId);ps.executeUpdate()}
            emit(c,e.accountId,"UNIT_COMPLETED","unit-complete:$mapId:$unitId",unitId,e.eventId,rev,"{\"unitId\":\"${json(unitId)}\"}")
            emit(c,e.accountId,"UNIT_REWARD_GRANTED","unit-reward:$mapId:$unitId",unitId,e.eventId,rev+1,"{\"xp\":${reward.first},\"coins\":${reward.second}}")
            unlockNextUnits(c,e.accountId,mapId)
        }
    }

    private fun unlockNextUnits(c:Connection,a:String,mapId:String){
        val candidates=c.prepareStatement("""SELECT p.unit_id FROM map_unit_progress p JOIN personal_map m ON m.id=p.personal_map_id JOIN map_unit u ON u.unit_id=p.unit_id AND u.map_definition_id=m.map_definition_id AND u.map_version=m.map_version WHERE p.account_id=?::uuid AND p.personal_map_id=?::uuid AND p.state IN ('HIDDEN','LOCKED') AND NOT EXISTS (SELECT 1 FROM map_unit_dependency d LEFT JOIN map_unit_progress prereq ON prereq.account_id=p.account_id AND prereq.personal_map_id=p.personal_map_id AND prereq.unit_id=d.prerequisite_unit_id WHERE d.map_definition_id=m.map_definition_id AND d.map_version=m.map_version AND d.unit_id=p.unit_id AND COALESCE(prereq.state,'HIDDEN') NOT IN ('COMPLETED','REWARD_GRANTED')) ORDER BY u.ordinal""").use{ps->ps.setString(1,a);ps.setString(2,mapId);ps.executeQuery().use{rs->buildList{while(rs.next())add(rs.getString(1))}}}
        for(id in candidates){val rev=c.prepareStatement("UPDATE map_unit_progress SET state='AVAILABLE',revision=revision+1,updated_at=now() WHERE account_id=?::uuid AND personal_map_id=?::uuid AND unit_id=? AND state IN ('HIDDEN','LOCKED') RETURNING revision").use{ps->ps.setString(1,a);ps.setString(2,mapId);ps.setString(3,id);ps.executeQuery().use{rs->if(rs.next())rs.getLong(1) else null}};if(rev!=null)emit(c,a,"UNIT_UNLOCKED","unit-unlock:$mapId:$id",id,null,rev,"{\"unitId\":\"${json(id)}\"}")}
        val unfinished=c.prepareStatement("SELECT count(*) FROM map_unit_progress WHERE account_id=?::uuid AND personal_map_id=?::uuid AND state NOT IN ('COMPLETED','REWARD_GRANTED')").use{ps->ps.setString(1,a);ps.setString(2,mapId);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}
        if(unfinished==0){val rev=c.prepareStatement("UPDATE personal_map SET state='COMPLETED',completed_at=COALESCE(completed_at,now()),revision=revision+1,updated_at=now() WHERE id=?::uuid AND account_id=?::uuid AND state='ACTIVE' RETURNING revision").use{ps->ps.setString(1,mapId);ps.setString(2,a);ps.executeQuery().use{rs->if(rs.next())rs.getLong(1)else null}};if(rev!=null){c.prepareStatement("UPDATE gaming_statistics SET maps_completed=maps_completed+1,revision=revision+1,updated_at=now() WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeUpdate()};emit(c,a,"MAP_COMPLETED","map-complete:$mapId",mapId,null,rev,"{\"mapId\":\"$mapId\"}")}}
    }

    private fun semanticEvidence(c:Connection,e:StoredActivity):Boolean {
        val id=e.objectId ?: return false
        return when(e.type){
            "PROJECT_CREATED" -> exists(c,"SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL AND length(trim(COALESCE(purpose,'')))>=8",id,e.accountId)
            "GOAL_COMPLETED" -> exists(c,"SELECT 1 FROM goal WHERE id=?::uuid AND account_id=?::uuid AND status='COMPLETED' AND deleted_at IS NULL",id,e.accountId)
            "SOURCE_ADDED" -> exists(c,"SELECT 1 FROM source s WHERE s.id=?::uuid AND s.account_id=?::uuid AND s.deleted_at IS NULL AND EXISTS(SELECT 1 FROM source_chunk sc WHERE sc.source_id=s.id AND sc.account_id=s.account_id)",id,e.accountId)
            "TEST_COMPLETED","QUIZ_COMPLETED" -> exists(c,"SELECT 1 FROM assessment_attempt WHERE id=?::uuid AND account_id=?::uuid AND state='GRADED' AND score IS NOT NULL",id,e.accountId)
            "PRACTICE_COMPLETED" -> exists(c,"SELECT 1 FROM practice_session WHERE id=?::uuid AND account_id=?::uuid AND state='COMPLETED'",id,e.accountId)
            "FLASHCARD_REVIEW_COMPLETED" -> exists(c,"SELECT 1 FROM flashcard_review WHERE card_id=?::uuid AND account_id=?::uuid",id,e.accountId)
            "MISTAKE_RESOLVED" -> exists(c,"SELECT 1 FROM mistake WHERE id=?::uuid AND account_id=?::uuid AND status='RESOLVED' AND deleted_at IS NULL",id,e.accountId)
            "NOTE_CREATED" -> exists(c,"SELECT 1 FROM note WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL AND length(trim(body))>=40",id,e.accountId)
            "MEANINGFUL_CHAT_SESSION" -> exists(c,"SELECT 1 FROM conversation_message a JOIN conversation_message u ON u.id=a.parent_message_id AND u.account_id=a.account_id WHERE a.id=?::uuid AND a.account_id=?::uuid AND a.role='ASSISTANT' AND a.state='COMPLETED' AND length(trim(a.content))>=40 AND u.role='USER' AND length(trim(u.content))>=10",id,e.accountId)
            else -> false
        }
    }
    private fun semanticDuplicate(c:Connection,e:StoredActivity):Boolean { val id=e.objectId?:return false;return c.prepareStatement("SELECT 1 FROM reward_decision_log r JOIN activity_event a ON a.event_id=r.source_event_id WHERE r.account_id=?::uuid AND r.eligible=true AND a.event_type=? AND a.object_id=? AND a.event_id<>?::uuid LIMIT 1").use{ps->ps.setString(1,e.accountId);ps.setString(2,e.type);ps.setString(3,id);ps.setString(4,e.eventId);ps.executeQuery().use{it.next()}} }

    private fun mapInTx(c:Connection,a:String,maturity:String,create:Boolean):PersonalMapResponse{
        val p=progression(c,a);val eligibility=PersonalMapEligibilityEngine.evaluate(effectiveLevel(c,a),MemoryMaturityState.valueOf(maturity))
        var map=loadMap(c,a)
        if(map==null&&create&&eligibility.eligible){
            val def=c.prepareStatement("SELECT map_definition_id,version,generation_version FROM map_definition WHERE active=true ORDER BY version DESC LIMIT 1").use{it.executeQuery().use{rs->rs.next();Triple(rs.getString(1),rs.getInt(2),rs.getString(3))}}
            val id=c.prepareStatement("INSERT INTO personal_map(account_id,map_definition_id,map_version,state,generation_provider,generation_version) VALUES (?::uuid,?,?,'ACTIVE','DETERMINISTIC',?) ON CONFLICT(account_id,map_definition_id,map_version,season_id) DO UPDATE SET updated_at=personal_map.updated_at RETURNING id").use{ps->ps.setString(1,a);ps.setString(2,def.first);ps.setInt(3,def.second);ps.setString(4,def.third);ps.executeQuery().use{rs->rs.next();rs.uuid("id")}}
            c.prepareStatement("INSERT INTO map_generation_record(account_id,personal_map_id,provider,generation_version,input_summary,output_payload,validation_status,fallback_used) VALUES (?::uuid,?::uuid,'DETERMINISTIC',?,'{}'::jsonb,'{\"template\":\"foundation-map\"}'::jsonb,'VALID',false)").use{ps->ps.setString(1,a);ps.setString(2,id);ps.setString(3,def.third);ps.executeUpdate()}
            c.prepareStatement("""INSERT INTO map_unit_progress(account_id,personal_map_id,unit_id,state,required_progress) SELECT ?::uuid,?::uuid,u.unit_id,CASE WHEN u.ordinal=1 THEN 'AVAILABLE' ELSE 'HIDDEN' END,COALESCE((u.completion_criteria->>'count')::bigint,1) FROM map_unit u WHERE u.map_definition_id=? AND u.map_version=? ON CONFLICT DO NOTHING""").use{ps->ps.setString(1,a);ps.setString(2,id);ps.setString(3,def.first);ps.setInt(4,def.second);ps.executeUpdate()}
            emit(c,a,"MAP_UNLOCKED","map-unlock:$id",id,null,1,"{\"mapId\":\"$id\"}");emit(c,a,"MAP_GENERATED","map-generated:$id",id,null,1,"{\"provider\":\"DETERMINISTIC\"}")
            map=loadMap(c,a)
        }
        val elig=MapEligibilityResponse(eligibility.eligible,eligibility.levelRequirement,eligibility.memoryRequirement,eligibility.levelSatisfied,eligibility.memorySatisfied,if(map!=null)map[3] as String else eligibility.unlockState.name)
        if(map==null)return PersonalMapResponse(null,"foundation-map",1,"LOCKED","DETERMINISTIC","map-gen-v1",elig,emptyList(),0)
        val id=map[0] as String;val units=mapUnits(c,a,id);return PersonalMapResponse(id,map[1] as String,map[2] as Int,map[3] as String,map[4] as String,map[5] as String,elig,units,map[6] as Long)
    }
    private fun loadMap(c:Connection,a:String):Array<Any>?=c.prepareStatement("SELECT id,map_definition_id,map_version,state,generation_provider,generation_version,revision FROM personal_map WHERE account_id=?::uuid ORDER BY created_at DESC LIMIT 1").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->if(!rs.next())null else arrayOf(rs.uuid("id"),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getLong(7))}}
    private fun mapUnits(c:Connection,a:String,map:String):List<MapUnitResponse> = c.prepareStatement("""SELECT p.unit_id,u.ordinal,u.semantic_key,u.title_key,p.state,p.progress,p.required_progress,u.reward_definition::text,p.revision FROM map_unit_progress p JOIN personal_map m ON m.id=p.personal_map_id JOIN map_unit u ON u.unit_id=p.unit_id AND u.map_definition_id=m.map_definition_id AND u.map_version=m.map_version WHERE p.account_id=?::uuid AND p.personal_map_id=?::uuid ORDER BY u.ordinal""").use{ps->ps.setString(1,a);ps.setString(2,map);ps.executeQuery().use{rs->buildList{while(rs.next())add(MapUnitResponse(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getLong(6),rs.getLong(7),rs.getString(8),rs.getLong(9)))}}}

    private fun seasonInTx(c:Connection,a:String):Pair<SeasonResponse?,SeasonProgressResponse?> {
        val s=c.prepareStatement("SELECT season_id,version,start_at,end_at,state,identity_metadata::text FROM season_definition WHERE start_at<=now() AND end_at>now() AND state='ACTIVE' ORDER BY start_at DESC LIMIT 1").use{it.executeQuery().use{rs->if(!rs.next())null else SeasonResponse(rs.getString(1),rs.getInt(2),rs.getObject(3,OffsetDateTime::class.java).toInstant().toString(),rs.getObject(4,OffsetDateTime::class.java).toInstant().toString(),rs.getString(5),rs.getString(6))}} ?: return null to null
        c.prepareStatement("INSERT INTO season_progress(account_id,season_id,season_version) VALUES (?::uuid,?,?) ON CONFLICT DO NOTHING").use{ps->ps.setString(1,a);ps.setString(2,s.seasonId);ps.setInt(3,s.version);ps.executeUpdate()}
        val p=c.prepareStatement("SELECT units_completed,achievements_unlocked,xp_earned,coins_earned,state,revision FROM season_progress WHERE account_id=?::uuid AND season_id=? AND season_version=?").use{ps->ps.setString(1,a);ps.setString(2,s.seasonId);ps.setInt(3,s.version);ps.executeQuery().use{rs->rs.next();SeasonProgressResponse(s.seasonId,s.version,rs.getInt(1),rs.getInt(2),rs.getLong(3),rs.getLong(4),rs.getString(5),rs.getLong(6))}}
        return s to p
    }

    fun reconcileSeasons():Int=db.tx{c->
        c.createStatement().use{it.execute("SELECT pg_advisory_xact_lock(918273645)")}
        val ended=c.prepareStatement("UPDATE season_definition SET state='CLOSED' WHERE state='ACTIVE' AND end_at<=now() RETURNING season_id,version").use{ps->ps.executeQuery().use{rs->buildList{while(rs.next())add(rs.getString(1) to rs.getInt(2))}}}
        for((id,v) in ended){c.prepareStatement("INSERT INTO season_rollover_execution(season_id,season_version,transition) VALUES (?,?,'CLOSE') ON CONFLICT DO NOTHING").use{ps->ps.setString(1,id);ps.setInt(2,v);ps.executeUpdate()};c.prepareStatement("UPDATE season_progress SET state='CLOSED',revision=revision+1,updated_at=now() WHERE season_id=? AND season_version=?").use{ps->ps.setString(1,id);ps.setInt(2,v);ps.executeUpdate()}}
        val started=c.prepareStatement("UPDATE season_definition SET state='ACTIVE' WHERE state='PLANNED' AND start_at<=now() AND end_at>now() RETURNING season_id,version").use{ps->ps.executeQuery().use{rs->buildList{while(rs.next())add(rs.getString(1) to rs.getInt(2))}}}
        for((id,v) in started)c.prepareStatement("INSERT INTO season_rollover_execution(season_id,season_version,transition) VALUES (?,?,'START') ON CONFLICT DO NOTHING").use{ps->ps.setString(1,id);ps.setInt(2,v);ps.executeUpdate()}
        ended.size+started.size
    }

    private fun ensureAccount(c:Connection,a:String){
        c.prepareStatement("INSERT INTO progression_profile(account_id,level_curve_version,reward_policy_version) VALUES (?::uuid,'level-curve-v1','reward-v1') ON CONFLICT DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO coin_account_projection(account_id) VALUES (?::uuid) ON CONFLICT DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO consistency_state(account_id,timezone_used) SELECT ?::uuid,timezone FROM user_profile WHERE account_id=?::uuid ON CONFLICT DO NOTHING").use{ps->ps.setString(1,a);ps.setString(2,a);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO gaming_statistics(account_id) VALUES (?::uuid) ON CONFLICT DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO inventory_ownership(account_id,item_id,ownership_source) VALUES (?::uuid,'avatar-noob-default','DEFAULT') ON CONFLICT DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO equipped_avatar(account_id,avatar_id) VALUES (?::uuid,'avatar-noob-default') ON CONFLICT DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}
        ensureAchievementRows(c,a)
    }
    private fun ensureAchievementRows(c:Connection,a:String){c.prepareStatement("INSERT INTO achievement_progress(account_id,achievement_id,definition_version,state) SELECT ?::uuid,achievement_id,version,'LOCKED' FROM achievement_definition WHERE active=true ON CONFLICT DO NOTHING").use{ps->ps.setString(1,a);ps.executeUpdate()}}
    private fun ensureDaily(c:Connection,a:String,date:LocalDate,zone:String){c.prepareStatement("INSERT INTO daily_activity_state(account_id,local_date,timezone_used) VALUES (?::uuid,?,?) ON CONFLICT DO NOTHING").use{ps->ps.setString(1,a);ps.setObject(2,date);ps.setString(3,zone);ps.executeUpdate()}}
    private fun dailyBonusTimeSafe(c:Connection,a:String,at:Instant):Boolean {val prev=c.prepareStatement("SELECT max(last_eligible_at) FROM daily_activity_state WHERE account_id=?::uuid AND daily_bonus_granted=true").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();rs.getObject(1,OffsetDateTime::class.java)?.toInstant()}}?:return true;return at.isAfter(prev.plusSeconds(20*3600))||at==prev.plusSeconds(20*3600)}
    private fun updateConsistency(c:Connection,a:String,eventId:String,at:Instant,zone:String,date:LocalDate){
        val row=c.prepareStatement("SELECT current_consistency,longest_consistency,last_eligible_local_date,revision FROM consistency_state WHERE account_id=?::uuid FOR UPDATE").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();arrayOf(rs.getInt(1),rs.getInt(2),rs.getObject(3,LocalDate::class.java),rs.getLong(4))}}
        val update=ConsistencyEngine.update(row[0] as Int,row[1] as Int,row[2] as LocalDate?,at,zone);if(!update.qualifiedNewDay)return
        c.prepareStatement("INSERT INTO consistency_history(account_id,local_date,qualified,timezone_used,source_event_id) VALUES (?::uuid,?,true,?,?::uuid) ON CONFLICT DO NOTHING").use{ps->ps.setString(1,a);ps.setObject(2,date);ps.setString(3,zone);ps.setString(4,eventId);ps.executeUpdate()}
        val rev=c.prepareStatement("UPDATE consistency_state SET current_consistency=?,longest_consistency=?,last_eligible_local_date=?,timezone_used=?,revision=revision+1,updated_at=now() WHERE account_id=?::uuid RETURNING revision").use{ps->ps.setInt(1,update.current);ps.setInt(2,update.longest);ps.setObject(3,date);ps.setString(4,zone);ps.setString(5,a);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
        emit(c,a,"CONSISTENCY_UPDATED","consistency:$date",eventId,eventId,rev,"{\"current\":${update.current},\"longest\":${update.longest},\"localDate\":\"$date\"}")
    }
    private fun insertDecision(c:Connection,e:StoredActivity,d:RewardDecision){c.prepareStatement("INSERT INTO reward_decision_log(account_id,source_event_id,event_type,eligible,decision_code,policy_version,calculated_xp,calculated_coins,details) VALUES (?::uuid,?::uuid,?,?,?,?,?,?,jsonb_build_object('objectId',?,'eventRevision',?)) ON CONFLICT(account_id,source_event_id,policy_version) DO NOTHING").use{ps->ps.setString(1,e.accountId);ps.setString(2,e.eventId);ps.setString(3,e.type);ps.setBoolean(4,d.eligible);ps.setString(5,d.code.name);ps.setString(6,d.policyVersion);ps.setLong(7,d.xp);ps.setLong(8,d.coins);ps.setString(9,e.objectId);ps.setLong(10,e.revision);ps.executeUpdate()}}
    private fun markQueue(c:Connection,id:String,status:String){c.prepareStatement("UPDATE activity_reward_queue SET status=?,attempt_count=attempt_count+1,updated_at=now() WHERE activity_event_id=?::uuid").use{ps->ps.setString(1,status);ps.setString(2,id);ps.executeUpdate()}}
    private fun grantInventory(c:Connection,a:String,item:String,source:String,sourceEvent:String?,key:String){val rev=c.prepareStatement("INSERT INTO inventory_ownership(account_id,item_id,ownership_source) VALUES (?::uuid,?,?) ON CONFLICT(account_id,item_id) DO NOTHING RETURNING revision").use{ps->ps.setString(1,a);ps.setString(2,item);ps.setString(3,source);ps.executeQuery().use{rs->if(rs.next())rs.getLong(1)else null}};if(rev!=null)emit(c,a,"ITEM_ACQUIRED","inventory:$key",item,sourceEvent,rev,"{\"itemId\":\"${json(item)}\"}")}
    private fun effectiveLevel(c:Connection,a:String):Int=c.prepareStatement("SELECT effective_level FROM progression_profile WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}
    private fun progression(c:Connection,a:String):Triple<Long,Int,Long> = c.prepareStatement("SELECT lifetime_xp,level,revision FROM progression_profile WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();Triple(rs.getLong(1),rs.getInt(2),rs.getLong(3))}}
    private fun coinBalance(c:Connection,a:String):Long=c.prepareStatement("SELECT balance FROM coin_account_projection WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
    private fun profileTimezone(c:Connection,a:String):String=c.prepareStatement("SELECT timezone FROM user_profile WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();rs.getString(1)}}
    private fun stats(c:Connection,a:String):GamingStatisticsResponse=c.prepareStatement("SELECT meaningful_activities,xp_earned,coins_earned,coins_spent,achievements_unlocked,units_completed,maps_completed,seasons_participated,revision FROM gaming_statistics WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();GamingStatisticsResponse(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6),rs.getLong(7),rs.getLong(8),rs.getLong(9))}}
    private fun profileInTx(c:Connection,a:String,maturity:String):GameProfileSnapshotResponse{
        val p=progression(c,a);val lp=LevelCurveV1.progress(p.first);val balance=coinBalance(c,a);val consistency=c.prepareStatement("SELECT current_consistency,longest_consistency FROM consistency_state WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();rs.getInt(1) to rs.getInt(2)}}
        val av=c.prepareStatement("SELECT e.avatar_id,a.asset_key,a.tier,e.revision FROM equipped_avatar e JOIN avatar_catalog a ON a.avatar_id=e.avatar_id WHERE e.account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();AvatarStateResponse(rs.getString(1),rs.getString(2),rs.getString(3),"OWNED","OWNED",equipped=true,revision=rs.getLong(4))}}
        val ach=c.prepareStatement("SELECT count(*) FILTER(WHERE state IN ('UNLOCKED','CLAIMED')),count(*) FILTER(WHERE state='IN_PROGRESS'),count(*) FROM achievement_progress WHERE account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();AchievementSummaryResponse(rs.getInt(1),rs.getInt(2),rs.getInt(3))}}
        val inv=c.prepareStatement("SELECT count(*),count(*) FILTER(WHERE i.item_type='AVATAR') FROM inventory_ownership o JOIN inventory_catalog i ON i.item_id=o.item_id WHERE o.account_id=?::uuid").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->rs.next();InventorySummaryResponse(rs.getInt(1),rs.getInt(2))}}
        val map=mapInTx(c,a,maturity,false);val season=seasonInTx(c,a);return GameProfileSnapshotResponse(a,lp.level,lp.lifetimeXp,lp.currentLevelXp,lp.nextLevelRequiredXp,lp.progressFraction,lp.curveVersion,"reward-v1",balance,consistency.first,consistency.second,av,ach,inv,map.eligibility,map.state,map.units.firstOrNull{it.state in setOf("AVAILABLE","IN_PROGRESS")}?.unitId,season.first,season.second,stats(c,a),p.third)
    }
    private fun existingPurchase(c:Connection,a:String,key:String):StorePurchaseResponse?=c.prepareStatement("SELECT id,item_id,catalog_version,authoritative_price,currency,status,COALESCE(entitlement_revision,1) FROM store_purchase WHERE account_id=?::uuid AND idempotency_key=?").use{ps->ps.setString(1,a);ps.setString(2,key);ps.executeQuery().use{rs->if(!rs.next())null else StorePurchaseResponse(rs.uuid("id"),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),0,rs.getLong(7))}}
    private fun ledger(rs:ResultSet)=LedgerEntryResponse(rs.uuid("id"),rs.getLong("amount"),rs.getString("entry_type"),rs.getString("reward_source"),rs.getObject("source_event_id",UUID::class.java)?.toString(),rs.getString("policy_version"),rs.getString("reason"),rs.getObject("created_at",OffsetDateTime::class.java).toInstant().toString())
    private fun exists(c:Connection,sql:String,id:String,a:String):Boolean=runCatching{c.prepareStatement(sql).use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{it.next()}}}.getOrDefault(false)
    private fun emit(c:Connection,a:String,type:String,key:String,causation:String?,sourceEvent:String?,revision:Long,payload:String){c.prepareStatement("INSERT INTO game_state_event(account_id,event_type,causation_id,correlation_id,source_event_id,resulting_revision,payload,idempotency_key) VALUES (?::uuid,?,?,?,?::uuid,?,?::jsonb,?) ON CONFLICT(account_id,idempotency_key) DO NOTHING").use{ps->ps.setString(1,a);ps.setString(2,type);ps.setString(3,causation);ps.setString(4,sourceEvent);ps.setString(5,sourceEvent);ps.setLong(6,revision);ps.setString(7,payload);ps.setString(8,key);ps.executeUpdate()}}
    private fun odt(i:Instant)=OffsetDateTime.ofInstant(i,ZoneOffset.UTC)
    private fun json(s:String)=s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")
}
