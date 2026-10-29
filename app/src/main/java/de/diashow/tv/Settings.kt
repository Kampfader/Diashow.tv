package de.diashow.tv

import android.content.Context

val IMAGE_SECONDS_OPTIONS = listOf(3, 5, 8, 10, 15, 20, 30)
val TRANSITION_OPTIONS = listOf(0, 500, 1000, 1500, 2000, 3000)
val ZOOM_MAX_OPTIONS = listOf(120, 140, 160, 180, 200)
val ZOOM_SPEED_OPTIONS = listOf(100, 70, 50, 30)
val NO_REPEAT_OPTIONS = listOf(0, 1, 2, 3, 7, 14, 30)
val VOLUME_OPTIONS = listOf(0, 20, 40, 50, 60, 80, 100)
val TEXT_SIZE_OPTIONS = listOf(25, 50, 75, 100, 125, 150) // 200% entfernt, 25% hinzugefügt

data class Settings(
    val imageSeconds: Int = 5,
    val transitionMs: Int = 1000,
    val zoomEnabled: Boolean = true,
    val faceZoom: Boolean = true,
    val zoomMaxPercent: Int = 160,
    val zoomSpeedPercent: Int = 100,
    val shuffleImages: Boolean = true,
    val noRepeatDays: Int = 0,
    val shuffleVideos: Boolean = false,
    val videoSound: Boolean = true,
    val autoRotate: Boolean = true,
    val musicEnabled: Boolean = true,
    val shuffleMusic: Boolean = true,
    val musicVolumePercent: Int = 50,
    val disabledFolders: Set<String> = emptySet(),
    val audioSourceIndex: Int = 0,
    val showFileName: Boolean = true,
    val showDate: Boolean = true,
    val textSizePercent: Int = 100,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        return Settings(
            imageSeconds = prefs.getInt("imageSeconds", 5),
            transitionMs = prefs.getInt("transitionMs", 1000),
            zoomEnabled = prefs.getBoolean("zoomEnabled", true),
            faceZoom = prefs.getBoolean("faceZoom", true),
            zoomMaxPercent = prefs.getInt("zoomMaxPercent", 160),
            zoomSpeedPercent = prefs.getInt("zoomSpeedPercent", 100),
            shuffleImages = prefs.getBoolean("shuffleImages", true),
            noRepeatDays = prefs.getInt("noRepeatDays", 0),
            shuffleVideos = prefs.getBoolean("shuffleVideos", false),
            videoSound = prefs.getBoolean("videoSound", true),
            autoRotate = prefs.getBoolean("autoRotate", true),
            musicEnabled = prefs.getBoolean("musicEnabled", true),
            shuffleMusic = prefs.getBoolean("shuffleMusic", true),
            musicVolumePercent = prefs.getInt("musicVolumePercent", 50),
            disabledFolders = prefs.getStringSet("disabledFolders", emptySet()) ?: emptySet(),
            audioSourceIndex = prefs.getInt("audioSourceIndex", 0),
            showFileName = prefs.getBoolean("showFileName", true),
            showDate = prefs.getBoolean("showDate", true),
            textSizePercent = prefs.getInt("textSizePercent", 100),
        )
    }

    fun save(s: Settings) {
        prefs.edit().apply {
            putInt("imageSeconds", s.imageSeconds)
            putInt("transitionMs", s.transitionMs)
            putBoolean("zoomEnabled", s.zoomEnabled)
            putBoolean("faceZoom", s.faceZoom)
            putInt("zoomMaxPercent", s.zoomMaxPercent)
            putInt("zoomSpeedPercent", s.zoomSpeedPercent)
            putBoolean("shuffleImages", s.shuffleImages)
            putInt("noRepeatDays", s.noRepeatDays)
            putBoolean("shuffleVideos", s.shuffleVideos)
            putBoolean("videoSound", s.videoSound)
            putBoolean("autoRotate", s.autoRotate)
            putBoolean("musicEnabled", s.musicEnabled)
            putBoolean("shuffleMusic", s.shuffleMusic)
            putInt("musicVolumePercent", s.musicVolumePercent)
            putStringSet("disabledFolders", s.disabledFolders)
            putInt("audioSourceIndex", s.audioSourceIndex)
            putBoolean("showFileName", s.showFileName)
            putBoolean("showDate", s.showDate)
            putInt("textSizePercent", s.textSizePercent)
        }.apply()
    }
}