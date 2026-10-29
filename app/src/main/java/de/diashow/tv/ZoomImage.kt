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

/**
 * Zeigt ein Bild mit langsamem Zoom. Mit faceZoom wird auf die erkannten Gesichter gezoomt
 * (das Gesicht wandert dabei Richtung Bildmitte), sonst sanft in die Bildmitte.
 *
 * zoomMax: größte Vergrößerung (z. B. 1,6 = 160 %)
 * speedFraction: Anteil der Anzeigedauer, in dem der Zoom abläuft (1,0 = ganze Dauer, 0,5 = erste Hälfte)
 */
@Composable
fun ZoomImage(
    file: File,
    durationMs: Int,
    zoomEnabled: Boolean,
    faceZoom: Boolean,
    zoomMax: Float,
    speedFraction: Float,
) {
    val context = LocalContext.current
    val searchFaces = zoomEnabled && faceZoom

    // Gesichtssuche im Hintergrund. null = Suche läuft noch.
    val result by produceState<FocusResult?>(
        if (searchFaces) null else FocusResult(null), file, searchFaces
    ) {
        value = if (searchFaces) FocusResult(FaceFinder.find(context, file)) else FocusResult(null)
    }

    val progress = remember(file) { Animatable(0f) }
    LaunchedEffect(file, zoomEnabled, result != null) {
        if (zoomEnabled && result != null) {
            val ms = (durationMs * speedFraction).toInt().coerceAtLeast(500)
            progress.animateTo(1f, tween(ms, easing = LinearEasing))
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
                    // keine Gesichter: sanfter Zoom in die Bildmitte
                    val s = 1f + (zoomMax - 1f) * 0.2f * p
                    scaleX = s
                    scaleY = s
                    return@graphicsLayer
                }

                // Je kleiner der Gesichtsbereich, desto näher; höchstens zoomMax
                val endScale = (0.5f / maxOf(f.w, f.h)).coerceIn(1f + (zoomMax - 1f) * 0.4f, zoomMax)
                val s = 1f + (endScale - 1f) * p

                // Bereich, den das Bild bei ContentScale.Fit auf dem Bildschirm belegt
                val dh = minOf(h, w / f.aspect)
                val dw = dh * f.aspect
                val ox = (w - dw) / 2f
                val oy = (h - dh) / 2f
                val px = ox + f.cx * dw
                val py = oy + f.cy * dh

                transformOrigin = TransformOrigin(px / w, py / h)
                scaleX = s
                scaleY = s
                // Gesicht wandert teilweise zur Bildschirmmitte, ohne dass Ränder sichtbar werden
                translationX = axisShift((w / 2f - px) * 0.6f * p, px, s, ox, dw, w)
                translationY = axisShift((h / 2f - py) * 0.6f * p, py, s, oy, dh, h)
            }
    )
}

/** Begrenzt die Verschiebung so, dass ein bildschirmfüllendes Bild keine Lücken am Rand bekommt. */
private fun axisShift(want: Float, pivot: Float, s: Float, o: Float, d: Float, screen: Float): Float {
    if (d < screen - 1f) return want // Bild füllt diese Achse nicht, keine Begrenzung nötig
    val lo = screen - (pivot + s * (o + d - pivot)) // Gegenkante muss mindestens den Rand erreichen
    val hi = -(pivot + s * (o - pivot))
    return if (lo <= hi) want.coerceIn(lo, hi) else 0f
}
