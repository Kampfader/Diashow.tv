package de.diashow.tv

import android.content.Context

/** Speichert die manuelle Drehung (0/90/180/270 Grad) pro Videodatei. */
class RotationStore(context: Context) {
    private val prefs = context.getSharedPreferences("video_rotations", Context.MODE_PRIVATE)

    fun get(path: String): Int = prefs.getInt(path, 0)

    fun set(path: String, degrees: Int) {
        prefs.edit().apply {
            if (degrees == 0) remove(path) else putInt(path, degrees)
        }.apply()
    }
}
