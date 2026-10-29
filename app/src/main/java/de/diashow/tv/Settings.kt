package de.diashow.tv

import android.content.Context

data class Settings(
    val imageSeconds: Int = 8,
    val zoomEnabled: Boolean = true,
    val faceZoom: Boolean = true,
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
