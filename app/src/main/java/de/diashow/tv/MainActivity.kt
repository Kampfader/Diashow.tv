package de.diashow.tv

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var audio: AudioController
    private var showSettings by mutableStateOf(false)
    private var usbEvent by mutableIntStateOf(0)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            usbEvent++
        }
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audio = AudioController(this)

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                addDataScheme("file")
            }
            ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {
        }

        setContent {
            val store = remember { SettingsStore(this@MainActivity) }
            var settings by remember { mutableStateOf(store.load()) }
            fun update(s: Settings) { settings = s; store.save(s) }
            val shownStore = remember { ShownStore(this@MainActivity) }

            var granted by remember { mutableStateOf(hasPermission()) }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted = it }

            var library by remember { mutableStateOf<MediaScanner.Library?>(null) }
            var status by remember { mutableStateOf("Suche USB-Stick …") }
            var isScanning by remember { mutableStateOf(true) }
            var duck by remember { mutableStateOf(false) }
            var playMode by remember { mutableStateOf<PlayMode?>(null) }
            var slideIndex by remember { mutableIntStateOf(0) }
            var startInfo by remember { mutableStateOf<String?>(null) }
            var historyReset by remember { mutableIntStateOf(0) }

            LaunchedEffect(Unit) {
                if (!granted) launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            LaunchedEffect(granted) {
                if (!granted) {
                    status = "Speicherberechtigung fehlt"
                    isScanning = false
                    return@LaunchedEffect
                }
                var lastSignature: String? = null
                while (true) {
                    try {
                        val roots = withContext(Dispatchers.IO) { MediaScanner.findUsbRoots(this@MainActivity) }
                        val signature = withContext(Dispatchers.IO) { MediaScanner.signature(roots) }
                        if (signature != lastSignature) {
                            val isChange = lastSignature != null

                            if (roots.isNotEmpty()) {
                                isScanning = true
                                status = "Durchsuche USB-Stick nach Bildern und Videos …"
                            } else {
                                isScanning = true
                                status = "Suche USB-Stick …"
                            }

                            val lib = withContext(Dispatchers.IO) {
                                roots.map { MediaScanner.scan(it) }.firstOrNull { it.visual.isNotEmpty() }
                            }

                            if (playMode != null) audio.stop()
                            playMode = null
                            slideIndex = 0
                            startInfo = if (isChange && lib != null) {
                                "USB-Stick gewechselt. Die Medien wurden neu geladen."
                            } else null

                            when {
                                roots.isEmpty() -> {
                                    library = null
                                    isScanning = true
                                    status = "Suche USB-Stick …\nBitte einen Stick einstecken, die App erkennt ihn automatisch."
                                }
                                lib == null -> {
                                    library = null
                                    isScanning = false
                                    status = "Keine Bilder oder Videos gefunden in:\n" +
                                            roots.joinToString("\n") { it.absolutePath }
                                }
                                else -> {
                                    library = lib
                                    isScanning = false
                                }
                            }
                            lastSignature = signature
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        audio.stop()
                        playMode = null
                        library = null
                        isScanning = true
                        status = "USB-Stick wird getrennt …"
                    }
                    delay(1500)
                }
            }

            LaunchedEffect(usbEvent) {
                if (usbEvent > 0) {
                    audio.stop()
                    if (playMode != null) {
                        startInfo = "USB-Stick wurde entfernt."
                        playMode = null
                    }
                }
            }

            val sources = remember(library) {
                buildList<AudioSource> {
                    library?.music?.takeIf { it.isNotEmpty() }?.let { add(AudioSource.Files(it)) }
                    radioStations.forEach { add(AudioSource.Radio(it)) }
                }
            }

            LaunchedEffect(settings.musicVolumePercent, duck) {
                audio.setVolume(if (duck) 0f else settings.musicVolumePercent / 100f)
            }
            LaunchedEffect(showSettings, playMode) {
                if (showSettings || playMode == null) duck = false
            }

            val lib = library

            val folderInfos = remember(lib) {
                lib?.visual.orEmpty()
                    .groupBy { it.parentFile?.absolutePath ?: "" }
                    .map { (path, files) ->
                        val videos = files.count { MediaScanner.isVideo(it) }
                        FolderInfo(path, files.size - videos, videos)
                    }
                    .sortedBy { it.path.lowercase() }
            }

            val selected = remember(lib, settings.disabledFolders) {
                lib?.visual.orEmpty().filter { (it.parentFile?.absolutePath ?: "") !in settings.disabledFolders }
            }

            val recent = remember(lib, settings.noRepeatDays, playMode, historyReset) {
                if (settings.noRepeatDays > 0) shownStore.recentPaths(settings.noRepeatDays) else emptySet()
            }
            val avail = remember(selected, recent) {
                selected.filter { MediaScanner.isVideo(it) || it.absolutePath !in recent }
            }
            val imageCount = avail.count { !MediaScanner.isVideo(it) }
            val videoCount = avail.size - imageCount

            val items = remember(playMode, avail, settings.shuffleImages, settings.shuffleVideos) {
                when (playMode) {
                    PlayMode.IMAGES -> avail.filter { !MediaScanner.isVideo(it) }
                        .let { if (settings.shuffleImages) it.shuffled() else it }
                    PlayMode.VIDEOS -> avail.filter { MediaScanner.isVideo(it) }
                        .let { if (settings.shuffleVideos) it.shuffled() else it }
                    PlayMode.MIXED -> avail.let { if (settings.shuffleImages) it.shuffled() else it }
                    null -> emptyList()
                }
            }

            val playing = playMode != null && items.isNotEmpty()
            LaunchedEffect(playing, sources, settings.audioSourceIndex, settings.musicEnabled, settings.shuffleMusic) {
                if (playing && settings.musicEnabled && sources.isNotEmpty()) {
                    audio.play(sources[settings.audioSourceIndex % sources.size], settings.shuffleMusic)
                } else {
                    audio.stop()
                }
            }

            var lastBackPress by remember { mutableLongStateOf(0L) }
            BackHandler(enabled = playMode != null && !showSettings) { playMode = null }
            BackHandler(enabled = showSettings) { showSettings = false }
            BackHandler(enabled = playMode == null && !showSettings) {
                val now = System.currentTimeMillis()
                if (now - lastBackPress < 2500) {
                    this@MainActivity.finish()
                } else {
                    lastBackPress = now
                    Toast.makeText(
                        this@MainActivity, "Zurück-Taste noch einmal drücken zum Beenden", Toast.LENGTH_SHORT
                    ).show()
                }
            }

            when {
                showSettings -> SettingsScreen(settings) { update(it) }
                lib == null -> Message(text = status, showLoading = isScanning)
                playMode == null || items.isEmpty() -> StartScreen(
                    folders = folderInfos,
                    disabled = settings.disabledFolders,
                    imageCount = imageCount,
                    videoCount = videoCount,
                    info = startInfo,
                    noRepeatDays = settings.noRepeatDays,
                    recentCount = recent.size,
                    onDisabledChange = { update(settings.copy(disabledFolders = it)) },
                    onResetHistory = {
                        shownStore.clear()
                        historyReset++
                        startInfo = null
                    },
                    onStart = {
                        slideIndex = 0
                        startInfo = null
                        playMode = it
                    },
                )
                else -> SlideshowScreen(
                    items = items,
                    initialIndex = slideIndex,
                    onIndexChange = { slideIndex = it },
                    imageSeconds = settings.imageSeconds.toLong(),
                    transitionMs = settings.transitionMs,
                    videoSound = settings.videoSound,
                    autoRotate = settings.autoRotate,
                    zoomEnabled = settings.zoomEnabled,
                    faceZoom = settings.faceZoom,
                    zoomMaxPercent = settings.zoomMaxPercent,
                    zoomSpeedPercent = settings.zoomSpeedPercent,
                    showFileName = settings.showFileName,
                    showDate = settings.showDate,
                    textSizePercent = settings.textSizePercent,
                    finishAtEnd = settings.noRepeatDays > 0 && playMode != PlayMode.VIDEOS,
                    onImageShown = { shownStore.markShown(it.absolutePath) },
                    onFinished = {
                        startInfo = "Alle Bilder wurden gezeigt. Die Diashow ist zu Ende."
                        playMode = null
                    },
                    onSourceLost = {
                        audio.stop()
                        startInfo = "Die Medien sind nicht mehr erreichbar. Wurde der USB-Stick entfernt?"
                        playMode = null
                    },
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
    private fun Message(text: String, showLoading: Boolean) {
        Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (showLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF7FB0FF),
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
            }
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
        try { unregisterReceiver(usbReceiver) } catch (e: Exception) { }
        audio.release()
        super.onDestroy()
    }
}