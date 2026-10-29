package de.diashow.tv

data class RadioStation(val name: String, val url: String)

val radioStations = listOf(
    RadioStation("Dorognoe Radio", "https://dorognoe.hostingradio.ru:8000/dorognoe"),
    // Alternativen, falls der Stream oben nicht läuft (URL oben ersetzen):
    // "https://dorognoe.hostingradio.ru:8000/radio"               (256 kbit/s)
    // "http://emgregion.hostingradio.ru:8064/moscow.dorognoe.mp3" (320 kbit/s)
)
