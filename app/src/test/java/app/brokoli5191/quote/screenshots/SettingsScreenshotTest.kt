package app.brokoli5191.quote.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import org.junit.Test
import org.robolectric.ParameterizedRobolectricTestRunner

class SettingsScreenshotTest(configId: String) : ScreenshotTestBase(configId) {

    private fun scheduledText(): String = text("Scheduled daily at {time}").substringBefore("{time}").trim()

    // Scrolling to the setting below the reminder card puts the whole card above the bottom bar.
    private fun scrollToReminder() {
        compose.onNodeWithText(text("Blur Navigation Surfaces")).performScrollTo()
        compose.settle()
    }

    @Test
    fun top() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Settings") }
        compose.settle()
        capture("settings", "top")
    }

    @Test
    fun reminderSection() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Settings") }
        compose.settle()
        scrollToReminder()
        capture("settings", "reminder")
    }

    @Test
    fun timePickerDialog() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Settings") }
        compose.settle()
        scrollToReminder()
        compose.onNodeWithText(scheduledText(), substring = true).assertExists()
        compose.onNode(hasClickAction() and hasText(text("Tap to change")))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.settle()
        capture("settings", "time_picker")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ShotConfig.parameters()
    }
}
