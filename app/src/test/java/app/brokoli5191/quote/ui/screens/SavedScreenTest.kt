package app.brokoli5191.quote.ui.screens

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import app.brokoli5191.quote.data.CommunitySyncManager
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.screenshots.FakeQuotes
import app.brokoli5191.quote.screenshots.SCREENSHOT_DEVICE
import app.brokoli5191.quote.screenshots.ScreenshotApp
import app.brokoli5191.quote.screenshots.ScreenshotEnvironmentRule
import app.brokoli5191.quote.screenshots.ShotConfig
import app.brokoli5191.quote.screenshots.TabFrame
import app.brokoli5191.quote.screenshots.awaitCondition
import app.brokoli5191.quote.screenshots.settle
import app.brokoli5191.quote.ui.SavedStatusFilter
import app.brokoli5191.quote.ui.components.QuoteEventsHost
import app.brokoli5191.quote.ui.components.QuoteSnackbarController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Saved's behaviour: favorites of own quotes, Undo, the review section, editing, search and selection. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE, sdk = [36])
class SavedScreenTest {
    private val config = ShotConfig.parse("en_dark")

    @get:Rule(order = 0)
    val environment = ScreenshotEnvironmentRule(config)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var app: ScreenshotApp? = null

    @After
    fun closeApp() {
        app?.close()
    }

    private val now = System.currentTimeMillis()
    private val writeItDown = FakeQuotes.personal.first { it.text.startsWith("Write it down") }
    private val coffee = FakeQuotes.personal.first { it.text.startsWith("Coffee first") }

    // Saved with the app's snackbar host, as MainContent shows it.
    private fun showSaved(
        quotes: List<QuoteEntity> = FakeQuotes.all,
        subTab: String = "Favorites",
        restoration: StateRestorationTester? = null,
        prefs: Map<String, Any> = emptyMap()
    ): ScreenshotApp {
        val app = ScreenshotApp(config = config, prefs = prefs, quotes = quotes).also { app = it }
        app.viewModel.selectSavedSubTab(subTab)
        if (restoration != null) {
            restoration.setContent { TabFrame(app.viewModel, "Saved") { QuoteEventsHost(app.viewModel) } }
        } else {
            compose.setContent { TabFrame(app.viewModel, "Saved") { QuoteEventsHost(app.viewModel) } }
        }
        awaitMain {
            (if (subTab == "Favorites") app.viewModel.favorites.value else app.viewModel.userAdded.value).isNotEmpty()
        }
        return app
    }

    // ViewModel work runs on the main looper, which only runs while it is idled.
    private fun awaitMain(condition: () -> Boolean) = compose.awaitCondition {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
        compose.settle()
    }

    private fun SemanticsNodeInteractionCollection.count(): Int = fetchSemanticsNodes().size

    // The sheet is its own dialog window with its own Back dispatcher.
    private fun pressBackOnTheSheet() {
        compose.runOnUiThread {
            val sheet = ShadowDialog.getShownDialogs().last { it.isShowing } as ComponentDialog
            sheet.onBackPressedDispatcher.onBackPressed()
        }
        compose.settle()
    }

    private fun storedQuote(text: String): QuoteEntity? =
        app!!.viewModel.userAdded.value.firstOrNull { it.text.startsWith(text) }

    private fun storedRow(id: Int): QuoteEntity? = runBlocking { app!!.database.quoteDao().getQuoteById(id) }

    @Test
    fun anOwnFavoriteHasTheHeartAndNoReviewControls() {
        val savedDate = (now + 60_000L).toString()
        val app = showSaved(FakeQuotes.all.map { if (it.text == writeItDown.text) it.copy(isFavorite = true, savedDate = savedDate) else it })
        scrollTo("Write it down")

        assertEquals(0, compose.onAllNodesWithContentDescription("Delete", useUnmergedTree = true).count())
        assertEquals(0, compose.onAllNodesWithContentDescription("Edit", useUnmergedTree = true).count())
        assertEquals(0, compose.onAllNodesWithText("Submit for review").count())

        // Unfavoriting the own quote leaves it in My Quotes and can be undone with its saved date.
        compose.onNode(hasText("Write it down", substring = true)).performCustomAccessibilityActionWithLabel("Remove from favorites")
        awaitMain { app.viewModel.favorites.value.none { it.text.startsWith("Write it down") } }
        assertTrue(storedQuote("Write it down") != null)
        compose.onNodeWithText("Removed from favorites").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        awaitMain { app.viewModel.favorites.value.any { it.text.startsWith("Write it down") } }
        assertEquals(savedDate, app.viewModel.favorites.value.first { it.text.startsWith("Write it down") }.savedDate)
    }

    @Test
    fun twoQuickRemovalsShareOneUndo() {
        val app = showSaved()
        val before = app.viewModel.favorites.value.map { it.id }.toSet()
        val first = app.viewModel.favorites.value[0]
        val second = app.viewModel.favorites.value[1]

        compose.onNode(hasText(first.text, substring = true)).performCustomAccessibilityActionWithLabel("Remove from favorites")
        awaitMain { app.viewModel.favorites.value.none { it.id == first.id } }
        compose.onNode(hasText(second.text, substring = true)).performCustomAccessibilityActionWithLabel("Remove from favorites")
        awaitMain { app.viewModel.favorites.value.none { it.id == second.id } }

        compose.onNodeWithText("2 quotes removed from favorites").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        awaitMain { app.viewModel.favorites.value.map { it.id }.toSet() == before }
    }

    @Test
    fun approvedQuotesShowTheReviewersNote() {
        val note = "Fixed the author's name."
        showSaved(
            FakeQuotes.all.map { if (it.isUserAdded && it.submissionStatus == QuoteSubmissionStatus.APPROVED) it.copy(reviewNote = note) else it },
            subTab = "My Quotes"
        )
        scrollTo(note)
        compose.onNodeWithText(note).assertIsDisplayed()
        compose.onNodeWithText("Approved for community").assertIsDisplayed()
    }

    @Test
    fun aRejectedQuoteCanBeEditedWithoutResubmitting() {
        val app = showSaved(FakeQuotes.bundledQuotes + coffee, subTab = "My Quotes")
        compose.onNode(hasContentDescription("Edit") and hasAnySibling(hasText("Edit and resubmit"))).performClick()
        compose.settle()

        compose.onNodeWithText("Edit quote").assertIsDisplayed()
        compose.onNodeWithText("The review stays, because the service still sees the same text and author.").assertIsDisplayed()
        compose.onNodeWithText("Life").performClick()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()

        awaitMain { storedQuote("Coffee first")?.category == "Life" }
        assertEquals(QuoteSubmissionStatus.REJECTED, storedQuote("Coffee first")?.submissionStatus)
    }

    @Test
    fun theEditSheetKeepsItsTextAcrossRecreationAndAsksBeforeDiscarding() {
        val restoration = StateRestorationTester(compose)
        showSaved(FakeQuotes.bundledQuotes + writeItDown, subTab = "My Quotes", restoration = restoration)
        compose.onNodeWithContentDescription("Edit").performClick()
        compose.settle()
        val edited = "Write it down, so tomorrow remembers."
        compose.onNode(hasSetTextAction() and hasText(writeItDown.text)).performTextReplacement(edited)
        compose.settle()

        restoration.emulateSavedInstanceStateRestore()
        compose.settle()
        compose.onNode(hasSetTextAction() and hasText(edited)).assertIsDisplayed()

        // Back on the sheet asks first, and "Keep editing" keeps the sheet and the text.
        pressBackOnTheSheet()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.settle()
        compose.onNode(hasSetTextAction() and hasText(edited)).assertIsDisplayed()

        // "Discard" closes the sheet and leaves the quote as it was.
        pressBackOnTheSheet()
        compose.onNodeWithText("Discard").performClick()
        awaitMain { compose.onAllNodesWithText("Edit quote").count() == 0 }
        assertEquals(writeItDown.text, storedQuote("Write it down")?.text)
    }

    @Test
    fun aSearchWithoutResultsOffersToClearIt() {
        val app = showSaved()
        compose.runOnUiThread { app.viewModel.setSavedSearchQuery("zzzz") }
        awaitMain { app.viewModel.favorites.value.isEmpty() }

        compose.onNodeWithText("No saved quotes match").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("No favorites yet").count())
        compose.onNodeWithText("Clear search and filters").performClick()
        awaitMain { app.viewModel.favorites.value.isNotEmpty() }
        assertEquals("", app.viewModel.savedSearchQuery.value)
    }

    @Test
    fun aLongPressSelectsAndOneUndoRestoresTheBulkRemoval() {
        val app = showSaved()
        val before = app.viewModel.favorites.value.map { it.id }.toSet()
        compose.onNode(hasText(app.viewModel.favorites.value[0].text, substring = true)).performTouchInput { longClick() }
        compose.settle()
        compose.onNodeWithText("1 selected").assertIsDisplayed()

        compose.onNodeWithContentDescription("Select all").performClick()
        compose.settle()
        compose.onNodeWithText("${before.size} selected").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove from favorites").performClick()
        awaitMain { app.viewModel.favorites.value.isEmpty() }
        assertFalse(compose.onAllNodesWithText("selected", substring = true).count() > 0)

        compose.onNodeWithText("${before.size} quotes removed from favorites").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        awaitMain { app.viewModel.favorites.value.map { it.id }.toSet() == before }
    }

    @Test
    fun editAndResubmitSendsTheLanguageChosenInTheSheet() {
        val restoration = StateRestorationTester(compose)
        val app = showSaved(FakeQuotes.bundledQuotes + coffee, subTab = "My Quotes", restoration = restoration)
        val id = storedQuote("Coffee first")!!.id
        assertEquals(QuoteLanguage.ENGLISH, storedRow(id)?.language)
        // Under "Not approved" the edited quote (its review cleared) leaves the list while its dialog is open.
        compose.runOnUiThread { app.viewModel.setSavedStatusFilter(SavedStatusFilter.NOT_APPROVED) }
        compose.settle()

        compose.onNodeWithText("Edit and resubmit").performClick()
        compose.settle()
        compose.onNode(hasSetTextAction() and hasText(coffee.text)).performTextReplacement("Coffee first, philosophy after lunch.")
        compose.onNodeWithText("German").performScrollTo().performClick()
        compose.onNodeWithText("Save and submit").performScrollTo().performClick()
        awaitMain {
            compose.onAllNodesWithText("Submit again?").count() == 1 && compose.onAllNodesWithText("Edit quote").count() == 0
        }
        assertEquals(QuoteSubmissionStatus.NOT_SUBMITTED, storedRow(id)?.submissionStatus)
        assertTrue(app.viewModel.userAdded.value.none { it.id == id })

        // The dialog starts from the sheet's choice, also after a recreation.
        compose.onNode(hasText("German") and isSelectable()).assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.settle()
        compose.onNodeWithText("Submit again?").assertIsDisplayed()
        compose.onNode(hasText("German") and isSelectable()).assertIsSelected()

        // The submission stores the confirmed language first (it then fails: the tests are offline).
        compose.onNodeWithText("Submit again").performScrollTo().performClick()
        awaitMain { app.viewModel.submittingQuoteIds.value.isEmpty() && compose.onAllNodesWithText("Submit again?").count() == 0 }
        assertEquals(QuoteLanguage.GERMAN, storedRow(id)?.language)
    }

    @Test
    fun undoingAnOfflineDeleteDropsTheWithdrawalNote() {
        val pending = FakeQuotes.personal.first { it.submissionStatus == QuoteSubmissionStatus.PENDING }
        val app = showSaved(
            FakeQuotes.bundledQuotes + pending,
            subTab = "My Quotes",
            prefs = mapOf(CommunitySyncManager.KEY_INSTALLATION_ID to "test-installation")
        )
        val snackbars = ViewModelProvider(compose.activity)[QuoteSnackbarController::class.java]
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.settle()
        compose.onNodeWithText("Delete").performClick()

        // Offline, the withdrawal is queued; its note waits behind the Undo instead of replacing it.
        awaitMain { app.viewModel.userAdded.value.isEmpty() && snackbars.messages.isNotEmpty() }
        compose.onNodeWithText("Quote deleted").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("withdrawn once Quote can reach", substring = true).count())

        // Undo brings the quote back in review, so "will be withdrawn" is not shown afterwards.
        compose.onNodeWithText("Undo").performClick()
        awaitMain { storedQuote(pending.text.take(12))?.submissionStatus == QuoteSubmissionStatus.PENDING }
        compose.settle()
        assertTrue(snackbars.messages.isEmpty())
        assertEquals(0, compose.onAllNodesWithText("withdrawn once Quote can reach", substring = true).count())
    }
}
