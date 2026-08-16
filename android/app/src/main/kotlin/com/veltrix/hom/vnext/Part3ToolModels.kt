package com.veltrix.hom.vnext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Frontend-facing Part 3 control/tool models. Backend values remain authoritative. */
data class CalculatorResultUiModel(val expression:String,val result:String,val deterministic:Boolean)
data class TranslationUiModel(val sourceText:String,val translatedText:String,val sourceLanguage:String?,val targetLanguage:String,val provider:String,val live:Boolean,val createdAt:String)
data class NotificationIntentUiModel(val id:String,val projectId:String?,val category:String,val payload:String,val scheduledFor:String?,val status:String,val createdAt:String)
data class NotificationPreferenceUiModel(val category:String,val enabled:Boolean,val quietHoursJson:String,val timezone:String,val revision:Long,val updatedAt:String)
data class SettingUiModel(val category:String,val key:String,val jsonValue:String,val revision:Long,val updatedAt:String)
data class ProfileUiModel(val accountId:String,val displayName:String,val username:String?,val preferredLanguage:String,val timezone:String,val onboardingComplete:Boolean,val memoryEnabled:Boolean,val revision:Long)
data class AccountExportUiModel(val accountId:String,val generatedAt:String,val displayName:String,val preferredLanguage:String,val timezone:String,val memoryEnabled:Boolean,val entityCounts:Map<String,Long>)

class Part3ControlRepository(private val api:VeltrixApiClient=VeltrixApiClient()) {
    suspend fun calculate(session:ApiSession, expression:String): RepositoryState<CalculatorResultUiModel> = withContext(Dispatchers.IO) {
        try {
            val body=JSONObject().put("toolId","calculator.basic").put("input",JSONObject().put("expression",expression.trim())).toString()
            val (_,text)=expectP3(api.request("POST","/v1/tools/invoke",session.token,body))
            val o=JSONObject(text); val output=o.optJSONObject("output") ?: JSONObject()
            RepositoryState(CalculatorResultUiModel(expression.trim(),output.optString("result"),o.optBoolean("deterministic",true)),DataFreshness.FRESH)
        } catch(e:BackendUiException) { RepositoryState(null,DataFreshness.STALE,errorCode=e.code,retryable=e.retryable) }
          catch(_:Throwable) { RepositoryState(null,DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true) }
    }

    suspend fun translate(session:ApiSession,text:String,target:String,source:String?,projectId:String?):RepositoryState<TranslationUiModel> = withContext(Dispatchers.IO) {
        try {
            val body=JSONObject().put("text",text).put("targetLanguage",target.trim()).apply {
                source?.trim()?.takeIf{it.isNotBlank()}?.let{put("sourceLanguage",it)}
                projectId?.let{put("projectId",it)}
            }.toString()
            val (_,raw)=expectP3(api.request("POST","/v1/translate",session.token,body))
            val o=JSONObject(raw)
            RepositoryState(TranslationUiModel(text,o.optString("translatedText"),o.optNullableStringP3("sourceLanguage"),o.optString("targetLanguage"),o.optString("provider"),o.optBoolean("live"),o.optString("createdAt")),DataFreshness.FRESH)
        } catch(e:BackendUiException) { RepositoryState(null,DataFreshness.STALE,errorCode=e.code,retryable=e.retryable) }
          catch(_:Throwable) { RepositoryState(null,DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true) }
    }

    suspend fun profile(session:ApiSession):RepositoryState<ProfileUiModel> = getObject(session,"/v1/profile",::parseProfileP3)
    suspend fun settings(session:ApiSession,category:String?=null):RepositoryState<List<SettingUiModel>> = withContext(Dispatchers.IO) {
        try {
            val path="/v1/settings" + (category?.takeIf{it.isNotBlank()}?.let{"?category=${java.net.URLEncoder.encode(it,"UTF-8")}"} ?: "")
            val (_,raw)=expectP3(api.request("GET",path,session.token,null)); val a=JSONArray(raw)
            RepositoryState(List(a.length()){i->parseSettingP3(a.getJSONObject(i))},DataFreshness.FRESH)
        } catch(e:BackendUiException) { RepositoryState(null,DataFreshness.STALE,errorCode=e.code,retryable=e.retryable) }
          catch(_:Throwable) { RepositoryState(null,DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true) }
    }
    suspend fun notificationIntents(session:ApiSession):RepositoryState<List<NotificationIntentUiModel>> = withContext(Dispatchers.IO) {
        try { val(_,raw)=expectP3(api.request("GET","/v1/notifications/intents?limit=100",session.token,null)); val a=JSONArray(raw); RepositoryState(List(a.length()){i->parseNotificationIntentP3(a.getJSONObject(i))},DataFreshness.FRESH) }
        catch(e:BackendUiException){RepositoryState(null,DataFreshness.STALE,errorCode=e.code,retryable=e.retryable)} catch(_:Throwable){RepositoryState(null,DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true)}
    }
    suspend fun notificationPreferences(session:ApiSession):RepositoryState<List<NotificationPreferenceUiModel>> = withContext(Dispatchers.IO) {
        try { val(_,raw)=expectP3(api.request("GET","/v1/notifications/preferences",session.token,null)); val a=JSONArray(raw); RepositoryState(List(a.length()){i->parseNotificationPreferenceP3(a.getJSONObject(i))},DataFreshness.FRESH) }
        catch(e:BackendUiException){RepositoryState(null,DataFreshness.STALE,errorCode=e.code,retryable=e.retryable)} catch(_:Throwable){RepositoryState(null,DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true)}
    }

    suspend fun updateProfile(session:ApiSession,current:ProfileUiModel,displayName:String,language:String,timezone:String,memoryEnabled:Boolean):ProfileUiModel = withContext(Dispatchers.IO) {
        val body=JSONObject().put("displayName",displayName.trim()).put("preferredLanguage",language.trim()).put("timezone",timezone.trim()).put("memoryEnabled",memoryEnabled).put("expectedRevision",current.revision).toString()
        val(_,raw)=expectP3(api.request("PATCH","/v1/profile",session.token,body)); parseProfileP3(JSONObject(raw))
    }
    suspend fun putSetting(session:ApiSession,category:String,key:String,jsonValue:String):SettingUiModel = withContext(Dispatchers.IO) {
        val body=JSONObject().put("category",category).put("key",key).put("jsonValue",jsonValue).toString(); val(_,raw)=expectP3(api.request("PUT","/v1/settings",session.token,body)); parseSettingP3(JSONObject(raw))
    }
    suspend fun putNotificationPreference(session:ApiSession,current:NotificationPreferenceUiModel,enabled:Boolean):NotificationPreferenceUiModel = withContext(Dispatchers.IO) {
        val body=JSONObject().put("category",current.category).put("enabled",enabled).put("quietHoursJson",current.quietHoursJson).put("timezone",current.timezone).toString(); val(_,raw)=expectP3(api.request("PUT","/v1/notifications/preferences",session.token,body)); parseNotificationPreferenceP3(JSONObject(raw))
    }
    suspend fun accountExport(session:ApiSession):RepositoryState<AccountExportUiModel> = getObject(session,"/v1/account/export",::parseAccountExportP3)
    suspend fun requestAccountDeletion(session:ApiSession,password:String):MutationFeedback = withContext(Dispatchers.IO) {
        try { val body=JSONObject().put("password",password).put("confirmation","DELETE").toString(); expectP3(api.request("POST","/v1/account/delete",session.token,body)); MutationFeedback(true) }
        catch(e:BackendUiException){MutationFeedback(false,e.code,e.detail,e.retryable)} catch(t:Throwable){MutationFeedback(false,"OFFLINE",t.message,true)}
    }

    private suspend fun <T> getObject(session:ApiSession,path:String,parse:(JSONObject)->T):RepositoryState<T> = withContext(Dispatchers.IO) {
        try { val(_,raw)=expectP3(api.request("GET",path,session.token,null)); RepositoryState(parse(JSONObject(raw)),DataFreshness.FRESH) }
        catch(e:BackendUiException){RepositoryState(null,DataFreshness.STALE,errorCode=e.code,retryable=e.retryable)} catch(_:Throwable){RepositoryState(null,DataFreshness.OFFLINE,errorCode="OFFLINE",retryable=true)}
    }
}

private fun expectP3(response:Pair<Int,String>, accepted:Set<Int> = setOf(200)):Pair<Int,String> {
    if(response.first in accepted) return response
    val root=runCatching{JSONObject(response.second)}.getOrNull(); val error=root?.optJSONObject("error") ?: root
    val code=error?.optString("code")?.takeIf{it.isNotBlank()} ?: "HTTP_${response.first}"
    val detail=error?.optString("message")?.takeIf{it.isNotBlank()} ?: error?.optString("detail")?.takeIf{it.isNotBlank()} ?: "Request failed"
    throw BackendUiException(response.first,code,response.first in setOf(408,409,425,429,500,502,503,504),detail)
}
private fun parseProfileP3(o:JSONObject)=ProfileUiModel(o.optString("accountId"),o.optString("displayName"),o.optNullableStringP3("username"),o.optString("preferredLanguage"),o.optString("timezone"),o.optBoolean("onboardingComplete"),o.optBoolean("memoryEnabled"),o.optLong("revision"))
private fun parseSettingP3(o:JSONObject)=SettingUiModel(o.optString("category"),o.optString("key"),o.optString("jsonValue"),o.optLong("revision"),o.optString("updatedAt"))
private fun parseNotificationIntentP3(o:JSONObject)=NotificationIntentUiModel(o.optString("id"),o.optNullableStringP3("projectId"),o.optString("category"),o.optString("payloadJson"),o.optNullableStringP3("scheduledFor"),o.optString("status"),o.optString("createdAt"))
private fun parseNotificationPreferenceP3(o:JSONObject)=NotificationPreferenceUiModel(o.optString("category"),o.optBoolean("enabled"),o.optString("quietHoursJson","{}"),o.optString("timezone","UTC"),o.optLong("revision"),o.optString("updatedAt"))
private fun parseAccountExportP3(o:JSONObject):AccountExportUiModel { val p=o.optJSONObject("profile")?:JSONObject(); val c=o.optJSONObject("entityCounts")?:JSONObject(); val counts=buildMap { c.keys().forEach { k->put(k,c.optLong(k)) } }; return AccountExportUiModel(o.optString("accountId"),o.optString("generatedAt"),p.optString("displayName"),p.optString("preferredLanguage"),p.optString("timezone"),p.optBoolean("memoryEnabled"),counts) }
private fun JSONObject.optNullableStringP3(key:String):String?=if(!has(key)||isNull(key))null else optString(key).takeIf{it.isNotBlank()}
