package org.rigbyfoundation.nuggetvpn.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.rigbyfoundation.nuggetvpn.data.models.Profile
import org.rigbyfoundation.nuggetvpn.ui.components.ProxyCard
import org.rigbyfoundation.nuggetvpn.ui.theme.NuggetAmber

@Composable
fun ProxiesScreen(
    profiles: List<Profile>,
    profilePings: Map<String, Long?>,
    selectedSourceDomain: String,
    selectedProxyMode: String,
    selectedProfileId: String,
    onSelectProxy: (String) -> Unit,
    onSelectAuto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredProfiles = if (selectedSourceDomain == "all") profiles
    else profiles.filter { it.sourceDomain == selectedSourceDomain }

    val bestPing = profilePings.values.filterNotNull().minOrNull()
    val isSubscription = selectedSourceDomain != "all" && selectedSourceDomain != "local"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Text(
            text = "Proxies",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        Text(
            text = if (selectedSourceDomain == "all") "Showing all proxies"
            else "Showing proxies from $selectedSourceDomain",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Auto selection card (for subscriptions)
        if (isSubscription || filteredProfiles.size > 1) {
            Card(
                onClick = onSelectAuto,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedProxyMode == "auto")
                        NuggetAmber.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = NuggetAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Auto (Best ping)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (bestPing != null) {
                                Text(
                                    text = "${bestPing}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    if (selectedProxyMode == "auto") {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = NuggetAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Proxies grid
        if (filteredProfiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No proxies available.\nAdd a profile or subscription first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredProfiles, key = { it.id }) { profile ->
                    ProxyCard(
                        profile = profile,
                        ping = profilePings[profile.id],
                        isSelected = selectedProxyMode == "manual" && profile.id == selectedProfileId,
                        onClick = { onSelectProxy(profile.id) }
                    )
                }
            }
        }
    }
}
