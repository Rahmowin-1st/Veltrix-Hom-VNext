package com.veltrix.hom.vnext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Part2RemoteSnapshot(val accountId:String,val type:String,val payload:String,val revision:Long)

/** Typed Android transport for Part 2. All economic writes remain online/server-authoritative. */
class Part2GameClient(private val api:VeltrixApiClient=VeltrixApiClient()) {
    fun profile(session:ApiSession):Part2RemoteSnapshot = get(session,"/v1/game/profile","PROFILE")
    fun personalMap(session:ApiSession):Part2RemoteSnapshot = get(session,"/v1/personal/map","MAP")
    fun store(session:ApiSession):Part2RemoteSnapshot = get(session,"/v1/store","STORE")
    fun achievements(session:ApiSession):Part2RemoteSnapshot = get(session,"/v1/achievements","ACHIEVEMENTS")
    fun inventory(session:ApiSession):Part2RemoteSnapshot = get(session,"/v1/inventory","INVENTORY")
    fun avatars(session:ApiSession):Part2RemoteSnapshot = get(session,"/v1/avatars","AVATARS")

    fun purchase(session:ApiSession,itemId:String,idempotencyKey:String):JSONObject {
        require(idempotencyKey.length>=8)
        val body=JSONObject().put("itemId",itemId).put("idempotencyKey",idempotencyKey).toString()
        val(code,text)=api.request("POST","/v1/store/purchase",session.token,body)
        require(code==201){"store purchase HTTP $code $text"}
        return JSONObject(text)
    }

    fun equipAvatar(session:ApiSession,avatarId:String,expectedRevision:Long):JSONObject {
        val body=JSONObject().put("avatarId",avatarId).put("expectedRevision",expectedRevision).toString()
        val(code,text)=api.request("POST","/v1/avatars/equip",session.token,body)
        require(code==200){"avatar equip HTTP $code $text"}
        return JSONObject(text)
    }

    fun unlockMap(session:ApiSession):Part2RemoteSnapshot {
        val(code,text)=api.request("POST","/v1/personal/map/unlock",session.token,"{}")
        require(code==200){"map unlock HTTP $code $text"}
        return snapshot(session.accountId,"MAP",text)
    }

    fun startMapUnit(session:ApiSession,unitId:String,expectedRevision:Long):JSONObject {
        val body=JSONObject().put("expectedRevision",expectedRevision).toString()
        val(code,text)=api.request("POST","/v1/personal/map/units/$unitId/start",session.token,body)
        require(code==200){"map unit start HTTP $code $text"}
        return JSONObject(text)
    }

    private fun get(session:ApiSession,path:String,type:String):Part2RemoteSnapshot {
        val(code,text)=api.request("GET",path,session.token,null)
        require(code==200){"$path HTTP $code $text"}
        return snapshot(session.accountId,type,text)
    }

    private fun snapshot(accountId:String,type:String,text:String):Part2RemoteSnapshot {
        val trimmed=text.trim()
        val revision=runCatching {
            if(trimmed.startsWith("{")) JSONObject(trimmed).optLong("revision",0L) else 0L
        }.getOrDefault(0L)
        return Part2RemoteSnapshot(accountId,type,text,revision)
    }
}

class Part2GameAndroidRepository(
    private val remote:Part2GameClient,
    private val local:Part2GameLocalStore,
) {
    suspend fun refreshProfile(session:ApiSession):Part2GameSnapshotEntity=withContext(Dispatchers.IO){
        val r=remote.profile(session);local.save(r.accountId,r.type,r.payload,r.revision)
    }

    suspend fun refreshOrCachedProfile(session:ApiSession):Part2GameSnapshotEntity {
        return try { refreshProfile(session) }
        catch(t:Throwable){ local.load(session.accountId,"PROFILE") ?: throw t }
    }

    suspend fun refreshMap(session:ApiSession):Part2GameSnapshotEntity=withContext(Dispatchers.IO){
        val r=remote.personalMap(session);local.save(r.accountId,r.type,r.payload,r.revision)
    }

    suspend fun cachedProfile(accountId:String)=local.load(accountId,"PROFILE")
    suspend fun cachedMap(accountId:String)=local.load(accountId,"MAP")

    /** Never queues spend offline: retry safety comes from the server idempotency key. */
    suspend fun purchaseOnline(session:ApiSession,itemId:String,idempotencyKey:String):JSONObject=withContext(Dispatchers.IO){
        remote.purchase(session,itemId,idempotencyKey)
    }

    suspend fun equipAvatarOnline(session:ApiSession,avatarId:String,expectedRevision:Long):JSONObject=withContext(Dispatchers.IO){
        remote.equipAvatar(session,avatarId,expectedRevision)
    }
}
