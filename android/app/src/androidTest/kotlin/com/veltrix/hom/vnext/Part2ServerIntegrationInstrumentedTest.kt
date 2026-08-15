package com.veltrix.hom.vnext

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Part2ServerIntegrationInstrumentedTest {
    @Test
    fun acceptedBackendContractsDrivePart2FrontendWithoutLocalAuthority() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val api = VeltrixApiClient()
        assertTrue(api.health())
        val suffix = UUID.randomUUID().toString().take(8)
        val session = api.register("part2-$suffix@example.test", "testing-password-12345", "Part2 Learner")
        SessionStore(context).save(LocalSession(session.accountId, session.token))
        val repository = Part2FeatureRepository(context, api)

        val project = api.createProject(session.token, "Motion Studio $suffix", "Learn mechanics with evidence")
        val projectId = project.id

        val sourceText = "Newton's second law relates force, mass, and acceleration."
        val sourceHash = java.security.MessageDigest.getInstance("SHA-256").digest(sourceText.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val sourceMeta = JSONObject()
            .put("title", "Mechanics Notes $suffix")
            .put("type", "TEXT")
            .put("mimeType", "text/plain")
            .put("contentHash", sourceHash)
            .put("sizeBytes", sourceText.toByteArray().size)
        val sourceCreated = requestJson(api, session.token, "POST", "/v1/sources", sourceMeta, 201)
        val sourceId = sourceCreated.getString("id")
        requestJson(api, session.token, "POST", "/v1/sources/$sourceId/text", JSONObject().put("text", sourceText), 200)
        requestJson(api, session.token, "POST", "/v1/sources/$sourceId/link-project", JSONObject().put("projectId", projectId), 200)

        val chatCreated = requestJson(
            api, session.token, "POST", "/v1/chats",
            JSONObject().put("scope", "PROJECT").put("projectId", projectId).put("title", "Mechanics chat $suffix").put("learningMode", "DEFAULT").put("memoryEnabled", true).put("projectMemoryEnabled", true),
            201,
        )
        val conversationId = chatCreated.getString("id")

        val question = JSONObject()
            .put("prompt", "Which equation represents Newton's second law?")
            .put("type", "MULTIPLE_CHOICE")
            .put("options", JSONArray(listOf("F=ma", "E=mc²", "p=mv")))
            .put("expectedAnswers", JSONArray(listOf("F=ma")))
        val assessment = requestJson(
            api, session.token, "POST", "/v1/assessments",
            JSONObject().put("kind", "QUIZ").put("title", "Motion Quiz $suffix").put("projectId", projectId).put("questions", JSONArray().put(question)),
            201,
        )
        val assessmentId = assessment.getString("id")
        val detail = repository.assessment(ApiSession(session.accountId, session.token), assessmentId)
        assertEquals("Motion Quiz $suffix", detail.value?.title)
        assertEquals(1, detail.value?.questions?.size)
        val attempt = repository.startAttempt(ApiSession(session.accountId, session.token), assessmentId)
        assertTrue(repository.answer(ApiSession(session.accountId, session.token), attempt.id, detail.value!!.questions.single().id, listOf("F=ma")).success)
        val result = repository.submitAttempt(ApiSession(session.accountId, session.token), attempt.id)
        assertEquals(1.0, result.accuracy, 0.0001)

        val practiceCreated = requestJson(
            api, session.token, "POST", "/v1/practice",
            JSONObject().put("projectId", projectId).put("focusTopic", "Motion").put("difficulty", 2).put("targetItemCount", 1).put("adaptive", true).put("hintPolicy", "ON_REQUEST").put("revealPolicy", "AFTER_CHECK"),
            201,
        )
        val practiceId = practiceCreated.getJSONObject("session").getString("id")
        val practiceItem = requestJson(
            api, session.token, "POST", "/v1/practice/$practiceId/items",
            JSONObject().put("prompt", "What is acceleration?").put("expectedAnswer", "change in velocity per unit time").put("explanation", "Acceleration measures velocity change over time.").put("topic", "Motion").put("difficulty", 2),
            201,
        )
        val practiceItemId = practiceItem.getString("id")
        val practice = repository.practice(ApiSession(session.accountId, session.token), practiceId)
        assertEquals("What is acceleration?", practice.value?.items?.single()?.prompt)
        assertTrue(repository.practiceAttempt(ApiSession(session.accountId, session.token), practiceId, practiceItemId, "change in velocity per unit time").success)
        val check = repository.practiceCheck(ApiSession(session.accountId, session.token), practiceId, practiceItemId)
        assertTrue(check.correct)

        val deck = requestJson(api, session.token, "POST", "/v1/flashcards/decks", JSONObject().put("title", "Motion Deck $suffix").put("scope", "PROJECT").put("projectId", projectId), 201)
        val card = requestJson(
            api, session.token, "POST", "/v1/flashcards/decks/${deck.getString("id")}/cards",
            JSONObject().put("front", "Force equation").put("back", "F = ma").put("explanation", "Force equals mass times acceleration.").put("projectId", projectId), 201,
        )
        val due = repository.flashcards(ApiSession(session.accountId, session.token), true)
        assertTrue(due.value.orEmpty().any { it.id == card.getString("id") })
        val reviewed = repository.reviewFlashcard(ApiSession(session.accountId, session.token), card.getString("id"), "GOOD")
        assertTrue(reviewed.intervalDays >= 1)

        val projects = repository.projects(ApiSession(session.accountId, session.token), true)
        assertTrue(projects.value.orEmpty().any { it.id == projectId })
        val workspace = repository.workspace(ApiSession(session.accountId, session.token), projectId, true)
        assertEquals(projectId, workspace.value?.project?.id)
        assertTrue((workspace.value?.sourceCount ?: 0) >= 1)
        assertTrue((workspace.value?.assessmentCount ?: 0) >= 1)
        assertTrue((workspace.value?.flashcardCount ?: 0) >= 1)
        assertTrue((workspace.value?.practiceCount ?: 0) >= 1)

        val chats = repository.chats(ApiSession(session.accountId, session.token), projectId, true)
        assertTrue(chats.value.orEmpty().any { it.id == conversationId })
        val sources = repository.sources(ApiSession(session.accountId, session.token), true)
        assertTrue(sources.value.orEmpty().any { it.id == sourceId && it.state == "READY" })
        val search = repository.search(ApiSession(session.accountId, session.token), "Motion Studio")
        assertTrue(search.value.orEmpty().any { it.id == projectId && it.deepLink.isNotBlank() })

        // GET-only identity/store/map reads must agree with backend truth and cannot mutate economy.
        val store = repository.store(ApiSession(session.accountId, session.token), true)
        assertNotNull(store.value)
        val balanceBeforeAi = store.value!!.coinBalance
        assertEquals(balanceBeforeAi, repository.gameProfile(ApiSession(session.accountId, session.token), true).value?.coinBalance)
        val avatars = repository.avatars(ApiSession(session.accountId, session.token), true)
        assertTrue(avatars.value.orEmpty().isNotEmpty())
        val personalMap = repository.personalMap(ApiSession(session.accountId, session.token), true)
        assertNotNull(personalMap.value)
        assertEquals(personalMap.value!!.eligible, personalMap.value!!.levelSatisfied && personalMap.value!!.memorySatisfied)
        assertEquals(balanceBeforeAi, repository.store(ApiSession(session.accountId, session.token), true).value!!.coinBalance)

        // A completed AI interaction is meaningful backend activity and may legitimately earn a
        // backend-defined reward. Frontend must display the resulting authoritative balance rather
        // than assuming that the pre-interaction balance remains fixed.
        val streamEvents = mutableListOf<StreamUiEvent>()
        repository.streamAi(
            ApiSession(session.accountId, session.token), conversationId, projectId, listOf(sourceId),
            "Explain Newton's second law using the selected source.", "DEFAULT",
        ) { streamEvents += it }
        assertTrue(streamEvents.any { it.type == "segment" && !it.segment.isNullOrBlank() })
        val messages = repository.messages(ApiSession(session.accountId, session.token), conversationId, true)
        assertTrue(messages.value.orEmpty().any { it.role == "ASSISTANT" && it.state == "COMPLETED" })

        val authoritativeStoreBalance = repository.store(ApiSession(session.accountId, session.token), true).value!!.coinBalance
        val authoritativeProfileBalance = repository.gameProfile(ApiSession(session.accountId, session.token), true).value!!.coinBalance
        assertEquals(authoritativeProfileBalance, authoritativeStoreBalance)
        assertTrue(authoritativeStoreBalance >= balanceBeforeAi)
    }

    private fun requestJson(api: VeltrixApiClient, token: String, method: String, path: String, body: JSONObject, expected: Int): JSONObject {
        val (code, text) = api.request(method, path, token, body.toString())
        assertEquals("$path -> $text", expected, code)
        return JSONObject(text)
    }
}