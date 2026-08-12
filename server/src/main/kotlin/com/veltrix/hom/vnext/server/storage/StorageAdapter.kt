package com.veltrix.hom.vnext.server.storage

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class StoredObject(
    val provider: String,
    val key: String,
    val etag: String?,
    val size: Long,
    val mimeType: String,
    val sha256: String,
    val version: String? = null,
)

data class ObjectHead(val size:Long,val mimeType:String?,val etag:String?,val sha256:String?)

interface StorageAdapter {
    val id: String
    val productionStyle: Boolean
    fun isConfigured(): Boolean
    fun put(key:String,input:InputStream,size:Long,mimeType:String,sha256:String):StoredObject
    fun open(key:String):InputStream
    fun delete(key:String)
    fun head(key:String):ObjectHead?
    fun signedReadUrl(key:String,ttlSeconds:Long=300):String?
    fun putMultipart(key:String,input:InputStream,size:Long,mimeType:String,sha256:String):StoredObject = put(key,input,size,mimeType,sha256)
}

object StorageKeys {
    private val segment=Regex("[A-Za-z0-9._-]{1,180}")
    fun source(accountId:String,sourceId:String,sha256:String,fileName:String):String {
        require(accountId.matches(Regex("[0-9a-fA-F-]{36}")))
        require(sourceId.matches(Regex("[0-9a-fA-F-]{36}")))
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        val safe=fileName.replace(Regex("[^A-Za-z0-9._-]"),"_").take(180).ifBlank{"source.bin"}
        return "accounts/$accountId/sources/$sourceId/$sha256-$safe"
    }
    fun validate(key:String):String {
        if(key.startsWith("/") || key.contains("..") || key.contains('\\') || key.split('/').any{it.isBlank()||!segment.matches(it)})
            throw DomainException(DomainError("STORAGE",ErrorCategory.STORAGE,"Unsafe storage key"))
        return key
    }
}

class LocalStorageAdapter(rootPath:String):StorageAdapter {
    override val id="local"
    override val productionStyle=false
    private val root=File(rootPath).canonicalFile.also{it.mkdirs()}
    override fun isConfigured()=true
    override fun put(key:String,input:InputStream,size:Long,mimeType:String,sha256:String):StoredObject {
        val f=safe(key);f.parentFile.mkdirs();val tmp=File.createTempFile("upload-",".tmp",f.parentFile)
        input.use{src->tmp.outputStream().use{dst->src.copyTo(dst)}}
        if(tmp.length()!=size) { tmp.delete();throw DomainException(DomainError("STORAGE",ErrorCategory.STORAGE,"Stored size mismatch",true)) }
        val actual=sha256(tmp.inputStream()); if(actual!=sha256){tmp.delete();throw DomainException(DomainError("STORAGE",ErrorCategory.STORAGE,"Stored hash mismatch"))}
        Files.move(tmp.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
        return StoredObject(id,key,null,size,mimeType,sha256)
    }
    override fun open(key:String):InputStream { val f=safe(key);if(!f.isFile)throw DomainException(DomainError("STORAGE",ErrorCategory.NOT_FOUND,"Stored object not found"));return f.inputStream().buffered() }
    override fun delete(key:String){safe(key).delete()}
    override fun head(key:String):ObjectHead?=safe(key).takeIf{it.isFile}?.let{ObjectHead(it.length(),null,null,sha256(it.inputStream()))}
    override fun signedReadUrl(key:String,ttlSeconds:Long)=null
    private fun safe(key:String):File{StorageKeys.validate(key);val f=File(root,key).canonicalFile;if(!f.path.startsWith(root.path+File.separator))throw DomainException(DomainError("STORAGE",ErrorCategory.STORAGE,"Unsafe storage key"));return f}
}

/** Minimal AWS Signature V4 S3-compatible adapter, verified in CI against MinIO. */
class S3CompatibleStorageAdapter(
    private val endpoint:String,
    private val region:String,
    private val bucket:String,
    private val accessKey:String?,
    private val secretKey:String?,
    private val pathStyle:Boolean=true,
    private val clock:Clock=Clock.systemUTC(),
):StorageAdapter{
    override val id="s3-compatible"
    override val productionStyle=true
    private val client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    override fun isConfigured()=!accessKey.isNullOrBlank()&&!secretKey.isNullOrBlank()&&(endpoint.startsWith("http://")||endpoint.startsWith("https://"))

    override fun put(key:String,input:InputStream,size:Long,mimeType:String,sha256:String):StoredObject{
        ensure();StorageKeys.validate(key);val bytes=input.use{it.readBytes()};if(bytes.size.toLong()!=size)throw storage("Stored size mismatch")
        if(sha256(bytes)!=sha256)throw storage("Stored hash mismatch")
        val headers=linkedMapOf("content-type" to mimeType,"x-amz-meta-sha256" to sha256)
        val response=execute("PUT",key,headers,bytes)
        if(response.statusCode() !in 200..299)throw storage("Object storage PUT failed",response.statusCode()>=500)
        return StoredObject(id,key,response.headers().firstValue("etag").orElse(null)?.trim('"'),size,mimeType,sha256,response.headers().firstValue("x-amz-version-id").orElse(null))
    }
    override fun open(key:String):InputStream{ensure();StorageKeys.validate(key);val r=execute("GET",key,emptyMap(),null);if(r.statusCode()==404)throw DomainException(DomainError("STORAGE",ErrorCategory.NOT_FOUND,"Stored object not found"));if(r.statusCode() !in 200..299)throw storage("Object storage GET failed",r.statusCode()>=500);return ByteArrayInputStream(r.body())}
    override fun delete(key:String){ensure();StorageKeys.validate(key);val r=execute("DELETE",key,emptyMap(),ByteArray(0));if(r.statusCode() !in 200..299 && r.statusCode()!=404)throw storage("Object storage DELETE failed",r.statusCode()>=500)}
    override fun head(key:String):ObjectHead?{ensure();StorageKeys.validate(key);val r=execute("HEAD",key,emptyMap(),null);if(r.statusCode()==404)return null;if(r.statusCode() !in 200..299)throw storage("Object storage HEAD failed",r.statusCode()>=500);return ObjectHead(r.headers().firstValue("content-length").orElse("0").toLongOrNull()?:0,r.headers().firstValue("content-type").orElse(null),r.headers().firstValue("etag").orElse(null)?.trim('"'),r.headers().firstValue("x-amz-meta-sha256").orElse(null))}

    override fun signedReadUrl(key:String,ttlSeconds:Long):String{
        ensure();StorageKeys.validate(key);val ttl=ttlSeconds.coerceIn(30,3600);val now=clock.instant();val amzDate=AMZ.format(now);val date=DATE.format(now);val host=URI.create(endpoint).host + (URI.create(endpoint).port.takeIf{it>0}?.let{":$it"}?:"")
        val canonicalUri=objectPath(key);val scope="$date/$region/s3/aws4_request";val credential=uri("$accessKey/$scope")
        val params=sortedMapOf("X-Amz-Algorithm" to "AWS4-HMAC-SHA256","X-Amz-Credential" to credential,"X-Amz-Date" to amzDate,"X-Amz-Expires" to ttl.toString(),"X-Amz-SignedHeaders" to "host")
        val canonicalQuery=params.entries.joinToString("&"){"${uri(it.key)}=${it.value}"}
        val canonicalRequest="GET\n$canonicalUri\n$canonicalQuery\nhost:$host\n\nhost\nUNSIGNED-PAYLOAD";val toSign="AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256(canonicalRequest.toByteArray())}"
        val sig=hex(hmac(signingKey(date),toSign.toByteArray()));return endpoint.trimEnd('/')+canonicalUri+"?"+canonicalQuery+"&X-Amz-Signature=$sig"
    }

    private fun execute(method:String,key:String,headers:Map<String,String>,body:ByteArray?):HttpResponse<ByteArray>{
        val now=clock.instant();val amzDate=AMZ.format(now);val date=DATE.format(now);val uri=URI.create(endpoint.trimEnd('/')+objectPath(key));val host=uri.host+(uri.port.takeIf{it>0}?.let{":$it"}?:"");val payloadHash=sha256(body?:ByteArray(0))
        val all=sortedMapOf<String,String>();all["host"]=host;all["x-amz-content-sha256"]=payloadHash;all["x-amz-date"]=amzDate;headers.forEach{(k,v)->all[k.lowercase()]=v.trim()}
        val canonicalHeaders=all.entries.joinToString(""){"${it.key}:${it.value}\n"};val signedHeaders=all.keys.joinToString(";");val canonicalRequest="$method\n${objectPath(key)}\n\n$canonicalHeaders\n$signedHeaders\n$payloadHash";val scope="$date/$region/s3/aws4_request";val toSign="AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256(canonicalRequest.toByteArray())}";val signature=hex(hmac(signingKey(date),toSign.toByteArray()));val auth="AWS4-HMAC-SHA256 Credential=$accessKey/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
        val builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45)).header("Authorization",auth);all.filterKeys{it!="host"}.forEach{(k,v)->builder.header(k,v)}
        val publisher=if(body==null)HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofByteArray(body);builder.method(method,publisher)
        return try{client.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray())}catch(_:Exception){throw storage("Object storage unavailable",true)}
    }
    private fun objectPath(key:String):String{val encoded=key.split('/').joinToString("/"){uri(it)};return if(pathStyle)"/${uri(bucket)}/$encoded" else "/$encoded"}
    private fun signingKey(date:String):ByteArray{val kDate=hmac(("AWS4"+secretKey!!).toByteArray(),date.toByteArray());val kRegion=hmac(kDate,region.toByteArray());val kService=hmac(kRegion,"s3".toByteArray());return hmac(kService,"aws4_request".toByteArray())}
    private fun ensure(){if(!isConfigured())throw storage("S3-compatible storage is not configured")}
    private fun storage(message:String,retryable:Boolean=false)=DomainException(DomainError("STORAGE",ErrorCategory.STORAGE,message,retryable))
    private fun uri(v:String)=URLEncoder.encode(v,StandardCharsets.UTF_8).replace("+","%20").replace("%7E","~")
    companion object {private val AMZ=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);private val DATE=DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)}
}

private fun hmac(key:ByteArray,data:ByteArray):ByteArray=Mac.getInstance("HmacSHA256").run{init(SecretKeySpec(key,"HmacSHA256"));doFinal(data)}
private fun sha256(bytes:ByteArray):String=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
private fun sha256(input:InputStream):String=input.use{stream->val d=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536);while(true){val n=stream.read(b);if(n<0)break;if(n>0)d.update(b,0,n)};d.digest().joinToString(""){"%02x".format(it)}}
private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it)}
