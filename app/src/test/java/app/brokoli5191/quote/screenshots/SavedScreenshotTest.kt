package app.brokoli5191.quote.screenshots

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
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
    fun favoritesTranslationToggled() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Saved") }
        compose.awaitCondition { app.viewModel.favorites.value.isNotEmpty() }
        // Only the first card switches; the cards below it follow its new height.
        compose.onAllNodes(
            hasContentDescription(text("Show in German")) or hasContentDescription(text("Show in English"))
        )[0].performClick()
        compose.settle()
        capture("saved", "favorites_translation_toggled")
    }

    @Test
    fun favoritesLongAuthor() {
        // A multi-word name whose words each fit beside the actions but need more than two lines there.
        val app = launch(quotes = FakeQuotes.all.map {
            if (it.author == "Johann Wolfgang von Goethe") {
                it.copy(isFavorite = true, savedDate = (System.currentTimeMillis() + 60_000L).toString())
            } else it
        })
        compose.setContent { TabFrame(app.viewModel, "Saved") }
        compose.awaitCondition { app.viewModel.favorites.value.any { it.author.startsWith("Johann") } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Johann", substring = true))
        capture("saved", "favorites_long_author")
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
