package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.runners.MethodSorters
import org.junit.Test

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PersistenceInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun aSeedProjectForProcessRestart() {
        compose.onNodeWithText("Projects").performClick()
        compose.onNodeWithText("Project name").performTextInput("CI Persistent Project")
        compose.onNodeWithText("Purpose").performTextInput("process death evidence")
        compose.onNodeWithText("Create Project").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("CI Persistent Project")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("CI Persistent Project").assertIsDisplayed()
    }

    @Test fun zVerifyProjectAfterProcessRestart() {
        compose.onNodeWithText("Projects").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("CI Persistent Project")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("CI Persistent Project").assertIsDisplayed()
    }
}
