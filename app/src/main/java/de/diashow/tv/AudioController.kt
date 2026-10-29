package de.diashow.tv

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

sealed class AudioSource(val label: String) {
    class Files(val files: List<File>) : AudioSource("USB-Musik")
    class Radio(val station: RadioStation) : AudioSource(station.name)
}

class AudioController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
        repeatMode = Player.REPEAT_MODE_ALL
    }

    fun play(source: AudioSource, shuffleFiles: Boolean = true) {
        player.stop()
        player.clearMediaItems()
        when (source) {
            is AudioSource.Files -> {
                player.shuffleModeEnabled = shuffleFiles
                player.setMediaItems(source.files.map { MediaItem.fromUri(Uri.fromFile(it)) })
            }
            is AudioSource.Radio -> {
                player.shuffleModeEnabled = false
                player.setMediaItem(MediaItem.fromUri(source.station.url))
            }
        }
        player.prepare()
        player.play()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    fun setVolume(v: Float) { player.volume = v }

    fun release() = player.release()
}
