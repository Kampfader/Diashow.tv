package de.diashow.tv

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Gemeinsamer Bereich aller Hauptgesichter. Alle Werte sind relativ zum Bild (0..1). */
data class FaceFocus(
    val cx: Float,      // Mittelpunkt X
    val cy: Float,      // Mittelpunkt Y
    val w: Float,       // Breite des Gesichtsbereichs
    val h: Float,       // Höhe des Gesichtsbereichs
    val aspect: Float,  // Bildbreite / Bildhöhe
)

object FaceFinder {
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.05f)
                .build()
        )
    }

    private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }

    /** Sucht Gesichter in einer verkleinerten Kopie des Bildes. Null = keine Gesichter oder Fehler. */
    suspend fun find(context: Context, file: File): FaceFocus? {
        return try {
            // Coil lädt das Bild verkleinert und berücksichtigt die EXIF-Ausrichtung
            val request = ImageRequest.Builder(context)
                .data(file)
                .size(640)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                ?: return null

            val faces = detector.process(InputImage.fromBitmap(bmp, 0)).await()
            if (faces.isNullOrEmpty()) return null

            // Nur die großen Gesichter zählen, kleine im Hintergrund werden ignoriert
            val areas = faces.map { it.boundingBox.width() * it.boundingBox.height() }
            val maxArea = areas.max()
            val main = faces.filterIndexed { i, _ -> areas[i] >= maxArea * 0.25f }

            val l = main.minOf { it.boundingBox.left }.toFloat()
            val t = main.minOf { it.boundingBox.top }.toFloat()
            val r = main.maxOf { it.boundingBox.right }.toFloat()
            val b = main.maxOf { it.boundingBox.bottom }.toFloat()
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()

            FaceFocus(
                cx = ((l + r) / 2f / bw).coerceIn(0f, 1f),
                cy = ((t + b) / 2f / bh).coerceIn(0f, 1f),
                w = ((r - l) / bw).coerceAtLeast(0.01f),
                h = ((b - t) / bh).coerceAtLeast(0.01f),
                aspect = bw / bh,
            )
        } catch (e: Exception) {
            null
        }
    }
}
