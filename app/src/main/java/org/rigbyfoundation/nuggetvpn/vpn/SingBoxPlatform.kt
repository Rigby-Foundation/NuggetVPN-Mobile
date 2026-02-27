package org.rigbyfoundation.nuggetvpn.vpn

import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.Inet6Address
import java.net.InetAddress
import java.security.KeyStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

class SingBoxPlatform(private val service: NuggetVpnService) : PlatformInterface {

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile var underlyingNetwork: Network? = null
        private set

    private val connectivity: ConnectivityManager
        get() = service.getSystemService(ConnectivityManager::class.java)

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        val result = service.protect(fd)
        if (!result) {
            Log.e("SingBoxPlatform", "protect(fd=$fd) returned FALSE — socket not protected!")
        }
    }

    override fun openTun(options: TunOptions): Int {
        return service.openTun(options)
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return connectivity.getConnectionOwnerUid(
                    ipProtocol,
                    java.net.InetSocketAddress(sourceAddress, sourcePort),
                    java.net.InetSocketAddress(destinationAddress, destinationPort)
                )
            } catch (e: Exception) {
                Log.e("SingBoxPlatform", "getConnectionOwnerUid", e)
            }
        }
        return -1
    }

    override fun packageNameByUid(uid: Int): String {
        val packages = service.packageManager.getPackagesForUid(uid)
        return packages?.firstOrNull() ?: ""
    }

    override fun uidByPackageName(packageName: String): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                service.packageManager.getApplicationInfo(
                    packageName, PackageManager.ApplicationInfoFlags.of(0)
                ).uid
            } else {
                @Suppress("DEPRECATION")
                service.packageManager.getApplicationInfo(packageName, 0).uid
            }
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val cm = connectivity

        // Capture current default network immediately
        underlyingNetwork = cm.activeNetwork
        underlyingNetwork?.let { notifyInterfaceUpdate(cm, it, listener) }

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                underlyingNetwork = network
                notifyInterfaceUpdate(cm, network, listener)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                underlyingNetwork = network
                notifyInterfaceUpdate(cm, network, listener)
            }

            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                underlyingNetwork = network
                val ifName = lp.interfaceName ?: ""
                val ifIndex = getInterfaceIndex(ifName)
                listener.updateDefaultInterface(ifName, ifIndex, false, false)
            }

            override fun onLost(network: Network) {
                if (underlyingNetwork == network) underlyingNetwork = null
                listener.updateDefaultInterface("", -1, false, false)
            }
        }
        cm.registerDefaultNetworkCallback(networkCallback!!)
    }

    private fun notifyInterfaceUpdate(cm: ConnectivityManager, network: Network, listener: InterfaceUpdateListener) {
        val lp = cm.getLinkProperties(network) ?: return
        val ifName = lp.interfaceName ?: return
        // Retry to get interface index (interface might not be ready immediately)
        for (i in 0 until 10) {
            try {
                val ifIndex = java.net.NetworkInterface.getByName(ifName)?.index ?: continue
                listener.updateDefaultInterface(ifName, ifIndex, false, false)
                return
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        networkCallback?.let {
            try { connectivity.unregisterNetworkCallback(it) } catch (_: Exception) {}
            networkCallback = null
        }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val cm = connectivity
        val networks = cm.allNetworks
        val systemInterfaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        val interfaces = mutableListOf<LibboxNetworkInterface>()

        for (network in networks) {
            val lp = cm.getLinkProperties(network) ?: continue
            val caps = cm.getNetworkCapabilities(network) ?: continue
            val sysIf = systemInterfaces.find { it.name == lp.interfaceName } ?: continue

            val boxIf = LibboxNetworkInterface()
            boxIf.name = lp.interfaceName
            boxIf.index = sysIf.index
            try { boxIf.mtu = sysIf.mtu } catch (_: Exception) {}
            boxIf.type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                else -> Libbox.InterfaceTypeOther
            }
            boxIf.dnsServer = StringArray(
                lp.dnsServers.mapNotNull { it.hostAddress }.iterator()
            )
            boxIf.addresses = StringArray(
                sysIf.interfaceAddresses.map { ia ->
                    if (ia.address is Inet6Address) {
                        "${Inet6Address.getByAddress(ia.address.address).hostAddress}/${ia.networkPrefixLength}"
                    } else {
                        "${ia.address.hostAddress}/${ia.networkPrefixLength}"
                    }
                }.iterator()
            )

            var flags = 0
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                flags = android.system.OsConstants.IFF_UP or android.system.OsConstants.IFF_RUNNING
            }
            if (sysIf.isLoopback) flags = flags or android.system.OsConstants.IFF_LOOPBACK
            if (sysIf.isPointToPoint) flags = flags or android.system.OsConstants.IFF_POINTOPOINT
            if (sysIf.supportsMulticast()) flags = flags or android.system.OsConstants.IFF_MULTICAST
            boxIf.flags = flags
            boxIf.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

            interfaces.add(boxIf)
        }

        return object : NetworkInterfaceIterator {
            private val iter = interfaces.iterator()
            override fun hasNext(): Boolean = iter.hasNext()
            override fun next(): LibboxNetworkInterface = iter.next()
        }
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun readWIFIState(): WIFIState? = null

    override fun clearDNSCache() {}

    override fun localDNSTransport(): LocalDNSTransport {
        return object : LocalDNSTransport {
            override fun raw(): Boolean = false

            override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
                try {
                    val net = underlyingNetwork ?: connectivity.activeNetwork
                    val addresses = if (net != null) {
                        net.getAllByName(domain)
                    } else {
                        InetAddress.getAllByName(domain)
                    }
                    ctx.success(addresses.mapNotNull { it.hostAddress }.joinToString("\n"))
                } catch (e: Exception) {
                    ctx.errorCode(3) // NXDOMAIN
                }
            }

            override fun exchange(ctx: ExchangeContext, message: ByteArray) {
                ctx.errorCode(0) // Not implemented for pre-Q
            }
        }
    }

    override fun sendNotification(notification: io.nekohasekai.libbox.Notification) {}

    override fun writeLog(message: String) {
        service.onLibboxLog(message)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun systemCertificates(): StringIterator {
        val certificates = mutableListOf<String>()
        try {
            val keyStore = KeyStore.getInstance("AndroidCAStore")
            keyStore.load(null, null)
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val cert = keyStore.getCertificate(aliases.nextElement())
                certificates.add(
                    "-----BEGIN CERTIFICATE-----\n" +
                    Base64.encode(cert.encoded) +
                    "\n-----END CERTIFICATE-----"
                )
            }
        } catch (e: Exception) {
            Log.e("SingBoxPlatform", "Failed to load system certificates", e)
        }
        return StringArray(certificates.iterator())
    }

    private fun getInterfaceIndex(name: String): Int {
        if (name.isEmpty()) return 0
        return try {
            java.net.NetworkInterface.getByName(name)?.index ?: 0
        } catch (_: Exception) { 0 }
    }

    class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun len(): Int = 0
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): String = iterator.next()
    }
}
