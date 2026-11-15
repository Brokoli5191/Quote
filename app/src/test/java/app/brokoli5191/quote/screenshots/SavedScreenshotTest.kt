package app.brokoli5191.quote.screenshots

import android.os.Looper
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import app.brokoli5191.quote.ui.SavedStatusFilter
import org.junit.Test
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf

class SavedScreenshotTest(configId: String) : ScreenshotTestBase(configId) {

    // ViewModel work resumes on the main looper, which waiting alone does not run.
    private fun awaitMain(condition: () -> Boolean) = compose.awaitCondition {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    @Test
    fun favorites() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Saved") }
        awaitMain { app.viewModel.favorites.value.isNotEmpty() }
        capture("saved", "favorites")
    }

    @Test
    fun favoritesTranslationToggled() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Saved") }
        awaitMain { app.viewModel.favorites.value.isNotEmpty() }
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
        awaitMain { app.viewModel.favorites.value.any { it.author.startsWith("Johann") } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Johann", substring = true))
        capture("saved", "favorites_long_author")
    }

    @Test
    fun favoritesSelection() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Saved") }
        awaitMain { app.viewModel.favorites.value.size > 1 }
        // The contextual bar replaces the title; the selected card is outlined.
        compose.onNode(hasText(app.viewModel.favorites.value[0].text, substring = true)).performTouchInput { longClick() }
        compose.settle()
        capture("saved", "favorites_selection")
    }

    @Test
    fun favoritesNoMatch() {
        val app = launch()
        app.viewModel.setSavedSearchQuery("zzzz")
        compose.setContent { TabFrame(app.viewModel, "Saved") }
        awaitMain { app.viewModel.favoritesTotal.value > 0 && app.viewModel.favorites.value.isEmpty() }
        capture("saved", "favorites_no_match")
    }

    @Test
    fun myQuotes() {
        val app = launch()
        app.viewModel.selectSavedSubTab("My Quotes")
        compose.setContent { TabFrame(app.viewModel, "Saved") }
        awaitMain { app.viewModel.userAdded.value.size == FakeQuotes.personal.size }
        // Top: pending and approved. Each later shot scrolls the next card's text into view, so the
        // card of interest sits fully above the bottom bar.
        capture("saved", "my_quotes_1_pending_approved")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Every sunrise", substring = true))
        capture("saved", "my_quotes_2_rejected")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Write it down", substring = true))
        capture("saved", "my_quotes_3_removed")
    }

    @Test
    fun myQuotesPendingOnly() {
        val app = launch()
        app.viewModel.selectSavedSubTab("My Quotes")
        app.viewModel.setSavedStatusFilter(SavedStatusFilter.PENDING)
        compose.setContent { TabFrame(app.viewModel, "Saved") }
        awaitMain { app.viewModel.userAdded.value.size == 1 }
        capture("saved", "my_quotes_pending_filter")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ShotConfig.parameters()
    }
}
