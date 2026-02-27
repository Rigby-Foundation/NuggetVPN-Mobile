package org.rigbyfoundation.nuggetvpn.util

fun Long.formatBytes(): String {
    if (this < 1024) return "$this B"
    val kb = this / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

fun Long.formatSpeed(): String {
    if (this < 1024) return "$this B/s"
    val kb = this / 1024.0
    if (kb < 1024) return "%.1f KB/s".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB/s".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB/s".format(gb)
}

fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
