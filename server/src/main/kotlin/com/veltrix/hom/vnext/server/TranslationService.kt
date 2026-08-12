package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

class TranslationService(private val config:ServerConfig, private val db:Database) {
    private val client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun translate(accountId:String, req:TranslationRequest):TranslationResponse {
        val text=req.text.trim(); if(text.isEmpty()||text.length>100_000)throw validation("Translation text must be 1..100000 chars")
        val target=req.targetLanguage.trim().lowercase().also{if(!it.matches(Regex("[a-z]{2,3}(-[a-z0-9]{2,8})?")))throw validation("Invalid target language")}
        val source=req.sourceLanguage?.trim()?.lowercase()?.takeIf{it.isNotEmpty()}
        req.projectId?.let{projectOwned(accountId,it)}
        val translated:Triple<String,String,Boolean> = when {
            config.environment=="test" && config.testTranslationEnabled -> Triple("[TEST:$target] $text","MOCK_TEST_ONLY",false)
            config.translationUrl!=null -> Triple(callGateway(text,source,target),"CONFIGURED_TRANSLATION_GATEWAY",true)
            else -> throw DomainException(DomainError("TRANSLATION_FAILED",ErrorCategory.AI_PROVIDER,"Translation provider is not configured in this environment",true))
        }
        persist(accountId,req.projectId,source,target,text,translated.first,translated.second)
        return TranslationResponse(translated.first,source,target,translated.second,translated.third,Instant.now().toString())
    }

    private fun callGateway(text:String,source:String?,target:String):String {
        val url=config.translationUrl ?: throw IllegalStateException()
        if(!url.startsWith("https://") && config.environment!="development")throw DomainException(DomainError("TRANSLATION_FAILED",ErrorCategory.VALIDATION,"Translation gateway must use HTTPS outside development"))
        val body=buildString {
            append("{\"q\":\"").append(json(text)).append("\",\"source\":\"").append(json(source?:"auto"))
            append("\",\"target\":\"").append(json(target)).append("\",\"format\":\"text\"")
            config.translationApiKey?.let{append(",\"api_key\":\"").append(json(it)).append("\"")}
            append("}")
        }
        val request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response=try{client.send(request,HttpResponse.BodyHandlers.ofString())}catch(_:Exception){throw DomainException(DomainError("TRANSLATION_FAILED",ErrorCategory.NETWORK_UPSTREAM,"Translation provider unavailable",true))}
        if(response.statusCode() !in 200..299)throw DomainException(DomainError("TRANSLATION_FAILED",ErrorCategory.AI_PROVIDER,"Translation provider returned ${response.statusCode()}",response.statusCode()>=500))
        val match=Regex("\\\"translatedText\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(response.body())
            ?: throw DomainException(DomainError("TRANSLATION_FAILED",ErrorCategory.AI_PROVIDER,"Translation provider response is invalid"))
        return unjson(match.groupValues[1])
    }
    private fun projectOwned(a:String,p:String){db.tx{c->c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,p);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}}}}
    private fun persist(a:String,p:String?,source:String?,target:String,input:String,output:String,provider:String){db.tx{c->c.prepareStatement("INSERT INTO translation_record(account_id,project_id,source_language,target_language,source_text,translated_text,provider) VALUES (?::uuid,?::uuid,?,?,?,?,?)").use{ps->ps.setString(1,a);ps.setString(2,p);ps.setString(3,source);ps.setString(4,target);ps.setString(5,input);ps.setString(6,output);ps.setString(7,provider);ps.executeUpdate()}}}
    private fun json(s:String)=s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")
    private fun unjson(s:String)=s.replace("\\n","\n").replace("\\r","\r").replace("\\\"","\"").replace("\\\\","\\")
}
