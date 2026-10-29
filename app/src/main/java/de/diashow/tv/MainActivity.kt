package de.diashow.tv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var audio: AudioController
    private var showSettings by mutableStateOf(false)

    private fun hasPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audio = AudioController(this)

        setContent {
            val store = remember { SettingsStore(this@MainActivity) }
            var settings by remember { mutableStateOf(store.load()) }
            fun update(s: Settings) { settings = s; store.save(s) }

            var granted by remember { mutableStateOf(hasPermission()) }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted = it }

            var library by remember { mutableStateOf<MediaScanner.Library?>(null) }
            var status by remember { mutableStateOf("Suche USB-Stick …") }
            var duck by remember { mutableStateOf(false) }
            var playMode by remember { mutableStateOf<PlayMode?>(null) }

            LaunchedEffect(Unit) {
                if (!granted) launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            LaunchedEffect(granted) {
                if (!granted) { status = "Speicherberechtigung fehlt"; return@LaunchedEffect }
                val roots = withContext(Dispatchers.IO) { MediaScanner.findUsbRoots(this@MainActivity) }
                val lib = withContext(Dispatchers.IO) {
                    roots.map { MediaScanner.scan(it) }.firstOrNull { it.visual.isNotEmpty() }
                }
                when {
                    roots.isEmpty() -> status = "Kein USB-Stick gefunden"
                    lib == null -> status = "Keine Bilder oder Videos gefunden in:\n" +
                        roots.joinToString("\n") { it.absolutePath }
                    else -> library = lib
                }
            }

            val sources = remember(library) {
                buildList<AudioSource> {
                    library?.music?.takeIf { it.isNotEmpty() }?.let { add(AudioSource.Files(it)) }
                    radioStations.forEach { add(AudioSource.Radio(it)) }
                }
            }

            // Lautstärke getrennt, damit die Musik dabei nicht neu startet
            LaunchedEffect(settings.musicVolumePercent, duck) {
                audio.setVolume(if (duck) 0f else settings.musicVolumePercent / 100f)
            }
            LaunchedEffect(showSettings, playMode) {
                if (showSettings || playMode == null) duck = false
            }

            val lib = library

            // Ordner-Übersicht für den Startbildschirm
            val folderInfos = remember(lib) {
                lib?.visual.orEmpty()
                    .groupBy { it.parentFile?.absolutePath ?: "" }
                    .map { (path, files) ->
                        val videos = files.count { MediaScanner.isVideo(it) }
                        FolderInfo(path, files.size - videos, videos)
                    }
                    .sortedBy { it.path.lowercase() }
            }

            // Dateien aus den ausgewählten Ordnern
            val selected = remember(lib, settings.disabledFolders) {
                lib?.visual.orEmpty().filter { (it.parentFile?.absolutePath ?: "") !in settings.disabledFolders }
            }
            val imageCount = selected.count { !MediaScanner.isVideo(it) }
            val videoCount = selected.size - imageCount

            // Wiedergabeliste je nach gewähltem Modus
            val items = remember(playMode, selected, settings.shuffleImages, settings.shuffleVideos) {
                when (playMode) {
                    PlayMode.IMAGES -> selected.filter { !MediaScanner.isVideo(it) }
                        .let { if (settings.shuffleImages) it.shuffled() else it }
                    PlayMode.VIDEOS -> selected.filter { MediaScanner.isVideo(it) }
                        .let { if (settings.shuffleVideos) it.shuffled() else it }
                    PlayMode.MIXED -> selected.let { if (settings.shuffleImages) it.shuffled() else it }
                    null -> emptyList()
                }
            }

            // Musik läuft nur, solange die Diashow läuft (nicht auf dem Startbildschirm)
            val playing = playMode != null && items.isNotEmpty()
            LaunchedEffect(playing, sources, settings.audioSourceIndex, settings.musicEnabled, settings.shuffleMusic) {
                if (playing && settings.musicEnabled && sources.isNotEmpty()) {
                    audio.play(sources[settings.audioSourceIndex % sources.size], settings.shuffleMusic)
                } else {
                    audio.stop()
                }
            }

            // Zurück: Diashow -> Startbildschirm; Einstellungen -> schließen
            BackHandler(enabled = playMode != null && !showSettings) { playMode = null }
            BackHandler(enabled = showSettings) { showSettings = false }

            when {
                showSettings -> SettingsScreen(settings) { update(it) }
                lib == null -> Message(status)
                playMode == null || items.isEmpty() -> StartScreen(
                    folders = folderInfos,
                    disabled = settings.disabledFolders,
                    imageCount = imageCount,
                    videoCount = videoCount,
                    onDisabledChange = { update(settings.copy(disabledFolders = it)) },
                    onStart = { playMode = it },
                )
                else -> SlideshowScreen(
                    items = items,
                    imageSeconds = settings.imageSeconds.toLong(),
                    transitionMs = settings.transitionMs,
                    videoSound = settings.videoSound,
                    autoRotate = settings.autoRotate,
                    zoomEnabled = settings.zoomEnabled,
                    faceZoom = settings.faceZoom,
                    audioLabel = if (settings.musicEnabled && sources.isNotEmpty())
                        sources[settings.audioSourceIndex % sources.size].label else "Musik aus",
                    onCycleAudio = {
                        if (settings.musicEnabled && sources.isNotEmpty()) {
                            update(settings.copy(audioSourceIndex = (settings.audioSourceIndex + 1) % sources.size))
                        }
                    },
                    onVideoActive = { duck = it },
                )
            }
        }
    }

    @Composable
    private fun Message(text: String) {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            Text(text, color = Color.White)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showSettings = !showSettings
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }
}
