package de.diashow.tv

data class RadioStation(val name: String, val url: String)

val radioStations = listOf(
    RadioStation("Dorognoe Radio", "https://dorognoe.hostingradio.ru:8000/dorognoe"),
    RadioStation("SomaFM – Drone Zone", "http://ice1.somafm.com/dronezone-128-mp3"),
    RadioStation("Classical Pan Flute", "https://0nlineradio.stream39.radiohost.de/classical-classical-pan-flute?upd-meta&upd-scheme=https&_art=dD0xNzkzMjkzODE1JmQ9YmJkZDkwN2UyZjA4YjVkNTlkMmE")
)