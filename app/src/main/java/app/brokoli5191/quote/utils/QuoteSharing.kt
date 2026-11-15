package app.brokoli5191.quote.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import app.brokoli5191.quote.data.isPlaceholderAuthor
import app.brokoli5191.quote.data.normalizeAuthorName
import app.brokoli5191.quote.data.normalizeQuoteText
import app.brokoli5191.quote.ui.QuoteDraft
import app.brokoli5191.quote.ui.formattedQuote
import app.brokoli5191.quote.ui.localizeUi
import app.brokoli5191.quote.widget.QuoteCardRenderer
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copying and sharing a quote, the same on every screen: one attribution rule (an unknown author
 * is left out, never written as "Unknown"), one localized share-sheet title and the quotation
 * marks of the quote's language. Also turns text shared into the app back into a draft.
 *
 * `language` is the app language (share-sheet title, messages); `textLanguage` is the language of
 * the shared text and picks its quotation marks. They differ when the user shows a translation.
 */
object QuoteSharing {
    private const val TAG = "QuoteSharing"
    private const val CHOOSER_TITLE = "Share Wisdom"
    private const val IMAGE_FAILED = "The image could not be created."
    private const val SHARE_DIR = "shared"
    private const val SHARE_IMAGE = "quote.png"
    private const val MAX_AUTHOR_LENGTH = 100
    private const val MAX_AUTHOR_WORDS = 8

    /** The author to credit: blank for a blank, "Unknown" or "Anonymous" author. */
    fun attribution(author: String): String {
        val name = author.trim()
        return if (isPlaceholderAuthor(name)) "" else name
    }

    /** The copied/shared text, e.g. `"Be yourself." — Oscar Wilde` or `„Sei du selbst.“ — Oscar Wilde`. */
    fun quoteText(text: String, author: String, textLanguage: String): String =
        formattedQuote(text.trim(), attribution(author), textLanguage)

    /** A share sheet for the quote as plain text, e.g. for a notification action. */
    fun textChooserIntent(text: String, author: String, language: String, textLanguage: String = language): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, quoteText(text, author, textLanguage))
        }
        return Intent.createChooser(send, localizeUi(CHOOSER_TITLE, language))
    }

    fun shareText(context: Context, text: String, author: String, language: String, textLanguage: String = language) {
        context.startChooser(textChooserIntent(text, author, language, textLanguage))
    }

    fun copyText(context: Context, text: String, author: String, textLanguage: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("quote", quoteText(text, author, textLanguage)))
    }

    /**
     * Shares the quote as a 1080 x 1350 card image, with the quote text as caption. Renders and
     * writes the PNG off the main thread; call it from the main thread. Shows a toast and returns
     * false when the image could not be made.
     */
    suspend fun shareImage(
        context: Context,
        text: String,
        author: String,
        language: String,
        textLanguage: String = language
    ): Boolean {
        val image = try {
            withContext(Dispatchers.IO) { writeShareImage(context, text.trim(), attribution(author)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Share image failed", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Share image failed", e)
            null
        }
        return withContext(Dispatchers.Main.immediate) {
            if (image == null) {
                Toast.makeText(context, localizeUi(IMAGE_FAILED, language), Toast.LENGTH_LONG).show()
                return@withContext false
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, quoteText(text, author, textLanguage))
                // createChooser hands the clip and the read grant on to the chosen app (and the preview).
                clipData = ClipData.newRawUri("quote", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startChooser(Intent.createChooser(send, localizeUi(CHOOSER_TITLE, language)))
        }
    }

    /**
     * A New Quote draft from text another app shared or selected: a trailing "— Author" or
     * " - Author" becomes the author, wrapping quotation marks and a trailing link are removed.
     * Null when no text is left.
     */
    internal fun draftFromSharedText(raw: String): QuoteDraft? {
        val shared = raw.trim().replace(TRAILING_LINK, "").trim()
        if (shared.isEmpty()) return null
        val split = TRAILING_AUTHOR.matchEntire(shared)?.takeIf { match ->
            val quote = match.groupValues[1].trim()
            val author = match.groupValues[2].trim()
            author.length <= MAX_AUTHOR_LENGTH &&
                author.split(WHITESPACE).size <= MAX_AUTHOR_WORDS &&
                (isWrappedInQuotes(quote) || looksLikeName(author))
        }
        val quote = stripWrappingMarks(normalizeQuoteText(split?.groupValues?.get(1) ?: shared))
        if (quote.isEmpty()) return null
        val author = split?.groupValues?.get(2)?.let(::normalizeAuthorName)?.let(::attribution).orEmpty()
        return QuoteDraft(quote, author)
    }

    // Separators that formattedQuote and other apps put before the author: an em/en dash or a
    // horizontal bar after a space, a hyphen between spaces, or a dash that starts a new line.
    // The greedy quote group makes the last separator count.
    private val TRAILING_AUTHOR =
        Regex("""^(.+)(?:\s+[—–―]\s*|\s+-\s+|\s*\n\s*-\s*)([^\n]+)$""", RegexOption.DOT_MATCHES_ALL)
    private val TRAILING_LINK = Regex("""\s+https?://\S+$""")
    private val WHITESPACE = Regex("""\s+""")
    private const val OPENING_MARKS = "\"“„«»‚‘"
    private const val CLOSING_MARKS = "\"”“»«’‘"
    private val NAME_PARTICLES = setOf(
        "von", "van", "der", "den", "de", "del", "della", "di", "da", "du", "des", "la", "le",
        "al", "el", "bin", "ibn", "y", "e", "zu", "of", "the", "and", "und"
    )
    private val WRAPPING_PAIRS = listOf('«' to '»', '»' to '«', '‚' to '‘', '‘' to '’')

    private fun isWrappedInQuotes(text: String): Boolean =
        text.length > 1 && text.first() in OPENING_MARKS && text.last() in CLOSING_MARKS

    // Without quotation marks a dash may just be part of the sentence ("Life is short - live it"),
    // so the part after it only counts as an author when it reads like a name.
    private fun looksLikeName(author: String): Boolean {
        val words = author.split(WHITESPACE).map { it.trim('(', ')', ',', ';', '.') }.filter { it.isNotEmpty() }
        return words.any { it.first().isUpperCase() } && words.all { word ->
            word.first().isUpperCase() || word.first().isDigit() || word.lowercase() in NAME_PARTICLES
        }
    }

    // normalizeQuoteText removes wrapping double quotation marks; this does the same for
    // guillemets and single curly marks.
    private fun stripWrappingMarks(text: String): String {
        val wrapped = text.length > 1 && WRAPPING_PAIRS.any { (open, close) ->
            text.first() == open && text.last() == close &&
                text.substring(1, text.length - 1).none { it == open || it == close }
        }
        return if (wrapped) text.substring(1, text.length - 1).trim() else text
    }

    private fun writeShareImage(context: Context, text: String, author: String): File {
        val bitmap = QuoteCardRenderer.renderShareImage(context, text, author)
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val target = File(dir, SHARE_IMAGE)
        // Written next to the target and moved over it, so an app still reading the previous
        // share never sees a half-written file.
        val partial = File(dir, "$SHARE_IMAGE.part")
        try {
            partial.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encoding failed")
            }
        } finally {
            bitmap.recycle()
        }
        if (!partial.renameTo(target)) throw IOException("Could not replace $target")
        return target
    }

    private fun Context.startChooser(chooser: Intent): Boolean {
        if (findActivity() == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No share sheet available", e)
            false
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
