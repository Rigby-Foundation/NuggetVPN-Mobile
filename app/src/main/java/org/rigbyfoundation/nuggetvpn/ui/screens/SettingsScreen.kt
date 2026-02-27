package org.rigbyfoundation.nuggetvpn.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.rigbyfoundation.nuggetvpn.data.models.AppSettings
import org.rigbyfoundation.nuggetvpn.ui.theme.NuggetOrange
import org.rigbyfoundation.nuggetvpn.data.models.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: String,
    onThemeChange: (String) -> Unit,
    settings: AppSettings,
    profiles: List<Profile>,
    onSettingsChange: (AppSettings) -> Unit,
    isSynced: Boolean,
    onConnectSync: () -> Unit,
    onDisconnectSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("General", "Connection", "TLS", "Split Tunneling")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )

        // Tab row
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            divider = {},
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            when (selectedTab) {
                0 -> { // General
                    item { SectionLabel("APPEARANCE") }
                    item {
                        SettingsCard {
                            ThemeSelector(theme = theme, onThemeChange = onThemeChange)
                        }
                    }
                    item { SectionLabel("SYNCHRONIZATION") }
                    item {
                        SettingsCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Server Sync", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = if (isSynced) "Connected to ${settings.authServer}" else "Not connected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSynced) {
                                    OutlinedButton(onClick = onDisconnectSync) {
                                        Text("Disconnect")
                                    }
                                } else {
                                    Button(onClick = onConnectSync) {
                                        Text("Connect")
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> { // Connection
                    item { SectionLabel("NETWORK") }
                    item {
                        SettingsCard {
                            SettingsTextField(
                                label = "MTU",
                                value = settings.mtu.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { mtu ->
                                        onSettingsChange(settings.copy(mtu = mtu))
                                    }
                                },
                                keyboardType = KeyboardType.Number
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SettingsTextField(
                                label = "DNS Server",
                                value = settings.dns,
                                onValueChange = { onSettingsChange(settings.copy(dns = it)) }
                            )
                        }
                    }
                    item { SectionLabel("PROXY CHAIN") }
                    item {
                        SettingsCard {
                            SettingsSwitch(
                                label = "Enable Proxy Chain",
                                checked = settings.proxyChainEnabled,
                                onCheckedChange = {
                                    onSettingsChange(settings.copy(proxyChainEnabled = it))
                                }
                            )

                            if (settings.proxyChainEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))

                                // Chain list
                                settings.proxyChain.forEachIndexed { index, profileId ->
                                    val profile = profiles.find { it.id == profileId }
                                    if (profile != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${index + 1}.",
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.width(24.dp)
                                            )
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            // Move up
                                            if (index > 0) {
                                                IconButton(
                                                    onClick = {
                                                        val chain = settings.proxyChain.toMutableList()
                                                        chain[index] = chain[index - 1].also { chain[index - 1] = chain[index] }
                                                        onSettingsChange(settings.copy(proxyChain = chain))
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowUp, "Move up", modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            // Move down
                                            if (index < settings.proxyChain.lastIndex) {
                                                IconButton(
                                                    onClick = {
                                                        val chain = settings.proxyChain.toMutableList()
                                                        chain[index] = chain[index + 1].also { chain[index + 1] = chain[index] }
                                                        onSettingsChange(settings.copy(proxyChain = chain))
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowDown, "Move down", modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            // Delete
                                            IconButton(
                                                onClick = {
                                                    val chain = settings.proxyChain.toMutableList()
                                                    chain.removeAt(index)
                                                    onSettingsChange(settings.copy(proxyChain = chain))
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                // Add to chain
                                var chainExpanded by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { chainExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add to chain")
                                    }
                                    DropdownMenu(expanded = chainExpanded, onDismissRequest = { chainExpanded = false }) {
                                        profiles.filter { it.id !in settings.proxyChain }.forEach { profile ->
                                            DropdownMenuItem(
                                                text = { Text(profile.name) },
                                                onClick = {
                                                    val chain = settings.proxyChain + profile.id
                                                    onSettingsChange(settings.copy(proxyChain = chain))
                                                    chainExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> { // TLS
                    item { SectionLabel("TLS FRAGMENTATION") }
                    item {
                        SettingsCard {
                            SettingsSwitch(
                                label = "TLS Fragmentation",
                                checked = settings.tlsFragment,
                                onCheckedChange = { onSettingsChange(settings.copy(tlsFragment = it)) }
                            )
                            if (settings.tlsFragment) {
                                Spacer(modifier = Modifier.height(12.dp))
                                SettingsTextField(
                                    label = "Fragment Size (e.g., 100-200)",
                                    value = settings.tlsFragmentSize,
                                    onValueChange = { onSettingsChange(settings.copy(tlsFragmentSize = it)) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                SettingsTextField(
                                    label = "Fragment Sleep (e.g., 10-20)",
                                    value = settings.tlsFragmentSleep,
                                    onValueChange = { onSettingsChange(settings.copy(tlsFragmentSleep = it)) }
                                )
                            }
                        }
                    }
                    item { SectionLabel("SNI SETTINGS") }
                    item {
                        SettingsCard {
                            SettingsSwitch(
                                label = "TLS Mixed SNI Case",
                                checked = settings.tlsMixedSniCase,
                                onCheckedChange = { onSettingsChange(settings.copy(tlsMixedSniCase = it)) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsSwitch(
                                label = "TLS Padding",
                                checked = settings.tlsPadding,
                                onCheckedChange = { onSettingsChange(settings.copy(tlsPadding = it)) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsSwitch(
                                label = "SNI Spoof",
                                checked = settings.sniSpoofEnabled,
                                onCheckedChange = { onSettingsChange(settings.copy(sniSpoofEnabled = it)) }
                            )
                            if (settings.sniSpoofEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SettingsTextField(
                                    label = "Custom SNI Domain",
                                    value = settings.sniSpoofValue,
                                    onValueChange = { onSettingsChange(settings.copy(sniSpoofValue = it)) }
                                )
                            }
                        }
                    }
                }

                3 -> { // Split Tunneling
                    item { SectionLabel("ROUTING MODE") }
                    item {
                        SettingsCard {
                            val modes = listOf(
                                "all" to "All Traffic",
                                "apps" to "Selected Apps",
                                "domains" to "Selected Domains",
                                "apps_domains" to "Apps & Domains"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                modes.forEach { (mode, label) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onSettingsChange(settings.copy(routingMode = mode)) }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = settings.routingMode == mode,
                                            onClick = { onSettingsChange(settings.copy(routingMode = mode)) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }

                    // App list
                    if (settings.routingMode in listOf("apps", "apps_domains")) {
                        item { SectionLabel("APPLICATIONS") }
                        item {
                            SettingsCard {
                                settings.routingApps.forEach { app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = app,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                onSettingsChange(settings.copy(
                                                    routingApps = settings.routingApps - app
                                                ))
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                // Add app input
                                var newApp by remember { mutableStateOf("") }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = newApp,
                                        onValueChange = { newApp = it },
                                        label = { Text("Package name") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            if (newApp.isNotBlank()) {
                                                onSettingsChange(settings.copy(
                                                    routingApps = settings.routingApps + newApp.trim()
                                                ))
                                                newApp = ""
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, "Add")
                                    }
                                }
                            }
                        }
                    }

                    // Domain list
                    if (settings.routingMode in listOf("domains", "apps_domains")) {
                        item { SectionLabel("DOMAINS") }
                        item {
                            SettingsCard {
                                settings.routingDomains.forEach { domain ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = domain,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                onSettingsChange(settings.copy(
                                                    routingDomains = settings.routingDomains - domain
                                                ))
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                var newDomain by remember { mutableStateOf("") }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = newDomain,
                                        onValueChange = { newDomain = it },
                                        label = { Text("Domain suffix") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            if (newDomain.isNotBlank()) {
                                                onSettingsChange(settings.copy(
                                                    routingDomains = settings.routingDomains + newDomain.trim()
                                                ))
                                                newDomain = ""
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, "Add")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Reusable settings components

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun ThemeSelector(
    theme: String,
    onThemeChange: (String) -> Unit
) {
    Column {
        Text("Theme", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("light", "Light", Icons.Default.LightMode),
                Triple("dark", "Dark", Icons.Default.DarkMode),
                Triple("system", "System", Icons.Default.SettingsBrightness)
            ).forEach { (value, label, icon) ->
                FilterChip(
                    selected = theme == value,
                    onClick = { onThemeChange(value) },
                    label = { Text(label) },
                    leadingIcon = {
                        Icon(icon, null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
    }
}
