package app.brokoli5191.quote.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.ui.localizeUi
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests run once per [ShotConfig] (see [ShotConfig.all]). Images are written only in
 * Roborazzi record mode: `./gradlew :app:recordRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE, sdk = [36])
abstract class ScreenshotTestBase(configId: String) {
    protected val config = ShotConfig.parse(configId)

    @get:Rule(order = 0)
    val environment = ScreenshotEnvironmentRule(config)

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var app: ScreenshotApp? = null

    protected fun launch(
        prefs: Map<String, Any> = emptyMap(),
        quotes: List<QuoteEntity> = FakeQuotes.all,
        dailyQuoteText: String? = null,
        showAnonymous: Boolean = false,
    ): ScreenshotApp = ScreenshotApp(
        config = config,
        prefs = prefs,
        quotes = quotes,
        dailyQuoteText = dailyQuoteText,
        showAnonymous = showAnonymous
    ).also { app = it }

    /** UI string in this run's app language. */
    protected fun text(english: String): String = localizeUi(english, config.language)

    protected fun capture(screen: String, state: String) = compose.capture(screen, state, config)

    @After
    fun closeApp() {
        app?.close()
    }
}
