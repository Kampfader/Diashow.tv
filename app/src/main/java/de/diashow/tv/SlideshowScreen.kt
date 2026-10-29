package de.diashow.tv

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SlideshowScreen(
    items: List<File>,
    imageSeconds: Long,
    transitionMs: Int,
    videoSound: Boolean,
    autoRotate: Boolean,
    zoomEnabled: Boolean,
    faceZoom: Boolean,
    audioLabel: String,
    onCycleAudio: () -> Unit,
    onVideoActive: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var labelVisible by remember { mutableStateOf(true) }
    var rotationHint by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    // Manuelle Zusatzdrehung als Korrektur (zusätzlich zur automatischen Drehung)
    val rotationStore = remember { RotationStore(context) }
    val rotations = remember { mutableStateMapOf<String, Int>() }
    fun rotationOf(f: File): Int = rotations[f.absolutePath] ?: rotationStore.get(f.absolutePath)

    val current = items[index.coerceIn(0, items.lastIndex)]
    val isVideo = MediaScanner.isVideo(current)
    fun next() { index = (index + 1) % items.size }

    fun rotateCurrent(delta: Int) {
        if (!isVideo) return
        val deg = ((rotationOf(current) + delta) % 360 + 360) % 360
        rotations[current.absolutePath] = deg
        rotationStore.set(current.absolutePath, deg)
        rotationHint = "Zusätzliche Drehung: $deg° (wird gespeichert)"
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    // Hinweis beim Start, bei Musikwechsel und bei jedem neuen Video
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
                VideoPlayer(file, videoSound, items.size == 1, rotationOf(file), autoRotate) { next() }
            } else {
                ZoomImage(file, (imageSeconds * 1000).toInt() + transitionMs, zoomEnabled, faceZoom)
            }
        }
        if (labelVisible) {
            Text(
                text = "♪ $audioLabel   ·   Menü-Taste: Einstellungen" +
                    if (isVideo) "   ·   ⏪ ⏩ Drehung korrigieren" else "",
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
                modifier = Modifier.align(Alignment.TopEnd).padding(32.dp)
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
    onEnded: () -> Unit,
) {
    // Die in der Datei gespeicherte Drehung wird gelesen, bevor das Video startet
    val tag by produceState<Int?>(if (autoRotate) null else 0, file, autoRotate) {
        value = if (autoRotate) withContext(Dispatchers.IO) { VideoRotation.readTag(file) } else 0
    }
    val t = tag
    if (t != null) {
        VideoPlayerContent(file, withSound, loop, (t + manualRotation) % 360, autoRotate, onEnded)
    }
}

@Composable
private fun VideoPlayerContent(
    file: File,
    withSound: Boolean,
    loop: Boolean,
    rotation: Int,
    autoRotate: Boolean,
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
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
            override fun onPlayerError(error: PlaybackException) = onEnded()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Ohne Drehung: schnelle SurfaceView. Mit Drehung: TextureView, die sich transformieren lässt.
    val useTexture = rotation != 0
    key(useTexture) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation.toFloat()
                    if (rotation % 180 != 0) {
                        // Bei 90°/270° so skalieren, dass das gedrehte Video genau in den Bildschirm passt
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
