package app.brokoli5191.quote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every database version an installed app may have must migrate to the current one. A missing
 * migration used to fall back to recreating the database, which silently deleted own quotes and
 * favorites; now it would crash, so this test catches the gap before a release.
 */
class MigrationChainTest {
    private val currentVersion = DATABASE_VERSION

    @Test
    fun everyOldVersionHasAPathToTheCurrentOne() {
        val byStart = AppDatabase.MIGRATIONS.associateBy { it.startVersion }
        val oldest = AppDatabase.RECREATED_VERSIONS.max() + 1
        for (version in oldest until currentVersion) {
            var reached = version
            while (reached < currentVersion) {
                val step = byStart[reached] ?: error("No migration from database version $reached")
                assertTrue("Migration ${step.startVersion}->${step.endVersion} goes backwards", step.endVersion > reached)
                reached = step.endVersion
            }
            assertEquals(currentVersion, reached)
        }
    }

    @Test
    fun migrationsDoNotOverlap() {
        val starts = AppDatabase.MIGRATIONS.map { it.startVersion }
        assertEquals("Two migrations start at the same version", starts.size, starts.toSet().size)
    }
}
