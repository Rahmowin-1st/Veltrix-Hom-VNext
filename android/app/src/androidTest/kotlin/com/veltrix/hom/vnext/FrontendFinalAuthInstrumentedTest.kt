package com.veltrix.hom.vnext

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/** Final account-first runtime proof on the real MainActivity root. */
class FrontendFinalAuthInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val targetContext get() = instrumentation.targetContext
    private val outDir: File get() = File(targetContext.filesDir, "stage90").apply { mkdirs() }

    @Before
    fun before() {
        runBlocking { SessionStore(targetContext).clear(explicitSignOut = true) }
    }

    @After
    fun after() {
        runBlocking { SessionStore(targetContext).clear(explicitSignOut = true) }
    }

    @Test
    fun signedOutColdLaunchServerSessionSignOutAndRelaunchAreFailClosed() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("continue-google", 20_000L)
            compose.onNodeWithTag("continue-google").assertIsDisplayed()
            assertNoTag("home-stage40")
            capture("auth-signed-out")
        }

        val stamp = System.currentTimeMillis()
        val serverSession = VeltrixApiClient().register(
            "frontend-final-auth-$stamp",
            "Veltrix!Runtime2026",
            "Final Auth Runtime",
        )
        runBlocking {
            SessionStore(targetContext).save(LocalSession(serverSession.accountId, serverSession.token))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("home-stage40", 30_000L)
            compose.onNodeWithTag("home-stage40").assertIsDisplayed()

            compose.onNodeWithTag("home-menu").performClick()
            awaitTag("root-sidebar-list", 10_000L)
            compose.onNodeWithTag("root-sidebar-list")
                .performScrollToNode(hasTestTag("drawer-secondary-SETTINGS"))
            compose.onNodeWithTag("drawer-secondary-SETTINGS").assertIsDisplayed().performClick()
            awaitTag("settings-stage70", 20_000L)
            compose.onNodeWithTag("settings-sign-out").assertIsDisplayed()
            capture("settings-account")

            compose.onNodeWithTag("settings-sign-out").performClick()
            awaitTag("continue-google", 30_000L)
            compose.onNodeWithTag("continue-google").assertIsDisplayed()
            assertNoTag("home-stage40")
            capture("signout-auth")
        }

        val store = SessionStore(targetContext)
        assertTrue("explicit sign out must clear the local Veltrix session", runBlocking { store.read() == null })
        assertTrue("explicit sign out marker must prevent silent Google auto-entry", runBlocking { store.wasExplicitlySignedOut() })

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("continue-google", 20_000L)
            compose.onNodeWithTag("continue-google").assertIsDisplayed()
            assertNoTag("home-stage40")
            capture("auth-relaunch-signed-out")
        }

        File(outDir, "final-auth-report.txt").writeText(
            buildString {
                appendLine("AUTH_GATEWAY=PASS")
                appendLine("NO_PRODUCT_BEFORE_SERVER_SESSION=PASS")
                appendLine("SERVER_SESSION_BOOTSTRAP=PASS")
                appendLine("SIGN_OUT=PASS")
                appendLine("LOCAL_SESSION_CLEARED=PASS")
                appendLine("EXPLICIT_SIGN_OUT_MARKER=PASS")
                appendLine("RELAUNCH_REMAINS_SIGNED_OUT=PASS")
                appendLine("CREDENTIAL_MANAGER_CLEAR_STATE=SOURCE_WIRED_RUNTIME_CALL_PATH")
            },
        )
    }

    private fun assertNoTag(tag: String) {
        val nodes = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
        assertTrue("$tag must not be exposed in this account state", nodes.isEmpty())
    }

    private fun awaitTag(tag: String, timeoutMillis: Long) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(220)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val file = File(outDir, "$name.png")
        FileOutputStream(file).use { output ->
            assertTrue("PNG compression failed for $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        assertTrue("Screenshot $name must have real phone dimensions", bitmap.width >= 720 && bitmap.height >= 1280)
        bitmap.recycle()
    }
}
