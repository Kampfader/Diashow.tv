package de.diashow.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun <T> next(options: List<T>, current: T): T {
    val i = options.indexOf(current)
    return options[(i + 1) % options.size]
}

internal fun onOff(b: Boolean) = if (b) "An" else "Aus"

private fun zoomLabel(percent: Int) =
    "bis " + "%.1f".format(percent / 100f).replace('.', ',') + "×"

private fun speedLabel(percent: Int) = when (percent) {
    100 -> "Langsam (ganze Anzeigedauer)"
    70 -> "Mittel"
    50 -> "Schnell"
    else -> "Sehr schnell"
}

private fun daysLabel(days: Int) = when (days) {
    0 -> "Aus"
    1 -> "1 Tag"
    else -> "$days Tage"
}

internal fun folderLabel(path: String): String =
    path.split("/").filter { it.isNotEmpty() }.takeLast(2).joinToString("/")

@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
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
                Text("Einstellungen", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "OK = Wert ändern   ·   Zurück oder Menü = schließen",
                    color = Color(0xFFAAAAAA), fontSize = 16.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        item { Header("Bilder") }
        item {
            SettingRow(
                "Anzeigedauer pro Bild", "${settings.imageSeconds} Sekunden",
                Modifier.focusRequester(first)
            ) {
                onChange(settings.copy(imageSeconds = next(IMAGE_SECONDS_OPTIONS, settings.imageSeconds)))
            }
        }
        item {
            val label = if (settings.transitionMs == 0) "Ohne"
            else "Überblendung " + "%.1f".format(settings.transitionMs / 1000.0).replace('.', ',') + " s"
            SettingRow("Übergang", label) {
                onChange(settings.copy(transitionMs = next(TRANSITION_OPTIONS, settings.transitionMs)))
            }
        }
        item {
            SettingRow("Zoom-Effekt", onOff(settings.zoomEnabled)) {
                onChange(settings.copy(zoomEnabled = !settings.zoomEnabled))
            }
        }
        item {
            SettingRow("Auf Gesichter zoomen", onOff(settings.faceZoom)) {
                onChange(settings.copy(faceZoom = !settings.faceZoom))
            }
        }
        item {
            SettingRow("Zoom-Stärke", zoomLabel(settings.zoomMaxPercent)) {
                onChange(settings.copy(zoomMaxPercent = next(ZOOM_MAX_OPTIONS, settings.zoomMaxPercent)))
            }
        }
        item {
            SettingRow("Zoom-Geschwindigkeit", speedLabel(settings.zoomSpeedPercent)) {
                onChange(settings.copy(zoomSpeedPercent = next(ZOOM_SPEED_OPTIONS, settings.zoomSpeedPercent)))
            }
        }
        item {
            SettingRow("Reihenfolge der Bilder", if (settings.shuffleImages) "Zufällig" else "Nach Ordner und Name") {
                onChange(settings.copy(shuffleImages = !settings.shuffleImages))
            }
        }
        item {
            SettingRow("Bilder nicht wiederholen (innerhalb von)", daysLabel(settings.noRepeatDays)) {
                onChange(settings.copy(noRepeatDays = next(NO_REPEAT_OPTIONS, settings.noRepeatDays)))
            }
        }

        item { Header("Einblendungen") }
        item {
            SettingRow("Dateiname anzeigen", onOff(settings.showFileName)) {
                onChange(settings.copy(showFileName = !settings.showFileName))
            }
        }
        item {
            SettingRow("Aufnahmedatum anzeigen", onOff(settings.showDate)) {
                onChange(settings.copy(showDate = !settings.showDate))
            }
        }
        item {
            SettingRow("Schriftgröße der Einblendungen", "${settings.textSizePercent} %") {
                onChange(settings.copy(textSizePercent = next(TEXT_SIZE_OPTIONS, settings.textSizePercent)))
            }
        }

        item { Header("Videos") }
        item {
            SettingRow("Reihenfolge der Videos", if (settings.shuffleVideos) "Zufällig" else "Nach Ordner und Name") {
                onChange(settings.copy(shuffleVideos = !settings.shuffleVideos))
            }
        }
        item {
            SettingRow("Videoton (Musik pausiert dann)", onOff(settings.videoSound)) {
                onChange(settings.copy(videoSound = !settings.videoSound))
            }
        }

        item {
            SettingRow("Videos automatisch drehen", onOff(settings.autoRotate)) {
                onChange(settings.copy(autoRotate = !settings.autoRotate))
            }
        }

        item { Header("Musik") }
        item {
            SettingRow("Musik", onOff(settings.musicEnabled)) {
                onChange(settings.copy(musicEnabled = !settings.musicEnabled))
            }
        }
        item {
            SettingRow("USB-Musik zufällig", onOff(settings.shuffleMusic)) {
                onChange(settings.copy(shuffleMusic = !settings.shuffleMusic))
            }
        }
        item {
            SettingRow("Lautstärke der Musik", "${settings.musicVolumePercent} %") {
                onChange(settings.copy(musicVolumePercent = next(VOLUME_OPTIONS, settings.musicVolumePercent)))
            }
        }
    }
}

@Composable
internal fun Header(text: String) {
    Text(
        text, color = Color(0xFF7FB0FF), fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp)
    )
}

@Composable
internal fun SettingRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .background(if (focused) Color(0xFF2A62FF) else Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = Color.White, fontSize = 20.sp)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}