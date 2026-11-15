package app.brokoli5191.quote.ui.screens

import app.brokoli5191.quote.ui.SyncUiState
import app.brokoli5191.quote.ui.localizeUi
import app.brokoli5191.quote.ui.strings.settingsGermanUi
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTextTest {
    private val utc = TimeZone.getTimeZone("UTC")
    // Newer JDKs put a narrow no-break space before AM/PM.
    private fun String?.plain() = this?.replace('\u202f', ' ')
    private val now = 1_700_000_000_000L // 2023-11-14 22:13:20 UTC

    @Test
    fun restoreToastUsesSingularAndPluralInBothLanguages() {
        assertEquals(
            "Restore complete! Imported 1 custom quote, updated 1 favorite, and restored settings.",
            restoreMessage("en", 1, 1, true)
        )
        assertEquals(
            "Restore complete! Imported 0 custom quotes and updated 3 favorites.",
            restoreMessage("en", 0, 3, false)
        )
        assertEquals(
            "Wiederherstellung abgeschlossen: 1 eigenes Quote importiert, 1 Favorit aktualisiert und Einstellungen wiederhergestellt.",
            restoreMessage("de", 1, 1, true)
        )
        assertEquals(
            "Wiederherstellung abgeschlossen: 2 eigene Quotes importiert und 5 Favoriten aktualisiert.",
            restoreMessage("de", 2, 5, false)
        )
    }

    @Test
    fun backupAndUpdateMessagesHaveGermanEntries() {
        listOf(
            "Could not open the backup file.",
            "This file is not a Quote backup.",
            "This backup was made by a newer version of Quote.",
            "The backup could not be saved.",
            "The backup could not be restored.",
            "No internet connection. Check your connection and try again.",
            "Too many update checks right now. Try again later.",
            "No downloadable update was found.",
            "The update server is having problems. Try again later."
        ).forEach { english ->
            assertTrue(english, settingsGermanUi[english]?.isNotBlank() == true)
            assertEquals(settingsGermanUi[english], localizeUi(english, "de"))
        }
    }

    @Test
    fun syncLineShowsClockTimeToday() {
        val at = now - 60_000
        val en = syncStatusLine(SyncUiState.Success(at), "en", now, utc)!!
        assertTrue(en, en.startsWith("Community quotes: updated 10:12"))
        assertEquals("Community-Quotes: aktualisiert 22:12", syncStatusLine(SyncUiState.Success(at), "de", now, utc))
    }

    @Test
    fun syncLineAddsTheDateForOlderSyncs() {
        val line = syncStatusLine(SyncUiState.Success(now - 3 * 24 * 3_600_000L), "de", now, utc)!!
        assertTrue(line, line.startsWith("Community-Quotes: aktualisiert 11.11."))
    }

    @Test
    fun syncLineReportsFailureAndNothingBeforeTheFirstSync() {
        assertNull(syncStatusLine(SyncUiState.Idle, "en", now, utc))
        assertEquals(
            "Community quotes: last sync failed",
            syncStatusLine(SyncUiState.Failed(now, null), "en", now, utc)
        )
        val failed = syncStatusLine(SyncUiState.Failed(now, now), "en", now, utc)!!
        assertTrue(failed, failed.startsWith("Community quotes: last sync failed, last updated 10:13"))
        assertEquals("Community quotes: syncing…", syncStatusLine(SyncUiState.Running, "en", now, utc))
    }
}
