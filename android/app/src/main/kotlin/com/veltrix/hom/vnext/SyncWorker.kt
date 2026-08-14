package com.veltrix.hom.vnext

import android.content.Context
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class SyncWorker(appContext:Context,params:WorkerParameters):CoroutineWorker(appContext,params){
    override suspend fun doWork():Result {
        val session=SessionStore(applicationContext).read() ?: return Result.success()
        val db=VeltrixLocalDatabase.get(applicationContext)
        val dao=db.sync()
        val part3=Part3LocalDatabase.get(applicationContext)
        val batch=dao.nextBatch(session.accountId,50);if(batch.isEmpty())return Result.success()
        val body=JSONObject().put("mutations",JSONArray().apply{batch.forEach{m->
            val payload=runCatching{JSONObject(m.payload)}.getOrElse{JSONObject()}
            put(JSONObject().put("mutationId",m.id).put("entityType",m.entityType).put("entityId",m.entityId).put("operation",m.operation).apply{m.expectedRevision?.let{put("expectedRevision",it)}}.put("idempotencyKey",m.idempotencyKey).put("payload",payload))
        }}).toString()
        val response=try{post("${BuildConfig.VELTRIX_API_BASE_URL}/v1/sync/mutations",session.token,body)}catch(_:Exception){return Result.retry()}
        if(response.first==401){SessionStore(applicationContext).clear();return Result.failure()}
        if(response.first !in 200..299)return if(response.first>=500||response.first==429)Result.retry() else Result.failure()
        val results=runCatching{JSONObject(response.second).getJSONArray("results")}.getOrElse{return Result.retry()}
        var retry=false
        for(i in 0 until results.length()){
            val r=results.getJSONObject(i);val id=r.getString("mutationId");val current=batch.firstOrNull{it.id==id}?:continue
            when(r.getString("status")){
                "APPLIED"->{
                    dao.updateState(id,"ACKED",current.attemptCount+1)
                    if(current.entityType.equals("CONTEXT_CARRY",true)) {
                        val serverRevision=if(r.has("serverRevision")&&!r.isNull("serverRevision"))r.optLong("serverRevision",current.expectedRevision?:1) else current.expectedRevision?:1
                        part3.contextCarry().markAcked(session.accountId,serverRevision,System.currentTimeMillis())
                    }
                }
                "CONFLICT"->dao.updateState(id,"CONFLICT",current.attemptCount+1)
                "REJECTED"->dao.updateState(id,"REJECTED",current.attemptCount+1)
                "RETRY"->{dao.updateState(id,"PENDING",current.attemptCount+1);retry=true}
            }
        }
        return if(retry)Result.retry() else Result.success()
    }
    private fun post(url:String,token:String,json:String):Pair<Int,String>{
        val c=(URL(url).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=7_000;readTimeout=20_000;doOutput=true;setRequestProperty("Authorization","Bearer $token");setRequestProperty("Content-Type","application/json");setRequestProperty("X-Request-ID","android-sync-${System.currentTimeMillis()}")}
        c.outputStream.bufferedWriter().use{it.write(json)};val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;return code to (stream?.bufferedReader()?.use{it.readText()}?:"")
    }
}

object SyncScheduler {
    private const val UNIQUE_PERIODIC="veltrix-vnext-sync-periodic"
    fun ensure(context:Context){
        val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_PERIODIC,ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<SyncWorker>(15,TimeUnit.MINUTES).setConstraints(constraints).build())
        WorkManager.getInstance(context).enqueueUniqueWork("veltrix-vnext-sync-now",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build())
    }
}
