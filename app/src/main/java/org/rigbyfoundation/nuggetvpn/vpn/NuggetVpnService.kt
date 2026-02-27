package org.rigbyfoundation.nuggetvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Connections
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.rigbyfoundation.nuggetvpn.MainActivity
import org.rigbyfoundation.nuggetvpn.data.models.TrafficStats
import org.rigbyfoundation.nuggetvpn.data.models.VpnState

class NuggetVpnService : VpnService(), CommandServerHandler {

    companion object {
        private const val CHANNEL_ID = "nugget_vpn_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "org.rigbyfoundation.nuggetvpn.START"
        const val ACTION_STOP = "org.rigbyfoundation.nuggetvpn.STOP"
        const val EXTRA_CONFIG = "config"

        private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
        val vpnState: StateFlow<VpnState> = _vpnState

        private val _logs = MutableSharedFlow<String>(replay = 100, extraBufferCapacity = 1000)
        val logs: SharedFlow<String> = _logs

        private val _trafficStats = MutableStateFlow(TrafficStats())
        val trafficStats: StateFlow<TrafficStats> = _trafficStats

        private var startTime = 0L
        val connectionDuration: Long
            get() = if (startTime > 0) System.currentTimeMillis() - startTime else 0

        fun prepare(context: Context): Intent? {
            return VpnService.prepare(context)
        }

        fun start(context: Context, config: String) {
            val intent = Intent(context, NuggetVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG, config)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NuggetVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var vpnFd: ParcelFileDescriptor? = null
    private var boxService: BoxService? = null
    private var commandServer: CommandServer? = null
    private var statusClient: CommandClient? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG) ?: return START_NOT_STICKY
                startVpn(config)
            }
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn(config: String) {
        _vpnState.value = VpnState.CONNECTING
        emitLog("Starting VPN with sing-box library...")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Connecting..."))

        try {
            commandServer = CommandServer(this, 300)
            commandServer?.start()
            emitLog("Command server started")

            val platform = SingBoxPlatform(this)
            boxService = Libbox.newService(config, platform)
            boxService?.start()

            commandServer?.setService(boxService)

            startTime = System.currentTimeMillis()
            _vpnState.value = VpnState.CONNECTED
            emitLog("VPN connected via sing-box")
            updateNotification("Connected")

            startStatusMonitor()
        } catch (e: Exception) {
            emitLog("VPN start failed: ${e.message}")
            _vpnState.value = VpnState.DISCONNECTED
            cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun stopVpn() {
        if (_vpnState.value == VpnState.DISCONNECTED) return

        _vpnState.value = VpnState.DISCONNECTING
        emitLog("Stopping VPN...")

        stopStatusMonitor()

        try { commandServer?.setService(null) } catch (_: Exception) {}

        try {
            boxService?.close()
        } catch (e: Exception) {
            emitLog("Error stopping sing-box: ${e.message}")
        }

        try { commandServer?.close() } catch (_: Exception) {}
        try { vpnFd?.close() } catch (_: Exception) {}

        boxService = null
        commandServer = null
        vpnFd = null
        startTime = 0
        _vpnState.value = VpnState.DISCONNECTED
        _trafficStats.value = TrafficStats()
        emitLog("VPN disconnected")

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        stopStatusMonitor()
        try { commandServer?.close() } catch (_: Exception) {}
        try { boxService?.close() } catch (_: Exception) {}
        try { vpnFd?.close() } catch (_: Exception) {}
        commandServer = null
        boxService = null
        vpnFd = null
    }

    /** Called by SingBoxPlatform when sing-box requests a TUN interface */
    fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) throw Exception("android: missing vpn permission")

        val builder = Builder()
            .setSession("NuggetVPN")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // IPv4 addresses
        val inet4Address = options.inet4Address
        while (inet4Address.hasNext()) {
            val prefix = inet4Address.next()
            builder.addAddress(prefix.address(), prefix.prefix())
        }

        // IPv6 addresses
        val inet6Address = options.inet6Address
        while (inet6Address.hasNext()) {
            val prefix = inet6Address.next()
            builder.addAddress(prefix.address(), prefix.prefix())
        }

        if (options.autoRoute) {
            // DNS server
            try {
                builder.addDnsServer(options.dnsServerAddress.value)
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+: use route addresses + exclude routes
                val inet4RouteAddr = options.inet4RouteAddress
                if (inet4RouteAddr.hasNext()) {
                    while (inet4RouteAddr.hasNext()) {
                        val prefix = inet4RouteAddr.next()
                        builder.addRoute(prefix.address(), prefix.prefix())
                    }
                } else {
                    builder.addRoute("0.0.0.0", 0)
                }

                val inet6RouteAddr = options.inet6RouteAddress
                if (inet6RouteAddr.hasNext()) {
                    while (inet6RouteAddr.hasNext()) {
                        val prefix = inet6RouteAddr.next()
                        builder.addRoute(prefix.address(), prefix.prefix())
                    }
                } else {
                    builder.addRoute("::", 0)
                }

                // Exclude routes (proxy server IP excluded from TUN)
                val inet4Exclude = options.inet4RouteExcludeAddress
                while (inet4Exclude.hasNext()) {
                    val prefix = inet4Exclude.next()
                    builder.excludeRoute(
                        android.net.IpPrefix(
                            java.net.InetAddress.getByName(prefix.address()),
                            prefix.prefix()
                        )
                    )
                }
                val inet6Exclude = options.inet6RouteExcludeAddress
                while (inet6Exclude.hasNext()) {
                    val prefix = inet6Exclude.next()
                    builder.excludeRoute(
                        android.net.IpPrefix(
                            java.net.InetAddress.getByName(prefix.address()),
                            prefix.prefix()
                        )
                    )
                }
            } else {
                // Pre-API 33: use RouteRange (already has proxy server excluded)
                val inet4RouteRange = options.inet4RouteRange
                if (inet4RouteRange.hasNext()) {
                    while (inet4RouteRange.hasNext()) {
                        val prefix = inet4RouteRange.next()
                        builder.addRoute(prefix.address(), prefix.prefix())
                    }
                }

                val inet6RouteRange = options.inet6RouteRange
                if (inet6RouteRange.hasNext()) {
                    while (inet6RouteRange.hasNext()) {
                        val prefix = inet6RouteRange.next()
                        builder.addRoute(prefix.address(), prefix.prefix())
                    }
                }
            }

            // Per-app routing
            val includePackage = options.includePackage
            if (includePackage.hasNext()) {
                while (includePackage.hasNext()) {
                    try { builder.addAllowedApplication(includePackage.next()) } catch (_: Exception) {}
                }
            }
            val excludePackage = options.excludePackage
            if (excludePackage.hasNext()) {
                while (excludePackage.hasNext()) {
                    try { builder.addDisallowedApplication(excludePackage.next()) } catch (_: Exception) {}
                }
            }
        }

        // HTTP proxy (Android 10+)
        if (options.isHTTPProxyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setHttpProxy(
                android.net.ProxyInfo.buildDirectProxy(
                    options.httpProxyServer,
                    options.httpProxyServerPort
                )
            )
        }

        // Exclude our own app from VPN so sing-box outbound sockets don't loop through TUN
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            emitLog("Warning: could not exclude own package: ${e.message}")
        }

        vpnFd = builder.establish() ?: throw Exception("android: the application is not prepared or is revoked")
        emitLog("TUN interface established (fd=${vpnFd!!.fd})")
        return vpnFd!!.fd
    }

    // -- Traffic stats monitoring via CommandClient --

    private fun startStatusMonitor() {
        val options = CommandClientOptions()
        options.command = Libbox.CommandStatus
        options.statusInterval = 500_000_000 // 500ms in nanoseconds

        statusClient = CommandClient(object : CommandClientHandler {
            override fun connected() {}
            override fun disconnected(message: String) {}
            override fun clearLogs() {}
            override fun writeLogs(messages: StringIterator) {}

            override fun writeStatus(message: StatusMessage) {
                _trafficStats.value = TrafficStats(
                    uploadSpeed = message.uplink,
                    downloadSpeed = message.downlink,
                    totalUp = message.uplinkTotal,
                    totalDown = message.downlinkTotal
                )
            }

            override fun writeGroups(message: OutboundGroupIterator) {}
            override fun initializeClashMode(modeList: StringIterator, currentMode: String) {}
            override fun updateClashMode(newMode: String) {}
            override fun writeConnections(message: Connections) {}
        }, options)

        serviceScope.launch {
            delay(500)
            try {
                statusClient?.connect()
                emitLog("Status monitor connected")
            } catch (e: Exception) {
                emitLog("Status monitor failed: ${e.message}")
            }
        }
    }

    private fun stopStatusMonitor() {
        try { statusClient?.disconnect() } catch (_: Exception) {}
        statusClient = null
    }

    // -- CommandServerHandler --

    override fun serviceReload() {
        emitLog("Service reload requested")
    }

    override fun getSystemProxyStatus(): SystemProxyStatus {
        return SystemProxyStatus()
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {}

    override fun postServiceClose() {
        stopVpn()
    }

    // -- Called by SingBoxPlatform for log forwarding --

    fun onLibboxLog(message: String) {
        serviceScope.launch { _logs.emit(message) }
    }

    // -- Helpers --

    private fun emitLog(message: String) {
        try { commandServer?.writeMessage(message) } catch (_: Exception) {}
        serviceScope.launch { _logs.emit(message) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows VPN connection status"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, NuggetVpnService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NuggetVPN")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Disconnect", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(status))
    }
}
