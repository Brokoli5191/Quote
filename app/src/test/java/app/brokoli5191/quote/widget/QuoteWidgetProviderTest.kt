package app.brokoli5191.quote.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.brokoli5191.quote.data.QuoteLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuoteWidgetProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `settings restored from another device do not stick to a new widget with the same id`() {
        // Before the app started once the list does not exist yet: nothing is dropped.
        WidgetConfig.write(context, 12, WidgetConfig(font = "Monospace"))
        WidgetConfig.dropForeign(context, 12)
        assertTrue(WidgetConfig.exists(context, 12))

        // Auto Backup brought widget 12's settings along, but this install never had widget 12.
        WidgetConfig.markKnown(context, emptyList())
        WidgetConfig.dropForeign(context, 12)
        assertFalse(WidgetConfig.exists(context, 12))

        // A widget this install rendered keeps its settings.
        WidgetConfig.write(context, 13, WidgetConfig(font = "Sans"))
        WidgetConfig.markKnown(context, listOf(13))
        WidgetConfig.dropForeign(context, 13)
        assertEquals("Sans", WidgetConfig.read(context, 13).font)
    }

    @Test
    fun `widgets the launcher restored count as known`() {
        WidgetConfig.write(context, 20, WidgetConfig(font = "Sans"))
        restoreWidgetConfigs(context, intArrayOf(20), intArrayOf(21), placedIds = setOf(21))
        WidgetConfig.dropForeign(context, 21)
        assertEquals("Sans", WidgetConfig.read(context, 21).font)
    }

    @Test
    fun `restore moves settings when old and new ids overlap`() {
        WidgetConfig.write(context, 3, WidgetConfig(font = "Sans"))
        WidgetConfig.write(context, 4, WidgetConfig(font = "Monospace"))

        restoreWidgetConfigs(context, intArrayOf(3, 4), intArrayOf(4, 5), placedIds = setOf(4, 5))

        assertFalse(WidgetConfig.exists(context, 3))
        assertEquals("Sans", WidgetConfig.read(context, 4).font)
        assertEquals("Monospace", WidgetConfig.read(context, 5).font)
    }

    @Test
    fun `restored widget without settings drops stale settings under its new id`() {
        WidgetConfig.write(context, 7, WidgetConfig(font = "Sans"))

        restoreWidgetConfigs(context, intArrayOf(3), intArrayOf(7), placedIds = setOf(7))

        assertFalse(WidgetConfig.exists(context, 7))
    }

    @Test
    fun `old id of a widget placed on this device keeps its settings`() {
        WidgetConfig.write(context, 2, WidgetConfig(font = "Sans"))

        restoreWidgetConfigs(context, intArrayOf(2), intArrayOf(8), placedIds = setOf(2, 8))

        assertEquals("Sans", WidgetConfig.read(context, 8).font)
        assertTrue(WidgetConfig.exists(context, 2))
    }

    @Test
    fun `content description reads label, quote and author in the app language`() {
        assertEquals(
            "Daily Quote: \"Be yourself.\" — Oscar Wilde",
            widgetContentDescription("Be yourself.", "Oscar Wilde", QuoteLanguage.ENGLISH)
        )
        assertEquals(
            "Tägliches Quote: „Sei du selbst.“ — Oscar Wilde",
            widgetContentDescription("Sei du selbst.", "Oscar Wilde", QuoteLanguage.GERMAN)
        )
        assertEquals("Daily Quote: \"Be yourself.\"", widgetContentDescription("Be yourself.", "", QuoteLanguage.ENGLISH))
    }
}
