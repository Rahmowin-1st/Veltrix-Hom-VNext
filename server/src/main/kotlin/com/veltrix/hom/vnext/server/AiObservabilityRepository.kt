package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.server.foundation.ModelProviderAdapter
import java.util.concurrent.ConcurrentHashMap

/** Persists provider-attempt metadata only; never raw prompts or source text. */
class AiObservabilityRepository(private val db: Database) {
    private data class Timing(val startedNanos:Long,val firstTokenNanos:Long?=null)
    private val timing=ConcurrentHashMap<String,Timing>()
    private fun key(requestId:String,attempt:Int)="$requestId:$attempt"

    fun attempt(accountId:String,conversationId:String?,messageId:String?,requestId:String,operation:String,provider:ModelProviderAdapter,attempt:Int,status:String,errorCode:String?){
        val k=key(requestId,attempt); val now=System.nanoTime()
        when(status){
            "STARTED" -> timing[k]=Timing(now)
            "SUCCEEDED","FAILED" -> {
                val t=timing.remove(k); val latency=t?.let{(now-it.startedNanos)/1_000_000}; val first=t?.firstTokenNanos?.let{(it-t.startedNanos)/1_000_000}
                db.tx { c -> c.prepareStatement("""INSERT INTO ai_provider_attempt(account_id,conversation_id,message_id,request_id,operation,provider_id,model_id,tier,attempt,status,latency_ms,first_token_ms,error_code)
                    VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?, ?,?,?)""").use { ps ->
                    ps.setString(1,accountId);ps.setString(2,conversationId);ps.setString(3,messageId);ps.setString(4,requestId);ps.setString(5,operation);ps.setString(6,provider.id);ps.setString(7,provider.modelId);ps.setString(8,provider.tier.name);ps.setInt(9,attempt);ps.setString(10,status);if(latency==null)ps.setNull(11,java.sql.Types.BIGINT) else ps.setLong(11,latency);if(first==null)ps.setNull(12,java.sql.Types.BIGINT) else ps.setLong(12,first);ps.setString(13,errorCode);ps.executeUpdate()
                }}
            }
        }
    }
    fun firstToken(requestId:String,attempt:Int){val k=key(requestId,attempt);timing.computeIfPresent(k){_,v->if(v.firstTokenNanos==null)v.copy(firstTokenNanos=System.nanoTime()) else v}}
}
