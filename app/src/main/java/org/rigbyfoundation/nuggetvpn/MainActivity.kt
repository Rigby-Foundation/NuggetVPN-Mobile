package org.rigbyfoundation.nuggetvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.rigbyfoundation.nuggetvpn.data.models.VpnState
import org.rigbyfoundation.nuggetvpn.ui.components.AddProfileSheet
import org.rigbyfoundation.nuggetvpn.ui.components.TopBar
import org.rigbyfoundation.nuggetvpn.ui.navigation.Screen
import org.rigbyfoundation.nuggetvpn.ui.screens.*
import org.rigbyfoundation.nuggetvpn.ui.theme.NuggetVPNTheme
import org.rigbyfoundation.nuggetvpn.viewmodel.MainViewModel
import org.rigbyfoundation.nuggetvpn.vpn.NuggetVpnService

class MainActivity : ComponentActivity() {

    private var pendingVpnToggle = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingVpnToggle = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: MainViewModel = viewModel()
            val theme by vm.themeFlow.collectAsStateWithLifecycle(initialValue = "system")

            val darkTheme = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            NuggetVPNTheme(darkTheme = darkTheme) {
                val showOnboarding by vm.showOnboarding.collectAsStateWithLifecycle()
                val error by vm.error.collectAsStateWithLifecycle()

                if (showOnboarding) {
                    val settings by vm.settings.collectAsStateWithLifecycle()
                    OnboardingScreen(
                        settings = settings,
                        onLogin = { server, user, pass -> vm.login(server, user, pass) },
                        onRegister = { server, user, pass -> vm.register(server, user, pass) },
                        onSkip = { vm.skipOnboarding() },
                        error = error
                    )
                } else {
                    MainContent(
                        vm = vm,
                        onVpnPermissionNeeded = {
                            val intent = VpnService.prepare(this)
                            if (intent != null) {
                                vpnPermissionLauncher.launch(intent)
                            }
                        }
                    )
                }
            }

            // Handle pending VPN toggle after permission granted
            LaunchedEffect(pendingVpnToggle) {
                if (pendingVpnToggle) {
                    pendingVpnToggle = false
                    vm.toggleVpn()
                }
            }
        }
    }
}

@Composable
private fun MainContent(
    vm: MainViewModel,
    onVpnPermissionNeeded: () -> Unit
) {
    val activeTab by vm.activeTab.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val selectedDomain by vm.selectedConfigDomain.collectAsStateWithLifecycle()
    val selectedProfileId by vm.selectedProfileId.collectAsStateWithLifecycle()
    val selectedProxyMode by vm.selectedProxyMode.collectAsStateWithLifecycle()
    val profilePings by vm.profilePings.collectAsStateWithLifecycle()
    val vpnState by vm.vpnState.collectAsStateWithLifecycle()
    val duration by vm.connectionDuration.collectAsStateWithLifecycle()
    val trafficStats by vm.trafficStats.collectAsStateWithLifecycle()
    val ipInfo by vm.ipInfo.collectAsStateWithLifecycle()
    val isCheckingIp by vm.isCheckingIp.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val logLimit by vm.logLimit.collectAsStateWithLifecycle()
    val showAddSheet by vm.showAddSheet.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val theme by vm.themeFlow.collectAsStateWithLifecycle(initialValue = "system")

    // Handle VPN permission error
    LaunchedEffect(error) {
        if (error == "VPN_PERMISSION_NEEDED") {
            vm.clearError()
            onVpnPermissionNeeded()
        }
    }

    // Refresh pings when on proxies tab
    LaunchedEffect(activeTab) {
        if (activeTab == "proxies") {
            vm.refreshPings()
        }
    }

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        topBar = {
            TopBar(
                sources = sources,
                selectedDomain = selectedDomain,
                isConnected = vpnState == VpnState.CONNECTED,
                onSourceSelect = { vm.selectSource(it) },
                onAddClick = { vm.setShowAddSheet(true) }
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = activeTab == screen.route,
                        onClick = { vm.setActiveTab(screen.route) },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn() + slideInVertically { it / 8 } togetherWith
                            fadeOut() + slideOutVertically { -it / 8 }
                },
                label = "tab_transition"
            ) { tab ->
                when (tab) {
                    "connection" -> ConnectionScreen(
                        vpnState = vpnState,
                        onToggleVpn = { vm.toggleVpn() },
                        duration = duration,
                        uploadSpeed = trafficStats.uploadSpeed,
                        downloadSpeed = trafficStats.downloadSpeed,
                        ipInfo = ipInfo,
                        isCheckingIp = isCheckingIp,
                        onCheckIp = { vm.checkIp() }
                    )
                    "configuration" -> ConfigurationScreen(
                        sources = sources,
                        selectedDomain = selectedDomain,
                        onSelectSource = { vm.selectSource(it) },
                        onDeleteSource = { vm.deleteSource(it) },
                        onAdd = { vm.setShowAddSheet(true) }
                    )
                    "proxies" -> ProxiesScreen(
                        profiles = profiles,
                        profilePings = profilePings,
                        selectedSourceDomain = selectedDomain,
                        selectedProxyMode = selectedProxyMode,
                        selectedProfileId = selectedProfileId,
                        onSelectProxy = { vm.selectProxy(it) },
                        onSelectAuto = { vm.selectAutoProxy() }
                    )
                    "logs" -> LogsScreen(
                        logs = logs,
                        logLimit = logLimit,
                        onLogLimitChange = { vm.setLogLimit(it) },
                        onExportLogs = { vm.exportLogs() }
                    )
                    "settings" -> SettingsScreen(
                        theme = theme,
                        onThemeChange = { vm.setTheme(it) },
                        settings = settings,
                        profiles = profiles,
                        onSettingsChange = { vm.updateSettings(it) },
                        isSynced = settings.authToken != null,
                        onConnectSync = { /* Re-show onboarding for sync */ },
                        onDisconnectSync = { vm.disconnectSync() }
                    )
                }
            }
        }

        // Add profile bottom sheet
        AddProfileSheet(
            isOpen = showAddSheet,
            onDismiss = { vm.setShowAddSheet(false) },
            onAddProfile = { name, link -> vm.addProfile(name, link) },
            onImportSubscription = { url -> vm.importSubscription(url) }
        )
    }
}
