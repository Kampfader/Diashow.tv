package de.diashow.tv

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import java.io.File

object VideoRotation {
    /** Liest die Drehung (0/90/180/270 Grad im Uhrzeigersinn), die in der Videodatei gespeichert ist. */
    fun readTag(file: File): Int = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val deg = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            ((deg % 360) + 360) % 360
        } finally {
            r.release()
        }
    } catch (e: Exception) {
        0
    }
}

/**
 * Erstellt den Player. Bei autoRotate wird die Drehinformation aus der Datei NICHT an den
 * Decoder weitergegeben, weil die App die Drehung selbst übernimmt. Dadurch wird genau einmal
 * gedreht, egal ob der Decoder des Geräts die Drehung sonst beherrscht oder nicht.
 */
@OptIn(UnstableApi::class)
fun createVideoPlayer(context: Context, autoRotate: Boolean): ExoPlayer {
    val builder = ExoPlayer.Builder(context)
    if (autoRotate) {
        builder.setMediaSourceFactory(DefaultMediaSourceFactory(context, NoRotationExtractorsFactory()))
    }
    return builder.build()
}

@OptIn(UnstableApi::class)
private class NoRotationExtractorsFactory : ExtractorsFactory {
    private val delegate = DefaultExtractorsFactory()

    private fun wrap(a: Array<Extractor>): Array<Extractor> =
        Array(a.size) { NoRotationExtractor(a[it]) }

    override fun createExtractors(): Array<Extractor> = wrap(delegate.createExtractors())

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>
    ): Array<Extractor> = wrap(delegate.createExtractors(uri, responseHeaders))
}

@OptIn(UnstableApi::class)
private class NoRotationExtractor(private val inner: Extractor) : Extractor by inner {
    override fun init(output: ExtractorOutput) {
        inner.init(NoRotationOutput(output))
    }
}

@OptIn(UnstableApi::class)
private class NoRotationOutput(private val out: ExtractorOutput) : ExtractorOutput {
    override fun track(id: Int, type: Int): TrackOutput {
        val t = out.track(id, type)
        return if (type == C.TRACK_TYPE_VIDEO) NoRotationTrack(t) else t
    }

    override fun endTracks() = out.endTracks()

    override fun seekMap(seekMap: SeekMap) = out.seekMap(seekMap)
}

@OptIn(UnstableApi::class)
private class NoRotationTrack(private val t: TrackOutput) : TrackOutput by t {
    override fun format(format: Format) {
        t.format(format.buildUpon().setRotationDegrees(0).build())
    }
}