package de.diashow.tv

import android.content.Context

/** Merkt sich, wann welches Bild zuletzt gezeigt wurde (für "Bilder nicht wiederholen"). */
class ShownStore(context: Context) {
    private val prefs = context.getSharedPreferences("shown_images", Context.MODE_PRIVATE)

    fun markShown(path: String) {
        prefs.edit().putLong(path, System.currentTimeMillis()).apply()
    }

    /** Alle Bilder, die innerhalb der letzten [days] Tage gezeigt wurden. */
    fun recentPaths(days: Int): Set<String> {
        val limit = System.currentTimeMillis() - days * 86_400_000L
        return prefs.all.filter { ((it.value as? Long) ?: 0L) >= limit }.keys.toSet()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
