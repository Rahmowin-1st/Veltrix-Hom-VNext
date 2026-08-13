package com.veltrix.hom.vnext

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ApiSession(val accountId:String,val token:String)
data class ApiProject(val id:String,val title:String,val revision:Long)

/** Minimal typed transport used by Part 1 repositories/tests. Production base URL is build-configured. */
class VeltrixApiClient(private val baseUrl:String = BuildConfig.VELTRIX_API_BASE_URL) {
    fun health():Boolean = request("GET","/health",null,null).first==200
    fun register(login:String,password:String,displayName:String):ApiSession {
        val body=JSONObject().put("login",login).put("password",password).put("displayName",displayName).toString()
        val (code,text)=request("POST","/v1/auth/register",null,body);require(code==201){"register HTTP $code"};val o=JSONObject(text);return ApiSession(o.getString("accountId"),o.getString("sessionToken"))
    }
    fun createProject(token:String,title:String,purpose:String):ApiProject {
        val body=JSONObject().put("title",title).put("purpose",purpose).toString();val(code,text)=request("POST","/v1/projects",token,body);require(code==201){"project HTTP $code $text"};return project(JSONObject(text))
    }
    fun getProject(token:String,id:String):ApiProject {val(code,text)=request("GET","/v1/projects/$id",token,null);require(code==200){"get project HTTP $code"};return project(JSONObject(text))}
    fun syncProjectUpsert(token:String,mutationId:String,projectId:String,idempotencyKey:String,title:String):JSONObject {
        val m=JSONObject().put("mutationId",mutationId).put("entityType","PROJECT").put("entityId",projectId).put("operation","UPSERT").put("idempotencyKey",idempotencyKey).put("payload",JSONObject().put("title",title).put("status","ACTIVE"))
        val body=JSONObject().put("mutations",org.json.JSONArray().put(m)).toString();val(code,text)=request("POST","/v1/sync/mutations",token,body);require(code==200){"sync HTTP $code $text"};return JSONObject(text).getJSONArray("results").getJSONObject(0)
    }
    private fun project(o:JSONObject)=ApiProject(o.getString("id"),o.getString("title"),o.getLong("revision"))
    internal fun request(method:String,path:String,token:String?,body:String?):Pair<Int,String>{
        val c=(URL(baseUrl.trimEnd('/')+path).openConnection() as HttpURLConnection).apply{requestMethod=method;connectTimeout=7000;readTimeout=30000;setRequestProperty("Accept","application/json");setRequestProperty("X-Request-ID","android-${UUID.randomUUID()}");token?.let{setRequestProperty("Authorization","Bearer $it")};if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json")}}
        if(body!=null)c.outputStream.bufferedWriter().use{it.write(body)};val code=c.responseCode;val stream=if(code in 200..299)c.inputStream else c.errorStream;val text=stream?.bufferedReader()?.use{it.readText()}?:"";c.disconnect();return code to text
    }
}
