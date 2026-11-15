package app.brokoli5191.quote.ui.strings

// German UI strings owned by the Settings area. Keys are the English source text passed to uiText().
// Placeholders in braces ({time}, {version}, …) are replaced by the caller after the lookup.
internal val settingsGermanUi: Map<String, String> = mapOf(
    // Author pictures
    "Author pictures" to "Autorenbilder",
    "Portraits are loaded from Wikipedia (Wikimedia) when enabled." to "Wenn aktiviert, werden Porträts von Wikipedia (Wikimedia) geladen.",

    // Daily reminder time
    "Change time" to "Uhrzeit ändern",
    "Scheduled daily at {time}" to "Täglich um {time}",

    // Review decisions
    "Notify me about review decisions" to "Über Prüfergebnisse benachrichtigen",
    "A notification when a quote you submitted is approved or not approved." to
        "Eine Benachrichtigung, wenn ein von dir eingereichtes Quote freigegeben oder nicht freigegeben wurde.",

    "Notifications permission is required to be notified about review decisions." to
        "Für Benachrichtigungen über Prüfergebnisse ist die Benachrichtigungs-Berechtigung nötig.",

    // Widgets
    "Widgets" to "Widgets",
    "Widget {number}" to "Widget {number}",
    "Edit style" to "Stil bearbeiten",
    "Add widget" to "Widget hinzufügen",
    "No widget is placed yet. Long-press an empty spot on your home screen, tap Widgets and pick Quote." to
        "Noch kein Widget platziert. Halte eine freie Stelle auf dem Startbildschirm gedrückt, tippe auf „Widgets“ und wähle Quote.",
    "No widget is placed yet. Add one here or long-press an empty spot on your home screen." to
        "Noch kein Widget platziert. Füge hier eines hinzu oder halte eine freie Stelle auf dem Startbildschirm gedrückt.",

    // Updates
    "Update available: v{version}" to "Update verfügbar: v{version}",
    "Downloading… {progress}%" to "Wird heruntergeladen … {progress}%",
    "v{version} ready to install." to "v{version} ist installationsbereit.",
    "No internet connection. Check your connection and try again." to
        "Keine Internetverbindung. Prüfe deine Verbindung und versuche es erneut.",
    "Too many update checks right now. Try again later." to
        "Gerade zu viele Update-Prüfungen. Versuche es später erneut.",
    "No downloadable update was found." to "Es wurde kein herunterladbares Update gefunden.",
    "The update server is having problems. Try again later." to
        "Der Update-Server hat Probleme. Versuche es später erneut.",

    // Community sync
    "Community quotes: syncing…" to "Community-Quotes: wird synchronisiert …",
    "Community quotes: updated {time}" to "Community-Quotes: aktualisiert {time}",
    "Community quotes: last sync failed" to "Community-Quotes: letzte Synchronisierung fehlgeschlagen",
    "Community quotes: last sync failed, last updated {time}" to
        "Community-Quotes: letzte Synchronisierung fehlgeschlagen, zuletzt aktualisiert {time}",

    // Backup & restore
    "Export failed: {error}" to "Export fehlgeschlagen: {error}",
    "Restore failed: {error}" to "Wiederherstellung fehlgeschlagen: {error}",
    "Restore complete! Imported {custom}, updated {favorites}, and restored settings." to
        "Wiederherstellung abgeschlossen: {custom} importiert, {favorites} aktualisiert und Einstellungen wiederhergestellt.",
    "Restore complete! Imported {custom} and updated {favorites}." to
        "Wiederherstellung abgeschlossen: {custom} importiert und {favorites} aktualisiert.",
    "{count} custom quote" to "{count} eigenes Quote",
    "{count} custom quotes" to "{count} eigene Quotes",
    "{count} favorite" to "{count} Favorit",
    "{count} favorites" to "{count} Favoriten",
    "Could not open the backup file." to "Die Sicherungsdatei konnte nicht geöffnet werden.",
    "This file is not a Quote backup." to "Diese Datei ist keine Quote-Sicherung.",
    "This backup was made by a newer version of Quote." to "Diese Sicherung stammt von einer neueren Version von Quote.",
    "The backup could not be saved." to "Die Sicherung konnte nicht gespeichert werden.",
    "The backup could not be restored." to "Die Sicherung konnte nicht wiederhergestellt werden.",

    // Developer options
    "Show onboarding again" to "Onboarding erneut anzeigen"
)
