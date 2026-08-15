package com.veltrix.hom.vnext

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

data class ProjectCardModel(val id:String,val title:String,val purpose:String?,val status:String,val priority:Int,val revision:Long,val updatedAt:String,val lastActiveAt:String)
data class ProjectGoalModel(val id:String,val title:String,val description:String?,val status:String,val priority:Int,val revision:Long)
data class ProjectWorkspaceUiModel(val project:ProjectCardModel,val goals:List<ProjectGoalModel>,val recentChats:List<ConversationUiModel>,val sourceCount:Int,val noteCount:Int,val assessmentCount:Int,val flashcardCount:Int,val mistakeCount:Int,val practiceCount:Int,val projectMemorySignals:Int,val meaningfulEvents:Long,val instruction:String?,val recommendationActions:List<String>,val contextTopic:String?,val contextLearningMode:String?,val revision:Long)
data class ConversationUiModel(val id:String,val projectId:String?,val scope:String,val title:String,val learningMode:String,val memoryEnabled:Boolean,val projectMemoryEnabled:Boolean,val pinned:Boolean,val archived:Boolean,val revision:Long,val updatedAt:String)
data class ChatMessageUiModel(val id:String,val conversationId:String,val role:String,val state:String,val content:String,val finalMarker:Boolean,val revision:Long,val createdAt:String)
data class CitationUiModel(val index:Int,val sourceId:String,val excerpt:String,val page:Int?,val section:String?,val relevance:Double)
data class SourceUiModel(val id:String,val title:String,val type:String,val mimeType:String,val state:String,val progress:Int,val revision:Long)
data class SearchUiModel(val type:String,val id:String,val title:String,val snippet:String,val projectId:String?,val deepLink:String,val score:Double)
data class ActivityUiModel(val eventId:String,val type:String,val occurredAt:String,val projectId:String?,val objectId:String?,val meaningful:Boolean,val deepLink:String?)
data class AssessmentQuestionUiModel(val id:String,val position:Int,val prompt:String,val type:String,val options:List<String>)
data class AssessmentDetailUiModel(val id:String,val kind:String,val title:String,val projectId:String?,val state:String,val questionCount:Int,val revision:Long,val questions:List<AssessmentQuestionUiModel>)
data class AttemptUiModel(val id:String,val assessmentId:String,val projectId:String?,val state:String,val score:Double?,val revision:Long)
data class AssessmentResultUiModel(val attempt:AttemptUiModel,val score:Double,val accuracy:Double,val incorrectQuestionIds:List<String>,val mistakeIds:List<String>)
data class PracticeItemUiModel(val id:String,val position:Int,val prompt:String,val itemType:String,val state:String,val difficulty:Int,val topic:String?,val userAnswer:String?,val revision:Long)
data class PracticeSessionUiModel(val id:String,val projectId:String?,val focusTopic:String?,val state:String,val revision:Long,val currentPosition:Int,val targetItemCount:Int,val difficulty:Int,val adaptive:Boolean,val hintPolicy:String,val revealPolicy:String,val items:List<PracticeItemUiModel>,val summary:String)
data class PracticeCheckUiModel(val item:PracticeItemUiModel,val correct:Boolean,val score:Double,val explanation:String,val nextItemId:String?,val mistakeId:String?)
data class PracticeCompleteUiModel(val answered:Int,val correct:Int,val accuracy:Double,val summary:String)
data class FlashcardUiModel(val id:String,val deckId:String,val projectId:String?,val front:String,val back:String,val explanation:String?,val dueAt:String,val intervalDays:Int,val repetitions:Int,val lapses:Int,val revision:Long)
data class MistakeUiModel(val id:String,val projectId:String?,val sourceId:String?,val topic:String,val prompt:String,val userAnswer:String?,val expectedAnswer:String?,val occurrenceCount:Int,val status:String,val revision:Long)
data class StoreItemUiModel(val itemId:String,val itemType:String,val priceCoins:Long,val owned:Boolean,val available:Boolean,val requirements:String,val metadata:String)
data class StoreCatalogUiModel(val catalogVersion:String,val coinBalance:Long,val items:List<StoreItemUiModel>)
data class InventoryItemUiModel(val itemId:String,val type:String,val ownershipSource:String,val acquiredAt:String,val quantity:Long,val metadata:String,val revision:Long)
data class AvatarCatalogUiModel(val avatarId:String,val name:String,val assetKey:String,val tier:String,val owned:Boolean,val equipped:Boolean,val storePrice:Long?,val catalogVersion:String,val identityMetadata:String)
data class MapUnitUiModel(val unitId:String,val ordinal:Int,val semanticKey:String,val titleKey:String,val state:String,val progress:Long,val requiredProgress:Long,val revision:Long)
data class PersonalMapUiModel(val mapId:String?,val definitionId:String,val version:Int,val state:String,val eligible:Boolean,val levelRequirement:Int,val memoryRequirement:String,val levelSatisfied:Boolean,val memorySatisfied:Boolean,val unlockState:String,val units:List<MapUnitUiModel>,val revision:Long)
data class GameProfileUiModel(val level:Int,val lifetimeXp:Long,val currentLevelXp:Long,val nextLevelXp:Long,val coinBalance:Long,val consistency:Int,val avatarId:String,val avatarAssetKey:String,val avatarTier:String,val avatarRevision:Long,val mapState:String,val revision:Long)
data class MutationFeedback(val success:Boolean,val code:String?=null,val message:String?=null,val retryable:Boolean=false)
class BackendUiException(val httpStatus:Int,val code:String,val retryable:Boolean,val detail:String):RuntimeException(detail)
data class StreamUiEvent(val type:String,val messageId:String?,val segment:String?,val final:Boolean,val errorCode:String?,val retryable:Boolean)

class Part2FeatureRepository(
    context:Context,
    private val api:VeltrixApiClient=VeltrixApiClient(),
    private val local:Part3LocalDatabase=Part3LocalDatabase.get(context),
){
    private val snapshots=local.snapshots()

    suspend fun projects(session:ApiSession,force:Boolean=false)=cachedArray(session,"P2_PROJECTS","GLOBAL","/v1/projects?limit=100",force,::parseProjects)
    suspend fun workspace(session:ApiSession,projectId:String,force:Boolean=false)=cachedObject(session,"P2_WORKSPACE",projectId,"/v1/projects/$projectId/workspace",force,::parseWorkspace)
    suspend fun chats(session:ApiSession,projectId:String?=null,force:Boolean=false):RepositoryState<List<ConversationUiModel>>{val scope=projectId?:"GLOBAL";val path=if(projectId==null)"/v1/chats?limit=100" else "/v1/chats?projectId=$projectId&limit=100";return cachedArray(session,"P2_CHATS",scope,path,force,::parseChats)}
    suspend fun messages(session:ApiSession,conversationId:String,force:Boolean=false)=cachedArray(session,"P2_MESSAGES",conversationId,"/v1/chats/$conversationId/messages?limit=200",force,::parseMessages)
    suspend fun sources(session:ApiSession,force:Boolean=false)=cachedArray(session,"P2_SOURCES","GLOBAL","/v1/sources?limit=200",force,::parseSources)
    suspend fun flashcards(session:ApiSession,force:Boolean=false)=cachedArray(session,"P2_FLASHCARDS","DUE","/v1/flashcards/due?limit=200",force,::parseFlashcards)
    suspend fun mistakes(session:ApiSession,projectId:String?=null,force:Boolean=false):RepositoryState<List<MistakeUiModel>>{val scope=projectId?:"GLOBAL";val path=if(projectId==null)"/v1/mistakes?limit=200" else "/v1/mistakes?projectId=$projectId&limit=200";return cachedArray(session,"P2_MISTAKES",scope,path,force,::parseMistakes)}
    suspend fun store(session:ApiSession,force:Boolean=false)=cachedObject(session,"P2_STORE","GLOBAL","/v1/store",force,::parseStore)
    suspend fun inventory(session:ApiSession,force:Boolean=false)=cachedArray(session,"P2_INVENTORY","GLOBAL","/v1/inventory?limit=200",force,::parseInventory)
    suspend fun avatars(session:ApiSession,force:Boolean=false)=cachedArray(session,"P2_AVATARS","GLOBAL","/v1/avatars/catalog",force,::parseAvatars)
    suspend fun personalMap(session:ApiSession,force:Boolean=false)=cachedObject(session,"P2_MAP","GLOBAL","/v1/personal/map",force,::parseMap)
    suspend fun gameProfile(session:ApiSession,force:Boolean=false)=cachedObject(session,"P2_GAME","GLOBAL","/v1/game/profile",force,::parseGameProfile)
    suspend fun history(session:ApiSession,force:Boolean=false)=cachedObject(session,"P2_HISTORY","GLOBAL","/v1/activity?limit=100",force,::parseActivityResponse)

    suspend fun search(session:ApiSession,query:String,projectId:String?=null):RepositoryState<List<SearchUiModel>> = withContext(Dispatchers.IO){
        if(query.isBlank())return@withContext RepositoryState(emptyList(),DataFreshness.FRESH)
        try{
            val body=JSONObject().put("query",query.trim()).put("limit",50).apply{projectId?.let{put("projectId",it)}}.toString()
            val(_,text)=expect(api.request("POST","/v1/search",session.token,body))
            val array=runCatching{JSONArray(text)}.getOrElse{JSONObject(text).optJSONArray("results")?:JSONArray()}
            RepositoryState(parseSearch(array),DataFreshness.FRESH)
        }catch(e:BackendUiException){RepositoryState(null,DataFreshness.STALE,errorCode=e.code,retryable=e.retryable)}catch(_:Throwable){RepositoryState(null,DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true)}
    }

    suspend fun createProject(session:ApiSession,title:String,purpose:String?):ProjectCardModel=withContext(Dispatchers.IO){val body=JSONObject().put("title",title.trim()).apply{purpose?.takeIf{it.isNotBlank()}?.let{put("purpose",it.trim())}}.toString();val(_,text)=expect(api.request("POST","/v1/projects",session.token,body),setOf(201));parseProject(JSONObject(text))}
    suspend fun createConversation(session:ApiSession,projectId:String?=null,title:String="New chat",learningMode:String="DEFAULT"):ConversationUiModel=withContext(Dispatchers.IO){val body=JSONObject().put("scope",if(projectId==null)"GLOBAL" else "PROJECT").put("title",title).put("learningMode",learningMode).put("memoryEnabled",true).put("projectMemoryEnabled",true).apply{projectId?.let{put("projectId",it)}}.toString();val(_,text)=expect(api.request("POST","/v1/chats",session.token,body),setOf(201));parseConversation(JSONObject(text))}
    suspend fun retryMessage(session:ApiSession,conversationId:String,messageId:String)=mutate(session,"/v1/chats/$conversationId/messages/$messageId/retry",JSONObject().put("idempotencyKey","android-retry-${UUID.randomUUID()}"),setOf(202))
    suspend fun regenerateMessage(session:ApiSession,conversationId:String,messageId:String)=mutate(session,"/v1/chats/$conversationId/messages/$messageId/regenerate",JSONObject().put("idempotencyKey","android-regenerate-${UUID.randomUUID()}"),setOf(202))
    suspend fun citations(session:ApiSession,conversationId:String,messageId:String):List<CitationUiModel> = withContext(Dispatchers.IO){val(_,text)=expect(api.request("GET","/v1/chats/$conversationId/messages/$messageId/citations",session.token,null));val array=runCatching{JSONArray(text)}.getOrElse{JSONObject(text).optJSONArray("citations")?:JSONArray()};parseCitations(array)}

    suspend fun createTextSource(session:ApiSession,title:String,text:String):SourceUiModel=withContext(Dispatchers.IO){
        val bytes=text.toByteArray();val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 0xff)}
        val meta=JSONObject().put("title",title.trim()).put("type","TEXT").put("mimeType","text/plain").put("contentHash",hash).put("sizeBytes",bytes.size)
        val(_,created)=expect(api.request("POST","/v1/sources",session.token,meta.toString()),setOf(201));val id=JSONObject(created).getString("id")
        val(_,ready)=expect(api.request("POST","/v1/sources/$id/text",session.token,JSONObject().put("text",text).toString()))
        parseSource(JSONObject(ready))
    }
    suspend fun retrySource(session:ApiSession,id:String)=mutate(session,"/v1/sources/$id/retry",JSONObject(),setOf(200))
    suspend fun linkSource(session:ApiSession,id:String,projectId:String,link:Boolean=true)=mutate(session,"/v1/sources/$id/${if(link)"link-project" else "unlink-project"}",JSONObject().put("projectId",projectId),setOf(200))

    suspend fun assessment(session:ApiSession,id:String)=cachedObject(session,"P2_ASSESSMENT",id,"/v1/assessments/$id",true,::parseAssessment)
    suspend fun startAttempt(session:ApiSession,assessmentId:String):AttemptUiModel=withContext(Dispatchers.IO){val(_,text)=expect(api.request("POST","/v1/assessments/$assessmentId/attempts",session.token,"{}"),setOf(201));parseAttempt(JSONObject(text))}
    suspend fun answer(session:ApiSession,attemptId:String,questionId:String,answers:List<String>)=mutate(session,"/v1/assessments/attempts/$attemptId/answer",JSONObject().put("questionId",questionId).put("answers",JSONArray(answers)),setOf(200),"PUT")
    suspend fun submitAttempt(session:ApiSession,attemptId:String):AssessmentResultUiModel=withContext(Dispatchers.IO){val(_,text)=expect(api.request("POST","/v1/assessments/attempts/$attemptId/submit",session.token,"{}"));parseAssessmentResult(JSONObject(text))}

    suspend fun createPractice(session:ApiSession,projectId:String?,focusTopic:String?):PracticeSessionUiModel=withContext(Dispatchers.IO){
        val body=JSONObject().put("difficulty",2).put("targetItemCount",8).put("adaptive",true).apply{projectId?.let{put("projectId",it)};focusTopic?.takeIf{it.isNotBlank()}?.let{put("focusTopic",it)}}
        val(_,created)=expect(api.request("POST","/v1/practice",session.token,body.toString()),setOf(201));val o=JSONObject(created);val id=o.optString("id").ifBlank{o.optJSONObject("session")?.optString("id").orEmpty()}
        if(id.isBlank())throw BackendUiException(500,"PRACTICE_PARSE",false,"Practice id unavailable")
        practice(session,id).value?:throw BackendUiException(500,"PRACTICE_PARSE",false,"Practice response unavailable")
    }
    suspend fun practice(session:ApiSession,id:String)=cachedObject(session,"P2_PRACTICE",id,"/v1/practice/$id",true,::parsePractice)
    suspend fun practiceAttempt(session:ApiSession,sessionId:String,itemId:String,answer:String)=mutate(session,"/v1/practice/$sessionId/items/$itemId/attempt",JSONObject().put("answer",answer).put("idempotencyKey","android-practice-${UUID.randomUUID()}"),setOf(200))
    suspend fun practiceHint(session:ApiSession,sessionId:String,itemId:String):String=withContext(Dispatchers.IO){val(_,text)=expect(api.request("POST","/v1/practice/$sessionId/items/$itemId/hint",session.token,"{}"));val o=JSONObject(text);o.optString("body").ifBlank{o.optString("hint")}}
    suspend fun practiceCheck(session:ApiSession,sessionId:String,itemId:String):PracticeCheckUiModel=withContext(Dispatchers.IO){val(_,text)=expect(api.request("POST","/v1/practice/$sessionId/items/$itemId/check",session.token,"{}"));parsePracticeCheck(JSONObject(text))}
    suspend fun practiceSkip(session:ApiSession,sessionId:String,itemId:String)=mutate(session,"/v1/practice/$sessionId/items/$itemId/skip",JSONObject(),setOf(200))
    suspend fun completePractice(session:ApiSession,sessionId:String,expectedRevision:Long):PracticeCompleteUiModel=withContext(Dispatchers.IO){val(_,text)=expect(api.request("POST","/v1/practice/$sessionId/complete",session.token,JSONObject().put("expectedRevision",expectedRevision).toString()));parsePracticeComplete(JSONObject(text))}

    suspend fun reviewFlashcard(session:ApiSession,cardId:String,rating:String):FlashcardUiModel=withContext(Dispatchers.IO){val(_,text)=expect(api.request("POST","/v1/flashcards/cards/$cardId/review",session.token,JSONObject().put("rating",rating).toString()));val o=JSONObject(text);parseFlashcard(o.optJSONObject("card")?:o)}
    suspend fun resolveMistake(session:ApiSession,mistake:MistakeUiModel)=mutate(session,"/v1/mistakes/${mistake.id}/resolve",JSONObject().put("expectedRevision",mistake.revision),setOf(200))
    suspend fun practiceFromMistake(session:ApiSession,mistakeId:String):String=withContext(Dispatchers.IO){val(_,text)=expect(api.request("POST","/v1/mistakes/$mistakeId/practice",session.token,JSONObject().put("idempotencyKey","android-mistake-practice-${UUID.randomUUID()}").toString()),setOf(201));val o=JSONObject(text);o.optString("id").ifBlank{o.optJSONObject("session")?.optString("id").orEmpty()}}
    suspend fun flashcardFromMistake(session:ApiSession,mistakeId:String)=mutate(session,"/v1/mistakes/$mistakeId/flashcard",JSONObject().put("deckTitle","Mistake Review").put("idempotencyKey","android-mistake-card-${UUID.randomUUID()}"),setOf(201))
    suspend fun purchase(session:ApiSession,itemId:String)=mutate(session,"/v1/store/purchase",JSONObject().put("itemId",itemId).put("idempotencyKey","android-purchase-${UUID.randomUUID()}"),setOf(201))
    suspend fun equipAvatar(session:ApiSession,avatarId:String,expectedRevision:Long)=mutate(session,"/v1/avatars/equip",JSONObject().put("avatarId",avatarId).put("expectedRevision",expectedRevision),setOf(200))
    suspend fun unlockMap(session:ApiSession)=mutate(session,"/v1/personal/map/unlock",JSONObject(),setOf(200))
    suspend fun startMapUnit(session:ApiSession,unitId:String,expectedRevision:Long)=mutate(session,"/v1/personal/map/units/$unitId/start",JSONObject().put("expectedRevision",expectedRevision),setOf(200))

    fun streamAi(session:ApiSession,conversationId:String,projectId:String?,sourceIds:List<String>,text:String,learningMode:String,onEvent:(StreamUiEvent)->Unit){
        val body=JSONObject().put("conversationId",conversationId).put("sourceIds",JSONArray(sourceIds)).put("text",text).put("learningMode",learningMode).put("memoryEnabled",true).put("projectMemoryEnabled",true).put("idempotencyKey","android-stream-${UUID.randomUUID()}").apply{projectId?.let{put("projectId",it)}}.toString()
        val connection=(URL(BuildConfig.VELTRIX_API_BASE_URL.trimEnd('/')+"/v1/ai/stream").openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=7_000;readTimeout=45_000;doOutput=true;setRequestProperty("Accept","text/event-stream");setRequestProperty("Content-Type","application/json");setRequestProperty("Authorization","Bearer ${session.token}");setRequestProperty("X-Request-ID","android-stream-${UUID.randomUUID()}")}
        connection.outputStream.bufferedWriter().use{it.write(body)};val status=connection.responseCode
        if(status !in 200..299){val detail=connection.errorStream?.bufferedReader()?.use{it.readText()}.orEmpty();connection.disconnect();throw error(status,detail)}
        var eventType="message"
        connection.inputStream.bufferedReader().useLines{lines->lines.forEach{line->when{line.startsWith("event:")->eventType=line.substringAfter(':').trim();line.startsWith("data:")->runCatching{JSONObject(line.substringAfter(':').trim())}.getOrNull()?.let{o->val err=o.optJSONObject("error")?:if(eventType=="error")o else null;onEvent(StreamUiEvent(eventType,o.optNullable("messageId"),o.optNullable("segment"),o.optBoolean("final",false),err?.optString("code")?.takeIf{it.isNotBlank()},err?.optBoolean("retryable",false)==true))}}}}
        connection.disconnect()
    }

    private suspend fun mutate(session:ApiSession,path:String,body:JSONObject,expected:Set<Int>,method:String="POST"):MutationFeedback=withContext(Dispatchers.IO){try{expect(api.request(method,path,session.token,body.toString()),expected);MutationFeedback(true)}catch(e:BackendUiException){MutationFeedback(false,e.code,e.detail,e.retryable)}catch(_:Throwable){MutationFeedback(false,"OFFLINE","Network unavailable",true)}}

    private suspend fun <T> cachedObject(session:ApiSession,kind:String,scope:String,path:String,force:Boolean,parser:(JSONObject)->T):RepositoryState<T> = withContext(Dispatchers.IO){
        val cached=snapshots.get(session.accountId,kind,scope)
        if(!force&&cached!=null&&System.currentTimeMillis()-cached.fetchedAtEpochMs<15_000)return@withContext RepositoryState(parser(JSONObject(cached.payload)),DataFreshness.FRESH,serverRevision=cached.serverRevision)
        try{val(_,text)=expect(api.request("GET",path,session.token,null));val o=JSONObject(text);val revision=o.optLong("revision",0);snapshots.put(Part3SnapshotEntity(session.accountId,kind,scope,text,revision,System.currentTimeMillis()));RepositoryState(parser(o),DataFreshness.FRESH,serverRevision=revision)}
        catch(e:BackendUiException){if(cached!=null)RepositoryState(parser(JSONObject(cached.payload)),DataFreshness.STALE,errorCode=e.code,retryable=e.retryable,serverRevision=cached.serverRevision)else RepositoryState(null,DataFreshness.STALE,errorCode=e.code,retryable=e.retryable)}
        catch(_:Throwable){if(cached!=null)RepositoryState(parser(JSONObject(cached.payload)),DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true,serverRevision=cached.serverRevision)else RepositoryState(null,DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true)}
    }

    private suspend fun <T> cachedArray(session:ApiSession,kind:String,scope:String,path:String,force:Boolean,parser:(JSONArray)->T):RepositoryState<T> = withContext(Dispatchers.IO){
        val cached=snapshots.get(session.accountId,kind,scope)
        if(!force&&cached!=null&&System.currentTimeMillis()-cached.fetchedAtEpochMs<15_000)return@withContext RepositoryState(parser(JSONArray(cached.payload)),DataFreshness.FRESH,serverRevision=cached.serverRevision)
        try{val(_,text)=expect(api.request("GET",path,session.token,null));val a=parseArrayEnvelope(text);val normalized=a.toString();val revision=maxRevision(a);snapshots.put(Part3SnapshotEntity(session.accountId,kind,scope,normalized,revision,System.currentTimeMillis()));RepositoryState(parser(a),DataFreshness.FRESH,serverRevision=revision)}
        catch(e:BackendUiException){if(cached!=null)RepositoryState(parser(JSONArray(cached.payload)),DataFreshness.STALE,errorCode=e.code,retryable=e.retryable,serverRevision=cached.serverRevision)else RepositoryState(null,DataFreshness.STALE,errorCode=e.code,retryable=e.retryable)}
        catch(_:Throwable){if(cached!=null)RepositoryState(parser(JSONArray(cached.payload)),DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true,serverRevision=cached.serverRevision)else RepositoryState(null,DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true)}
    }

    private fun expect(result:Pair<Int,String>,expected:Set<Int> = setOf(200)):Pair<Int,String>{if(result.first !in expected)throw error(result.first,result.second);return result}
    private fun error(status:Int,text:String):BackendUiException{val envelope=runCatching{JSONObject(text).optJSONObject("error")}.getOrNull();return BackendUiException(status,envelope?.optString("code")?.takeIf{it.isNotBlank()}?:"HTTP_$status",envelope?.optBoolean("retryable",status>=500)==true,envelope?.optString("message")?.takeIf{it.isNotBlank()}?:"Request failed ($status)")}
}

private fun parseProjects(a:JSONArray):List<ProjectCardModel> = a.objects().map(::parseProject)
private fun parseProject(o:JSONObject)=ProjectCardModel(o.getString("id"),o.optString("title"),o.optNullable("purpose"),o.optString("status"),o.optInt("priority"),o.optLong("revision"),o.optString("updatedAt"),o.optString("lastActiveAt"))
private fun parseGoal(o:JSONObject)=ProjectGoalModel(o.getString("id"),o.optString("title"),o.optNullable("description"),o.optString("status"),o.optInt("priority"),o.optLong("revision"))
private fun parseWorkspace(o:JSONObject):ProjectWorkspaceUiModel{val instruction=o.optJSONObject("activeInstruction")?.optString("body")?.takeIf{it.isNotBlank()};val recommendations=o.optJSONArray("recommendations")?.objects()?.mapNotNull{it.optString("action").takeIf(String::isNotBlank)}.orEmpty();val carry=o.optJSONObject("contextCarry");return ProjectWorkspaceUiModel(parseProject(o.getJSONObject("project")),o.optJSONArray("goals").orEmptyArray().objects().map(::parseGoal),o.optJSONArray("recentChats").orEmptyArray().objects().map(::parseConversation),o.optInt("sourceCount"),o.optInt("noteCount"),o.optInt("assessmentCount"),o.optInt("flashcardCount"),o.optInt("mistakeCount"),o.optInt("practiceCount"),o.optInt("projectMemorySignals"),o.optLong("meaningfulEvents"),instruction,recommendations,carry?.optNullable("topic"),carry?.optNullable("learningMode"),o.optLong("revision"))}
private fun parseChats(a:JSONArray):List<ConversationUiModel> = a.objects().map(::parseConversation)
private fun parseConversation(o:JSONObject)=ConversationUiModel(o.getString("id"),o.optNullable("projectId"),o.optString("scope"),o.optString("title"),o.optString("learningMode","DEFAULT"),o.optBoolean("memoryEnabled",true),o.optBoolean("projectMemoryEnabled",true),o.optBoolean("pinned"),o.optBoolean("archived"),o.optLong("revision"),o.optString("updatedAt"))
private fun parseMessages(a:JSONArray):List<ChatMessageUiModel> = a.objects().map{ChatMessageUiModel(it.getString("id"),it.getString("conversationId"),it.optString("role"),it.optString("state"),it.optString("content"),it.optBoolean("finalMarker"),it.optLong("revision"),it.optString("createdAt"))}
private fun parseCitations(a:JSONArray):List<CitationUiModel> = a.objects().map{val c=it.optJSONObject("citation")?:it;CitationUiModel(it.optInt("index"),c.optString("sourceId"),c.optString("excerpt"),if(c.has("page")&&!c.isNull("page"))c.optInt("page") else null,c.optNullable("section"),c.optDouble("relevance",0.0))}
private fun parseSources(a:JSONArray):List<SourceUiModel> = a.objects().map(::parseSource)
private fun parseSource(o:JSONObject)=SourceUiModel(o.getString("id"),o.optString("title"),o.optString("type"),o.optString("mimeType"),o.optString("state"),o.optInt("processingProgress"),o.optLong("revision"))
private fun parseSearch(a:JSONArray):List<SearchUiModel> = a.objects().map{SearchUiModel(it.optString("type"),it.optString("id"),it.optString("title"),it.optString("snippet"),it.optNullable("projectId"),it.optString("deepLink"),it.optDouble("score"))}
private fun parseActivityResponse(o:JSONObject):List<ActivityUiModel> = o.optJSONArray("items").orEmptyArray().objects().map{ActivityUiModel(it.optString("eventId"),it.optString("type"),it.optString("occurredAt"),it.optNullable("projectId"),it.optNullable("objectId")?:it.optNullable("entityId"),it.optBoolean("meaningful"),it.optNullable("deepLink"))}
private fun parseAssessment(o:JSONObject):AssessmentDetailUiModel{val a=o.optJSONObject("assessment")?:o;return AssessmentDetailUiModel(a.getString("id"),a.optString("kind"),a.optString("title"),a.optNullable("projectId"),a.optString("state"),a.optInt("questionCount"),a.optLong("revision"),o.optJSONArray("questions").orEmptyArray().objects().map{AssessmentQuestionUiModel(it.getString("id"),it.optInt("position"),it.optString("prompt"),it.optString("type"),it.optJSONArray("options").stringValues())})}
private fun parseAttempt(o:JSONObject)=AttemptUiModel(o.getString("id"),o.getString("assessmentId"),o.optNullable("projectId"),o.optString("state"),if(o.has("score")&&!o.isNull("score"))o.optDouble("score") else null,o.optLong("revision"))
private fun parseAssessmentResult(o:JSONObject):AssessmentResultUiModel{val attempt=parseAttempt(o.optJSONObject("attempt")?:o);return AssessmentResultUiModel(attempt,o.optDouble("score",attempt.score?:0.0),o.optDouble("accuracy"),o.optJSONArray("incorrectQuestionIds").stringValues(),o.optJSONArray("mistakeIds").stringValues())}
private fun parsePractice(o:JSONObject):PracticeSessionUiModel{val s=o.optJSONObject("session")?:o;return PracticeSessionUiModel(s.getString("id"),s.optNullable("projectId"),s.optNullable("focusTopic"),s.optString("state"),s.optLong("revision"),o.optInt("currentPosition",s.optInt("currentPosition")),o.optInt("targetItemCount",s.optInt("targetItemCount")),o.optInt("difficulty",s.optInt("difficulty")),o.optBoolean("adaptive",s.optBoolean("adaptive")),o.optString("hintPolicy",s.optString("hintPolicy")),o.optString("revealPolicy",s.optString("revealPolicy")),o.optJSONArray("items").orEmptyArray().objects().map(::parsePracticeItem),o.optString("summaryJson",s.optString("summaryJson")))}
private fun parsePracticeItem(o:JSONObject)=PracticeItemUiModel(o.getString("id"),o.optInt("position"),o.optString("prompt"),o.optString("itemType"),o.optString("state"),o.optInt("difficulty"),o.optNullable("topic"),o.optNullable("userAnswer"),o.optLong("revision"))
private fun parsePracticeCheck(o:JSONObject)=PracticeCheckUiModel(parsePracticeItem(o.getJSONObject("item")),o.optBoolean("correct"),o.optDouble("score"),o.optString("explanation"),o.optNullable("nextItemId"),o.optNullable("mistakeId"))
private fun parsePracticeComplete(o:JSONObject)=PracticeCompleteUiModel(o.optInt("answered"),o.optInt("correct"),o.optDouble("accuracy"),o.optString("summaryJson"))
private fun parseFlashcards(a:JSONArray):List<FlashcardUiModel> = a.objects().map(::parseFlashcard)
private fun parseFlashcard(o:JSONObject)=FlashcardUiModel(o.getString("id"),o.getString("deckId"),o.optNullable("projectId"),o.optString("front"),o.optString("back"),o.optNullable("explanation"),o.optString("dueAt"),o.optInt("intervalDays"),o.optInt("repetitions"),o.optInt("lapses"),o.optLong("revision"))
private fun parseMistakes(a:JSONArray):List<MistakeUiModel> = a.objects().map{MistakeUiModel(it.getString("id"),it.optNullable("projectId"),it.optNullable("sourceId"),it.optString("topic"),it.optString("prompt"),it.optNullable("userAnswer"),it.optNullable("expectedAnswer"),it.optInt("occurrenceCount"),it.optString("status"),it.optLong("revision"))}
private fun parseStore(o:JSONObject):StoreCatalogUiModel{val catalog=o.optJSONObject("catalog")?:o;val items=o.optJSONArray("items")?:catalog.optJSONArray("items");return StoreCatalogUiModel(o.optString("catalogVersion",catalog.optString("catalogVersion")),o.optLong("coinBalance",catalog.optLong("coinBalance")),items.orEmptyArray().objects().map{StoreItemUiModel(it.optString("itemId"),it.optString("itemType"),it.optLong("priceCoins"),it.optBoolean("owned"),it.optBoolean("available"),it.optString("requirements"),it.optString("metadata"))})}
private fun parseInventory(a:JSONArray):List<InventoryItemUiModel> = a.objects().map{InventoryItemUiModel(it.optString("itemId"),it.optString("type"),it.optString("ownershipSource"),it.optString("acquiredAt"),it.optLong("quantity"),it.optString("metadata"),it.optLong("revision"))}
private fun parseAvatars(a:JSONArray):List<AvatarCatalogUiModel> = a.objects().map{AvatarCatalogUiModel(it.optString("avatarId"),it.optString("permanentName").ifBlank{it.optString("avatarId")},it.optString("assetKey"),it.optString("tier"),it.optBoolean("owned"),it.optBoolean("equipped"),if(it.has("storePrice")&&!it.isNull("storePrice"))it.optLong("storePrice") else null,it.optString("catalogVersion"),it.optString("identityMetadataJson"))}
private fun parseMap(o:JSONObject):PersonalMapUiModel{val e=o.optJSONObject("eligibility")?:JSONObject();return PersonalMapUiModel(o.optNullable("mapId"),o.optString("mapDefinitionId"),o.optInt("mapVersion"),o.optString("state"),e.optBoolean("eligible"),e.optInt("levelRequirement"),e.optString("memoryRequirement"),e.optBoolean("levelSatisfied"),e.optBoolean("memorySatisfied"),e.optString("unlockState"),o.optJSONArray("units").orEmptyArray().objects().map{MapUnitUiModel(it.optString("unitId"),it.optInt("ordinal"),it.optString("semanticKey"),it.optString("titleKey"),it.optString("state"),it.optLong("progress"),it.optLong("requiredProgress"),it.optLong("revision"))},o.optLong("revision"))}
private fun parseGameProfile(o:JSONObject):GameProfileUiModel{val a=o.optJSONObject("equippedAvatar")?:JSONObject();return GameProfileUiModel(o.optInt("level"),o.optLong("lifetimeXp"),o.optLong("currentLevelXp"),o.optLong("nextLevelXp"),o.optLong("coinBalance"),o.optInt("currentConsistency"),a.optString("avatarId"),a.optString("assetKey"),a.optString("tier"),a.optLong("revision",o.optLong("revision")),o.optString("mapState"),o.optLong("revision"))}

private fun parseArrayEnvelope(text:String):JSONArray{val trimmed=text.trim();if(trimmed.startsWith("["))return JSONArray(trimmed);val o=JSONObject(trimmed);for(key in listOf("items","results","projects","chats","sources","cards","mistakes","inventory","avatars")){o.optJSONArray(key)?.let{return it}};return JSONArray()}
private fun JSONArray?.orEmptyArray():JSONArray = this?:JSONArray()
private fun JSONArray?.stringValues():List<String> = if(this==null)emptyList() else List(length()){index->optString(index)}
private fun JSONArray.objects():List<JSONObject> = List(length()){index->getJSONObject(index)}
private fun JSONObject.optNullable(key:String):String? = if(!has(key)||isNull(key))null else optString(key).takeIf{it.isNotBlank()}
private fun maxRevision(a:JSONArray):Long = a.objects().maxOfOrNull{it.optLong("revision",0)}?:0L
