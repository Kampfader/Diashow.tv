package de.diashow.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PlayMode { IMAGES, VIDEOS, MIXED }

data class FolderInfo(val path: String, val images: Int, val videos: Int)

/** Startbildschirm: erst Ordner auswählen, dann die Wiedergabe starten. */
@Composable
fun StartScreen(
    folders: List<FolderInfo>,
    disabled: Set<String>,
    imageCount: Int,
    videoCount: Int,
    onDisabledChange: (Set<String>) -> Unit,
    onStart: (PlayMode) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(horizontal = 80.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("Diashow", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "OK = auswählen   ·   Menü-Taste = Einstellungen",
                    color = Color(0xFFAAAAAA), fontSize = 16.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        item { Header("Starten") }
        item {
            SettingRow("▶  Bilder-Diashow starten", "$imageCount Bilder", Modifier.focusRequester(first)) {
                if (imageCount > 0) onStart(PlayMode.IMAGES)
            }
        }
        item {
            SettingRow("▶  Videos abspielen", "$videoCount Videos") {
                if (videoCount > 0) onStart(PlayMode.VIDEOS)
            }
        }
        item {
            SettingRow("▶  Bilder und Videos gemischt", "${imageCount + videoCount} Dateien") {
                if (imageCount + videoCount > 0) onStart(PlayMode.MIXED)
            }
        }

        item { Header("Ordner auswählen") }
        item {
            SettingRow("Alle Ordner auswählen", "${folders.size} Ordner") { onDisabledChange(emptySet()) }
        }
        item {
            SettingRow("Alle Ordner abwählen", "") { onDisabledChange(folders.map { it.path }.toSet()) }
        }
        items(count = folders.size, key = { folders[it].path }) { i ->
            val f = folders[i]
            val off = f.path in disabled
            SettingRow(
                folderLabel(f.path),
                "${f.images} Bilder, ${f.videos} Videos   ${if (off) "Aus" else "An"}"
            ) {
                onDisabledChange(if (off) disabled - f.path else disabled + f.path)
            }
        }
    }
}
