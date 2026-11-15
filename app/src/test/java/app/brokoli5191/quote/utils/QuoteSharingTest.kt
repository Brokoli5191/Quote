package app.brokoli5191.quote.utils

import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import app.brokoli5191.quote.MainActivity
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.localized
import app.brokoli5191.quote.widget.QuoteCardRenderer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class QuoteSharingTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    // FileProvider keeps its roots in a static cache, but every Robolectric test has its own cache dir.
    @Before
    fun forgetFileProviderRoots() {
        val roots = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (roots.get(null) as MutableMap<*, *>).clear()
    }

    @Test
    fun `unknown and blank authors are never credited`() {
        assertEquals("\"Be yourself.\"", QuoteSharing.quoteText("Be yourself.", "", QuoteLanguage.ENGLISH))
        assertEquals("\"Be yourself.\"", QuoteSharing.quoteText("Be yourself.", "Unknown", QuoteLanguage.ENGLISH))
        assertEquals("\"Be yourself.\"", QuoteSharing.quoteText("Be yourself.", " anonymous ", QuoteLanguage.ENGLISH))
        assertEquals("„Sei du selbst.“", QuoteSharing.quoteText("Sei du selbst.", "Unbekannt", QuoteLanguage.GERMAN))
    }

    @Test
    fun `known authors are credited with the marks of the text language`() {
        assertEquals("\"Be yourself.\" — Oscar Wilde", QuoteSharing.quoteText(" Be yourself. ", " Oscar Wilde ", QuoteLanguage.ENGLISH))
        assertEquals("„Sei du selbst.“ — Oscar Wilde", QuoteSharing.quoteText("Sei du selbst.", "Oscar Wilde", QuoteLanguage.GERMAN))
    }

    @Test
    fun `share text opens a share sheet titled in the app language`() {
        QuoteSharing.shareText(context, "Be yourself.", "Oscar Wilde", QuoteLanguage.GERMAN, QuoteLanguage.ENGLISH)

        val chooser = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Quote teilen", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        assertTrue("started from a non-activity context", chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("\"Be yourself.\" — Oscar Wilde", send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `the share sheet does not offer saving the quote back into Quote`() {
        QuoteSharing.shareText(context, "Be yourself.", "Oscar Wilde", QuoteLanguage.ENGLISH)

        val chooser = shadowOf(context).nextStartedActivity
        val excluded = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ComponentName::class.java)!!.toList()
        assertEquals(listOf(ComponentName(context, MainActivity::class.java)), excluded)
    }

    @Test
    fun `quotation marks follow the language of the text on screen`() {
        val english = QuoteEntity(text = "Be yourself.", author = "Oscar Wilde", category = "Wisdom", textDe = "Sei du selbst.")
        val shownInGerman = english.localized(QuoteLanguage.GERMAN)
        assertEquals(QuoteLanguage.GERMAN, QuoteSharing.textLanguage(shownInGerman, showingTranslation = false))
        assertEquals(QuoteLanguage.ENGLISH, QuoteSharing.textLanguage(shownInGerman, showingTranslation = true))
        assertEquals(QuoteLanguage.ENGLISH, QuoteSharing.textLanguage(english.localized(QuoteLanguage.ENGLISH), showingTranslation = false))

        val germanOnly = QuoteEntity(text = "Sei du selbst.", author = "Oscar Wilde", category = "Wisdom", language = QuoteLanguage.GERMAN)
        assertEquals(QuoteLanguage.GERMAN, QuoteSharing.textLanguage(germanOnly.localized(QuoteLanguage.ENGLISH), showingTranslation = true))

        // An own quote stores the app language it was written in, which says nothing about its text.
        val own = QuoteEntity(text = "Mein Satz.", author = "", category = "Custom", isUserAdded = true, language = QuoteLanguage.GERMAN)
        assertEquals(QuoteLanguage.ENGLISH, QuoteSharing.textLanguage(own, showingTranslation = false))
    }

    @Test
    fun `copy puts the formatted quote on the clipboard`() {
        QuoteSharing.copyText(context, "Sei du selbst.", "Unbekannt", QuoteLanguage.GERMAN)

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertEquals("„Sei du selbst.“", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun `share image sends a png card through the file provider`() {
        val started = runBlocking {
            QuoteSharing.shareImage(context, "Be yourself.", "Oscar Wilde", QuoteLanguage.ENGLISH)
        }
        assertTrue(started)

        val png = File(context.cacheDir, "shared/quote.png").readBytes()
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertNotNull("a decodable PNG in the cache", bitmap)
        assertEquals(QuoteCardRenderer.SHARE_IMAGE_WIDTH_PX, bitmap.width)
        assertEquals(QuoteCardRenderer.SHARE_IMAGE_HEIGHT_PX, bitmap.height)

        val chooser = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertTrue("the read grant reaches the chosen app", chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("image/png", send.type)
        assertEquals("\"Be yourself.\" — Oscar Wilde", send.getStringExtra(Intent.EXTRA_TEXT))
        val uri = send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
    }

    @Test
    fun `share image shows a message instead of crashing when the file provider refuses the image`() {
        // No provider is registered for this authority, so getUriForFile throws IllegalArgumentException,
        // as it does for a provider whose paths no longer cover the shared folder.
        val unknownAuthority = object : ContextWrapper(context) {
            override fun getPackageName() = "app.brokoli5191.quote.other"
        }

        val started = runBlocking {
            QuoteSharing.shareImage(unknownAuthority, "Be yourself.", "Oscar Wilde", QuoteLanguage.GERMAN)
        }

        assertFalse(started)
        assertEquals("Das Bild konnte nicht erstellt werden.", ShadowToast.getTextOfLatestToast())
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun `share image leaves an unknown author out of the caption`() {
        runBlocking { QuoteSharing.shareImage(context, "Begin again.", "Unknown", QuoteLanguage.ENGLISH) }

        val send = shadowOf(context).nextStartedActivity.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("\"Begin again.\"", send.getStringExtra(Intent.EXTRA_TEXT))
    }
}
