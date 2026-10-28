package app.brokoli5191.quote.screenshots

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.brokoli5191.quote.data.QuoteLanguage
import org.junit.Test
import org.robolectric.ParameterizedRobolectricTestRunner

class LibraryScreenshotTest(configId: String) : ScreenshotTestBase(configId) {

    @Test
    fun bentoHome() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Library") }
        compose.settle()
        capture("library", "bento_home")
    }

    @Test
    fun searchResults() {
        val app = launch()
        app.viewModel.setSearchQuery(if (config.language == QuoteLanguage.GERMAN) "Leben" else "life")
        compose.setContent { TabFrame(app.viewModel, "Library") }
        compose.awaitCondition { app.viewModel.filteredQuotes.value.isNotEmpty() }
        capture("library", "search_results")
    }

    @Test
    fun categoryFilterSheet() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Library") }
        compose.settle()
        compose.onNodeWithContentDescription(text("Filter Categories")).performClick()
        compose.settle()
        capture("library", "filter_sheet")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ShotConfig.parameters()
    }
}
