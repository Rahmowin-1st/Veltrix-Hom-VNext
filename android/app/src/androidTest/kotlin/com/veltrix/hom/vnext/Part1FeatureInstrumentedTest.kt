package com.veltrix.hom.vnext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** Device-side proof that the Android transport can exercise real Part 1 feature contracts. */
class Part1FeatureInstrumentedTest {
    private val api = VeltrixApiClient()

    private fun obj(method: String, path: String, token: String?, body: JSONObject? = null, expected: Set<Int> = setOf(200)): JSONObject {
        val (code, text) = api.request(method, path, token, body?.toString())
        assertTrue("$method $path -> $code $text", code in expected)
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test
    fun projectSourceQuizMistakePracticeFlashcardContractsWorkOnDevice() {
        assertTrue(api.health())
        val suffix = System.nanoTime().toString()
        val session = api.register("device-$suffix@example.invalid", "Part1-device-password-$suffix", "Device Part1")
        val token = session.token

        val profile = obj("GET", "/v1/profile", token)
        obj("PATCH", "/v1/profile", token, JSONObject()
            .put("displayName", "Device Part1")
            .put("onboardingComplete", true)
            .put("expectedRevision", profile.getLong("revision")))
        assertEquals("COLD", obj("GET", "/v1/home", token).getString("memoryMaturity"))

        val project = obj("POST", "/v1/projects", token, JSONObject()
            .put("title", "CEFR C1 Device")
            .put("purpose", "Part 1 device acceptance"), setOf(201))
        val projectId = project.getString("id")
        obj("PUT", "/v1/projects/$projectId/instructions", token, JSONObject()
            .put("body", "Use British English. Correct grammar."))

        val goals = mutableListOf<JSONObject>()
        repeat(3) { i ->
            goals += obj("POST", "/v1/projects/$projectId/goals", token, JSONObject().put("title", "Goal ${i + 1}").put("priority", 3 - i), setOf(201))
        }
        val g = goals.first()
        val completed = obj("POST", "/v1/projects/$projectId/goals/${g.getString("id")}/transition", token,
            JSONObject().put("target", "COMPLETED").put("expectedRevision", g.getLong("revision")))
        assertEquals("COMPLETED", completed.getString("status"))

        val source = obj("POST", "/v1/sources", token, JSONObject()
            .put("title", "Device Source")
            .put("type", "TEXT")
            .put("mimeType", "text/plain")
            .put("contentHash", sha256Hex("device-source-$suffix"))
            .put("sizeBytes", 80), setOf(201))
        val sourceId = source.getString("id")
        val ready = obj("POST", "/v1/sources/$sourceId/text", token, JSONObject()
            .put("text", "The CEFR C1 target sentence says velocity is distance divided by time."))
        assertEquals("READY", ready.getString("state"))
        obj("POST", "/v1/sources/$sourceId/link-project", token, JSONObject().put("projectId", projectId))
        val (searchCode, searchText) = api.request("POST", "/v1/sources/search", token, JSONObject()
            .put("query", "velocity distance time")
            .put("sourceIds", JSONArray().put(sourceId))
            .put("projectId", projectId)
            .put("limit", 5).toString())
        assertEquals(200, searchCode)
        val hits = JSONArray(searchText)
        assertTrue("source retrieval returned no citation candidates: $searchText", hits.length() > 0)
        val citation = hits.getJSONObject(0).getJSONObject("citation")
        assertEquals(sourceId, citation.getString("sourceId"))

        val conversation = obj("POST", "/v1/chats", token, JSONObject()
            .put("scope", "PROJECT").put("projectId", projectId).put("title", "Device project chat"), setOf(201))
        val conversationId = conversation.getString("id")
        val queued = obj("POST", "/v1/chats/$conversationId/messages", token, JSONObject()
            .put("text", "What is velocity?").put("idempotencyKey", "device-message-$suffix"), setOf(202))
        assertEquals("QUEUED", queued.getString("state"))

        val question = JSONObject()
            .put("type", "SINGLE_CHOICE")
            .put("prompt", "Velocity equals?")
            .put("options", JSONArray().put("time/distance").put("distance/time"))
            .put("expectedAnswers", JSONArray().put("distance/time"))
            .put("evidence", JSONArray().put(citation))
        val quiz = obj("POST", "/v1/assessments", token, JSONObject()
            .put("kind", "QUIZ").put("title", "Device Source Quiz").put("projectId", projectId)
            .put("questions", JSONArray().put(question)), setOf(201))
        val quizId = quiz.getString("id")
        val detail = obj("GET", "/v1/assessments/$quizId", token)
        val questions = detail.getJSONArray("questions")
        assertEquals(1, questions.length())
        assertFalse("expected answer leaked to client", questions.getJSONObject(0).has("expectedAnswers"))
        val questionId = questions.getJSONObject(0).getString("id")
        val attempt = obj("POST", "/v1/assessments/$quizId/attempts", token, expected = setOf(201))
        val attemptId = attempt.getString("id")
        obj("PUT", "/v1/assessments/attempts/$attemptId/answer", token, JSONObject()
            .put("questionId", questionId).put("answers", JSONArray().put("time/distance")))
        val submitted = obj("POST", "/v1/assessments/attempts/$attemptId/submit", token)
        assertEquals(1, submitted.getJSONArray("mistakeIds").length())
        val mistakeId = submitted.getJSONArray("mistakeIds").getString(0)
        val (mistakesCode, mistakesText) = api.request("GET", "/v1/mistakes?projectId=$projectId", token, null)
        assertEquals(200, mistakesCode)
        val mistakes = JSONArray(mistakesText)
        val mistake = (0 until mistakes.length()).map { mistakes.getJSONObject(it) }.first { it.getString("id") == mistakeId }
        assertEquals(sourceId, mistake.getString("sourceId"))

        val practice = obj("POST", "/v1/mistakes/$mistakeId/practice", token,
            JSONObject().put("idempotencyKey", "device-practice-$suffix"), setOf(201))
        assertEquals(projectId, practice.getString("projectId"))
        val card = obj("POST", "/v1/mistakes/$mistakeId/flashcard", token,
            JSONObject().put("deckTitle", "Device Mistakes").put("idempotencyKey", "device-card-$suffix"), setOf(201))
        val reviewed = obj("POST", "/v1/flashcards/cards/${card.getString("id")}/review", token, JSONObject().put("rating", "GOOD"))
        assertTrue(reviewed.getInt("intervalDays") >= 1)

        val workspace = obj("GET", "/v1/projects/$projectId/workspace", token)
        assertTrue(workspace.getJSONArray("goals").toString().contains("COMPLETED"))
        assertTrue(workspace.getInt("sourceCount") >= 1)
        assertTrue(workspace.getInt("assessmentCount") >= 1)
        assertTrue(workspace.getInt("mistakeCount") >= 1)
    }
}
