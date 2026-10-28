package app.brokoli5191.quote.screenshots

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import app.brokoli5191.quote.ui.screens.NewQuoteScreen
import org.junit.Test
import org.robolectric.ParameterizedRobolectricTestRunner

class NewQuoteScreenshotTest(configId: String) : ScreenshotTestBase(configId) {

    @Test
    fun emptyForm() {
        val app = launch()
        app.viewModel.selectSavedSubTab("My Quotes")
        compose.setContent {
            TabFrame(app.viewModel, "Saved") {
                NewQuoteScreen(
                    viewModel = app.viewModel,
                    onBack = {},
                    bottomInsets = WindowInsets.ime.union(
                        WindowInsets.navigationBars.add(WindowInsets(bottom = BottomBarHeight))
                    )
                )
            }
        }
        compose.settle()
        capture("new_quote", "empty")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ShotConfig.parameters()
    }
}
