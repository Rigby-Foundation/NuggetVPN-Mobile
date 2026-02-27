package org.rigbyfoundation.nuggetvpn.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val server: String,
    val protocol: String,
    @SerialName("config_link") val configLink: String,
    @SerialName("source_domain") val sourceDomain: String = "local",
    @SerialName("total_up") val totalUp: Long? = null,
    @SerialName("total_down") val totalDown: Long? = null
)

@Serializable
data class AppSettings(
    val mtu: Int = 9000,
    val dns: String = "1.1.1.1",
    @SerialName("tls_fragment") val tlsFragment: Boolean = false,
    @SerialName("tls_fragment_size") val tlsFragmentSize: String = "100-200",
    @SerialName("tls_fragment_sleep") val tlsFragmentSleep: String = "10-20",
    @SerialName("tls_mixed_sni_case") val tlsMixedSniCase: Boolean = false,
    @SerialName("tls_padding") val tlsPadding: Boolean = false,
    @SerialName("sni_spoof_enabled") val sniSpoofEnabled: Boolean = false,
    @SerialName("sni_spoof_value") val sniSpoofValue: String = "",
    @SerialName("auth_server") val authServer: String? = null,
    @SerialName("auth_token") val authToken: String? = null,
    @SerialName("skip_auth") val skipAuth: Boolean = false,
    @SerialName("pending_sync_upload") val pendingSyncUpload: Boolean = false,
    @SerialName("routing_mode") val routingMode: String = "all",
    @SerialName("routing_apps") val routingApps: List<String> = emptyList(),
    @SerialName("routing_domains") val routingDomains: List<String> = emptyList(),
    @SerialName("proxy_chain_enabled") val proxyChainEnabled: Boolean = false,
    @SerialName("proxy_chain") val proxyChain: List<String> = emptyList(),
    @SerialName("proxy_chain_exit") val proxyChainExit: String = ""
)

@Serializable
data class IpInfo(
    val ip: String,
    val region: String = ""
)

@Serializable
data class ProfilePing(
    val id: String,
    @SerialName("ping_ms") val pingMs: Long?
)

sealed class ConfigSource {
    abstract val key: String
    abstract val label: String
    abstract val detail: String
    abstract val domain: String

    data class Subscription(
        override val key: String,
        override val domain: String,
        override val label: String,
        override val detail: String,
        val count: Int
    ) : ConfigSource()

    data class SingleProfile(
        override val key: String,
        override val label: String,
        override val detail: String,
        val profileId: String
    ) : ConfigSource() {
        override val domain: String = "local"
    }
}

data class TrafficStats(
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val totalUp: Long = 0,
    val totalDown: Long = 0
)

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}
