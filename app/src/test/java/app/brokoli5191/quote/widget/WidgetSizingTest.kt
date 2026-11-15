package app.brokoli5191.quote.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class WidgetSizingTest {
    @Test
    fun `widgets render at the display density`() {
        val sizes = widgetBitmapSizes(listOf(130f to 102f, 269f to 51f), 2.75f, 1080, 2400)
        assertEquals(WidgetBitmapSize(357, 280, 2.75f), sizes[0])
        assertEquals(WidgetBitmapSize(739, 140, 2.75f), sizes[1])
    }

    @Test
    fun `large widgets on low density screens stay below the AppWidgetService limit`() {
        // hdpi phone and mdpi tablet with a 4x3 widget in both orientations
        listOf(Triple(1.5f, 480, 800), Triple(1f, 1024, 600)).forEach { (density, screenWidth, screenHeight) ->
            val sizes = widgetBitmapSizes(listOf(300f to 270f, 560f to 200f), density, screenWidth, screenHeight)
            val bytes = sizes.sumOf { 4L * it.widthPx * it.heightPx }
            assertTrue("$bytes bytes on $screenWidth x $screenHeight", bytes <= 6L * screenWidth * screenHeight / 2)
        }
    }

    @Test
    fun `scaling down keeps the aspect ratio and the dp layout`() {
        val size = widgetBitmapSizes(listOf(1000f to 800f), 3f, 1080, 2400).single()
        assertTrue(size.density < 3f)
        assertEquals(1000f / 800f, size.widthPx.toFloat() / size.heightPx, 0.01f)
        assertEquals(1000f * size.density, size.widthPx.toFloat(), 1f)
    }

    @Test
    fun `afternoon arms the coming midnight`() {
        assertEquals(
            at(BERLIN, 2026, 11, 16, 0, 0, 5),
            WidgetRefreshScheduler.nextMidnightRefreshAt(at(BERLIN, 2026, 11, 15, 15, 0), BERLIN)
        )
    }

    @Test
    fun `a refresh right after midnight arms the next night`() {
        assertEquals(
            at(BERLIN, 2026, 11, 17, 0, 0, 5),
            WidgetRefreshScheduler.nextMidnightRefreshAt(at(BERLIN, 2026, 11, 16, 0, 0, 5), BERLIN)
        )
    }

    @Test
    fun `the refresh follows the start of the next local day, also across DST changes`() {
        // Santiago, Havana and Beirut skip midnight on their DST start in 2026; Havana repeats it in November.
        listOf("Europe/Berlin", "America/Santiago", "America/Havana", "Asia/Beirut", "Asia/Kolkata").forEach { id ->
            val zone = TimeZone.getTimeZone(id)
            var now = at(zone, 2026, 1, 1, 0, 30)
            repeat(366 * 4) {
                // The refresh runs 5 s after the day starts.
                val dayStart = WidgetRefreshScheduler.nextMidnightRefreshAt(now, zone) - 5_000
                assertEquals("$id after ${dateKey(now, zone)}", nextDateKey(now, zone), dateKey(dayStart, zone))
                assertNotEquals("$id after ${dateKey(now, zone)}", dateKey(dayStart - 1, zone), dateKey(dayStart, zone))
                now += 6 * 60 * 60 * 1000L
            }
        }
    }

    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    private fun dateKey(millis: Long, zone: TimeZone): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(millis)

    private fun nextDateKey(millis: Long, zone: TimeZone): String {
        val next = Calendar.getInstance(zone).apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return dateKey(next.timeInMillis, zone)
    }

    private companion object {
        val BERLIN: TimeZone = TimeZone.getTimeZone("Europe/Berlin")
    }
}
