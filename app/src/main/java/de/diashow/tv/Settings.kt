package de.diashow.tv

import android.content.Context

data class Settings(
    val imageSeconds: Int = 8,
    val zoomEnabled: Boolean = true,
    val faceZoom: Boolean = true,
    val zoomMaxPercent: Int = 160,
    val zoomSpeedPercent: Int = 100,
    val noRepeatDays: Int = 0,
    val transitionMs: Int = 800,
    val shuffleImages: Boolean = false,
    val shuffleVideos: Boolean = false,
    val videoSound: Boolean = false,
    val autoRotate: Boolean = true,
    val musicEnabled: Boolean = true,
    val shuffleMusic: Boolean = true,
    val musicVolumePercent: Int = 100,
    val audioSourceIndex: Int = 0,
    val disabledFolders: Set<String> = emptySet(),
)

val IMAGE_SECONDS_OPTIONS = listOf(3, 5, 8, 10, 15, 30, 60)
val ZOOM_MAX_OPTIONS = listOf(110, 130, 160, 200, 250)
val ZOOM_SPEED_OPTIONS = listOf(100, 70, 50, 30)
val NO_REPEAT_OPTIONS = listOf(0, 1, 3, 7, 14, 30, 90, 365)
val TRANSITION_OPTIONS = listOf(0, 500, 800, 1500, 3000)
val VOLUME_OPTIONS = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("diashow_settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            imageSeconds = prefs.getInt("imageSeconds", d.imageSeconds),
            zoomEnabled = prefs.getBoolean("zoomEnabled", d.zoomEnabled),
            faceZoom = prefs.getBoolean("faceZoom", d.faceZoom),
            zoomMaxPercent = prefs.getInt("zoomMaxPercent", d.zoomMaxPercent),
            zoomSpeedPercent = prefs.getInt("zoomSpeedPercent", d.zoomSpeedPercent),
            noRepeatDays = prefs.getInt("noRepeatDays", d.noRepeatDays),
            transitionMs = prefs.getInt("transitionMs", d.transitionMs),
            // "shuffleVisual" war der Name in der vorigen Version
            shuffleImages = prefs.getBoolean("shuffleImages", prefs.getBoolean("shuffleVisual", d.shuffleImages)),
            shuffleVideos = prefs.getBoolean("shuffleVideos", d.shuffleVideos),
            videoSound = prefs.getBoolean("videoSound", d.videoSound),
            autoRotate = prefs.getBoolean("autoRotate", d.autoRotate),
            musicEnabled = prefs.getBoolean("musicEnabled", d.musicEnabled),
            shuffleMusic = prefs.getBoolean("shuffleMusic", d.shuffleMusic),
            musicVolumePercent = prefs.getInt("musicVolumePercent", d.musicVolumePercent),
            audioSourceIndex = prefs.getInt("audioSourceIndex", d.audioSourceIndex),
            disabledFolders = prefs.getStringSet("disabledFolders", emptySet())?.toSet() ?: emptySet(),
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putInt("imageSeconds", s.imageSeconds)
            .putBoolean("zoomEnabled", s.zoomEnabled)
            .putBoolean("faceZoom", s.faceZoom)
            .putInt("zoomMaxPercent", s.zoomMaxPercent)
            .putInt("zoomSpeedPercent", s.zoomSpeedPercent)
            .putInt("noRepeatDays", s.noRepeatDays)
            .putInt("transitionMs", s.transitionMs)
            .putBoolean("shuffleImages", s.shuffleImages)
            .putBoolean("shuffleVideos", s.shuffleVideos)
            .putBoolean("videoSound", s.videoSound)
            .putBoolean("autoRotate", s.autoRotate)
            .putBoolean("musicEnabled", s.musicEnabled)
            .putBoolean("shuffleMusic", s.shuffleMusic)
            .putInt("musicVolumePercent", s.musicVolumePercent)
            .putInt("audioSourceIndex", s.audioSourceIndex)
            .putStringSet("disabledFolders", s.disabledFolders.toSet())
            .apply()
    }
}
