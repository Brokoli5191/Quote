package app.brokoli5191.quote

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.screenshots.ScreenshotApp
import app.brokoli5191.quote.screenshots.ScreenshotEnvironmentRule
import app.brokoli5191.quote.screenshots.ShotConfig
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.utils.AppLinks
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Entry points (widget, notification, shortcut, share sheet) reaching MainActivity. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class MainActivityAppLinkTest {
    private val config = ShotConfig.parse("en_dark")

    @get:Rule(order = 0)
    val environment = ScreenshotEnvironmentRule(config)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private var app: ScreenshotApp? = null
    private var controller: ActivityController<MainActivity>? = null

    @After
    fun close() {
        controller?.pause()?.stop()?.destroy()
        awaitRepositoryWrites()
        app?.close()
    }

    @Test
    fun aLinkWaitsForOnboardingAndSurvivesRecreation() {
        val activity = launch(openIntent(AppLinks.DEST_FAVORITES), onboardingCompleted = false)
        val viewModel = viewModelOf(activity)
        compose.waitForIdle()
        assertTrue(viewModel.showOnboarding.value)
        assertEquals("Daily", viewModel.selectedTab.value)

        // Rotating during onboarding keeps the request, but a recreated activity does not read its
        // launch intent again.
        val recreated = controller!!.recreate().get()
        compose.waitForIdle()
        assertTrue(viewModelOf(recreated) === viewModel)
        assertEquals("Daily", viewModel.selectedTab.value)

        compose.runOnUiThread { viewModel.completeOnboarding() }
        compose.waitForIdle()
        assertEquals("Saved", viewModel.selectedTab.value)
        assertEquals("Favorites", viewModel.savedSubTab.value)

        compose.runOnUiThread { viewModel.selectTab("Library") }
        controller!!.recreate()
        compose.waitForIdle()
        assertEquals("an opened link is not opened again", "Library", viewModel.selectedTab.value)
    }

    @Test
    fun aRunningActivityOpensNewLinksWithoutRestarting() {
        val activity = launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        val viewModel = viewModelOf(activity)
        compose.runOnUiThread { viewModel.selectTab("Settings") }
        compose.waitForIdle()

        controller!!.newIntent(openIntent(AppLinks.DEST_DAILY))
        compose.waitForIdle()
        assertEquals("Daily", viewModel.selectedTab.value)
        assertTrue("the same activity and view model", viewModelOf(controller!!.get()) === viewModel)

        controller!!.newIntent(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "\"Be yourself.\" — Oscar Wilde")
        )
        compose.waitForIdle()
        assertTrue(viewModel.showNewQuoteScreen.value)
        assertEquals("Saved", viewModel.selectedTab.value)
        assertEquals("My Quotes", viewModel.savedSubTab.value)

        // New Quote holds a draft now: another entry point selects its tab but keeps the form open.
        compose.runOnUiThread { viewModel.setNewQuoteHasDraft(true) }
        controller!!.newIntent(openIntent(AppLinks.DEST_FAVORITES))
        compose.waitForIdle()
        assertTrue(viewModel.showNewQuoteScreen.value)
        assertEquals("Favorites", viewModel.savedSubTab.value)
    }

    @Test
    fun aPlainLaunchOpensNothing() {
        val activity = launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        val viewModel = viewModelOf(activity)
        compose.waitForIdle()
        assertEquals("Daily", viewModel.selectedTab.value)
        assertFalse(viewModel.showNewQuoteScreen.value)
    }

    private fun openIntent(destination: String) =
        Intent(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(AppLinks.EXTRA_DEST, destination)

    private fun launch(intent: Intent, onboardingCompleted: Boolean = true): MainActivity {
        // Prefs, in-memory database and WorkManager for the activity's own ViewModel.
        app = ScreenshotApp(config = config, prefs = mapOf("onboarding_completed" to onboardingCompleted))
        val launched = Robolectric.buildActivity(MainActivity::class.java, intent).also { controller = it }
        return launched.setup().get()
    }

    // The activity created its ViewModel in onCreate; the provider returns that instance.
    private fun viewModelOf(activity: MainActivity): QuoteViewModel =
        ViewModelProvider(activity)[QuoteViewModel::class.java]

    // The repository runs its locked sections (seeding, daily pick) on Dispatchers.IO, so one the
    // destroyed activity started can still be querying when the test ends. Taking the process-wide
    // write lock once lets it finish before the database closes.
    private fun awaitRepositoryWrites() {
        val field = QuoteRepository::class.java.getDeclaredField("writeLock")
        field.isAccessible = true
        runBlocking { (field.get(null) as Mutex).withLock { } }
    }
}
