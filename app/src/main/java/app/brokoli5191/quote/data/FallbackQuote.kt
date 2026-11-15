package app.brokoli5191.quote.data

/**
 * The quote shown when no quote can be loaded (empty database before the first seed, or an
 * error). Widget and notification use the same text so both stay in sync and keep attribution
 * in both languages.
 */
object FallbackQuote {
    const val AUTHOR = "Marcus Aurelius"
    private const val TEXT_EN = "The happiness of your life depends upon the quality of your thoughts."
    private const val TEXT_DE = "Das Glück deines Lebens hängt von der Qualität deiner Gedanken ab."

    fun text(language: String): String = if (language == QuoteLanguage.GERMAN) TEXT_DE else TEXT_EN
}
