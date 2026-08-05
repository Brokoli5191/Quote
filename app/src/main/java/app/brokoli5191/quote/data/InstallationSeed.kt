package app.brokoli5191.quote.data

import android.content.Context
import java.security.SecureRandom

object InstallationSeed {
    private const val KEY = "daily_quote_installation_seed"

    fun get(context: Context): Long {
        val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        if (prefs.contains(KEY)) return prefs.getLong(KEY, 0L)

        val seed = SecureRandom().nextLong()
        prefs.edit().putLong(KEY, seed).commit()
        return seed
    }
}
