package de.diashow.tv

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

object MediaScanner {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val videoExt = setOf("mp4", "mkv", "mov", "webm", "avi", "m4v")
    private val audioExt = setOf("mp3", "m4a", "aac", "flac", "ogg", "wav")

    data class Library(val visual: List<File>, val music: List<File>)

    fun isVideo(file: File) = file.extension.lowercase() in videoExt

    /**
     * Sucht Wechseldatenträger auf drei Wegen, weil Fire OS /storage oft nicht auflisten lässt:
     * 1. StorageManager (liefert Volumes samt UUID)
     * 2. App-spezifische Ordner auf allen Volumes (daraus wird die Wurzel abgeleitet)
     * 3. Direkter Blick in /storage
     */
    fun findUsbRoots(context: Context): List<File> {
        val primary = Environment.getExternalStorageDirectory().absolutePath
        val roots = linkedSetOf<File>()

        // 1. StorageManager
        runCatching {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.storageVolumes.forEach { v ->
                if (v.isPrimary) return@forEach
                val dir = (if (Build.VERSION.SDK_INT >= 30) v.directory else null)
                    ?: v.uuid?.let { File("/storage/$it") }
                if (dir != null) roots += dir
            }
        }

        // 2. getExternalFilesDirs -> Wurzel ableiten
        runCatching {
            context.getExternalFilesDirs(null).filterNotNull().forEach { d ->
                val i = d.absolutePath.indexOf("/Android/data")
                if (i > 0) roots += File(d.absolutePath.substring(0, i))
            }
        }

        // 3. /storage direkt
        File("/storage").listFiles()?.forEach {
            if (it.isDirectory && it.name != "emulated" && it.name != "self") roots += it
        }

        return roots.filter { it.absolutePath != primary && !it.absolutePath.startsWith("$primary/") }
    }

    /** Durchsucht den Stick rekursiv. Bilder und Videos bleiben nach Ordner/Name sortiert zusammen. */
    fun scan(root: File): Library {
        val visual = mutableListOf<File>()
        val music = mutableListOf<File>()
        root.walkTopDown()
            .onEnter { !it.name.startsWith(".") && it.name != "Android" }
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { f ->
                val ext = f.extension.lowercase()
                when {
                    ext in imageExt || ext in videoExt -> visual += f
                    ext in audioExt -> music += f
                }
            }
        return Library(visual.sortedBy { it.path.lowercase() }, music)
    }
}
