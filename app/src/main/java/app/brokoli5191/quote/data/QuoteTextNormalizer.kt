package app.brokoli5191.quote.data

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

// Text cleanup for stored quotes. Mirrors cleanText in tools/quote_seed_rules.mjs, so the bundled
// seed (already cleaned by that script) passes through unchanged.

private val WINDOWS_1252: Charset = Charset.forName("windows-1252")
private val WHITESPACE_REGEX = Regex("\\s+")
private val DIALOGUE_SPACING_REGEX = Regex("([.!?,”])(?=“)")
private const val STRAIGHT_QUOTE = '"'

internal fun normalizeAuthorName(value: String): String {
    var author = value.replace("\"", "").replace("“", "").replace("”", "").trim()
    author = when (author) {
        "Ø£Ø­ÙØ§Ù… Ù…Ø³ØªØºØ§Ù†Ù…ÙŠ" -> "Ahlam Mosteghanemi"
        "Ø£Ø­Ù…Ø¯ Ø®Ø§ÙØ¯ ØªÙˆÙ�ÙŠÙ‚" -> "Ahmed Khaled Towfik"
        else -> author
    }
    author = repairMojibake(author)
    return author
        .replace('�'.toString(), "")
        .replace(' ', ' ')
        .trim()
        .trimEnd(',')
        .trim()
}

/**
 * Cleans quote text: repairs mojibake, collapses whitespace, removes a quotation-mark pair only
 * when it wraps the entire text, and closes an unmatched opening mark instead of dropping
 * the inner closing one.
 */
internal fun normalizeQuoteText(value: String): String {
    val text = repairMojibake(value)
        .replace('�'.toString(), "")
        .replace(' ', ' ')
        .replace(WHITESPACE_REGEX, " ")
        .replace(DIALOGUE_SPACING_REGEX, "$1 ")
        .trim()
    return spaceAfterClosingQuotes(stripWrappingQuotes(balanceQuoteMarks(stripWrappingQuotes(text))))
}

/** Loose key for matching the same quote across text cleanups (case, punctuation, quote marks). */
internal fun quoteMatchKey(value: String): String = normalizeQuoteText(value)
    .lowercase()
    .filter { it.isLetterOrDigit() || it == ' ' }
    .replace(WHITESPACE_REGEX, " ")
    .trim()

internal class QuoteMarkPairs(
    val pairs: Map<Int, Int>,
    val unmatchedOpen: List<Int>,
    val unmatchedClose: List<Int>
)

private fun isOpeningContext(char: Char): Boolean =
    char.isWhitespace() || char in "([{—–-/“„"

// Pairs double quotation marks. Curly („ “ ”) and straight (") marks are tracked separately.
// A straight mark right after a word or punctuation closes; one at the start or after a
// space/bracket/dash opens. Only the first straight mark may be an unmatched closer.
internal fun pairQuoteMarks(text: String): QuoteMarkPairs {
    val pairs = HashMap<Int, Int>()
    val unmatchedClose = mutableListOf<Int>()
    val curly = ArrayList<Int>()
    val straight = ArrayList<Int>()
    var seenStraight = false
    for (i in text.indices) {
        val char = text[i]
        if (char == '„' || (char == '“' && (curly.isEmpty() || text[curly.last()] != '„'))) {
            curly.add(i)
        } else if (char == '”' || char == '“') {
            if (curly.isNotEmpty()) pairs[curly.removeAt(curly.lastIndex)] = i else unmatchedClose.add(i)
        } else if (char == STRAIGHT_QUOTE) {
            val prev = text.getOrNull(i - 1)
            val next = text.getOrNull(i + 1)
            if (prev != null && prev.isAsciiDigit() && straight.isEmpty()) continue // inch mark, e.g. 12"
            val prevIsCloser = prev == STRAIGHT_QUOTE && (i - 1) !in straight
            val opensByContext = prev == null || isOpeningContext(prev) || prevIsCloser
            val isolated = opensByContext && (next == null || next.isWhitespace())
            val opens = if (isolated || (!opensByContext && seenStraight)) straight.isEmpty() else opensByContext
            seenStraight = true
            if (opens) straight.add(i)
            else if (straight.isNotEmpty()) pairs[straight.removeAt(straight.lastIndex)] = i
            else unmatchedClose.add(i)
        }
    }
    return QuoteMarkPairs(pairs, (curly + straight).sorted(), unmatchedClose)
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun closingMarkFor(mark: Char): Char = when (mark) {
    '„' -> '“'
    '“' -> '”'
    else -> STRAIGHT_QUOTE
}

private fun openingMarkFor(mark: Char): Char = if (mark == STRAIGHT_QUOTE) STRAIGHT_QUOTE else '“'

// Removes a quotation-mark pair only when it wraps the entire text.
private fun stripWrappingQuotes(text: String): String {
    var result = text
    while (result.length > 1 && pairQuoteMarks(result).pairs[0] == result.length - 1) {
        result = result.substring(1, result.length - 1).trim()
    }
    return result
}

// Closes unmatched opening marks at the end and opens unmatched closing marks at the start.
internal fun balanceQuoteMarks(text: String): String {
    val marks = pairQuoteMarks(text)
    if (marks.unmatchedOpen.isEmpty() && marks.unmatchedClose.isEmpty()) return text
    val prefix = marks.unmatchedClose.map { openingMarkFor(text[it]) }.reversed().joinToString("")
    val suffix = marks.unmatchedOpen.map { closingMarkFor(text[it]) }.reversed().joinToString("")
    return prefix + text + suffix
}

// "Hi?"She said → "Hi?" She said: only after closing straight marks that end a sentence or clause.
private fun spaceAfterClosingQuotes(text: String): String {
    if (STRAIGHT_QUOTE !in text) return text
    val marks = pairQuoteMarks(text)
    val closers = marks.pairs.values.toHashSet().apply { addAll(marks.unmatchedClose) }
    val result = StringBuilder(text.length + 4)
    for (i in text.indices) {
        result.append(text[i])
        val prev = text.getOrNull(i - 1)
        val next = text.getOrNull(i + 1)
        if (text[i] == STRAIGHT_QUOTE && i in closers && prev != null && prev in ".!?," &&
            next != null && (next.isLetter() || next == STRAIGHT_QUOTE)
        ) result.append(' ')
    }
    return result.toString()
}

private fun repairMojibake(value: String): String {
    if ("Ø£Ø¬" in value && "Ø­Ø¨" in value) {
        return "أجمل حب هو الذي نعثر عليه أثناء بحثنا عن شيء آخر"
    }
    var repaired = value
    repeat(2) {
        repaired = replaceCommonMojibake(repaired)
        if (repaired.none(::isMojibakeMarker)) return repaired

        val decoded = String(
            repaired.toByteArray(WINDOWS_1252),
            StandardCharsets.UTF_8
        )
        if ('�' in decoded || mojibakeScore(decoded) >= mojibakeScore(repaired)) return repaired
        repaired = decoded
    }
    return replaceCommonMojibake(repaired)
}

private fun replaceCommonMojibake(value: String): String = value
    .replace("â€™", "’").replace("â€˜", "‘")
    .replace("â€œ", "“").replace("â€�", "”")
    .replace("â€“", "–").replace("â€”", "—")
    .replace("â€¦", "…").replace("â€²", "′")
    .replace("â€\"", "—")
    .replace("â€", "—")
    .replace("Â ", " ")
    .replace("Â ", " ")

private fun isMojibakeMarker(char: Char): Boolean =
    char == 'Ã' || char == 'Â' || char == 'â' || char == 'Ø' || char == 'Ù'

private fun mojibakeScore(value: String): Int = value.count(::isMojibakeMarker)

internal fun QuoteEntity.normalizedText(): QuoteEntity = copy(
    text = normalizeQuoteText(text),
    author = normalizeAuthorName(author),
    textDe = textDe?.let(::normalizeQuoteText)?.takeIf { it.isNotBlank() },
    textEn = textEn?.let(::normalizeQuoteText)?.takeIf { it.isNotBlank() }
)
