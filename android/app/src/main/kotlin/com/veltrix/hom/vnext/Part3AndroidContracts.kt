package com.veltrix.hom.vnext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class DataFreshness { FRESH, STALE, OFFLINE }
data class RepositoryState<T>(val value:T?,val freshness:DataFreshness,val loading:Boolean=false,val errorCode:String?=null,val retryable:Boolean=false,val serverRevision:Long=0)

data class HomeFinalModel(
    val accountId:String,val displayName:String,val avatarId:String,val level:Int,val lifetimeXp:Long,val currentLevelXp:Long,
    val nextLevelXp:Long,val remainingXp:Long,val coins:Long,val qualifiedActiveDays:Int,val consistency:Int,val currentFocus:String?,
    val memoryMaturity:String,val mapState:String,val currentMapUnit:String?,val seasonId:String?,val unreadNotifications:Int,
    val priorityKeys:List<String>,val insightCodes:List<String>,val revision:Long,
)

data class PersonalFinalModel(
    val accountId:String,val displayName:String,val avatarId:String,val level:Int,val lifetimeXp:Long,val coins:Long,
    val memoryMaturity:String,val strengths:List<String>,val weaknesses:List<String>,val interests:List<String>,val goals:List<String>,
    val mapState:String,val seasonId:String?,val achievementCount:Int,val inventoryCount:Int,val currentConsistency:Int,val revision:Long,
)

data class ProjectWorkspaceFinalModel(
    val projectId:String,val title:String,val status:String,val goalCount:Int,val sourceCount:Int,val chatCount:Int,val noteCount:Int,
    val assessmentCount:Int,val flashcardCount:Int,val mistakeCount:Int,val practiceCount:Int,val signalCount:Int,val memoryMaturity:String,
    val instructionRevision:Long?,val revision:Long,
)

data class ContextCarryModel(
    val accountId:String,val projectId:String?,val sourceIds:List<String>,val conversationId:String?,val assessmentId:String?,
    val topic:String?,val learningMode:String?,val origin:String,val returnDestination:String?,val contextRevision:Long,
)

data class UniversalCommandResultModel(val kind:String,val deterministic:Boolean,val requiresConfirmation:Boolean,val query:String?,val targetHint:String?)
data class FrontendSemanticEventModel(val eventId:String,val eventType:String,val subjectId:String?,val projectId:String?,val payload:String,val occurredAtEpochMs:Long)

data class SearchResultModel(val type:String,val id:String,val title:String,val snippet:String,val projectId:String?,val score:Double,val deepLink:String)

class Part3RemoteDataSource(private val api:VeltrixApiClient=VeltrixApiClient()) {
    fun home(session:ApiSession)=get(session,"/v1/home")
    fun personal(session:ApiSession)=get(session,"/v1/personal")
    fun workspace(session:ApiSession,projectId:String)=get(session,"/v1/projects/$projectId/workspace")
    fun contextCarry(session:ApiSession):JSONObject? {
        val(code,text)=api.request("GET","/v1/context-carry",session.token,null)
        if(code==204)return null
        require(code==200){"context carry HTTP $code $text"}
        return JSONObject(text)
    }
    fun putContextCarry(session:ApiSession,value:ContextCarryModel):JSONObject {
        val body=JSONObject().put("projectId",value.projectId).put("sourceIds",JSONArray(value.sourceIds)).put("conversationId",value.conversationId)
            .put("assessmentId",value.assessmentId).put("topic",value.topic).put("learningMode",value.learningMode).put("origin",value.origin)
            .put("returnDestination",value.returnDestination).put("expectedRevision",value.contextRevision.takeIf{it>0}).toString()
        val(code,text)=api.request("PUT","/v1/context-carry",session.token,body)
        require(code==200){"context carry PUT HTTP $code $text"};return JSONObject(text)
    }
    fun resolveCommand(session:ApiSession,text:String,projectId:String?=null,sourceId:String?=null):JSONObject {
        val body=JSONObject().put("text",text).put("projectId",projectId).put("sourceId",sourceId).toString()
        val(code,response)=api.request("POST","/v1/commands/resolve",session.token,body)
        require(code==200){"command HTTP $code $response"};return JSONObject(response)
    }
    fun search(session:ApiSession,query:String,projectId:String?=null,limit:Int=50):JSONArray {
        val body=JSONObject().put("query",query).put("projectId",projectId).put("limit",limit.coerceIn(1,100)).toString()
        val(code,response)=api.request("POST","/v1/search",session.token,body)
        require(code==200){"search HTTP $code $response"};return JSONArray(response)
    }
    fun frontendEvents(session:ApiSession,limit:Int=100):JSONArray {
        val(code,text)=api.request("GET","/v1/frontend-events?limit=${limit.coerceIn(1,200)}",session.token,null)
        require(code==200){"frontend events HTTP $code $text"};return JSONArray(text)
    }
    private fun get(session:ApiSession,path:String):JSONObject { val(code,text)=api.request("GET",path,session.token,null);require(code==200){"$path HTTP $code $text"};return JSONObject(text) }
}

class Part3AndroidRepository(
    private val context:android.content.Context,
    private val remote:Part3RemoteDataSource=Part3RemoteDataSource(),
    private val local:Part3LocalDatabase=Part3LocalDatabase.get(context),
) {
    suspend fun home(session:ApiSession,forceRefresh:Boolean=false):RepositoryState<HomeFinalModel> = snapshot(session,"HOME","GLOBAL",forceRefresh,::parseHome)
    suspend fun personal(session:ApiSession,forceRefresh:Boolean=false):RepositoryState<PersonalFinalModel> = snapshot(session,"PERSONAL","GLOBAL",forceRefresh,::parsePersonal)
    suspend fun projectWorkspace(session:ApiSession,projectId:String,forceRefresh:Boolean=false):RepositoryState<ProjectWorkspaceFinalModel> =
        snapshot(session,"PROJECT_WORKSPACE",projectId,forceRefresh,{parseWorkspace(it,projectId)})

    suspend fun contextCarry(session:ApiSession):RepositoryState<ContextCarryModel> = withContext(Dispatchers.IO) {
        val cached=local.contextCarry().get(session.accountId)
        try {
            val json=remote.contextCarry(session)
            if(json==null) RepositoryState(cached?.let(::localContextModel),DataFreshness.FRESH,serverRevision=cached?.contextRevision?:0)
            else {
                val value=parseContext(session.accountId,json)
                local.contextCarry().put(contextEntity(value,"ACKED"))
                RepositoryState(value,DataFreshness.FRESH,serverRevision=value.contextRevision)
            }
        } catch(t:Throwable) {
            RepositoryState(cached?.let(::localContextModel),if(cached==null)DataFreshness.OFFLINE else DataFreshness.STALE,errorCode=t::class.simpleName,retryable=true,serverRevision=cached?.contextRevision?:0)
        }
    }

    suspend fun updateContextCarryOnlineOnly(session:ApiSession,value:ContextCarryModel):RepositoryState<ContextCarryModel> = withContext(Dispatchers.IO) {
        require(value.accountId==session.accountId)
        val json = remote.putContextCarry(session, value)
        val confirmed = parseContext(session.accountId, json)
        local.contextCarry().put(contextEntity(confirmed,"ACKED"))
        RepositoryState(confirmed, DataFreshness.FRESH, serverRevision = confirmed.contextRevision)
    }

    @Deprecated(
        message = "Offline product mutation mode was removed by the Final Root Reset mission.",
        replaceWith = ReplaceWith("updateContextCarryOnlineOnly(session, value)"),
    )
    suspend fun updateContextCarryOfflineFirst(session: ApiSession, value: ContextCarryModel): RepositoryState<ContextCarryModel> =
        updateContextCarryOnlineOnly(session, value)

    suspend fun resolveCommand(session:ApiSession,text:String,projectId:String?=null,sourceId:String?=null):UniversalCommandResultModel=withContext(Dispatchers.IO) {
        val o=remote.resolveCommand(session,text,projectId,sourceId)
        UniversalCommandResultModel(o.getString("kind"),o.getBoolean("deterministic"),o.optBoolean("requiresConfirmation",false),o.optNullableString("query"),o.optNullableString("targetHint"))
    }

    suspend fun search(session:ApiSession,query:String,projectId:String?=null):List<SearchResultModel> = withContext(Dispatchers.IO) {
        val arr=remote.search(session,query,projectId);List(arr.length()){i->val o=arr.getJSONObject(i);SearchResultModel(o.getString("type"),o.getString("id"),o.getString("title"),o.optString("snippet"),o.optNullableString("projectId"),o.optDouble("score",0.0),o.getString("deepLink"))}
    }

    suspend fun refreshFrontendEvents(session:ApiSession):List<FrontendSemanticEventModel> = withContext(Dispatchers.IO) {
        val arr=remote.frontendEvents(session);val values=List(arr.length()){i->parseFrontendEvent(arr.getJSONObject(i))}
        local.frontendEvents().insertAll(values.map{Part3FrontendEventEntity(session.accountId,it.eventId,it.eventType,it.subjectId,it.projectId,it.payload,it.occurredAtEpochMs)})
        values
    }
    suspend fun pendingFrontendEvents(accountId:String,limit:Int=100):List<FrontendSemanticEventModel> = local.frontendEvents().pending(accountId,limit).map{FrontendSemanticEventModel(it.eventId,it.eventType,it.subjectId,it.projectId,it.payload,it.occurredAtEpochMs)}
    suspend fun consumeFrontendEvent(accountId:String,eventId:String):Boolean = local.frontendEvents().consume(accountId,eventId,System.currentTimeMillis())==1

    private suspend fun <T> snapshot(session:ApiSession,kind:String,scopeId:String,forceRefresh:Boolean,parser:(JSONObject)->T):RepositoryState<T> = withContext(Dispatchers.IO) {
        val cached=local.snapshots().get(session.accountId,kind,scopeId)
        if(!forceRefresh && cached!=null && System.currentTimeMillis()-cached.fetchedAtEpochMs<60_000) return@withContext RepositoryState(parser(JSONObject(cached.payload)),DataFreshness.FRESH,serverRevision=cached.serverRevision)
        try {
            val json=when(kind){"HOME"->remote.home(session);"PERSONAL"->remote.personal(session);"PROJECT_WORKSPACE"->remote.workspace(session,scopeId);else->error("unsupported snapshot $kind")}
            val revision=json.optLong("revision",cached?.serverRevision?:0);val payload=json.toString()
            local.snapshots().put(Part3SnapshotEntity(session.accountId,kind,scopeId,payload,revision,System.currentTimeMillis()))
            RepositoryState(parser(json),DataFreshness.FRESH,serverRevision=revision)
        } catch(t:Throwable) {
            RepositoryState(cached?.let{parser(JSONObject(it.payload))},if(cached==null)DataFreshness.OFFLINE else DataFreshness.STALE,errorCode=t::class.simpleName,retryable=true,serverRevision=cached?.serverRevision?:0)
        }
    }

    private fun parseHome(o:JSONObject)=HomeFinalModel(
        o.getString("accountId"),o.optString("displayName"),o.optString("avatarId"),o.optInt("effectiveLevel",o.optInt("level",1)),o.optLong("lifetimeXp"),
        o.optLong("currentLevelXp"),o.optLong("nextLevelXp"),o.optLong("remainingXp"),o.optLong("coins"),o.optInt("qualifiedActiveDays"),o.optInt("currentConsistency",o.optInt("consistency")),
        parseCurrentFocus(o),o.optString("memoryMaturity"),o.optString("mapState"),o.optNullableString("currentMapUnit"),o.optNullableString("seasonId"),
        o.optInt("unreadNotifications"),o.optJSONObject("priorities")?.optJSONArray("orderedKeys")?.stringList() ?: o.optJSONArray("priorityKeys")?.stringList().orEmpty(),
        o.optJSONArray("insights")?.let{a->List(a.length()){i->a.getJSONObject(i).optString("code")}}.orEmpty(),o.optLong("revision",1)
    )
    private fun parsePersonal(o:JSONObject)=PersonalFinalModel(
        o.getString("accountId"),o.optString("displayName"),o.optString("avatarId"),o.optInt("effectiveLevel",o.optInt("level",1)),o.optLong("lifetimeXp"),o.optLong("coins"),
        o.optJSONObject("memory")?.optString("maturity")?.takeIf{it.isNotBlank()} ?: o.optString("memoryMaturity"),
        o.optJSONArray("strengths")?.stringList().orEmpty(),o.optJSONArray("weaknesses")?.stringList().orEmpty(),o.optJSONArray("interests")?.stringList().orEmpty(),
        o.optJSONArray("goals")?.stringList().orEmpty(),o.optString("mapState"),o.optNullableString("seasonId"),o.optInt("achievements",o.optInt("achievementCount")),
        o.optInt("inventoryItems",o.optInt("inventoryCount")),o.optInt("currentConsistency"),o.optLong("revision",1)
    )
    private fun parseWorkspace(o:JSONObject,projectId:String):ProjectWorkspaceFinalModel {
        val project=o.optJSONObject("project");return ProjectWorkspaceFinalModel(projectId,project?.optString("title").orEmpty(),project?.optString("status").orEmpty(),o.optInt("goalCount"),o.optInt("sourceCount"),o.optInt("chatCount"),o.optInt("noteCount"),o.optInt("assessmentCount"),o.optInt("flashcardCount"),o.optInt("mistakeCount"),o.optInt("practiceCount"),o.optInt("signalCount"),o.optString("memoryMaturity"),o.optLong("instructionRevision").takeIf{it>0},o.optLong("revision",project?.optLong("revision",1)?:1))
    }
    private fun parseContext(accountId:String,o:JSONObject)=ContextCarryModel(accountId,o.optNullableString("projectId"),o.optJSONArray("sourceIds")?.stringList().orEmpty(),o.optNullableString("conversationId"),o.optNullableString("assessmentId"),o.optNullableString("topic"),o.optNullableString("learningMode"),o.optString("origin","UNKNOWN"),o.optNullableString("returnDestination"),o.optLong("contextRevision",1))
    private fun localContextModel(e:Part3ContextCarryEntity)=ContextCarryModel(e.accountId,e.projectId,runCatching{JSONArray(e.sourceIdsJson).stringList()}.getOrDefault(emptyList()),e.conversationId,e.assessmentId,e.topic,e.learningMode,e.origin,e.returnDestination,e.contextRevision)
    private fun contextEntity(v:ContextCarryModel,state:String)=Part3ContextCarryEntity(v.accountId,v.projectId,JSONArray(v.sourceIds).toString(),v.conversationId,v.assessmentId,v.topic,v.learningMode,v.origin,v.returnDestination,v.contextRevision,state,System.currentTimeMillis())
    private fun parseFrontendEvent(o:JSONObject):FrontendSemanticEventModel { val raw=o.optString("occurredAt");val epoch=runCatching{java.time.Instant.parse(raw).toEpochMilli()}.getOrDefault(System.currentTimeMillis());return FrontendSemanticEventModel(o.getString("eventId"),o.getString("eventType"),o.optNullableString("subjectId"),o.optNullableString("projectId"),o.optJSONObject("payload")?.toString()?:"{}",epoch) }
}

private fun parseCurrentFocus(o:JSONObject):String? {
    if(!o.has("currentFocus") || o.isNull("currentFocus")) return null
    return when(val raw=o.opt("currentFocus")) {
        is JSONObject -> raw.optString("title").takeIf{it.isNotBlank()}
        is String -> runCatching{JSONObject(raw).optString("title").takeIf{it.isNotBlank()}}.getOrNull() ?: raw.takeIf{it.isNotBlank()}
        else -> null
    }
}
private fun JSONObject.optNullableString(key:String):String?=if(!has(key)||isNull(key))null else optString(key).takeIf{it.isNotBlank()}
private fun JSONArray.stringList():List<String> = List(length()) { i -> getString(i) }
