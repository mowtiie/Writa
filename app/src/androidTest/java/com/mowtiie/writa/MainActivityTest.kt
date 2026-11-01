package com.mowtiie.writa

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** The real activity with the real Hilt graph: the app as a user finds it right after installing. */
class MainActivityTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aFreshInstallStartsOnTheServerSetup() {
        rule.waitUntil(WAIT_MS) { rule.onAllNodesWithTextCount(context.getString(R.string.setup_title)) > 0 }

        rule.onNodeWithText(context.getString(R.string.setup_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.setup_connect)).assertIsDisplayed()
    }

    @Test
    fun screenCaptureIsAllowedUntilTheUserTurnsPrivacyOn() {
        rule.waitUntil(WAIT_MS) { rule.onAllNodesWithTextCount(context.getString(R.string.setup_title)) > 0 }

        val secure = rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

        assertFalse(secure)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(text: String) =
        onAllNodes(
            androidx.compose.ui.test
                .hasText(text),
        ).fetchSemanticsNodes().size

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
