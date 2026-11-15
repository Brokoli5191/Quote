package app.brokoli5191.quote.widget

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetConfigTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `normalizeWidgetColor accepts 6 and 8 digit hex with optional hash`() {
        assertEquals("#AABBCC", normalizeWidgetColor("aabbcc"))
        assertEquals("#2DFFFFFF", normalizeWidgetColor(" #2dffffff "))
        assertNull(normalizeWidgetColor(""))
        assertNull(normalizeWidgetColor("#12345"))
        assertNull(normalizeWidgetColor("red"))
        assertNull(normalizeWidgetColor("#GGGGGG"))
    }

    @Test
    fun `parseWidgetColor falls back to the given default for invalid input`() {
        assertEquals(Color.parseColor("#594983"), parseWidgetColor("#12345", "#594983"))
        assertEquals(Color.parseColor("#112233"), parseWidgetColor("112233", "#594983"))
    }

    @Test
    fun `small widgets hide label icon and author like the renderer`() {
        val config = WidgetConfig()
        val small = widgetElementVisibility(config, 130f, 60f)
        assertFalse(small.label)
        assertFalse(small.icon)
        assertFalse(small.author)
        val roomy = widgetElementVisibility(config, 280f, 140f)
        assertTrue(roomy.label && roomy.icon && roomy.author)
        // 0.65 x 102dp = 66dp keeps the author but drops label and icon
        val shrunk = widgetElementVisibility(config.copy(visualScale = 0.65f), 130f, 102f)
        assertFalse(shrunk.label)
        assertFalse(shrunk.icon)
        assertTrue(shrunk.author)
    }

    @Test
    fun `disabled elements stay hidden`() {
        val visible = widgetElementVisibility(WidgetConfig(showAuthor = false), 280f, 140f)
        assertFalse(visible.author)
    }

    @Test
    fun `light presets bring dark text`() {
        widgetBackgroundPresets.forEach { preset ->
            val background = ColorUtils.blendARGB(
                Color.parseColor(preset.start), Color.parseColor(preset.end), 0.5f
            )
            val contrast = ColorUtils.calculateContrast(Color.parseColor(preset.quoteColor), background)
            assertTrue("${preset.name} quote contrast $contrast", contrast >= 4.5)
        }
        val paper = widgetBackgroundPresets.first { it.name == "Paper" }
        assertEquals("#1C1B1F", paper.quoteColor)
    }

    @Test
    fun `position limits match the renderer clamps`() {
        assertEquals(0.12f, WidgetElement.QUOTE.clampY(0f), 0f)
        assertEquals(0.1f, WidgetElement.ICON.clampX(0f), 0f)
        assertEquals(0.2f, WidgetElement.AUTHOR.clampY(0f), 0f)
        assertEquals(0.85f, WidgetElement.QUOTE.clampX(0.95f), 0f)
    }

    @Test
    fun `transfer moves all settings to the new widget id`() {
        WidgetConfig.write(context, 7, WidgetConfig(font = "Sans", quoteColor = "#123456"))
        WidgetConfig.write(context, 70, WidgetConfig(font = "Monospace"))
        assertTrue(WidgetConfig.exists(context, 7))

        WidgetConfig.transfer(context, 7, 9)

        assertFalse(WidgetConfig.exists(context, 7))
        assertEquals("Sans", WidgetConfig.read(context, 9).font)
        assertEquals("#123456", WidgetConfig.read(context, 9).quoteColor)
        assertEquals("Monospace", WidgetConfig.read(context, 70).font)
    }

    @Test
    fun `transfer can keep the source`() {
        WidgetConfig.write(context, 3, WidgetConfig(font = "Sans"))
        WidgetConfig.transfer(context, 3, 4, keepSource = true)
        assertEquals("Sans", WidgetConfig.read(context, 3).font)
        assertEquals("Sans", WidgetConfig.read(context, 4).font)
    }
}
