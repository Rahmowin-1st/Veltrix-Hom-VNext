package com.veltrix.hom.vnext

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ApiSession(val accountId: String, val token: String)
data class ApiProject(val id: String, val title: String, val revision: Long)

class GoogleBackendContractMissingException : IllegalStateException(
    "Google identity exchange is not configured by the trusted backend contract for this build.",
)

/** Minimal typed transport. Product account state is trusted only after server responses. */
class VeltrixApiClient(private val baseUrl: String = BuildConfig.VELTRIX_API_BASE_URL) {
    fun health(): Boolean = request("GET", "/health", null, null).first == 200

    fun register(login: String, password: String, displayName: String): ApiSession {
        val body = JSONObject()
            .put("login", login)
            .put("password", password)
            .put("displayName", displayName)
            .put("preferredLanguage", "en")
            .toString()
        return sessionResponse(expectAuth(request("POST", "/v1/auth/register", null, body), setOf(201)))
    }

    fun login(login: String, password: String): ApiSession {
        val body = JSONObject()
            .put("login", login)
            .put("password", password)
            .put("deviceLabel", "Veltrix Hom Android")
            .toString()
        return sessionResponse(expectAuth(request("POST", "/v1/auth/login", null, body), setOf(200)))
    }

    fun validateSession(session: ApiSession): Pair<Int, String> =
        request("GET", "/v1/profile", session.token, null)

    fun logout(session: ApiSession): Pair<Int, String> =
        request("POST", "/v1/auth/logout", session.token, "{}")

    /**
     * The route is intentionally build-configured instead of invented here. The frontend can obtain
     * a Google ID token through Credential Manager, but only the trusted backend may validate it and
     * mint a Veltrix session. An empty endpoint therefore fails closed.
     */
    fun exchangeGoogleIdentity(idToken: String, nonce: String): ApiSession {
        val endpoint = BuildConfig.VELTRIX_GOOGLE_AUTH_ENDPOINT.trim()
        if (endpoint.isBlank()) throw GoogleBackendContractMissingException()
        val body = JSONObject().put("idToken", idToken).put("nonce", nonce).toString()
        return sessionResponse(expectAuth(request("POST", endpoint, null, body), setOf(200, 201)))
    }

    fun createProject(token: String, title: String, purpose: String): ApiProject {
        val body = JSONObject().put("title", title).put("purpose", purpose).toString()
        val (code, text) = request("POST", "/v1/projects", token, body)
        require(code == 201) { "project HTTP $code $text" }
        return project(JSONObject(text))
    }

    fun getProject(token: String, id: String): ApiProject {
        val (code, text) = request("GET", "/v1/projects/$id", token, null)
        require(code == 200) { "get project HTTP $code" }
        return project(JSONObject(text))
    }

    fun syncProjectUpsert(token: String, mutationId: String, projectId: String, idempotencyKey: String, title: String): JSONObject {
        val m = JSONObject().put("mutationId", mutationId).put("entityType", "PROJECT").put("entityId", projectId)
            .put("operation", "UPSERT").put("idempotencyKey", idempotencyKey)
            .put("payload", JSONObject().put("title", title).put("status", "ACTIVE"))
        val body = JSONObject().put("mutations", org.json.JSONArray().put(m)).toString()
        val (code, text) = request("POST", "/v1/sync/mutations", token, body)
        require(code == 200) { "sync HTTP $code $text" }
        return JSONObject(text).getJSONArray("results").getJSONObject(0)
    }

    private fun sessionResponse(response: Pair<Int, String>): ApiSession {
        val o = JSONObject(response.second)
        return ApiSession(o.getString("accountId"), o.getString("sessionToken"))
    }

    private fun expectAuth(response: Pair<Int, String>, accepted: Set<Int>): Pair<Int, String> {
        if (response.first in accepted) return response
        val root = runCatching { JSONObject(response.second) }.getOrNull()
        val error = root?.optJSONObject("error") ?: root
        val code = error?.optString("code")?.takeIf { it.isNotBlank() } ?: "HTTP_${response.first}"
        val detail = error?.optString("message")?.takeIf { it.isNotBlank() }
            ?: error?.optString("detail")?.takeIf { it.isNotBlank() }
            ?: "Sign in failed"
        throw BackendUiException(response.first, code, response.first in setOf(408, 425, 429, 500, 502, 503, 504), detail)
    }

    private fun project(o: JSONObject) = ApiProject(o.getString("id"), o.getString("title"), o.getLong("revision"))

    internal fun request(method: String, path: String, token: String?, body: String?, requestId: String? = null): Pair<Int, String> {
        val c = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 7_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Request-ID", requestId ?: "android-${UUID.randomUUID()}")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) c.outputStream.bufferedWriter().use { it.write(body) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to text
    }
}
