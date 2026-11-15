package app.brokoli5191.quote

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.brokoli5191.quote.ui.QuoteDraft
import app.brokoli5191.quote.utils.AppLinks
import app.brokoli5191.quote.widget.QuoteWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLinkRequestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun openIntent(destination: String, action: String? = null) =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(AppLinks.EXTRA_DEST, destination)

    @Test
    fun `widget, notification and shortcut destinations open their screen`() {
        assertEquals(
            AppLinkRequest(AppLinks.DEST_DAILY),
            appLinkRequest(openIntent(AppLinks.DEST_DAILY, QuoteWidgetProvider.ACTION_OPEN_DAILY))
        )
        for (destination in listOf(AppLinks.DEST_FAVORITES, AppLinks.DEST_MY_QUOTES, AppLinks.DEST_NEW_QUOTE)) {
            assertEquals(AppLinkRequest(destination), appLinkRequest(openIntent(destination, Intent.ACTION_MAIN)))
        }
    }

    @Test
    fun `plain launches and unknown destinations open nothing`() {
        assertNull(appLinkRequest(null))
        assertNull(appLinkRequest(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
        assertNull(appLinkRequest(openIntent("settings")))
    }

    @Test
    fun `an intent redelivered from recents is not a new request`() {
        val reopened = openIntent(AppLinks.DEST_NEW_QUOTE).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(appLinkRequest(reopened))
    }

    @Test
    fun `shared and selected text open New Quote with a draft`() {
        val shared = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "\"Be yourself.\" — Oscar Wilde")
        assertEquals(
            AppLinkRequest(AppLinks.DEST_NEW_QUOTE, QuoteDraft("Be yourself.", "Oscar Wilde")),
            appLinkRequest(shared)
        )

        val selected = Intent(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "Know thyself.")
        assertEquals(AppLinkRequest(AppLinks.DEST_NEW_QUOTE, QuoteDraft("Know thyself.", "")), appLinkRequest(selected))
    }

    @Test
    fun `sharing empty text opens nothing`() {
        assertNull(appLinkRequest(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "   ")))
    }
}
