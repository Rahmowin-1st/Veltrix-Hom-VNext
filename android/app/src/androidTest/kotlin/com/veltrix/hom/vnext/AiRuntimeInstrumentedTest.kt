package com.veltrix.hom.vnext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device-side proof for Part 1 AI stream, cancellation and retry semantics against real Ktor. */
class AiRuntimeInstrumentedTest {
    private val api = VeltrixApiClient()

    @Test
    fun streamCancelAndRetryWorkOnDevice() {
        assertTrue(api.health())
        val suffix = System.nanoTime().toString()
        val session = api.register("ai-device-$suffix@example.invalid", "Part1-ai-password-$suffix", "AI Device")
        val token = session.token

        val (conversationCode, conversationText) = api.request(
            "POST", "/v1/chats", token,
            JSONObject().put("scope", "GLOBAL").put("title", "AI runtime device").toString(),
        )
        assertEquals(201, conversationCode)
        val conversationId = JSONObject(conversationText).getString("id")

        val successKey = "ai-success-$suffix"
        val successBody = JSONObject()
            .put("conversationId", conversationId)
            .put("text", "Device SSE success proof")
            .put("idempotencyKey", successKey)
            .toString()
        val (successCode, successText) = api.request(
            "POST", "/v1/ai/stream", token, successBody, "android-ai-success-$suffix",
        )
        assertEquals(200, successCode)
        assertTrue("missing SSE segment: $successText", successText.contains("event: segment"))
        assertTrue("missing deterministic provider output: $successText", successText.contains("TEST_ONLY:"))
        assertTrue("missing final stream marker: $successText", successText.contains("\"final\":true"))

        val cancelRequestId = "android-ai-cancel-$suffix"
        val slowMarker = "CI_SLOW_STREAM_MARKER device-$suffix"
        val slowBody = JSONObject()
            .put("conversationId", conversationId)
            .put("text", slowMarker)
            .put("idempotencyKey", "ai-cancel-$suffix")
            .toString()

        var streamResult: Pair<Int, String>? = null
        var streamFailure: Throwable? = null
        val streamThread = Thread {
            try {
                streamResult = api.request("POST", "/v1/ai/stream", token, slowBody, cancelRequestId)
            } catch (t: Throwable) {
                streamFailure = t
            }
        }.apply { name = "veltrix-ai-cancel-test"; start() }

        var cancelled = false
        for (attempt in 1..15) {
            Thread.sleep(100)
            val (cancelCode, cancelText) = api.request(
                "POST", "/v1/ai/cancel", token,
                JSONObject().put("requestId", cancelRequestId).toString(),
            )
            assertEquals(200, cancelCode)
            if (JSONObject(cancelText).getBoolean("cancelled")) {
                cancelled = true
                break
            }
        }
        assertTrue("server never exposed active slow stream for cancellation", cancelled)
        streamThread.join(10_000)
        assertFalse("cancelled SSE request did not terminate", streamThread.isAlive)
        streamFailure?.let { throw AssertionError("cancelled stream transport failed", it) }
        val cancelledStream = streamResult
        assertNotNull(cancelledStream)
        assertEquals(200, cancelledStream!!.first)
        assertTrue("cancelled stream did not emit AI_CANCELLED: ${cancelledStream!!.second}", cancelledStream!!.second.contains("AI_CANCELLED"))

        val (messagesCode, messagesText) = api.request("GET", "/v1/chats/$conversationId/messages", token, null)
        assertEquals(200, messagesCode)
        val messages = JSONArray(messagesText)
        var failedUser: JSONObject? = null
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            if (m.optString("role") == "USER" && m.optString("content").contains(slowMarker)) {
                failedUser = m
                break
            }
        }
        assertNotNull("cancelled user message missing from chat history: $messagesText", failedUser)
        assertEquals("FAILED", failedUser!!.getString("state"))

        val (retryCode, retryText) = api.request(
            "POST", "/v1/chats/$conversationId/messages/${failedUser!!.getString("id")}/retry", token,
            JSONObject().put("idempotencyKey", "ai-retry-$suffix").toString(),
        )
        assertEquals(202, retryCode)
        val retry = JSONObject(retryText)
        assertEquals("USER", retry.getString("role"))
        assertEquals("QUEUED", retry.getString("state"))
        assertEquals(slowMarker, retry.getString("content"))
    }
}
