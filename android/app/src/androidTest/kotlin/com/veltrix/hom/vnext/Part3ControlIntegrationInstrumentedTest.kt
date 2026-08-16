package com.veltrix.hom.vnext

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Exercises the final Part 3 frontend repository against the accepted backend runtime.
 * These assertions intentionally verify transport/authority boundaries rather than duplicating
 * calculator, translation, settings, notification, or account logic in the Android client.
 */
@RunWith(AndroidJUnit4::class)
class Part3ControlIntegrationInstrumentedTest {
    @Test
    fun calculatorTranslateSettingsNotificationsAndExportUseAcceptedBackendTruth() = runBlocking {
        val api = VeltrixApiClient()
        assertTrue(api.health())
        val suffix = UUID.randomUUID().toString().take(8)
        val session = api.register(
            "part3-controls-$suffix@example.test",
            "testing-password-12345",
            "Part3 Controls $suffix",
        )
        val repo = Part3ControlRepository(api)

        val calculator = repo.calculate(session, "(18 + 6) / 3")
        assertEquals(DataFreshness.FRESH, calculator.freshness)
        assertEquals("8", calculator.value?.result)
        assertEquals(true, calculator.value?.deterministic)

        val translation = repo.translate(
            session = session,
            text = "Salom",
            target = "en",
            source = "uz",
            projectId = null,
        )
        assertEquals(DataFreshness.FRESH, translation.freshness)
        assertEquals("[TEST:en] Salom", translation.value?.translatedText)
        assertEquals("MOCK_TEST_ONLY", translation.value?.provider)
        assertFalse(translation.value?.live ?: true)

        val profileBefore = repo.profile(session)
        assertEquals(DataFreshness.FRESH, profileBefore.freshness)
        assertEquals(session.accountId, profileBefore.value?.accountId)
        val updatedProfile = repo.updateProfile(
            session = session,
            current = requireNotNull(profileBefore.value),
            displayName = "Part3 Final $suffix",
            language = "en",
            timezone = "Asia/Tashkent",
            memoryEnabled = true,
        )
        assertEquals("Part3 Final $suffix", updatedProfile.displayName)
        assertEquals("Asia/Tashkent", updatedProfile.timezone)
        assertTrue(updatedProfile.revision > profileBefore.value!!.revision)

        val setting = repo.putSetting(
            session = session,
            category = "ACCESSIBILITY",
            key = "reduceTransparency",
            jsonValue = "true",
        )
        assertEquals("ACCESSIBILITY", setting.category)
        assertEquals("reduceTransparency", setting.key)
        assertEquals("true", setting.jsonValue)
        val settings = repo.settings(session, "ACCESSIBILITY")
        assertEquals(DataFreshness.FRESH, settings.freshness)
        assertTrue(settings.value.orEmpty().any { it.key == "reduceTransparency" && it.jsonValue == "true" })

        // The backend may start with no notification preferences for a new account, so create one
        // through the accepted contract, then read it back through the frontend repository.
        val seedPreference = NotificationPreferenceUiModel(
            category = "FLASHCARD_DUE",
            enabled = true,
            quietHoursJson = "{}",
            timezone = "Asia/Tashkent",
            revision = 0,
            updatedAt = "",
        )
        val preference = repo.putNotificationPreference(session, seedPreference, enabled = true)
        assertEquals("FLASHCARD_DUE", preference.category)
        assertTrue(preference.enabled)
        val preferences = repo.notificationPreferences(session)
        assertEquals(DataFreshness.FRESH, preferences.freshness)
        assertTrue(preferences.value.orEmpty().any { it.category == "FLASHCARD_DUE" && it.enabled })

        val intents = repo.notificationIntents(session)
        assertEquals(DataFreshness.FRESH, intents.freshness)
        assertNotNull(intents.value)

        val export = repo.accountExport(session)
        assertEquals(DataFreshness.FRESH, export.freshness)
        assertEquals(session.accountId, export.value?.accountId)
        assertEquals("Part3 Final $suffix", export.value?.displayName)
        assertEquals("Asia/Tashkent", export.value?.timezone)
        assertTrue(export.value?.entityCounts?.containsKey("projects") == true)

        // Account deletion is intentionally excluded here: ordinary integration evidence must not
        // destroy the session whose remaining assertions/provenance are still needed.
    }
}
