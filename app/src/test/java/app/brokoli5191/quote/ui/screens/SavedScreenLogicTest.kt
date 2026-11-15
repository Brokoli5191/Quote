package app.brokoli5191.quote.ui.screens

import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteOrigin
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.SubmissionError
import app.brokoli5191.quote.data.SubmissionRefreshResult
import app.brokoli5191.quote.data.SubmissionUpdate
import app.brokoli5191.quote.ui.QuoteUiEvent
import app.brokoli5191.quote.ui.components.QuoteSnackbarController
import app.brokoli5191.quote.ui.components.SnackbarMessage
import app.brokoli5191.quote.ui.components.SnackbarMessageAction
import app.brokoli5191.quote.ui.components.UndoKind
import app.brokoli5191.quote.ui.components.UndoRequest
import app.brokoli5191.quote.ui.components.eventMessage
import app.brokoli5191.quote.ui.components.quoteSnippet
import app.brokoli5191.quote.ui.components.undoMessage
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedScreenLogicTest {
    private val day = 86_400_000L

    private fun millisOf(year: Int, month: Int, dayOfMonth: Int): Long = Calendar.getInstance(TimeZone.getDefault()).apply {
        clear()
        set(year, month, dayOfMonth, 12, 0)
    }.timeInMillis

    private fun own(text: String, author: String = "Sam Rivera", tags: String = "", id: Int = 1) = QuoteEntity(
        id = id,
        text = text,
        author = author,
        category = "Wisdom",
        tags = tags,
        isUserAdded = true,
        origin = QuoteOrigin.PERSONAL
    )

    @Test
    fun germanDatesWriteTheDayAsAnOrdinal() {
        val saved = millisOf(2026, Calendar.OCTOBER, 14)
        val now = saved + 40 * day
        assertEquals("14. Okt. 2026", formatSavedDate(saved.toString(), now, QuoteLanguage.GERMAN))
        assertEquals("14 Oct 2026", formatSavedDate(saved.toString(), now, QuoteLanguage.ENGLISH))
    }

    @Test
    fun recentDatesAreRelativeInBothLanguages() {
        val now = millisOf(2026, Calendar.NOVEMBER, 15)
        assertEquals("just now", formatSavedDate((now - 10_000).toString(), now, QuoteLanguage.ENGLISH))
        assertEquals("gerade eben", formatSavedDate((now - 10_000).toString(), now, QuoteLanguage.GERMAN))
        assertEquals("vor 5 Min.", formatSavedDate((now - 5 * 60_000).toString(), now, QuoteLanguage.GERMAN))
        assertEquals("3 h ago", formatSavedDate((now - 3 * 3_600_000).toString(), now, QuoteLanguage.ENGLISH))
        assertEquals("gestern", formatSavedDate((now - 30 * 3_600_000L).toString(), now, QuoteLanguage.GERMAN))
        assertEquals("vor 3 Tagen", formatSavedDate((now - 3 * day).toString(), now, QuoteLanguage.GERMAN))
        // Backups from before 1.7 stored a formatted date; it is shown as it is.
        assertEquals("05 Nov 2024", formatSavedDate("05 Nov 2024", now, QuoteLanguage.GERMAN))
        assertEquals("", formatSavedDate(null, now, QuoteLanguage.ENGLISH))
    }

    @Test
    fun submittedTextReadsNaturallyInGerman() {
        val now = millisOf(2026, Calendar.NOVEMBER, 15)
        assertEquals("Submitted 3 days ago", submittedText(now - 3 * day, now, QuoteLanguage.ENGLISH))
        assertEquals("Vor 3 Tagen eingereicht", submittedText(now - 3 * day, now, QuoteLanguage.GERMAN))
        assertEquals("Gerade eben eingereicht", submittedText(now - 1_000, now, QuoteLanguage.GERMAN))
        val october = millisOf(2026, Calendar.OCTOBER, 14)
        assertEquals("Submitted on 14 Oct 2026", submittedText(october, october + 40 * day, QuoteLanguage.ENGLISH))
        assertEquals("Am 14. Okt. 2026 eingereicht", submittedText(october, october + 40 * day, QuoteLanguage.GERMAN))
    }

    @Test
    fun privacyPolicyLinkFollowsTheAppLanguage() {
        assertEquals("https://quote.cowsay.win/privacy?lang=de", privacyPolicyUrl(QuoteLanguage.GERMAN))
        assertEquals("https://quote.cowsay.win/privacy?lang=en", privacyPolicyUrl(QuoteLanguage.ENGLISH))
    }

    @Test
    fun submissionCheckCountsCodePointsOfTheStoredText() {
        // Two emoji are two characters for the server (and its database), not four.
        assertEquals("Too short to submit: a quote needs at least 3 characters.", own("😀😀").submissionProblem())
        assertNull(own("a😀b").submissionProblem())
        // 500 emoji fit, although they are 1000 UTF-16 units.
        assertNull(own("😀".repeat(500)).submissionProblem())
        assertEquals("Too long to submit: a quote can have at most 500 characters.", own("😀".repeat(501)).submissionProblem())
        assertEquals("Too many tags to submit (max. 8).", own("Be kind.", tags = (1..9).joinToString { "t$it" }).submissionProblem())
        // The same tag in another case is one tag for the server.
        assertNull(own("Be kind.", tags = (1..8).joinToString { "t$it" } + ", T1").submissionProblem())
        assertEquals("A tag is too long to submit (max. 30 characters each).", own("Be kind.", tags = "x".repeat(31)).submissionProblem())
        assertNull(own("Be kind.", tags = "😀".repeat(30)).submissionProblem())
    }

    @Test
    fun severalQuotesAreJoinedWithBlankLines() {
        val english = own("Be kind.", author = "Sam Rivera")
        val german = QuoteEntity(
            text = "Sei freundlich.",
            author = "Unknown",
            category = "Wisdom",
            language = QuoteLanguage.GERMAN,
            origin = QuoteOrigin.COMMUNITY
        )
        assertEquals("\"Be kind.\" — Sam Rivera\n\n„Sei freundlich.“", joinedQuoteText(listOf(english, german)))
    }

    @Test
    fun checkNowReportsItsResultUnlessDecisionsArrived() {
        val incomplete = SubmissionRefreshResult(complete = false, changed = false, updates = emptyList())
        assertEquals("Some review statuses could not be checked. Try again later.", refreshResultMessage(incomplete, QuoteLanguage.ENGLISH))
        val decided = SubmissionRefreshResult(
            complete = true,
            changed = true,
            updates = listOf(SubmissionUpdate(1, "Be kind.", QuoteSubmissionStatus.PENDING, QuoteSubmissionStatus.APPROVED, null))
        )
        assertNull(refreshResultMessage(decided, QuoteLanguage.ENGLISH))
        val nothingNew = SubmissionRefreshResult(complete = true, changed = false, updates = emptyList())
        assertEquals("Noch keine neuen Prüfergebnisse.", refreshResultMessage(nothingNew, QuoteLanguage.GERMAN))
    }

    @Test
    fun eventsBecomeLocalizedMessages() {
        assertEquals(
            "Deleted. The submission will be withdrawn once Quote can reach the service.",
            eventMessage(QuoteUiEvent.WithdrawalQueued(1), QuoteLanguage.ENGLISH).text
        )
        assertEquals(
            "2 Quotes gelöscht. Ihre Einreichungen werden zurückgezogen, sobald Quote den Dienst erreicht.",
            eventMessage(QuoteUiEvent.WithdrawalQueued(2), QuoteLanguage.GERMAN).text
        )
        assertEquals(
            "A quote you deleted could not be withdrawn: a moderator had already reviewed it.",
            eventMessage(QuoteUiEvent.WithdrawalFailed(SubmissionError.ALREADY_REVIEWED, 1), QuoteLanguage.ENGLISH).text
        )
        // Connection and server errors are retried, so only final refusals arrive; the quote is gone and
        // there is nothing to try again, so no error text is added.
        assertEquals(
            "A quote you deleted could not be withdrawn and may still be published.",
            eventMessage(QuoteUiEvent.WithdrawalFailed(SubmissionError.INVALID_REQUEST, 1), QuoteLanguage.ENGLISH).text
        )
        assertEquals(
            "2 gelöschte Quotes konnten nicht zurückgezogen werden und werden eventuell noch veröffentlicht.",
            eventMessage(QuoteUiEvent.WithdrawalFailed(SubmissionError.UNKNOWN, 2), QuoteLanguage.GERMAN).text
        )
        assertTrue(eventMessage(QuoteUiEvent.WithdrawalQueued(1), QuoteLanguage.ENGLISH).withdrawalNote)
        assertFalse(eventMessage(QuoteUiEvent.WithdrawalFailed(SubmissionError.UNKNOWN, 1), QuoteLanguage.ENGLISH).withdrawalNote)
        val approved = eventMessage(
            QuoteUiEvent.ReviewDecisions(
                listOf(SubmissionUpdate(1, "Be kind.", QuoteSubmissionStatus.PENDING, QuoteSubmissionStatus.APPROVED, null))
            ),
            QuoteLanguage.GERMAN
        )
        assertEquals("Dein Quote „Be kind.“ ist jetzt in der Community-Sammlung.", approved.text)
        assertEquals(SnackbarMessageAction.SHOW_MY_QUOTES, approved.action)
        val two = eventMessage(
            QuoteUiEvent.ReviewDecisions(
                listOf(
                    SubmissionUpdate(1, "Be kind.", QuoteSubmissionStatus.PENDING, QuoteSubmissionStatus.APPROVED, null),
                    SubmissionUpdate(2, "Be brave.", QuoteSubmissionStatus.PENDING, QuoteSubmissionStatus.REJECTED, "No source")
                )
            ),
            QuoteLanguage.ENGLISH
        )
        assertEquals("2 updates on your quotes", two.text)
    }

    @Test
    fun longQuotesAreShortenedBetweenCodePoints() {
        assertEquals("Be kind.", quoteSnippet("  Be kind.  "))
        val snippet = quoteSnippet("a".repeat(39) + "😀" + "tail")
        assertEquals("a".repeat(39) + "😀…", snippet)
    }

    @Test
    fun undoOfTheSameKindJoinsTheOneOnScreen() {
        val controller = QuoteSnackbarController()
        val first = own("One", id = 1)
        val second = own("Two", id = 2)
        controller.showUndo(UndoRequest(UndoKind.DELETED, listOf(first)))
        controller.showUndo(UndoRequest(UndoKind.DELETED, listOf(second)))
        val joined = controller.undo.value!!
        assertEquals(listOf(1, 2), joined.quotes.map { it.id })
        assertEquals("2 quotes deleted", undoMessage(joined, QuoteLanguage.ENGLISH))

        // Another kind replaces it: its Undo restores only what it belongs to.
        val unfavorited = UndoRequest(UndoKind.UNFAVORITED, listOf(first))
        controller.showUndo(unfavorited)
        assertSame(unfavorited, controller.undo.value)
        assertEquals("Aus Favoriten entfernt", undoMessage(unfavorited, QuoteLanguage.GERMAN))
    }

    @Test
    fun undoingADeleteDropsItsWithdrawalNote() {
        val controller = QuoteSnackbarController()
        val note = { eventMessage(QuoteUiEvent.WithdrawalQueued(1), QuoteLanguage.ENGLISH) }
        val other = SnackbarMessage("Copied")

        // Undo tapped: the quote is back with its submission, so "will be withdrawn" is dropped.
        val deleted = UndoRequest(UndoKind.DELETED, listOf(own("One", id = 1)))
        controller.showUndo(deleted)
        val undoneNote = note()
        controller.showMessage(undoneNote)
        controller.showMessage(other)
        controller.undoFinished(deleted, undone = true)
        assertEquals(listOf(other), controller.messages.toList())
        assertNull(controller.undo.value)
        controller.messageFinished(other)

        // Undo not tapped: the note shows after it.
        val expired = UndoRequest(UndoKind.DELETED, listOf(own("Two", id = 2)))
        controller.showUndo(expired)
        val expiredNote = note()
        controller.showMessage(expiredNote)
        controller.undoFinished(expired, undone = false)
        assertEquals(listOf(expiredNote), controller.messages.toList())
        controller.messageFinished(expiredNote)

        // A delete Undo replaced by another kind is gone: its note stays, also when the new Undo is tapped.
        val replaced = UndoRequest(UndoKind.DELETED, listOf(own("Three", id = 3)))
        controller.showUndo(replaced)
        val replacedNote = note()
        controller.showMessage(replacedNote)
        val unfavorited = UndoRequest(UndoKind.UNFAVORITED, listOf(own("Four", id = 4)))
        controller.showUndo(unfavorited)
        controller.undoFinished(unfavorited, undone = true)
        assertEquals(listOf(replacedNote), controller.messages.toList())
    }
}
