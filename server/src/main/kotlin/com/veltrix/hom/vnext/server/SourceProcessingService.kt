package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.*
import com.veltrix.hom.vnext.server.rag.EmbeddingFactory
import com.veltrix.hom.vnext.server.rag.HybridRetrievalRepository
import com.veltrix.hom.vnext.server.storage.StorageAdapter
import com.veltrix.hom.vnext.server.storage.StorageFactory
import com.veltrix.hom.vnext.server.storage.StorageKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.tika.Tika
import java.io.Closeable
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SourceProcessingService(
    private val config: ServerConfig,
    private val db: Database,
    private val sources: SourceRepository,
    val storage: StorageAdapter = StorageFactory.create(config),
    val rag: HybridRetrievalRepository = HybridRetrievalRepository(db, EmbeddingFactory.create(config)),
) : Closeable {
    companion object {
        const val MAX_FILE_BYTES: Long = 50L * 1024 * 1024
        val ALLOWED_MIME = setOf(
            "text/plain", "text/markdown", "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg", "image/png", "image/webp"
        )
    }
    private val tika = Tika().apply { setMaxStringLength(10_000_000) }
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r,"veltrix-source-worker").apply{isDaemon=true} }
        .also { if(config.workerEnabled) it.scheduleWithFixedDelay({ runCatching { processOne() } },1,2,TimeUnit.SECONDS) }

    fun enqueueUpload(accountId:String,title:String,type:String,mimeType:String,fileName:String?,tempFile:File):SourceResponse {
        try {
            val size=tempFile.length(); if(size<=0 || size>MAX_FILE_BYTES)throw DomainException(DomainError("SOURCE_TOO_LARGE",ErrorCategory.VALIDATION,"Source file must be 1..${MAX_FILE_BYTES} bytes"))
            if(mimeType !in ALLOWED_MIME)throw DomainException(DomainError("SOURCE_UNSUPPORTED",ErrorCategory.SOURCE_PROCESSING,"Unsupported source MIME type: $mimeType"))
            val hash=sha256File(tempFile)
            val source=sources.createMetadata(accountId,SourceCreateRequest(title,type,mimeType,hash,size))
            if(source.state=="READY") return source
            val safeName=(fileName?:"source.bin").replace(Regex("[^A-Za-z0-9._-]"),"_").take(180).ifBlank{"source.bin"}
            val key=StorageKeys.source(accountId,source.id,hash,safeName)
            val stored=storage.put(key,tempFile.inputStream().buffered(),size,mimeType,hash)
            db.tx{c->
                c.prepareStatement("UPDATE source SET file_name=?,storage_key=?,storage_provider=?,storage_etag=?,object_version=?,storage_metadata=jsonb_build_object('sha256',?,'size',?,'mime',?),state='PROCESSING',processing_progress=10,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,safeName);ps.setString(2,stored.key);ps.setString(3,stored.provider);ps.setString(4,stored.etag);ps.setString(5,stored.version);ps.setString(6,stored.sha256);ps.setLong(7,stored.size);ps.setString(8,stored.mimeType);ps.setString(9,source.id);ps.setString(10,accountId);ps.executeUpdate()}
                c.prepareStatement("INSERT INTO source_processing_job(source_id,account_id,storage_key,mime_type,status) VALUES (?::uuid,?::uuid,?,?,'PENDING') ON CONFLICT(source_id) DO UPDATE SET storage_key=excluded.storage_key,mime_type=excluded.mime_type,status='PENDING',available_at=now(),updated_at=now(),last_error_code=NULL").use{ps->ps.setString(1,source.id);ps.setString(2,accountId);ps.setString(3,key);ps.setString(4,mimeType);ps.executeUpdate()}
            }
            return sources.get(accountId,source.id)
        } finally { if(tempFile.exists())tempFile.delete() }
    }

    fun ingestText(accountId:String,sourceId:String,text:String):SourceResponse = ingestDirectText(accountId,sourceId,text)

    fun ingestDirectText(accountId:String,sourceId:String,text:String):SourceResponse {
        sources.ingestExtractedText(accountId,sourceId,text)
        if(rag.semanticConfigured) rag.indexSource(accountId,sourceId,1) else sources.markProcessingFailure(accountId,sourceId,"PARTIAL",88)
        return sources.get(accountId,sourceId)
    }

    fun retry(accountId:String,sourceId:String):SourceResponse { db.tx{c->
        c.prepareStatement("UPDATE source_processing_job SET status='PENDING',available_at=now(),last_error_code=NULL,updated_at=now() WHERE source_id=?::uuid AND account_id=?::uuid AND status='FAILED'").use{ps->ps.setString(1,sourceId);ps.setString(2,accountId);if(ps.executeUpdate()!=1)throw DomainException(DomainError("SOURCE_PROCESSING_FAILED",ErrorCategory.CONFLICT,"Source has no failed processing job"))}
        c.prepareStatement("UPDATE source SET state='PROCESSING',processing_progress=10,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,sourceId);ps.setString(2,accountId);ps.executeUpdate()}
    };return sources.get(accountId,sourceId)}

    val storageConfigured:Boolean get() = storage.isConfigured()
    val embeddingConfigured:Boolean get() = rag.semanticConfigured

    fun processPendingNow(max:Int=50):Int { var n=0; repeat(max.coerceIn(1,200)){if(!processOne())return n;n++};return n }
    fun hybridSearch(accountId:String,req:HybridSearchRequest):List<HybridSearchHitResponse> = rag.search(accountId,req.query,req.sourceIds,req.projectId,req.limit).map{HybridSearchHitResponse(it.citation,it.lexicalScore,it.semanticScore,it.fusedScore)}
    fun storageHead(accountId:String,sourceId:String):StorageHeadResponse = db.tx{c->
        val row=c.prepareStatement("SELECT storage_provider,storage_key FROM source WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,sourceId);ps.setString(2,accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("SOURCE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Source not found"));rs.getString(1) to rs.getString(2)}}
        val key=row.second ?: throw DomainException(DomainError("STORAGE",ErrorCategory.STORAGE,"Source has no stored object"));val head=storage.head(key) ?: throw DomainException(DomainError("STORAGE",ErrorCategory.NOT_FOUND,"Stored object not found"));StorageHeadResponse(row.first,key,head.size,head.mimeType,head.etag,head.sha256,storage.signedReadUrl(key,config.storageSignedUrlTtlSeconds))
    }

    private fun processOne():Boolean {
        val job=db.tx{c->
            val row=c.prepareStatement("SELECT id,source_id,account_id,storage_key,mime_type FROM source_processing_job WHERE status='PENDING' AND available_at<=now() ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED").use{ps->ps.executeQuery().use{rs->if(!rs.next())null else Job(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5))}}
            row?.also{j->c.prepareStatement("UPDATE source_processing_job SET status='RUNNING',attempts=attempts+1,updated_at=now() WHERE id=?::uuid").use{ps->ps.setString(1,j.id);ps.executeUpdate()};c.prepareStatement("UPDATE source SET state='PROCESSING',processing_progress=30,updated_at=now() WHERE id=?::uuid").use{ps->ps.setString(1,j.sourceId);ps.executeUpdate()}}
            row
        } ?: return false
        try {
            val bytes=storage.open(job.storageKey).use{it.readBytes()}
            if(bytes.isEmpty())throw DomainException(DomainError("STORAGE",ErrorCategory.STORAGE,"Stored source file is empty",true))
            val text=when{
                job.mime.startsWith("image/")->ocrImage(bytes,job.mime)
                else->runCatching{tika.parseToString(bytes.inputStream())}.getOrElse{throw DomainException(DomainError("SOURCE_PROCESSING_FAILED",ErrorCategory.SOURCE_PROCESSING,"Document text extraction failed",true))}
            }.replace("\u0000","").trim()
            if(text.isBlank())throw DomainException(DomainError("SOURCE_PROCESSING_FAILED",ErrorCategory.SOURCE_PROCESSING,"Extracted text is empty"))
            sources.ingestExtractedText(job.accountId,job.sourceId,text)
            if(rag.semanticConfigured){
                rag.indexSource(job.accountId,job.sourceId,1)
                if(!rag.ensureAllChunksIndexed(job.accountId,job.sourceId))throw DomainException(DomainError("SOURCE_PROCESSING_FAILED",ErrorCategory.SOURCE_PROCESSING,"Semantic indexing incomplete",true))
            } else sources.markProcessingFailure(job.accountId,job.sourceId,"PARTIAL",88)
            db.tx{c->c.prepareStatement("UPDATE source_processing_job SET status='SUCCEEDED',updated_at=now(),last_error_code=NULL WHERE id=?::uuid").use{ps->ps.setString(1,job.id);ps.executeUpdate()}}
        } catch(e:DomainException) {
            val attempts=db.tx{c->c.prepareStatement("SELECT attempts FROM source_processing_job WHERE id=?::uuid").use{ps->ps.setString(1,job.id);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}}
            val retry=e.error.retryable && attempts<4
            db.tx{c->c.prepareStatement("UPDATE source_processing_job SET status=?,available_at=now()+(LEAST(120,?*?)*interval '1 second'),last_error_code=?,updated_at=now() WHERE id=?::uuid").use{ps->ps.setString(1,if(retry)"PENDING" else "FAILED");ps.setInt(2,attempts);ps.setInt(3,attempts*5);ps.setString(4,e.error.code);ps.setString(5,job.id);ps.executeUpdate()}}
            if(!retry)runCatching{sources.markProcessingFailure(job.accountId,job.sourceId,if(e.error.code=="SOURCE_UNSUPPORTED")"UNSUPPORTED" else "FAILED")}
        }
        return true
    }

    private fun ocrImage(bytes:ByteArray,mime:String):String {
        if(config.environment=="test" && config.testOcrEnabled) return "TEST_ONLY_OCR extracted text for deterministic CI source"
        val url=config.ocrGatewayUrl ?: throw DomainException(DomainError("OCR_FAILED",ErrorCategory.SOURCE_PROCESSING,"OCR gateway is not configured",true))
        if(!url.startsWith("https://") && config.environment !in setOf("development","test"))throw DomainException(DomainError("OCR_FAILED",ErrorCategory.VALIDATION,"OCR gateway must use HTTPS outside development/test"))
        val request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Content-Type",mime).apply{config.ocrGatewayApiKey?.let{header("Authorization","Bearer $it")}}.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build()
        val response=try{http.send(request,HttpResponse.BodyHandlers.ofString())}catch(_:java.net.http.HttpTimeoutException){throw DomainException(DomainError("OCR_FAILED",ErrorCategory.NETWORK_UPSTREAM,"OCR gateway timed out",true))}catch(_:Exception){throw DomainException(DomainError("OCR_FAILED",ErrorCategory.NETWORK_UPSTREAM,"OCR gateway unavailable",true))}
        if(response.statusCode() !in 200..299)throw DomainException(DomainError("OCR_FAILED",ErrorCategory.SOURCE_PROCESSING,"OCR gateway failed with ${response.statusCode()}",response.statusCode()>=500))
        val obj=runCatching{Json.parseToJsonElement(response.body()).jsonObject}.getOrElse{throw DomainException(DomainError("OCR_FAILED",ErrorCategory.SOURCE_PROCESSING,"OCR gateway response invalid"))}
        return obj["text"]?.jsonPrimitive?.content?.takeIf{it.isNotBlank()} ?: throw DomainException(DomainError("OCR_FAILED",ErrorCategory.SOURCE_PROCESSING,"OCR gateway returned no text"))
    }
    private fun sha256File(file:File):String { val d=MessageDigest.getInstance("SHA-256");file.inputStream().buffered().use{input->val buf=ByteArray(64*1024);while(true){val n=input.read(buf);if(n<0)break;if(n>0)d.update(buf,0,n)}};return d.digest().joinToString(""){"%02x".format(it)} }
    override fun close(){worker.shutdownNow()}
    private data class Job(val id:String,val sourceId:String,val accountId:String,val storageKey:String,val mime:String)
}
