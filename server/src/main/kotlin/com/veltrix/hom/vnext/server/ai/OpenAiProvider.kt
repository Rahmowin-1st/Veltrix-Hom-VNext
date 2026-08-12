package com.veltrix.hom.vnext.server.ai

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.server.foundation.*
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Server-side OpenAI Responses API adapter. Keys are read only through ServerConfig/env;
 * this class is never referenced by Android. The adapter keeps system policy and user
 * content in separate provider fields and does not request provider-side storage.
 */
class OpenAiResponsesProvider(
    private val apiKey: String?,
    private val baseUrl: String,
    override val modelId: String,
    override val tier: ModelTier,
    private val timeout: Duration = Duration.ofSeconds(60),
) : ModelProviderAdapter {
    override val id: String = "openai-responses"
    override val capabilities = ModelCapability(streaming = true, structuredOutput = true, vision = true, maxContextUnits = 200_000)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

    override fun isConfigured(): Boolean = !apiKey.isNullOrBlank() && baseUrl.startsWith("https://")

    override fun stream(request: AiProviderRequest, cancellation: RequestCancellation): Sequence<AiProviderChunk> = sequence {
        if (!isConfigured()) throw DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.AI_PROVIDER, "OpenAI provider is not configured", retryable = true))
        cancellation.throwIfCancelled()
        val body = buildJsonObject {
            put("model", modelId)
            put("stream", true)
            put("store", false)
            put("max_output_tokens", request.maxOutputUnits.coerceAtLeast(64))
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { put("instructions", it) }
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", request.input)
                })
            })
            if (request.metadata.isNotEmpty()) put("metadata", buildJsonObject {
                request.metadata.entries.take(16).forEach { (k, v) -> put(k.take(64), v.take(512)) }
            })
        }.toString()
        val httpRequest = HttpRequest.newBuilder(URI.create("${baseUrl.trimEnd('/')}/responses"))
            .timeout(timeout)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = try {
            client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
        } catch (_: java.net.http.HttpTimeoutException) {
            throw DomainException(DomainError("AI_TIMEOUT", ErrorCategory.AI_PROVIDER, "AI provider timed out", retryable = true))
        } catch (_: Exception) {
            throw DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.NETWORK_UPSTREAM, "AI provider is unavailable", retryable = true))
        }
        if (response.statusCode() !in 200..299) {
            response.body().close()
            throw mapStatus(response.statusCode())
        }
        BufferedReader(InputStreamReader(response.body())).use { reader ->
            while (true) {
                cancellation.throwIfCancelled()
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isBlank() || payload == "[DONE]") continue
                val obj = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    "response.output_text.delta" -> obj["delta"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { yield(AiProviderChunk(it, false)) }
                    "response.completed" -> yield(AiProviderChunk("", true))
                    "response.failed", "error" -> throw DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.AI_PROVIDER, "AI provider returned a generation failure", retryable = true))
                }
            }
        }
    }

    override fun structuredGenerate(request: AiProviderRequest, schemaName: String, schemaJson: String, cancellation: RequestCancellation): String {
        if (!isConfigured()) throw DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.AI_PROVIDER, "OpenAI provider is not configured", retryable = true))
        cancellation.throwIfCancelled()
        val schemaElement = runCatching { Json.parseToJsonElement(schemaJson) }.getOrElse {
            throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.VALIDATION, "Invalid structured output schema"))
        }
        val body = buildJsonObject {
            put("model", modelId)
            put("store", false)
            put("max_output_tokens", request.maxOutputUnits.coerceAtLeast(64))
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { put("instructions", it) }
            put("input", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", request.input) }) })
            put("text", buildJsonObject {
                put("format", buildJsonObject {
                    put("type", "json_schema")
                    put("name", schemaName.take(64).replace(Regex("[^A-Za-z0-9_-]"), "_"))
                    put("strict", true)
                    put("schema", schemaElement)
                })
            })
        }.toString()
        val httpRequest = HttpRequest.newBuilder(URI.create("${baseUrl.trimEnd('/')}/responses"))
            .timeout(timeout)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = try { client.send(httpRequest, HttpResponse.BodyHandlers.ofString()) }
        catch (_: java.net.http.HttpTimeoutException) { throw DomainException(DomainError("AI_TIMEOUT", ErrorCategory.AI_PROVIDER, "AI provider timed out", true)) }
        catch (_: Exception) { throw DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.NETWORK_UPSTREAM, "AI provider is unavailable", true)) }
        if (response.statusCode() !in 200..299) throw mapStatus(response.statusCode())
        val root = runCatching { Json.parseToJsonElement(response.body()).jsonObject }.getOrElse {
            throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "AI provider response was invalid"))
        }
        val output = root["output"]?.jsonArray ?: throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "AI provider output missing"))
        return output.asSequence().mapNotNull { item ->
            item.jsonObject["content"]?.jsonArray?.asSequence()?.mapNotNull { part ->
                val po = part.jsonObject
                if (po["type"]?.jsonPrimitive?.contentOrNull == "output_text") po["text"]?.jsonPrimitive?.contentOrNull else null
            }?.firstOrNull()
        }.firstOrNull() ?: throw DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "AI provider returned no structured text"))
    }

    private fun mapStatus(status: Int): DomainException = when (status) {
        401, 403 -> DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.AI_PROVIDER, "AI provider authentication failed", retryable = false))
        408 -> DomainException(DomainError("AI_TIMEOUT", ErrorCategory.AI_PROVIDER, "AI provider timed out", retryable = true))
        429 -> DomainException(DomainError("AI_RATE_LIMIT", ErrorCategory.RATE_LIMIT, "AI provider rate limited request", retryable = true))
        in 500..599 -> DomainException(DomainError("AI_PROVIDER_DOWN", ErrorCategory.AI_PROVIDER, "AI provider temporarily unavailable", retryable = true))
        else -> DomainException(DomainError("AI_OUTPUT_INVALID", ErrorCategory.AI_PROVIDER, "AI provider rejected request", retryable = false))
    }
}
