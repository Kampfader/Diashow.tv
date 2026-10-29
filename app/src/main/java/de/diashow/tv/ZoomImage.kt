package de.diashow.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.io.File

private class FocusResult(val focus: FaceFocus?)

@Composable
fun ZoomImage(
    file: File,
    durationMs: Int,
    zoomEnabled: Boolean,
    faceZoom: Boolean,
    zoomMax: Float,
    speedFraction: Float,
    isPaused: Boolean, // NEU: Anhalten des Zooms bei Pause
) {
    val context = LocalContext.current
    val searchFaces = zoomEnabled && faceZoom

    val result by produceState<FocusResult?>(
        if (searchFaces) null else FocusResult(null), file, searchFaces
    ) {
        value = if (searchFaces) FocusResult(FaceFinder.find(context, file)) else FocusResult(null)
    }

    val progress = remember(file) { Animatable(0f) }

    LaunchedEffect(file, zoomEnabled, result != null, isPaused) {
        if (zoomEnabled && result != null) {
            if (isPaused) {
                progress.stop()
            } else {
                val totalMs = (durationMs * speedFraction).toInt().coerceAtLeast(500)
                val remainingMs = (totalMs * (1f - progress.value)).toInt().coerceAtLeast(10)
                if (progress.value < 1f) {
                    progress.animateTo(1f, tween(remainingMs, easing = LinearEasing))
                }
            }
        }
    }

    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                if (!zoomEnabled || p <= 0f) return@graphicsLayer
                val w = size.width
                val h = size.height
                val f = result?.focus

                if (f == null) {
                    val s = 1f + (zoomMax - 1f) * 0.2f * p
                    scaleX = s
                    scaleY = s
                    return@graphicsLayer
                }

                val endScale = (0.5f / maxOf(f.w, f.h)).coerceIn(1f + (zoomMax - 1f) * 0.4f, zoomMax)
                val s = 1f + (endScale - 1f) * p

                val dh = minOf(h, w / f.aspect)
                val dw = dh * f.aspect
                val ox = (w - dw) / 2f
                val oy = (h - dh) / 2f
                val px = ox + f.cx * dw
                val py = oy + f.cy * dh

                transformOrigin = TransformOrigin(px / w, py / h)
                scaleX = s
                scaleY = s
                translationX = axisShift((w / 2f - px) * 0.6f * p, px, s, ox, dw, w)
                translationY = axisShift((h / 2f - py) * 0.6f * p, py, s, oy, dh, h)
            }
    )
}

private fun axisShift(want: Float, pivot: Float, s: Float, o: Float, d: Float, screen: Float): Float {
    if (d < screen - 1f) return want
    val lo = screen - (pivot + s * (o + d - pivot))
    val hi = -(pivot + s * (o - pivot))
    return if (lo <= hi) want.coerceIn(lo, hi) else 0f
}