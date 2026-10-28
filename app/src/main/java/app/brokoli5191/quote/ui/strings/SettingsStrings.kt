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

    // Updates
    "Update available: v{version}" to "Update verfügbar: v{version}",
    "Downloading… {progress}%" to "Wird heruntergeladen … {progress}%",
    "v{version} ready to install." to "v{version} ist installationsbereit.",

    // Backup & restore
    "Export failed: {error}" to "Export fehlgeschlagen: {error}",
    "Restore failed: {error}" to "Wiederherstellung fehlgeschlagen: {error}",
    "Restore complete! Imported {custom} custom quotes, updated {favorites} favorites, and restored settings." to
        "Wiederherstellung abgeschlossen: {custom} eigene Quotes importiert, {favorites} Favoriten aktualisiert und Einstellungen wiederhergestellt.",
    "Restore complete! Imported {custom} custom quotes and updated {favorites} favorites." to
        "Wiederherstellung abgeschlossen: {custom} eigene Quotes importiert und {favorites} Favoriten aktualisiert.",
    "Could not open the selected backup file" to "Die ausgewählte Sicherungsdatei konnte nicht geöffnet werden",
    "Could not open file" to "Die Datei konnte nicht geöffnet werden",
    "Backup does not contain a quotes list" to "Die Sicherung enthält keine Quote-Liste",
    "Unknown backup format" to "Unbekanntes Sicherungsformat",
    "Unknown file format" to "Unbekanntes Dateiformat",
    "Unknown error" to "Unbekannter Fehler",

    // Developer options
    "Show onboarding again" to "Onboarding erneut anzeigen",
    "Onboarding will be shown again" to "Das Onboarding wird erneut angezeigt"
)
