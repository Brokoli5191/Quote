package app.brokoli5191.quote.screenshots

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Test
import org.robolectric.ParameterizedRobolectricTestRunner

class SavedScreenshotTest(configId: String) : ScreenshotTestBase(configId) {

    @Test
    fun favorites() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Saved") }
        compose.awaitCondition { app.viewModel.favorites.value.isNotEmpty() }
        capture("saved", "favorites")
    }

    @Test
    fun myQuotes() {
        val app = launch()
        app.viewModel.selectSavedSubTab("My Quotes")
        compose.setContent { TabFrame(app.viewModel, "Saved") }
        compose.awaitCondition { app.viewModel.userAdded.value.size == FakeQuotes.personal.size }
        // Top: pending and approved. Each later shot scrolls the next card's text into view, so the
        // card of interest sits fully above the bottom bar.
        capture("saved", "my_quotes_1_pending_approved")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Every sunrise", substring = true))
        capture("saved", "my_quotes_2_rejected")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Write it down", substring = true))
        capture("saved", "my_quotes_3_removed")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ShotConfig.parameters()
    }
}
