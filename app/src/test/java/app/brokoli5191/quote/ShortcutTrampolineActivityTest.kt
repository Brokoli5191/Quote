package app.brokoli5191.quote

import android.content.Intent
import app.brokoli5191.quote.utils.AppLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShortcutTrampolineActivityTest {
    private fun trampoline(intent: Intent) =
        Robolectric.buildActivity(ShortcutTrampolineActivity::class.java, intent).create().get()

    @Test
    fun forwardsTheDestinationToMainActivityInTheExistingTask() {
        val activity = trampoline(
            Intent(Intent.ACTION_MAIN).putExtra(AppLinks.EXTRA_DEST, AppLinks.DEST_FAVORITES)
        )

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(AppLinks.DEST_FAVORITES, started.getStringExtra(AppLinks.EXTRA_DEST))
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun startsMainActivityWithoutADestinationWhenNoneIsGiven() {
        val activity = trampoline(Intent(Intent.ACTION_MAIN))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertFalse(started.hasExtra(AppLinks.EXTRA_DEST))
        assertNull(started.getStringExtra(AppLinks.EXTRA_DEST))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun everyShortcutIntentTargetsTheTrampoline() {
        val context = RuntimeEnvironment.getApplication()
        val parser = context.resources.getXml(R.xml.shortcuts)
        val targets = mutableListOf<String?>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "intent") {
                targets += parser.getAttributeValue("http://schemas.android.com/apk/res/android", "targetClass")
            }
        }

        assertEquals(2, targets.size)
        targets.forEach { assertEquals(ShortcutTrampolineActivity::class.java.name, it) }
    }
}
