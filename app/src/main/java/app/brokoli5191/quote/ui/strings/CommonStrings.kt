package app.brokoli5191.quote.ui.strings

import androidx.compose.runtime.Composable
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.localizeUi

// German UI strings shared across areas (dialogs, components, submission errors).
// Keys are the English source text passed to uiText().
internal val commonGermanUi: Map<String, String> = mapOf(
    // Time picker
    "OK" to "OK",
    "Select time" to "Uhrzeit auswählen",
    "Enter time" to "Uhrzeit eingeben",
    "Switch to text input" to "Zur Texteingabe wechseln",
    "Switch to clock" to "Zur Uhransicht wechseln",

    // Translation toggle
    "Show in English" to "Auf Englisch anzeigen",
    "Show in German" to "Auf Deutsch anzeigen",

    // Author avatar
    "Unknown author" to "Unbekannter Autor",

    // Submission errors (see submissionErrorText)
    "No connection. Please check your internet and try again." to "Keine Verbindung. Bitte prüfe deine Internetverbindung und versuche es erneut.",
    "Too many submissions. Please try again later." to "Zu viele Einreichungen. Bitte versuche es später erneut.",
    "This quote has already been submitted." to "Dieses Quote wurde bereits eingereicht.",
    "The quote could not be accepted. Please check text and author." to "Das Quote konnte nicht angenommen werden. Bitte prüfe Text und Autor.",
    "The quote is too long." to "Das Quote ist zu lang.",
    "The server is unavailable right now. Please try again later." to "Der Server ist gerade nicht erreichbar. Bitte versuche es später erneut.",
    "This quote has already been reviewed and can no longer be withdrawn." to "Dieses Quote wurde bereits geprüft und kann nicht mehr zurückgezogen werden.",
    "This submission no longer exists." to "Diese Einreichung existiert nicht mehr.",
    "Something went wrong. Please try again." to "Etwas ist schiefgelaufen. Bitte versuche es erneut."
)

/**
 * English message for a submission error code. Accepts the client codes (e.g. "network", "server")
 * as well as the raw backend codes (e.g. "rate_limited", "server_error"); unknown codes get a
 * generic message.
 */
fun submissionErrorEnglish(code: String?): String = when (code?.trim()?.lowercase()) {
    "network", "connection", "timeout", "offline" -> "No connection. Please check your internet and try again."
    "rate_limited", "rate_limit", "too_many_requests" -> "Too many submissions. Please try again later."
    "duplicate" -> "This quote has already been submitted."
    "validation", "invalid_request" -> "The quote could not be accepted. Please check text and author."
    "too_large" -> "The quote is too long."
    "server", "server_error" -> "The server is unavailable right now. Please try again later."
    "already_reviewed" -> "This quote has already been reviewed and can no longer be withdrawn."
    "not_found" -> "This submission no longer exists."
    else -> "Something went wrong. Please try again."
}

fun submissionErrorText(code: String?, language: String): String =
    localizeUi(submissionErrorEnglish(code), language)

@Composable
fun submissionErrorText(code: String?): String =
    submissionErrorText(code, LocalAppLanguage.current)
