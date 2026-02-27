package org.rigbyfoundation.nuggetvpn.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.rigbyfoundation.nuggetvpn.data.models.IpInfo
import org.rigbyfoundation.nuggetvpn.data.models.VpnState
import org.rigbyfoundation.nuggetvpn.ui.components.PowerButton
import org.rigbyfoundation.nuggetvpn.ui.theme.NuggetGreen
import org.rigbyfoundation.nuggetvpn.ui.theme.NuggetRed
import org.rigbyfoundation.nuggetvpn.util.formatDuration
import org.rigbyfoundation.nuggetvpn.util.formatSpeed

@Composable
fun ConnectionScreen(
    vpnState: VpnState,
    onToggleVpn: () -> Unit,
    duration: Long,
    uploadSpeed: Long,
    downloadSpeed: Long,
    ipInfo: IpInfo?,
    isCheckingIp: Boolean,
    onCheckIp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = vpnState == VpnState.CONNECTED
    val isConnecting = vpnState == VpnState.CONNECTING

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Power button
        PowerButton(
            isConnected = isConnected,
            isConnecting = isConnecting,
            onClick = onToggleVpn
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status text
        Text(
            text = when (vpnState) {
                VpnState.CONNECTED -> "Connected"
                VpnState.CONNECTING -> "Connecting..."
                VpnState.DISCONNECTING -> "Disconnecting..."
                VpnState.DISCONNECTED -> "Tap to connect"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        // Stats grid (visible when connected)
        AnimatedVisibility(
            visible = isConnected,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Upload
                    StatItem(
                        icon = Icons.Default.ArrowUpward,
                        iconColor = NuggetRed,
                        label = "Upload",
                        value = uploadSpeed.formatSpeed()
                    )

                    // Download
                    StatItem(
                        icon = Icons.Default.ArrowDownward,
                        iconColor = NuggetGreen,
                        label = "Download",
                        value = downloadSpeed.formatSpeed()
                    )

                    // Duration
                    StatItem(
                        icon = Icons.Default.Timer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "Duration",
                        value = duration.formatDuration()
                    )

                    // IP
                    StatItem(
                        icon = Icons.Default.Language,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "IP",
                        value = if (isCheckingIp) "..." else ipInfo?.ip ?: "—",
                        onClick = onCheckIp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .let { if (onClick != null) it.then(Modifier) else it }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
