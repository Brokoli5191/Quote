package app.brokoli5191.quote.screenshots

import android.graphics.BitmapFactory
import coil.Coil
import org.junit.Test
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The README screenshots (.github/screenshots): English, dark theme, 1080 x 2196 px like the
 * earlier device captures with the status and navigation bar cropped (Robolectric draws no system
 * bars, so the device is simply that much shorter). Copied over the README images only in Roborazzi
 * record mode: `./gradlew :app:recordRoborazziDebug`.
 */
@Config(qualifiers = README_DEVICE)
class ReadmeScreenshotTest(configId: String) : ScreenshotTestBase(configId) {

    private val startedAt = System.currentTimeMillis()

    private fun readmeShot(name: String) {
        capture("readme", name)
        val recorded = File("$OUTPUT_DIR/readme_${name}_${config.id}.png")
        // Outside record mode nothing new was written; leave the README images alone.
        if (!recorded.exists() || recorded.lastModified() < startedAt - 2_000) return
        recorded.copyTo(File(README_SCREENSHOT_DIR, "$name.png"), overwrite = true)
    }

    @Test
    fun daily() {
        val app = launch(dailyQuoteText = FakeQuotes.MARCUS)
        // A real portrait instead of the generated placeholder: public-domain photo of the
        // Marcus Aurelius bust in the Glyptothek Munich (Bibi Saint-Pol, Wikimedia Commons,
        // File:Marcus_Aurelius_Glyptothek_Munich.jpg).
        val portrait = javaClass.getResourceAsStream("/readme/marcus_aurelius.jpg").use { BitmapFactory.decodeStream(it) }
        Coil.setImageLoader(fakeImageLoader(app.context, portrait))
        compose.setContent { TabFrame(app.viewModel, "Daily") }
        // Same fallback as DailyScreenshotTest for the occasionally unresumed init load.
        if (!compose.tryAwait(3_000) { app.viewModel.dailyQuote.value != null }) app.publishDailyQuote()
        compose.awaitCondition { app.viewModel.dailyQuote.value != null }
        compose.awaitCondition { app.viewModel.authorInfo("Marcus Aurelius").value != null }
        readmeShot("daily")
    }

    @Test
    fun library() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Library") }
        compose.settle()
        readmeShot("library")
    }

    @Test
    fun settings() {
        val app = launch()
        compose.setContent { TabFrame(app.viewModel, "Settings") }
        compose.settle()
        readmeShot("settings")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = listOf(arrayOf<Any>("en_dark"))
    }
}

/** 1080 x 2196 px: the README images' size (screen minus system bars on a 1080 px wide phone). */
private const val README_DEVICE = "w360dp-h732dp-xxhdpi"

/** Relative to the app module, the working directory of unit tests. */
private const val README_SCREENSHOT_DIR = "../.github/screenshots"
