package de.diashow.tv

import android.graphics.SurfaceTexture
import android.media.ExifInterface
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun rememberFormattedDate(file: File): String? {
    return produceState<String?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            try {
                val exif = ExifInterface(file.absolutePath)
                val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                if (!dateStr.isNullOrBlank()) {
                    val inFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                    val outFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    inFormat.parse(dateStr)?.let { outFormat.format(it) }
                } else if (file.lastModified() > 0) {
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(file.lastModified()))
                } else null
            } catch (e: Exception) {
                if (file.lastModified() > 0) {
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(file.lastModified()))
                } else null
            }
        }
    }.value
}

@Composable
fun SlideshowScreen(
    items: List<File>,
    initialIndex: Int,
    onIndexChange: (Int) -> Unit,
    imageSeconds: Long,
    transitionMs: Int,
    videoSound: Boolean,
    autoRotate: Boolean,
    zoomEnabled: Boolean,
    faceZoom: Boolean,
    zoomMaxPercent: Int,
    zoomSpeedPercent: Int,
    showFileName: Boolean,
    showDate: Boolean,
    textSizePercent: Int,
    finishAtEnd: Boolean,
    onImageShown: (File) -> Unit,
    onFinished: () -> Unit,
    onSourceLost: () -> Unit,
    audioLabel: String,
    onCycleAudio: () -> Unit,
    onVideoActive: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(initialIndex.coerceIn(0, items.lastIndex)) }
    var paused by remember { mutableStateOf(false) }
    var labelVisible by remember { mutableStateOf(true) }
    var rotationHint by remember { mutableStateOf<String?>(null) }
    var errorStreak by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    val rotationStore = remember { RotationStore(context) }
    val rotations = remember { mutableStateMapOf<String, Int>() }
    fun rotationOf(f: File): Int = rotations[f.absolutePath] ?: rotationStore.get(f.absolutePath)

    val current = items[index.coerceIn(0, items.lastIndex)]
    val isVideo = MediaScanner.isVideo(current)

    val formattedDate = if (showDate) rememberFormattedDate(current) else null

    fun next() {
        if (finishAtEnd && index >= items.lastIndex) onFinished()
        else index = (index + 1) % items.size
    }

    fun rotateCurrent(delta: Int) {
        if (!isVideo) return
        val deg = ((rotationOf(current) + delta) % 360 + 360) % 360
        rotations[current.absolutePath] = deg
        rotationStore.set(current.absolutePath, deg)
        rotationHint = "Zusätzliche Drehung: $deg° (wird gespeichert)"
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(index) { onIndexChange(index) }
    LaunchedEffect(current) {
        val exists = withContext(Dispatchers.IO) { current.exists() }
        if (!exists) onSourceLost()
    }
    LaunchedEffect(current) { if (!MediaScanner.isVideo(current)) onImageShown(current) }
    LaunchedEffect(audioLabel, if (isVideo) current else null) {
        labelVisible = true; delay(4000); labelVisible = false
    }
    LaunchedEffect(rotationHint) {
        if (rotationHint != null) { delay(2500); rotationHint = null }
    }
    LaunchedEffect(isVideo, videoSound) { onVideoActive(isVideo && videoSound) }

    LaunchedEffect(index, paused) {
        if (!isVideo && !paused) {
            delay(imageSeconds * 1000)
            next()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { next(); true }
                    Key.DirectionLeft -> { index = (index - 1 + items.size) % items.size; true }
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> { paused = !paused; true }
                    Key.DirectionUp, Key.DirectionDown -> { onCycleAudio(); true }
                    Key.MediaFastForward -> { rotateCurrent(90); true }
                    Key.MediaRewind -> { rotateCurrent(-90); true }
                    else -> false
                }
            }
    ) {
        Crossfade(targetState = current, animationSpec = tween(transitionMs), label = "slide") { file ->
            if (MediaScanner.isVideo(file)) {
                val ext = file.extension.lowercase()
                if (ext == "avi" || ext == "wmv" || ext == "asf" || ext == "mov") {
                    LegacyAviPlayer(
                        file = file,
                        withSound = videoSound,
                        loop = items.size == 1 && !finishAtEnd,
                        rotation = rotationOf(file),
                        isPaused = paused,
                        onError = {
                            errorStreak++
                            if (errorStreak >= 3 || !file.exists()) onSourceLost() else next()
                        }
                    ) { errorStreak = 0; next() }
                } else {
                    VideoPlayer(
                        file = file,
                        withSound = videoSound,
                        loop = items.size == 1 && !finishAtEnd,
                        manualRotation = rotationOf(file),
                        autoRotate = autoRotate,
                        isPaused = paused,
                        onError = {
                            errorStreak++
                            if (errorStreak >= 3 || !file.exists()) onSourceLost() else next()
                        }
                    ) { errorStreak = 0; next() }
                }
            } else {
                ZoomImage(
                    file = file,
                    durationMs = (imageSeconds * 1000).toInt() + transitionMs,
                    zoomEnabled = zoomEnabled,
                    faceZoom = faceZoom,
                    zoomMax = zoomMaxPercent / 100f,
                    speedFraction = zoomSpeedPercent / 100f,
                    isPaused = paused
                )
            }
        }

        if (showFileName || formattedDate != null) {
            val scale = textSizePercent / 100f
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (formattedDate != null) {
                    Text(
                        text = formattedDate,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = (22 * scale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (showFileName) {
                    Text(
                        text = current.name,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = (16 * scale).sp,
                        modifier = Modifier.padding(top = (4 * scale).dp)
                    )
                }
            }
        }

        if (labelVisible) {
            Text(
                text = "♪ $audioLabel   ·   Menü: Einstellungen" +
                        if (isVideo) "   ·   ⏪ ⏩ Drehung" else "",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(32.dp)
            )
        }
        rotationHint?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(32.dp)
            )
        }
        if (paused) {
            Text(
                text = "Pause",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(32.dp)
            )
        }
    }
}

@Composable
private fun VideoPlayer(
    file: File,
    withSound: Boolean,
    loop: Boolean,
    manualRotation: Int,
    autoRotate: Boolean,
    isPaused: Boolean,
    onError: () -> Unit,
    onEnded: () -> Unit,
) {
    val tag by produceState<Int?>(if (autoRotate) null else 0, file, autoRotate) {
        value = if (autoRotate) withContext(Dispatchers.IO) { VideoRotation.readTag(file) } else 0
    }
    val t = tag
    if (t != null) {
        VideoPlayerContent(file, withSound, loop, (t + manualRotation) % 360, autoRotate, isPaused, onError, onEnded)
    }
}

@Composable
private fun VideoPlayerContent(
    file: File,
    withSound: Boolean,
    loop: Boolean,
    rotation: Int,
    autoRotate: Boolean,
    isPaused: Boolean,
    onError: () -> Unit,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    var videoSize by remember { mutableStateOf<VideoSize?>(null) }

    val player = remember(file, autoRotate) {
        createVideoPlayer(context, autoRotate).apply {
            volume = if (withSound) 1f else 0f
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(isPaused) {
        player.playWhenReady = !isPaused
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
            override fun onPlayerError(error: PlaybackException) = onError()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val useTexture = rotation != 0
    key(useTexture) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation.toFloat()
                    if (rotation % 180 != 0) {
                        val w = size.width
                        val h = size.height
                        val vs = videoSize
                        val s = if (vs != null && vs.width > 0 && vs.height > 0) {
                            val f = minOf(w / vs.width, h / vs.height)
                            val fw = vs.width * f
                            val fh = vs.height * f
                            minOf(w / fh, h / fw)
                        } else {
                            minOf(w, h) / maxOf(w, h)
                        }
                        scaleX = s
                        scaleY = s
                    }
                },
            factory = { ctx ->
                if (useTexture) {
                    LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null) as PlayerView
                } else {
                    PlayerView(ctx).apply { useController = false }
                }
            },
            update = { it.player = player }
        )
    }
}

/** Spezial-Player für AVI-Dateien mit ParcelFileDescriptor zur Umgehung von USB-Berechtigungsproblemen */
@Composable
private fun LegacyAviPlayer(
    file: File,
    withSound: Boolean,
    loop: Boolean,
    rotation: Int,
    isPaused: Boolean,
    onError: () -> Unit,
    onEnded: () -> Unit
) {
    val context = LocalContext.current
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var mediaPlayerFailed by remember { mutableStateOf(false) }

    if (mediaPlayerFailed) {
        // Falls der MediaPlayer fehlschlägt, versuchen wir ExoPlayer ohne Rotations-Wrapper
        VideoPlayer(
            file = file,
            withSound = withSound,
            loop = loop,
            manualRotation = rotation,
            autoRotate = false,
            isPaused = isPaused,
            onError = onError,
            onEnded = onEnded
        )
        return
    }

    val mediaPlayer = remember(file) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            MediaPlayer().apply {
                setDataSource(pfd.fileDescriptor)
                pfd.close()
                val vol = if (withSound) 1f else 0f
                setVolume(vol, vol)
                isLooping = loop
                setOnCompletionListener { onEnded() }
                setOnErrorListener { _, _, _ ->
                    mediaPlayerFailed = true
                    true
                }
                setOnVideoSizeChangedListener { _, w, h ->
                    videoWidth = w
                    videoHeight = h
                }
                setOnPreparedListener { mp ->
                    if (!isPaused) mp.start()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            mediaPlayerFailed = true
            null
        }
    }

    LaunchedEffect(isPaused, mediaPlayer) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        try {
            if (isPaused) {
                if (mp.isPlaying) mp.pause()
            } else {
                if (videoWidth > 0 && !mp.isPlaying) mp.start()
            }
        } catch (e: Exception) { }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                } catch (e: Exception) { }
            }
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationZ = rotation.toFloat()
                if (rotation % 180 != 0 && videoWidth > 0 && videoHeight > 0) {
                    val w = size.width
                    val h = size.height
                    val f = minOf(w / videoWidth, h / videoHeight)
                    val fw = videoWidth * f
                    val fh = videoHeight * f
                    val s = minOf(w / fh, h / fw)
                    scaleX = s
                    scaleY = s
                }
            },
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        try {
                            mediaPlayer?.setSurface(Surface(surface))
                        } catch (e: Exception) {
                            mediaPlayerFailed = true
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        try {
                            mediaPlayer?.setSurface(null)
                        } catch (e: Exception) { }
                        return true
                    }
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        }
    )
}