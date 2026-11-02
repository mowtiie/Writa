package com.mowtiie.writa.feature.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.mowtiie.writa.R
import com.mowtiie.writa.core.datastore.AppSettings
import com.mowtiie.writa.core.datastore.Theme
import com.mowtiie.writa.core.ui.theme.Contrast
import com.mowtiie.writa.core.ui.theme.WritaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    private var state by mutableStateOf(SettingsUiState())
    private val calls = mutableListOf<String>()

    private val actions =
        SettingsActions(
            onThemeChange = { calls += "theme:${it.key}" },
            onContrastChange = { calls += "contrast:${it.key}" },
            onDynamicColorChange = { calls += "dynamic:$it" },
            onScreenPrivacyChange = { calls += "privacy:$it" },
            onExport = { calls += "export" },
            onImport = { calls += "import" },
            onImportConfirm = { calls += "importConfirm" },
            onImportDismiss = { calls += "importDismiss" },
            onChangeServer = { calls += "changeServer" },
            onMessageShown = {
                calls += "shown"
                state = state.copy(message = null)
            },
        )

    private fun show(
        initial: SettingsUiState = SettingsUiState(),
        dynamicColorAvailable: Boolean = true,
    ) {
        state = initial
        rule.setContent {
            WritaTheme {
                SettingsScreen(state, actions, dynamicColorAvailable, onBack = { calls += "back" })
            }
        }
    }

    // ---------------------------- what is shown ----------------------------

    @Test
    fun everySectionAndSettingIsShown() {
        show()

        rule.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
        for (
        id in
        listOf(
            R.string.settings_appearance,
            R.string.settings_theme,
            R.string.settings_contrast,
            R.string.settings_dynamic_color,
            R.string.settings_security,
            R.string.settings_screen_privacy,
            R.string.settings_backup,
            R.string.settings_import,
            R.string.settings_export,
            R.string.settings_configuration,
            R.string.settings_change_server,
        )
        ) {
            rule.onNodeWithText(text(id)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun theTitleAndTheSectionsAreHeadings() {
        show()

        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        for (id in listOf(R.string.settings_title, R.string.settings_appearance, R.string.settings_backup)) {
            rule.onNodeWithText(text(id)).assert(heading)
        }
    }

    @Test
    fun theCurrentThemeAndContrastAreShownAsTheSummary() {
        show(SettingsUiState(settings = AppSettings(theme = Theme.Dark, contrast = Contrast.High)))

        rule.onNodeWithText(text(R.string.settings_theme_dark)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.settings_contrast_high)).assertIsDisplayed()
    }

    @Test
    fun dynamicColorIsOfferedOnlyWhereTheSystemHasIt() {
        show(dynamicColorAvailable = false)

        rule.onAllNodesWithText(text(R.string.settings_dynamic_color)).assertCountEquals(0)
    }

    @Test
    fun backIsAvailable() {
        show()

        rule.onNodeWithContentDescription(text(R.string.navigate_up)).performClick()

        assertEquals(listOf("back"), calls)
    }

    // ---------------------------- appearance and privacy ----------------------------

    @Test
    fun choosingAThemeOffersAllOfThemAndAppliesTheChoice() {
        show()

        rule.onNodeWithText(text(R.string.settings_theme)).performClick()
        for (
        id in
        listOf(
            R.string.settings_theme_battery,
            R.string.settings_theme_light,
            R.string.settings_theme_dark,
        )
        ) {
            rule.onNodeWithText(text(id)).assertIsDisplayed()
        }
        rule.onNodeWithText(text(R.string.settings_theme_dark)).performClick()

        assertEquals(listOf("theme:dark"), calls)
        rule.onAllNodesWithText(text(R.string.settings_theme_light)).assertCountEquals(0)
    }

    @Test
    fun choosingAContrastAppliesTheChoice() {
        show()

        rule.onNodeWithText(text(R.string.settings_contrast)).performClick()
        rule.onNodeWithText(text(R.string.settings_contrast_medium)).performClick()

        assertEquals(listOf("contrast:contrast_medium"), calls)
    }

    @Test
    fun theSwitchesShowAndChangeTheirSetting() {
        show(SettingsUiState(settings = AppSettings(screenPrivacy = true)))

        rule.onNodeWithText(text(R.string.settings_screen_privacy)).assertIsOn()
        rule.onNodeWithText(text(R.string.settings_screen_privacy)).performClick()
        rule.onNodeWithText(text(R.string.settings_dynamic_color)).performClick()

        assertEquals(listOf("privacy:false", "dynamic:true"), calls)
    }

    @Test
    fun theSwitchesAreReadAsSwitches() {
        show()

        rule
            .onNodeWithText(text(R.string.settings_dynamic_color))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }

    // ---------------------------- backup ----------------------------

    @Test
    fun exportAndImportStartTheirFlows() {
        show()

        rule.onNodeWithText(text(R.string.settings_export)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.settings_import)).performScrollTo().performClick()

        assertEquals(listOf("export", "import"), calls)
    }

    @Test
    fun theExportSaysItIsNotEncrypted() {
        show()

        rule.onNodeWithText(text(R.string.settings_export_summary)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aChosenBackupAsksBeforeImporting() {
        show(SettingsUiState(importCount = 3))

        rule.onNodeWithText(text(R.string.settings_import_title)).assertIsDisplayed()
        rule
            .onNodeWithText(context.resources.getQuantityString(R.plurals.settings_import_message, 3, 3))
            .assertIsDisplayed()
        assertTrue(calls.isEmpty())

        rule.onAllNodesWithText(text(R.string.settings_import)).onLast().performClick()

        assertEquals(listOf("importConfirm"), calls)
    }

    @Test
    fun decliningTheImportDismissesIt() {
        show(SettingsUiState(importCount = 3))

        rule.onNodeWithText(text(R.string.notes_confirm_cancel)).performClick()

        assertEquals(listOf("importDismiss"), calls)
    }

    @Test
    fun whileWorkingAProgressMessageIsShown() {
        show(SettingsUiState(busy = Busy.Exporting))

        rule.onNodeWithText(text(R.string.settings_exporting)).assertIsDisplayed()
    }

    @Test
    fun eachResultIsAnnouncedOnceAndThenCleared() {
        show(SettingsUiState(message = SettingsMessage.ImportPartial(200, 500)))

        rule
            .onNodeWithText(context.getString(R.string.settings_import_partial, 200, 500))
            .assertIsDisplayed()
        rule.waitUntil(timeoutMillis = SNACKBAR_WAIT_MS) { calls == listOf("shown") }
    }

    // ---------------------------- change server ----------------------------

    @Test
    fun changingTheServerAsksFirst() {
        show()

        rule.onNodeWithText(text(R.string.settings_change_server)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.settings_change_server_title)).assertIsDisplayed()
        assertTrue(calls.isEmpty())

        rule.onNodeWithText(text(R.string.settings_continue)).performClick()

        assertEquals(listOf("changeServer"), calls)
    }

    @Test
    fun theWarningCountsTheChangesThatWouldBeLost() {
        show(SettingsUiState(pendingChanges = 4))

        rule.onNodeWithText(text(R.string.settings_change_server)).performScrollTo().performClick()

        rule.onNodeWithText("4", substring = true).assertIsDisplayed()
    }

    @Test
    fun cancellingTheServerChangeDoesNothing() {
        show()

        rule.onNodeWithText(text(R.string.settings_change_server)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.notes_confirm_cancel)).performClick()

        assertTrue(calls.isEmpty())
        rule.onAllNodesWithText(text(R.string.settings_change_server_title)).assertCountEquals(0)
    }

    private companion object {
        const val SNACKBAR_WAIT_MS = 10_000L
    }
}
