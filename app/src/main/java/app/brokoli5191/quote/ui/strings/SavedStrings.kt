package app.brokoli5191.quote.ui.strings

// German UI strings owned by the Saved area (and its snackbar host, ui/components/QuoteEventsHost).
// Keys are the English source text passed to uiText().
internal val savedGermanUi: Map<String, String> = mapOf(
    // Header: search, sort, status filter
    "Search saved quotes" to "Gespeicherte Quotes durchsuchen",
    "Close search" to "Suche schließen",
    "Sort" to "Sortieren",
    "Newest first" to "Neueste zuerst",
    "Oldest first" to "Älteste zuerst",
    "Author A–Z" to "Autor A–Z",
    "Quote A–Z" to "Quote A–Z",
    "Not submitted" to "Nicht eingereicht",
    "Pending" to "Ausstehend",
    "Approved" to "Freigegeben",
    "No saved quotes match" to "Keine gespeicherten Quotes gefunden",
    "Try another search or filter." to "Versuche eine andere Suche oder einen anderen Filter.",
    "Clear search and filters" to "Suche und Filter zurücksetzen",

    // Selection
    "Select" to "Auswählen",
    "Select all" to "Alle auswählen",
    "Cancel selection" to "Auswahl beenden",
    "%d selected" to "%d ausgewählt",
    "%d quotes copied" to "%d Quotes kopiert",

    // Card actions and review status
    "Not saved" to "Nicht gespeichert",
    "Remove from favorites" to "Aus Favoriten entfernen",
    "Edit" to "Bearbeiten",
    "Edit and resubmit" to "Bearbeiten und erneut einreichen",
    "Submit again" to "Erneut einreichen",
    "Withdraw" to "Zurückziehen",
    "No longer published" to "Nicht mehr veröffentlicht",
    "This quote was removed from the community collection. To submit it again, change its text or author." to "Dieses Quote wurde aus der Community-Sammlung entfernt. Um es erneut einzureichen, ändere Text oder Autor.",
    "Note from the reviewer" to "Hinweis aus der Prüfung",
    "Reviewed by hand; this can take a few days." to "Die Prüfung erfolgt von Hand und kann ein paar Tage dauern.",
    "Check now" to "Jetzt prüfen",
    "Checking review status" to "Prüfstatus wird abgefragt",

    // Times (saved date, submission date)
    "just now" to "gerade eben",
    "%d min ago" to "vor %d Min.",
    "%d h ago" to "vor %d Std.",
    "yesterday" to "gestern",
    "%d days ago" to "vor %d Tagen",
    "Submitted %s" to "%s eingereicht",
    "Submitted on %s" to "Am %s eingereicht",

    // Local submission checks (same limits as the server)
    "Too short to submit: a quote needs at least 3 characters." to "Zu kurz zum Einreichen: Ein Quote braucht mindestens 3 Zeichen.",
    "Too long to submit: a quote can have at most 500 characters." to "Zu lang zum Einreichen: Ein Quote darf höchstens 500 Zeichen haben.",
    "The author name is too long to submit (max. 100 characters)." to "Der Autorname ist zu lang zum Einreichen (max. 100 Zeichen).",
    "Too many tags to submit (max. 8)." to "Zu viele Tags zum Einreichen (max. 8).",
    "A tag is too long to submit (max. 30 characters each)." to "Ein Tag ist zu lang zum Einreichen (max. 30 Zeichen pro Tag).",
    "Edit the quote to fix this." to "Bearbeite das Quote, um das zu beheben.",

    // Submission consent dialog
    "Submit again?" to "Erneut einreichen?",
    "A moderator reviews the quote. If approved, it is published in the community collection under the author's name and may be slightly corrected." to "Eine Moderation prüft das Quote. Nach der Freigabe wird es unter dem Namen des Autors in der Community-Sammlung veröffentlicht und eventuell leicht korrigiert.",
    "What is sent" to "Was gesendet wird",
    "Quote text" to "Quote-Text",
    "Language of the quote" to "Sprache des Quotes",
    "Decides who sees the quote if you submit it to the community." to "Bestimmt, wer das Quote sieht, wenn du es der Community vorschlägst.",
    "App version" to "App-Version",
    "A random installation ID (used for the review status and spam limits)" to "Eine zufällige Installations-ID (für den Prüfstatus und Spam-Limits)",
    "Your IP address is processed for spam protection. Its hash is deleted after 30 days. Nothing else from your device is sent." to "Deine IP-Adresse wird zum Spamschutz verarbeitet. Ihr Hash wird nach 30 Tagen gelöscht. Sonst wird nichts von deinem Gerät gesendet.",
    "No app can open the link. The policy is at %s" to "Keine App kann den Link öffnen. Die Richtlinie findest du unter %s",
    "This exact quote was already reviewed. Edit it before submitting it again." to "Genau dieses Quote wurde bereits geprüft. Bearbeite es, bevor du es erneut einreichst.",
    "This quote is already published in the community collection." to "Dieses Quote ist bereits in der Community-Sammlung veröffentlicht.",

    // Review status check ("Check now")
    "Some review statuses could not be checked. Try again later." to "Einige Prüfstatus konnten nicht abgefragt werden. Versuche es später erneut.",
    "Review statuses updated." to "Prüfstatus aktualisiert.",
    "No new review decisions yet." to "Noch keine neuen Prüfergebnisse.",

    // Delete and withdraw dialogs
    "Delete and withdraw?" to "Löschen und zurückziehen?",
    "This quote is still waiting for review. Deleting it also withdraws the submission, so it won't be published. Without a connection, Quote withdraws it as soon as it can reach the service." to "Dieses Quote wartet noch auf die Prüfung. Beim Löschen wird auch die Einreichung zurückgezogen, damit es nicht veröffentlicht wird. Ohne Verbindung zieht Quote sie zurück, sobald der Dienst erreichbar ist.",
    "Delete quote?" to "Quote löschen?",
    "This only deletes your own copy. The published quote stays in the community collection." to "Dadurch wird nur deine eigene Kopie gelöscht. Das veröffentlichte Quote bleibt in der Community-Sammlung.",
    "Delete %d quotes?" to "%d Quotes löschen?",
    "Some of these quotes are still waiting for review. Deleting them also withdraws their submissions, so they won't be published." to "Einige dieser Quotes warten noch auf die Prüfung. Beim Löschen werden auch ihre Einreichungen zurückgezogen, damit sie nicht veröffentlicht werden.",
    "Published quotes stay in the community collection. This only deletes your own copies." to "Veröffentlichte Quotes bleiben in der Community-Sammlung. Dadurch werden nur deine eigenen Kopien gelöscht.",
    "Withdraw submission?" to "Einreichung zurückziehen?",
    "The quote leaves the review queue and stays in My Quotes. You can submit it again later." to "Das Quote wird aus der Prüfung genommen und bleibt unter „Meine Quotes“. Du kannst es später erneut einreichen.",
    "Submission withdrawn" to "Einreichung zurückgezogen",

    // Edit sheet
    "Edit quote" to "Quote bearbeiten",
    "Revise the quote, then submit it again." to "Überarbeite das Quote und reiche es dann erneut ein.",
    "Saving resets the review status so you can submit the quote again." to "Beim Speichern wird der Prüfstatus zurückgesetzt, damit du das Quote erneut einreichen kannst.",
    "The review stays, because the service still sees the same text and author." to "Die Prüfung bleibt bestehen, weil der Dienst weiterhin denselben Text und Autor sieht.",
    "At most 500 characters." to "Höchstens 500 Zeichen.",
    "At most 100 characters." to "Höchstens 100 Zeichen.",
    "Change the text or author to submit it again." to "Ändere Text oder Autor, um es erneut einzureichen.",
    "Save and submit" to "Speichern und einreichen",
    "Changes could not be saved." to "Die Änderungen konnten nicht gespeichert werden.",
    "Discard changes?" to "Änderungen verwerfen?",
    "Your changes to this quote will be lost." to "Deine Änderungen an diesem Quote gehen verloren.",

    // Snackbars (QuoteEventsHost)
    "Removed from favorites" to "Aus Favoriten entfernt",
    "%d quotes deleted" to "%d Quotes gelöscht",
    "%d quotes removed from favorites" to "%d Quotes aus Favoriten entfernt",
    "Show" to "Anzeigen",
    "Deleted. The submission will be withdrawn once Quote can reach the service." to "Gelöscht. Die Einreichung wird zurückgezogen, sobald Quote den Dienst erreicht.",
    "%d quotes deleted. Their submissions will be withdrawn once Quote can reach the service." to "%d Quotes gelöscht. Ihre Einreichungen werden zurückgezogen, sobald Quote den Dienst erreicht.",
    "A quote you deleted could not be withdrawn: a moderator had already reviewed it." to "Ein gelöschtes Quote konnte nicht zurückgezogen werden: Die Moderation hatte es bereits geprüft.",
    "%d quotes you deleted could not be withdrawn: a moderator had already reviewed them." to "%d gelöschte Quotes konnten nicht zurückgezogen werden: Die Moderation hatte sie bereits geprüft.",
    "A quote you deleted could not be withdrawn and may still be published." to "Ein gelöschtes Quote konnte nicht zurückgezogen werden und wird eventuell noch veröffentlicht.",
    "%d quotes you deleted could not be withdrawn and may still be published." to "%d gelöschte Quotes konnten nicht zurückgezogen werden und werden eventuell noch veröffentlicht.",
    "Your quote “%s” is now in the community collection." to "Dein Quote „%s“ ist jetzt in der Community-Sammlung.",
    "Your quote “%s” was not approved." to "Dein Quote „%s“ wurde nicht freigegeben.",
    "Your quote “%s” is no longer published." to "Dein Quote „%s“ ist nicht mehr veröffentlicht.",
    "%d updates on your quotes" to "%d Neuigkeiten zu deinen Quotes"
)
