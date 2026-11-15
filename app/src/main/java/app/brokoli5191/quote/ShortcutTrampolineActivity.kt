package app.brokoli5191.quote

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.brokoli5191.quote.utils.AppLinks

/**
 * Static shortcuts always start their first intent with NEW_TASK | CLEAR_TASK, which would finish a
 * running MainActivity and drop its unsaved state. This hop takes the clear in its own task and hands
 * the destination to MainActivity the way the widget and notifications do.
 */
class ShortcutTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Exported: malformed extras from other apps must not crash the hop (same guard as MainActivity's appLinkRequest).
        val dest = try {
            intent?.getStringExtra(AppLinks.EXTRA_DEST)
        } catch (e: RuntimeException) {
            null
        }
        dest?.let { open.putExtra(AppLinks.EXTRA_DEST, it) }
        startActivity(open)
        finish()
    }
}
