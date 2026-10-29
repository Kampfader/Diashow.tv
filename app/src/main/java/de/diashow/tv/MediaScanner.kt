package de.diashow.tv

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

object MediaScanner {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    // NEU: wmv und asf hinzugefügt
    private val videoExt = setOf("mp4", "mkv", "mov", "webm", "avi", "m4v", "wmv", "asf")
    // NEU: wma hinzugefügt
    private val audioExt = setOf("mp3", "m4a", "aac", "flac", "ogg", "wav", "wma")

    data class Library(val visual: List<File>, val music: List<File>)

    fun isVideo(file: File) = file.extension.lowercase() in videoExt

    fun findUsbRoots(context: Context): List<File> {
        val primary = Environment.getExternalStorageDirectory().absolutePath
        val roots = linkedSetOf<File>()

        runCatching {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.storageVolumes.forEach { v ->
                if (v.isPrimary) return@forEach
                val dir = (if (Build.VERSION.SDK_INT >= 30) v.directory else null)
                    ?: v.uuid?.let { File("/storage/$it") }
                if (dir != null) roots += dir
            }
        }

        runCatching {
            context.getExternalFilesDirs(null).filterNotNull().forEach { d ->
                val i = d.absolutePath.indexOf("/Android/data")
                if (i > 0) roots += File(d.absolutePath.substring(0, i))
            }
        }

        runCatching {
            File("/storage").listFiles()?.forEach {
                if (it.isDirectory && it.name != "emulated" && it.name != "self") roots += it
            }
        }

        return roots.filter { it.absolutePath != primary && !it.absolutePath.startsWith("$primary/") }
    }

    fun signature(roots: List<File>): String =
        roots.joinToString(";") { r ->
            val count = try { r.listFiles()?.size ?: -1 } catch (e: Exception) { -1 }
            "${r.absolutePath}|$count"
        }

    fun scan(root: File): Library {
        val visual = mutableListOf<File>()
        val music = mutableListOf<File>()
        try {
            root.walkTopDown()
                .onFail { _, _ -> }
                .onEnter { !it.name.startsWith(".") && it.name != "Android" }
                .filter { it.isFile && !it.name.startsWith(".") }
                .forEach { f ->
                    val ext = f.extension.lowercase()
                    when {
                        ext in imageExt || ext in videoExt -> visual += f
                        ext in audioExt -> music += f
                    }
                }
        } catch (e: Exception) {
        }
        return Library(visual.sortedBy { it.path.lowercase() }, music)
    }
}