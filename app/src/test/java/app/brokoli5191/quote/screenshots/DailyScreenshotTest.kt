package app.brokoli5191.quote.screenshots

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.brokoli5191.quote.data.QuoteLanguage
import org.junit.Test
import org.robolectric.ParameterizedRobolectricTestRunner

class DailyScreenshotTest(configId: String) : ScreenshotTestBase(configId) {

    private fun showDaily(quoteText: String, author: String?, showAnonymous: Boolean = false): ScreenshotApp {
        val app = launch(dailyQuoteText = quoteText, showAnonymous = showAnonymous)
        compose.setContent { TabFrame(app.viewModel, "Daily") }
        // The ViewModel loads the daily quote in init; under Robolectric that load is occasionally
        // never resumed, so fall back to the repository result the ViewModel would have published.
        if (!compose.tryAwait(3_000) { app.viewModel.dailyQuote.value != null }) app.publishDailyQuote()
        compose.awaitCondition { app.viewModel.dailyQuote.value != null }
        if (author != null) compose.awaitCondition { app.viewModel.authorInfo(author).value != null }
        return app
    }

    @Test
    fun authorPortrait() {
        showDaily(FakeQuotes.MARCUS, "Marcus Aurelius")
        capture("daily", "portrait")
    }

    @Test
    fun longQuote() {
        showDaily(FakeQuotes.SENECA_LONG, "Seneca")
        capture("daily", "long_quote")
    }

    @Test
    fun unknownAuthor() {
        showDaily(FakeQuotes.ANONYMOUS, author = null, showAnonymous = true)
        capture("daily", "unknown_author")
    }

    @Test
    fun translationToggled() {
        showDaily(FakeQuotes.GOETHE_DE, "Johann Wolfgang von Goethe")
        // English UI shows the English translation first, German UI the German original.
        val toggle = if (config.language == QuoteLanguage.GERMAN) "Show in English" else "Show in German"
        compose.onNodeWithContentDescription(text(toggle)).performClick()
        compose.settle()
        capture("daily", "translation_toggled")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ShotConfig.parameters()
    }
}
