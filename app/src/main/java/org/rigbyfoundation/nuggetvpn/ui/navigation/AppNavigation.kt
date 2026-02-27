package org.rigbyfoundation.nuggetvpn.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    Connection("connection", "Connection", Icons.Default.Power),
    Configuration("configuration", "Config", Icons.Default.Storage),
    Proxies("proxies", "Proxies", Icons.Default.Public),
    Logs("logs", "Logs", Icons.Default.Schedule),
    Settings("settings", "Settings", Icons.Default.Settings)
}
