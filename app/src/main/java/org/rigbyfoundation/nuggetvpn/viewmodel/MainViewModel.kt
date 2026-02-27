package org.rigbyfoundation.nuggetvpn.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.rigbyfoundation.nuggetvpn.data.models.*
import org.rigbyfoundation.nuggetvpn.data.network.NetworkClient
import org.rigbyfoundation.nuggetvpn.data.repository.ProfileRepository
import org.rigbyfoundation.nuggetvpn.data.repository.SettingsRepository
import org.rigbyfoundation.nuggetvpn.util.PingManager
import org.rigbyfoundation.nuggetvpn.util.ProxyParser
import org.rigbyfoundation.nuggetvpn.vpn.ConfigBuilder
import org.rigbyfoundation.nuggetvpn.vpn.NuggetVpnService
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepo = ProfileRepository(application)
    private val settingsRepo = SettingsRepository(application)

    // Profiles
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Theme
    val themeFlow = settingsRepo.themeFlow

    // Selection state
    private val _selectedConfigDomain = MutableStateFlow("all")
    val selectedConfigDomain: StateFlow<String> = _selectedConfigDomain.asStateFlow()

    private val _selectedProfileId = MutableStateFlow("")
    val selectedProfileId: StateFlow<String> = _selectedProfileId.asStateFlow()

    private val _selectedProxyMode = MutableStateFlow("manual")
    val selectedProxyMode: StateFlow<String> = _selectedProxyMode.asStateFlow()

    // Pings
    private val _profilePings = MutableStateFlow<Map<String, Long?>>(emptyMap())
    val profilePings: StateFlow<Map<String, Long?>> = _profilePings.asStateFlow()

    // IP info
    private val _ipInfo = MutableStateFlow<IpInfo?>(null)
    val ipInfo: StateFlow<IpInfo?> = _ipInfo.asStateFlow()

    private val _isCheckingIp = MutableStateFlow(false)
    val isCheckingIp: StateFlow<Boolean> = _isCheckingIp.asStateFlow()

    // Logs
    private val _logs = MutableStateFlow<List<String>>(listOf("System initialized.", "Waiting for commands..."))
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _logLimit = MutableStateFlow(500)
    val logLimit: StateFlow<Int> = _logLimit.asStateFlow()

    // VPN State
    val vpnState = NuggetVpnService.vpnState

    // Connection duration
    private val _connectionDuration = MutableStateFlow(0L)
    val connectionDuration: StateFlow<Long> = _connectionDuration.asStateFlow()

    // Traffic
    val trafficStats = NuggetVpnService.trafficStats

    // Onboarding
    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    // Add modal
    private val _showAddSheet = MutableStateFlow(false)
    val showAddSheet: StateFlow<Boolean> = _showAddSheet.asStateFlow()

    // Active tab
    private val _activeTab = MutableStateFlow("connection")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Config sources (computed)
    val sources: StateFlow<List<ConfigSource>> = _profiles.map { profiles ->
        val sources = mutableListOf<ConfigSource>()
        val grouped = profiles.groupBy { it.sourceDomain }

        grouped.forEach { (domain, domainProfiles) ->
            if (domain == "local") {
                domainProfiles.forEach { profile ->
                    sources.add(ConfigSource.SingleProfile(
                        key = profile.id,
                        label = profile.name,
                        detail = "${profile.protocol} • ${profile.server}",
                        profileId = profile.id
                    ))
                }
            } else {
                sources.add(ConfigSource.Subscription(
                    key = domain,
                    domain = domain,
                    label = domain,
                    detail = "${domainProfiles.size} proxies",
                    count = domainProfiles.size
                ))
            }
        }
        sources
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            loadProfiles()
            loadSettings()
        }

        // Collect VPN logs
        viewModelScope.launch {
            NuggetVpnService.logs.collect { log ->
                val current = _logs.value.toMutableList()
                current.add(log)
                val limit = _logLimit.value
                if (current.size > limit) {
                    _logs.value = current.takeLast(limit)
                } else {
                    _logs.value = current
                }
            }
        }

        // Duration ticker
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (vpnState.value == VpnState.CONNECTED) {
                    _connectionDuration.value = NuggetVpnService.connectionDuration
                }
            }
        }
    }

    private suspend fun loadProfiles() {
        _profiles.value = profileRepo.loadProfiles()
        if (_selectedProfileId.value.isEmpty() && _profiles.value.isNotEmpty()) {
            _selectedProfileId.value = _profiles.value.first().id
        }
    }

    private suspend fun loadSettings() {
        _settings.value = settingsRepo.loadSettings()
        if (!_settings.value.skipAuth && _settings.value.authToken == null) {
            _showOnboarding.value = true
        }
    }

    fun setActiveTab(tab: String) { _activeTab.value = tab }
    fun setShowAddSheet(show: Boolean) { _showAddSheet.value = show }
    fun setLogLimit(limit: Int) { _logLimit.value = limit }
    fun clearError() { _error.value = null }

    fun selectSource(source: ConfigSource) {
        _selectedConfigDomain.value = source.domain
        if (source is ConfigSource.SingleProfile) {
            _selectedProfileId.value = source.profileId
        }
    }

    fun selectProxy(id: String) {
        _selectedProfileId.value = id
        _selectedProxyMode.value = "manual"
    }

    fun selectAutoProxy() {
        _selectedProxyMode.value = "auto"
        val pings = _profilePings.value
        val filtered = _profiles.value
            .filter { pings[it.id] != null }
            .minByOrNull { pings[it.id]!! }
        if (filtered != null) {
            _selectedProfileId.value = filtered.id
        }
    }

    fun addProfile(name: String, link: String) {
        viewModelScope.launch {
            try {
                val profile = ProxyParser.parseProfileFromLink(link, name.ifEmpty { null })
                    ?: throw Exception("Failed to parse profile link")
                profileRepo.addProfile(profile)
                loadProfiles()
                addLog("Profile '${profile.name}' added successfully.")
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun importSubscription(url: String) {
        viewModelScope.launch {
            try {
                addLog("Importing subscription from $url...")
                val content = NetworkClient.importSubscription(url).getOrThrow()
                val domain = try {
                    java.net.URI(url).host ?: url
                } catch (e: Exception) { url }

                // Delete old profiles from same source
                profileRepo.deleteProfilesBySource(domain)

                val profiles = ProxyParser.parseSubscription(content, domain)
                if (profiles.isEmpty()) throw Exception("No valid profiles found in subscription")

                val current = profileRepo.loadProfiles().toMutableList()
                current.addAll(profiles)
                profileRepo.saveProfiles(current)
                loadProfiles()
                addLog("Imported ${profiles.size} profiles from $domain")
            } catch (e: Exception) {
                _error.value = e.message
                addLog("Import failed: ${e.message}")
            }
        }
    }

    fun deleteSource(source: ConfigSource) {
        viewModelScope.launch {
            when (source) {
                is ConfigSource.SingleProfile -> {
                    profileRepo.deleteProfile(source.profileId)
                    addLog("Profile '${source.label}' deleted.")
                }
                is ConfigSource.Subscription -> {
                    profileRepo.deleteProfilesBySource(source.domain)
                    addLog("Subscription '${source.domain}' deleted.")
                }
            }
            loadProfiles()
        }
    }

    fun toggleVpn() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            if (vpnState.value == VpnState.CONNECTED || vpnState.value == VpnState.CONNECTING) {
                NuggetVpnService.stop(context)
                addLog("VPN disconnected.")
            } else {
                val profileId = _selectedProfileId.value
                val profile = _profiles.value.find { it.id == profileId }
                if (profile == null) {
                    _error.value = "No profile selected"
                    return@launch
                }

                val config = ConfigBuilder.buildConfig(
                    profile = profile,
                    settings = _settings.value,
                    allProfiles = _profiles.value
                )

                addLog("Starting VPN with profile '${profile.name}'...")

                // Check if VPN permission is granted
                val prepareIntent = NuggetVpnService.prepare(context)
                if (prepareIntent != null) {
                    // Need to request VPN permission from Activity
                    _error.value = "VPN_PERMISSION_NEEDED"
                    return@launch
                }

                NuggetVpnService.start(context, config)
            }
        }
    }

    fun refreshPings() {
        viewModelScope.launch {
            val currentProfiles = _profiles.value.let { profiles ->
                val domain = _selectedConfigDomain.value
                if (domain == "all") profiles else profiles.filter { it.sourceDomain == domain }
            }
            addLog("Pinging ${currentProfiles.size} profiles...")
            val pings = PingManager.pingProfiles(currentProfiles)
            val pingMap = pings.associate { it.id to it.pingMs }
            _profilePings.value = _profilePings.value + pingMap
            addLog("Ping complete.")
        }
    }

    fun checkIp() {
        viewModelScope.launch {
            _isCheckingIp.value = true
            val result = NetworkClient.checkIp()
            _ipInfo.value = result.getOrNull()
            _isCheckingIp.value = false
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            _settings.value = newSettings
            settingsRepo.saveSettings(newSettings)
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingsRepo.setTheme(theme)
        }
    }

    fun completeOnboarding() {
        _showOnboarding.value = false
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            val newSettings = _settings.value.copy(skipAuth = true)
            updateSettings(newSettings)
            _showOnboarding.value = false
        }
    }

    fun login(server: String, username: String, password: String) {
        viewModelScope.launch {
            val result = NetworkClient.login(server, username, password)
            result.onSuccess { token ->
                val newSettings = _settings.value.copy(
                    authServer = server,
                    authToken = token,
                    skipAuth = false
                )
                updateSettings(newSettings)
                _showOnboarding.value = false
                addLog("Logged in to $server")
            }.onFailure { e ->
                _error.value = e.message
            }
        }
    }

    fun register(server: String, username: String, password: String) {
        viewModelScope.launch {
            val result = NetworkClient.register(server, username, password)
            result.onSuccess { token ->
                val newSettings = _settings.value.copy(
                    authServer = server,
                    authToken = token,
                    skipAuth = false
                )
                updateSettings(newSettings)
                _showOnboarding.value = false
                addLog("Registered on $server")
            }.onFailure { e ->
                _error.value = e.message
            }
        }
    }

    fun pushProfiles() {
        viewModelScope.launch {
            val result = NetworkClient.pushProfiles(_settings.value, _profiles.value)
            result.onSuccess { addLog("Profiles synced to server.") }
                .onFailure { addLog("Sync failed: ${it.message}") }
        }
    }

    fun pullProfiles() {
        viewModelScope.launch {
            val result = NetworkClient.pullProfiles(_settings.value)
            result.onSuccess { profiles ->
                profileRepo.saveProfiles(profiles)
                loadProfiles()
                addLog("Pulled ${profiles.size} profiles from server.")
            }.onFailure { addLog("Pull failed: ${it.message}") }
        }
    }

    fun disconnectSync() {
        viewModelScope.launch {
            val newSettings = _settings.value.copy(authServer = null, authToken = null)
            updateSettings(newSettings)
            addLog("Disconnected from sync server.")
        }
    }

    fun exportLogs(): String = _logs.value.joinToString("\n")

    private fun addLog(message: String) {
        val current = _logs.value.toMutableList()
        current.add(message)
        _logs.value = current
    }
}
