package com.mowtiie.writa.feature.about

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.R
import com.mowtiie.writa.core.ui.theme.WritaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AboutScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private var state by mutableStateOf(AboutUiState())
    private val calls = mutableListOf<String>()

    private val actions =
        AboutActions(
            onLicenseOpen = { calls += "licenseOpen" },
            onLicenseClose = { calls += "licenseClose" },
            onVersionTap = { calls += "versionTap" },
            onEasterEggShown = {
                calls += "eggShown"
                state = state.copy(easterEgg = false)
            },
            onOpenLink = { calls += "link:$it" },
        )

    private fun show(initial: AboutUiState = AboutUiState()) {
        state = initial
        rule.setContent {
            WritaTheme { AboutScreen(state, actions, versionName = "1.2.3", onBack = { calls += "back" }) }
        }
    }

    @Test
    fun everyRowIsShownWithItsSummary() {
        show()

        rule.onNodeWithText(text(R.string.about_title)).assertIsDisplayed()
        for (
        id in
        listOf(
            R.string.about_license,
            R.string.about_license_name,
            R.string.about_source_code,
            R.string.about_source_code_summary,
            R.string.about_support,
            R.string.about_support_summary,
            R.string.about_author,
            R.string.about_author_name,
            R.string.about_version,
        )
        ) {
            rule.onNodeWithText(text(id)).assertIsDisplayed()
        }
    }

    @Test
    fun theVersionOfTheAppIsShown() {
        show()

        rule.onNodeWithText("1.2.3").assertIsDisplayed()
    }

    @Test
    fun theTitleIsMarkedAsAHeading() {
        show()

        rule
            .onNodeWithText(text(R.string.about_title))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun theLinksOpenTheirAddresses() {
        show()

        rule.onNodeWithText(text(R.string.about_source_code)).performClick()
        rule.onNodeWithText(text(R.string.about_support)).performClick()
        rule.onNodeWithText(text(R.string.about_author)).performClick()

        assertEquals(
            listOf(
                "link:${text(R.string.url_source_code)}",
                "link:${text(R.string.url_support)}",
                "link:${text(R.string.url_author)}",
            ),
            calls,
        )
    }

    @Test
    fun theLinksPointToHttpsAddresses() {
        for (id in listOf(R.string.url_source_code, R.string.url_support, R.string.url_author)) {
            assertTrue("${text(id)} is not https", text(id).startsWith("https://"))
        }
    }

    @Test
    fun tappingTheLicenseAsksToOpenIt() {
        show()

        rule.onNodeWithText(text(R.string.about_license)).performClick()

        assertEquals(listOf("licenseOpen"), calls)
    }

    @Test
    fun tappingTheVersionIsReported() {
        show()

        rule.onNodeWithText(text(R.string.about_version)).performClick()

        assertEquals(listOf("versionTap"), calls)
    }

    @Test
    fun theLicenseTextIsShownInADialogThatCanBeClosed() {
        show(AboutUiState(license = LicenseState.Shown("GNU GENERAL PUBLIC LICENSE")))

        rule.onNodeWithText("GNU GENERAL PUBLIC LICENSE").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.about_close)).performClick()

        assertEquals(listOf("licenseClose"), calls)
    }

    @Test
    fun anUnreadableLicenseSaysSo() {
        show(AboutUiState(license = LicenseState.Unreadable))

        rule.onNodeWithText(text(R.string.about_license_unreadable)).assertIsDisplayed()
    }

    @Test
    fun noDialogIsShownWhileTheLicenseIsHidden() {
        show()

        rule.onAllNodesWithText(text(R.string.about_close)).assertCountEquals(0)
    }

    @Test
    fun theEasterEggIsAnnouncedOnceAndThenCleared() {
        show(AboutUiState(easterEgg = true))

        rule.onNodeWithText(text(R.string.about_easter_egg)).assertIsDisplayed()
        rule.waitUntil(timeoutMillis = SNACKBAR_WAIT_MS) { calls == listOf("eggShown") }
    }

    @Test
    fun backLeavesTheScreen() {
        show()

        rule.onNodeWithContentDescription(text(R.string.navigate_up)).performClick()

        assertEquals(listOf("back"), calls)
    }

    private companion object {
        const val SNACKBAR_WAIT_MS = 10_000L
    }
}
