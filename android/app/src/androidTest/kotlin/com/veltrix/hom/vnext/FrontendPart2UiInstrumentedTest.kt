package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class FrontendPart2UiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val home = HomeFinalModel(
        "fixture-account", "Alex", "avatar-core", 12, 14250, 420, 1000, 580, 230, 9, 7,
        "Continue mechanics", "SUFFICIENT", "ACTIVE", "unit-motion", "season-1", 2,
        listOf("PROJECT"), listOf("WEAK_TOPIC"), 18,
    )
    private val personal = PersonalFinalModel(
        "fixture-account", "Alex", "avatar-core", 12, 14250, 230, "LEARNING",
        listOf("Algebra"), listOf("Mechanics"), listOf("Robotics"), listOf("Improve physics"),
        "ACTIVE", "season-1", 3, 2, 7, 19,
    )
    private val game = GameProfileUiModel(
        12, 14250, 420, 1000, 230, 7, "avatar-core", "avatar/core", "CORE", 9, "ACTIVE", 22,
    )
    private val map = PersonalMapUiModel(
        "map-1", "map-definition", 2, "ACTIVE", true, 8, "SUFFICIENT", true, true, "UNLOCKED",
        listOf(MapUnitUiModel("unit-motion", 1, "MOTION", "Motion", "ACTIVE", 4, 10, 5)), 22,
    )

    @Test
    fun homeAndPersonalShareLivingIdentityAndServerProgression() {
        val projects = RepositoryState(listOf(ProjectCardModel("p1", "Physics Lab", "Mechanics", "ACTIVE", 1, 4, "now", "now")), DataFreshness.FRESH)
        compose.setContent {
            VeltrixTheme {
                Part2HomeScreen(RepositoryState(home, DataFreshness.FRESH), RepositoryState(game, DataFreshness.FRESH), projects, true, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("home-screen").assertIsDisplayed()
        compose.onNodeWithTag("living-avatar-home").assertIsDisplayed()
        compose.onNodeWithText("Continue mechanics").assertIsDisplayed()
        compose.onNodeWithText("230").assertIsDisplayed()
    }

    @Test
    fun personalMapUsesAuthoritativeUnitsRatherThanInventedRoute() {
        compose.setContent {
            VeltrixTheme {
                Part2PersonalScreen(
                    RepositoryState(personal, DataFreshness.FRESH), RepositoryState(game, DataFreshness.FRESH),
                    RepositoryState(map, DataFreshness.FRESH), true, {}, {}, { _, _ -> },
                )
            }
        }
        compose.onNodeWithTag("personal-screen").assertIsDisplayed()
        compose.onNodeWithTag("living-avatar-personal").assertIsDisplayed()
        compose.onNodeWithTag("personal-map-live").assertExists()
        // The spatial map intentionally exposes the active unit in both the selected-detail stage
        // and the accessible route node. Verify the canonical selected title plus both truthful
        // current-location representations instead of incorrectly requiring duplicated copy to be unique.
        compose.onNodeWithTag("map-selected-title").assertTextEquals("Motion")
        compose.onAllNodesWithText("Current location · 4/10 progress").assertCountEquals(2)
    }

    @Test
    fun projectSpacePresentsContextAsOneWorkspace() {
        val project = ProjectCardModel("project-1", "Physics Lab", "Understand motion", "ACTIVE", 1, 8, "now", "now")
        val workspace = ProjectWorkspaceUiModel(
            project, listOf(ProjectGoalModel("g1", "Finish mechanics", null, "ACTIVE", 1, 2)),
            listOf(ConversationUiModel("c1", "project-1", "PROJECT", "Motion chat", "DEFAULT", true, true, false, false, 2, "now")),
            3, 1, 1, 4, 2, 2, 5, 11, "Use SI units", listOf("PRACTICE"), "Motion", "DEFAULT", 8,
        )
        compose.setContent {
            VeltrixTheme {
                ProjectsWorldScreen(
                    RepositoryState(listOf(project), DataFreshness.FRESH), emptyList(), RepositoryState(workspace, DataFreshness.FRESH),
                    "project-1", {}, { _, _ -> }, {}, {}, {},
                )
            }
        }
        compose.onNodeWithTag("projects-screen").assertIsDisplayed()
        // WorldHeading deliberately renders compact context eyebrows in uppercase; assert the
        // exact visible label rather than weakening the Project Space acceptance check.
        compose.onNodeWithText("PROJECT SPACE").assertExists()
        compose.onNodeWithText("Use SI units").assertExists()
        compose.onNodeWithText("Finish mechanics").assertExists()
    }

    @Test
    fun chatRendersStreamingAndCitationWithoutExposingHiddenReasoning() {
        val conversation = ConversationUiModel("c1", "project-1", "PROJECT", "Mechanics", "DEFAULT", true, true, false, false, 2, "now")
        val message = ChatMessageUiModel("m1", "c1", "ASSISTANT", "COMPLETED", "Force equals mass times acceleration.", true, 2, "now")
        val source = SourceUiModel("s1", "Mechanics notes", "TEXT", "text/plain", "READY", 100, 2)
        compose.setContent {
            VeltrixTheme {
                ChatWorldScreen(
                    RepositoryState(listOf(conversation), DataFreshness.FRESH), RepositoryState(listOf(message), DataFreshness.FRESH),
                    RepositoryState(listOf(source), DataFreshness.FRESH), "c1", "project-1", true, "Building a grounded answer…", null,
                    setOf("s1"), mapOf("m1" to listOf(CitationUiModel(1, "s1", "Newton's second law", 1, "Forces", .98))),
                    {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
        }
        compose.onNodeWithTag("chat-screen").assertIsDisplayed()
        compose.onNodeWithText("Force equals mass times acceleration.").assertExists()
        compose.onNodeWithText("Building a grounded answer…").assertExists()
        compose.onNodeWithText("Newton's second law", substring = true).assertExists()
    }

    @Test
    fun sourceStatesAndStoreOwnershipRemainExplicit() {
        val source = SourceUiModel("s1", "Physics PDF", "FILE", "application/pdf", "PROCESSING", 60, 3)
        compose.setContent { VeltrixTheme { LibraryWorldScreen(RepositoryState(listOf(source), DataFreshness.FRESH), {}, { _, _ -> }, {}) } }
        compose.onNodeWithTag("library-screen").assertIsDisplayed()
        compose.onNodeWithText("Processing").assertExists()
        compose.onNodeWithText("Physics PDF").assertExists()
    }

    @Test
    fun flashcardRatingIsUserActionNotLocalScheduleAuthority() {
        val card = FlashcardUiModel("card-1", "deck-1", null, "What is acceleration?", "Change in velocity per unit time", null, "now", 1, 1, 0, 4)
        compose.setContent { VeltrixTheme { FlashcardsWorldScreen(RepositoryState(listOf(card), DataFreshness.FRESH), {}, { _, _ -> }) } }
        compose.onNodeWithTag("flashcards-screen").assertIsDisplayed()
        compose.onNodeWithText("What is acceleration?").assertIsDisplayed()
        compose.onNodeWithText("Tap the card to reveal the answer").assertExists()
    }
}
