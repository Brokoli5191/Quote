package app.brokoli5191.quote.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.SubmissionError
import app.brokoli5191.quote.data.SubmissionUpdate
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.QuoteUiEvent
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.localizeUi
import app.brokoli5191.quote.utils.AppLinks
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** What an Undo snackbar reverts. */
enum class UndoKind { DELETED, UNFAVORITED }

/**
 * An Undo snackbar for [quotes], the copies the user acted on. Compared by identity, so every
 * request shows, also one with the same content as an earlier one.
 */
class UndoRequest(val kind: UndoKind, val quotes: List<QuoteEntity>)

/** What the button of a [SnackbarMessage] does. */
enum class SnackbarMessageAction { SHOW_MY_QUOTES }

/**
 * A queued snackbar text, already in the app language. Compared by identity. [withdrawalNote] marks
 * the note that a deleted quote's submission is withdrawn later: an Undo of that delete drops it.
 */
class SnackbarMessage(
    val text: String,
    val action: SnackbarMessageAction? = null,
    internal val withdrawalNote: Boolean = false
)

/**
 * The main screen's snackbars. It lives in the activity's ViewModelStore, so an Undo survives a tab
 * switch or a rotation; [QuoteEventsHost] shows what it holds. Get it with [quoteSnackbarController].
 */
class QuoteSnackbarController : ViewModel() {
    private val _undo = MutableStateFlow<UndoRequest?>(null)

    /** The Undo snackbar to show; it goes before queued messages. */
    val undo: StateFlow<UndoRequest?> = _undo.asStateFlow()

    // Shown in order, one at a time.
    internal val messages = mutableStateListOf<SnackbarMessage>()

    // Withdrawal notes queued while the current delete Undo was on screen: they are about those quotes.
    private val notesOfUndo = ArrayList<SnackbarMessage>()

    /**
     * Shows an Undo snackbar now, in place of the one on screen. A request of the same kind joins
     * the one still on screen, so its Undo is not lost: one Undo then restores both.
     */
    fun showUndo(request: UndoRequest) {
        val current = _undo.value
        _undo.value = if (current != null && current.kind == request.kind) {
            UndoRequest(request.kind, (current.quotes + request.quotes).distinctBy { it.id })
        } else {
            // The replaced Undo is gone, so its quotes stay deleted and their notes still apply.
            notesOfUndo.clear()
            request
        }
    }

    /** Queues a message; it shows after the current snackbar. */
    fun showMessage(message: SnackbarMessage) {
        if (message.withdrawalNote && _undo.value?.kind == UndoKind.DELETED) notesOfUndo += message
        messages += message
    }

    /** [request] left the screen; [undone]: its Undo was tapped. */
    internal fun undoFinished(request: UndoRequest, undone: Boolean) {
        if (!_undo.compareAndSet(request, null)) return
        // The restored quotes keep their submissions, so "will be withdrawn" no longer holds.
        if (undone && request.kind == UndoKind.DELETED) messages.removeAll(notesOfUndo)
        notesOfUndo.clear()
    }

    internal fun messageFinished(message: SnackbarMessage) {
        messages.remove(message)
    }
}

/** The activity's [QuoteSnackbarController]. */
@Composable
fun quoteSnackbarController(): QuoteSnackbarController = viewModel()

/**
 * The app's snackbars, above the bottom bar on every tab: what [QuoteViewModel.events] reports
 * (withdrawals of deleted quotes, review decisions found while the app is open), messages screens
 * queue and Undo snackbars, both through [QuoteSnackbarController]. Place it once, in MainContent:
 * the events are delivered to a single collector.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuoteEventsHost(viewModel: QuoteViewModel, modifier: Modifier = Modifier) {
    val controller = quoteSnackbarController()
    val language by rememberUpdatedState(LocalAppLanguage.current)
    val hostState = remember { SnackbarHostState() }
    val activeTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val newQuoteOpen by viewModel.showNewQuoteScreen.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, controller) {
        viewModel.events.collect { event -> controller.showMessage(eventMessage(event, language)) }
    }

    val undo by controller.undo.collectAsStateWithLifecycle()
    // A new Undo cancels whatever is on screen: an older Undo is replaced (or joined, see showUndo),
    // a message comes back once the Undo is done. Messages queued meanwhile wait and never
    // interrupt the Undo.
    LaunchedEffect(undo) {
        val request = undo
        if (request != null) {
            val result = hostState.showSnackbar(
                message = undoMessage(request, language),
                actionLabel = localizeUi("Undo", language),
                duration = SnackbarDuration.Short
            )
            val undone = result == SnackbarResult.ActionPerformed
            if (undone) {
                when (request.kind) {
                    UndoKind.DELETED -> viewModel.restoreDeletedQuotes(request.quotes)
                    UndoKind.UNFAVORITED -> viewModel.restoreFavorites(request.quotes)
                }
            }
            controller.undoFinished(request, undone)
            return@LaunchedEffect
        }
        while (true) {
            val message = snapshotFlow { controller.messages.firstOrNull() }.filterNotNull().first()
            // "Show" is left out where it would lead to the screen already open.
            val action = message.action?.takeUnless {
                it == SnackbarMessageAction.SHOW_MY_QUOTES && viewModel.selectedTab.value == "Saved" &&
                    viewModel.savedSubTab.value == "My Quotes" && !viewModel.showNewQuoteScreen.value
            }
            val result = hostState.showSnackbar(
                message = message.text,
                actionLabel = action?.let { localizeUi("Show", language) },
                withDismissAction = action == null,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed && action == SnackbarMessageAction.SHOW_MY_QUOTES) {
                viewModel.openDestination(AppLinks.DEST_MY_QUOTES)
            }
            controller.messageFinished(message)
        }
    }

    // Above the bottom bar, and on Saved also above its add button; over the keyboard while it shows.
    val bottom = when {
        WindowInsets.isImeVisible -> 8.dp
        activeTab == "Saved" && !newQuoteOpen -> floatingBottomBarHeight() + SavedFabClearance
        else -> floatingBottomBarHeight() + 16.dp
    }
    Box(modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, bottom = bottom)
        )
    }
}

// The Saved add button: 23dp above the bar, 56dp tall, 16dp gap.
private val SavedFabClearance = 95.dp

private const val SNIPPET_LENGTH = 40

internal fun undoMessage(request: UndoRequest, language: String): String {
    val count = request.quotes.size
    return when (request.kind) {
        UndoKind.DELETED ->
            if (count == 1) localizeUi("Quote deleted", language) else countText("%d quotes deleted", count, language)
        UndoKind.UNFAVORITED ->
            if (count == 1) localizeUi("Removed from favorites", language)
            else countText("%d quotes removed from favorites", count, language)
    }
}

/** The snackbar for a ViewModel event, in [language]. */
internal fun eventMessage(event: QuoteUiEvent, language: String): SnackbarMessage = when (event) {
    is QuoteUiEvent.WithdrawalQueued -> SnackbarMessage(
        if (event.count == 1) {
            localizeUi("Deleted. The submission will be withdrawn once Quote can reach the service.", language)
        } else {
            countText(
                "%d quotes deleted. Their submissions will be withdrawn once Quote can reach the service.",
                event.count,
                language
            )
        },
        withdrawalNote = true
    )
    is QuoteUiEvent.WithdrawalFailed -> SnackbarMessage(withdrawalFailedText(event.code, event.count, language))
    is QuoteUiEvent.ReviewDecisions -> SnackbarMessage(
        reviewDecisionsText(event.updates, language),
        SnackbarMessageAction.SHOW_MY_QUOTES
    )
}

// Only refusals that a retry cannot change arrive here (connection and server errors are retried),
// and the quote is gone, so there is nothing to try again: the text names no error to act on.
private fun withdrawalFailedText(code: String, count: Int, language: String): String = when {
    code == SubmissionError.ALREADY_REVIEWED && count == 1 ->
        localizeUi("A quote you deleted could not be withdrawn: a moderator had already reviewed it.", language)
    code == SubmissionError.ALREADY_REVIEWED ->
        countText("%d quotes you deleted could not be withdrawn: a moderator had already reviewed them.", count, language)
    count == 1 -> localizeUi("A quote you deleted could not be withdrawn and may still be published.", language)
    else -> countText("%d quotes you deleted could not be withdrawn and may still be published.", count, language)
}

// Same wording as the notification the background check posts (NotificationHelper.showSubmissionUpdates).
private fun reviewDecisionsText(updates: List<SubmissionUpdate>, language: String): String {
    val update = updates.singleOrNull() ?: return countText("%d updates on your quotes", updates.size, language)
    val template = when (update.newStatus) {
        QuoteSubmissionStatus.APPROVED -> "Your quote “%s” is now in the community collection."
        QuoteSubmissionStatus.REJECTED -> "Your quote “%s” was not approved."
        else -> "Your quote “%s” is no longer published."
    }
    return localizeUi(template, language).format(quoteSnippet(update.quoteText))
}

internal fun quoteSnippet(text: String): String {
    val trimmed = text.trim()
    if (trimmed.codePointCount(0, trimmed.length) <= SNIPPET_LENGTH) return trimmed
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, SNIPPET_LENGTH)).trimEnd() + "…"
}

internal fun countText(english: String, count: Int, language: String): String =
    String.format(if (language == QuoteLanguage.GERMAN) Locale.GERMAN else Locale.ENGLISH, localizeUi(english, language), count)
