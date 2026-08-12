package com.veltrix.hom.vnext.server.rag

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import kotlin.math.sqrt

interface EmbeddingAdapter {
    val id: String
    val model: String
    val dimensions: Int
    val testOnly: Boolean get() = false
    fun isConfigured(): Boolean
    fun embed(texts: List<String>): List<FloatArray>
}


class DisabledEmbeddingAdapter(override val dimensions:Int=64):EmbeddingAdapter {
    override val id="disabled"
    override val model="disabled"
    override fun isConfigured()=false
    override fun embed(texts:List<String>):List<FloatArray> = throw DomainException(DomainError("AI_PROVIDER_DOWN",ErrorCategory.AI_PROVIDER,"Embedding provider is not configured",true))
}

class DeterministicEmbeddingAdapter(override val dimensions: Int = 64) : EmbeddingAdapter {
    override val id = "DETERMINISTIC_TEST_EMBEDDING"
    override val model = "deterministic-hash-v1"
    override val testOnly = true
    override fun isConfigured() = true
    override fun embed(texts: List<String>): List<FloatArray> = texts.map(::embedOne)

    private fun embedOne(text: String): FloatArray {
        val normalized = text.lowercase().replace(Regex("\\s+"), " ").trim()
        val v = FloatArray(dimensions)
        if (normalized.isBlank()) return v
        val terms = normalized.split(Regex("[^\\p{L}\\p{N}]+")) .filter { it.isNotBlank() }
        for (term in terms) {
            val hash = MessageDigest.getInstance("SHA-256").digest(term.toByteArray())
            for (i in hash.indices) {
                val idx = ((hash[i].toInt() and 0xff) + i * 31) % dimensions
                val sign = if ((hash[(i + 7) % hash.size].toInt() and 1) == 0) 1f else -1f
                v[idx] += sign * (0.5f + ((hash[(i + 13) % hash.size].toInt() and 0xff) / 255f))
            }
        }
        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-9f)
        for (i in v.indices) v[i] /= norm
        return v
    }
}


class UnavailableEmbeddingAdapter(override val dimensions:Int=64):EmbeddingAdapter {
    override val id="unavailable"
    override val model="unconfigured"
    override fun isConfigured()=false
    override fun embed(texts:List<String>):List<FloatArray> = throw DomainException(DomainError("AI_PROVIDER_DOWN",ErrorCategory.AI_PROVIDER,"Embedding provider is not configured",true))
}

class OpenAiEmbeddingAdapter(
    private val apiKey: String?,
    private val baseUrl: String,
    override val model: String,
    override val dimensions: Int = 64,
) : EmbeddingAdapter {
    override val id = "openai-embeddings"
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    override fun isConfigured() = !apiKey.isNullOrBlank() && baseUrl.startsWith("https://")

    override fun embed(texts: List<String>): List<FloatArray> {
        if (!isConfigured()) throw DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.AI_PROVIDER, "Embedding provider is not configured", true))
        if (texts.isEmpty()) return emptyList()
        if (texts.size > 128) throw DomainException(DomainError("VALIDATION", ErrorCategory.VALIDATION, "Embedding batch too large"))
        val body = buildJsonObject {
            put("model", model)
            put("dimensions", dimensions)
            put("encoding_format", "float")
            put("input", buildJsonArray { texts.forEach { add(it.take(30_000)) } })
        }.toString()
        val req = HttpRequest.newBuilder(URI.create("${baseUrl.trimEnd('/')}/embeddings"))
            .timeout(Duration.ofSeconds(45))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response = try { client.send(req, HttpResponse.BodyHandlers.ofString()) }
        catch (_: Exception) { throw DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.NETWORK_UPSTREAM, "Embedding provider unavailable", true)) }
        if (response.statusCode() == 429) throw DomainException(DomainError("AI_RATE_LIMIT", ErrorCategory.RATE_LIMIT, "Embedding provider rate limited request", true))
        if (response.statusCode() !in 200..299) throw DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.AI_PROVIDER, "Embedding provider rejected request", response.statusCode() >= 500))
        val root = runCatching { Json.parseToJsonElement(response.body()).jsonObject }.getOrElse {
            throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "Embedding provider response invalid"))
        }
        val data = root["data"]?.jsonArray ?: throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "Embedding provider data missing"))
        val ordered = data.map { row ->
            val o = row.jsonObject
            val idx = o["index"]?.jsonPrimitive?.intOrNull ?: -1
            val arr = o["embedding"]?.jsonArray?.map { it.jsonPrimitive.float }?.toFloatArray()
                ?: throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "Embedding vector missing"))
            if (arr.size != dimensions) throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "Embedding dimension mismatch"))
            idx to arr
        }.sortedBy { it.first }.map { it.second }
        if (ordered.size != texts.size) throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "Embedding result count mismatch"))
        return ordered
    }
}

internal fun vectorLiteral(v: FloatArray): String = v.joinToString(prefix = "[", postfix = "]") { x ->
    if (x.isFinite()) "%.8f".format(java.util.Locale.ROOT, x) else "0.00000000"
}
