package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class FrontendPart3ClosureUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun flashcardCriticalPromptHasPlausibleVisibleBounds() {
        val card = FlashcardUiModel("card-visual", "deck", null, "Explain why acceleration changes when net force changes.", "Because F = ma for constant mass.", "Force determines acceleration.", "now", 2, 3, 0, 7)
        compose.setContent { VeltrixTheme { FlashcardsWorldScreen(RepositoryState(listOf(card), DataFreshness.FRESH), {}, { _, _ -> }) } }
        compose.onNodeWithTag("flashcards-screen").assertIsDisplayed()
        compose.onNodeWithTag("flashcard-stage").assertIsDisplayed().assertHeightIsAtLeast(300.dp)
        compose.onNodeWithTag("flashcard-primary-text", useUnmergedTree = true).assertIsDisplayed().assertHeightIsAtLeast(72.dp)
        compose.onNodeWithText("Explain why acceleration changes when net force changes.", useUnmergedTree = true).assertIsDisplayed().assertHeightIsAtLeast(72.dp)
    }

    @Test
    fun calculatorPresentsBackendResultWithoutInventingAuthority() {
        compose.setContent {
            VeltrixTheme {
                CalculatorWorldScreen(
                    RepositoryState(CalculatorResultUiModel("(18 + 6) / 3", "8", true), DataFreshness.FRESH),
                    listOf(CalculatorResultUiModel("2 + 2", "4", true)),
                    {},
                )
            }
        }
        compose.onNodeWithTag("calculator-screen").assertIsDisplayed()
        compose.onNodeWithTag("calculator-result").assertIsDisplayed()
        compose.onNodeWithText("Deterministic backend result").assertIsDisplayed()
    }

    @Test
    fun translatePresentsProviderTruthWithoutInventingAuthority() {
        compose.setContent {
            VeltrixTheme {
                TranslateWorldScreen(
                    RepositoryState(TranslationUiModel("Salom", "Hello", "uz", "en", "test-mock", false, "now"), DataFreshness.FRESH),
                    null,
                    { _, _, _, _ -> },
                )
            }
        }
        compose.onNodeWithTag("translate-screen").assertIsDisplayed()
        compose.onNodeWithTag("translate-result").assertIsDisplayed()
        compose.onNodeWithText("Deterministic/test provider · test-mock").assertIsDisplayed()
    }

    @Test
    fun notificationStateSeparatesBackendPreferenceFromAndroidPermission() {
        val intents = listOf(NotificationIntentUiModel("n1", null, "LEARNING", "Review mechanics", null, "PENDING", "now"))
        val prefs = listOf(NotificationPreferenceUiModel("LEARNING", true, "{}", "Asia/Tashkent", 3, "now"))
        compose.setContent { VeltrixTheme { NotificationsWorldScreen(RepositoryState(intents, DataFreshness.FRESH), RepositoryState(prefs, DataFreshness.FRESH), {}, { _, _ -> }) } }
        compose.onNodeWithTag("notifications-screen").assertIsDisplayed()
        compose.onNodeWithText("Review mechanics").assertIsDisplayed()
        compose.onNodeWithText("Backend preference truth remains separate from Android permission state.").assertIsDisplayed()
    }

    @Test
    fun settingsStatePreservesRevisionConflictTruth() {
        val profile = ProfileUiModel("account", "Alex", "alex", "en", "Asia/Tashkent", true, true, 9)
        compose.setContent {
            VeltrixTheme {
                SettingsWorldScreen(
                    RepositoryState(profile, DataFreshness.FRESH),
                    RepositoryState(emptyList(), DataFreshness.FRESH),
                    RepositoryState(null, DataFreshness.FRESH), null,
                    {}, { _, _, _, _, _ -> }, { _, _, _ -> }, {}, {},
                )
            }
        }
        compose.onNodeWithTag("settings-screen").assertIsDisplayed()
        compose.onNodeWithText("Account profile").assertIsDisplayed()
        compose.onNodeWithText("Revision 9 · conflicts are never overwritten silently.").assertIsDisplayed()
    }
}
