package app.brokoli5191.quote.data

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{Mn}+")

// Letters that NFD does not split into a base letter and a mark, and typographic apostrophes.
private val FOLDED_CHARS = mapOf(
    'ß' to "ss", 'ø' to "o", 'æ' to "ae", 'œ' to "oe", 'ł' to "l", 'đ' to "d",
    '’' to "'", '‘' to "'"
)

/** Lowercase form without accents, so "Brontë", "BRONTE" and "bronte" compare equal. */
private fun foldForSearch(value: String): String {
    val lower = value.lowercase(Locale.ROOT)
    if (lower.all { it < '\u0080' }) return lower
    val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
    if (stripped.none { it in FOLDED_CHARS }) return stripped
    return buildString(stripped.length + 4) {
        for (char in stripped) append(FOLDED_CHARS[char] ?: char)
    }
}

/**
 * Search match for Library and Saved. The trimmed [query] is compared without case and accents
 * against the displayed text and its other-language version (textEn/textDe), the author (an
 * unknown author also as the "Unknown" label of [language]), the tag slugs and the tag labels of
 * [language]. Tags about misattribution only match when the query asks for them. A blank query
 * matches every quote.
 */
fun quoteMatchesQuery(quote: QuoteEntity, query: String, language: String): Boolean {
    val needle = foldForSearch(query.trim())
    if (needle.isEmpty()) return true
    fun String?.containsNeedle() = this != null && foldForSearch(this).contains(needle)

    if (sequenceOf(quote.text, quote.textEn, quote.textDe, quote.author).any { it.containsNeedle() }) return true
    if (quote.hasUnknownAuthor && unknownAuthorLabel(language).containsNeedle()) return true
    return quote.tags.splitToSequence(',').any { rawTag ->
        val tag = rawTag.trim()
        tag.isNotEmpty() && (tag.containsNeedle() || tagLabel(tag, language).containsNeedle()) &&
            (!tag.contains("misattributed", ignoreCase = true) || needle.contains("misattributed"))
    }
}
