package org.rigbyfoundation.nuggetvpn.util

import android.util.Base64
import kotlinx.serialization.json.*
import org.rigbyfoundation.nuggetvpn.data.models.Profile
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

object ProxyParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun detectProtocol(link: String): String {
        val trimmed = link.trim()
        return when {
            trimmed.startsWith("vless://") -> "vless"
            trimmed.startsWith("vmess://") -> "vmess"
            trimmed.startsWith("trojan://") -> "trojan"
            trimmed.startsWith("ss://") -> "shadowsocks"
            trimmed.startsWith("hysteria://") -> "hysteria"
            trimmed.startsWith("hy2://") || trimmed.startsWith("hysteria2://") -> "hysteria2"
            trimmed.startsWith("tuic://") -> "tuic"
            trimmed.startsWith("wg://") || trimmed.startsWith("wireguard://") -> "wireguard"
            trimmed.startsWith("socks://") || trimmed.startsWith("socks5://") -> "socks"
            trimmed.startsWith("http://") && trimmed.indexOf("://", 7) == -1 -> "http"
            trimmed.startsWith("https://") -> "https"
            trimmed.startsWith("ssh://") -> "ssh"
            trimmed.startsWith("{") -> detectJsonProtocol(trimmed)
            else -> "unknown"
        }
    }

    private fun detectJsonProtocol(jsonStr: String): String {
        return try {
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            if (obj.containsKey("outbounds") || obj.containsKey("inbounds")) {
                "sing-box-config"
            } else {
                obj["type"]?.jsonPrimitive?.content ?: "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun parseProfileFromLink(link: String, name: String? = null, sourceDomain: String = "local"): Profile? {
        val trimmed = link.trim()
        val protocol = detectProtocol(trimmed)
        if (protocol == "unknown") return null

        val profileName = name ?: extractName(trimmed, protocol)
        val server = extractServer(trimmed, protocol)

        return Profile(
            id = UUID.randomUUID().toString(),
            name = profileName,
            server = server,
            protocol = protocol,
            configLink = trimmed,
            sourceDomain = sourceDomain
        )
    }

    private fun extractName(link: String, protocol: String): String {
        return try {
            when {
                link.startsWith("{") -> {
                    val obj = json.parseToJsonElement(link).jsonObject
                    obj["tag"]?.jsonPrimitive?.content ?: "Imported Config"
                }
                link.contains("#") -> {
                    URLDecoder.decode(link.substringAfterLast("#"), "UTF-8")
                }
                else -> "$protocol proxy"
            }
        } catch (e: Exception) {
            "$protocol proxy"
        }
    }

    private fun extractServer(link: String, protocol: String): String {
        return try {
            when {
                link.startsWith("{") -> {
                    val obj = json.parseToJsonElement(link).jsonObject
                    obj["server"]?.jsonPrimitive?.content ?: "Auto"
                }
                protocol == "vmess" -> {
                    val encoded = link.removePrefix("vmess://").substringBefore("#")
                    val decoded = String(Base64.decode(encoded, Base64.DEFAULT or Base64.NO_WRAP))
                    val obj = json.parseToJsonElement(decoded).jsonObject
                    obj["add"]?.jsonPrimitive?.content ?: "Auto"
                }
                else -> {
                    val withoutScheme = link.substringAfter("://")
                    val hostPart = withoutScheme.substringBefore("#").substringBefore("?")
                    val atPart = if (hostPart.contains("@")) hostPart.substringAfter("@") else hostPart
                    atPart.substringBefore(":").substringBefore("/")
                }
            }
        } catch (e: Exception) {
            "Auto"
        }
    }

    fun parseOutbound(profile: Profile, settings: org.rigbyfoundation.nuggetvpn.data.models.AppSettings? = null, tag: String = "proxy"): JsonObject? {
        val link = profile.configLink.trim()

        return try {
            when {
                link.startsWith("{") -> parseJsonOutbound(link, tag)
                link.startsWith("vless://") -> parseVless(link, tag, settings)
                link.startsWith("vmess://") -> parseVmess(link, tag, settings)
                link.startsWith("trojan://") -> parseTrojan(link, tag, settings)
                link.startsWith("ss://") -> parseShadowsocks(link, tag)
                link.startsWith("hysteria://") -> parseHysteria(link, tag)
                link.startsWith("hy2://") || link.startsWith("hysteria2://") -> parseHysteria2(link, tag, settings)
                link.startsWith("tuic://") -> parseTuic(link, tag)
                link.startsWith("wg://") || link.startsWith("wireguard://") -> parseWireguard(link, tag)
                link.startsWith("socks://") || link.startsWith("socks5://") -> parseSocks(link, tag)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isFullSingboxConfig(link: String): Boolean {
        return try {
            val obj = json.parseToJsonElement(link.trim()).jsonObject
            obj.containsKey("outbounds") || obj.containsKey("inbounds")
        } catch (e: Exception) {
            false
        }
    }

    fun getFullSingboxConfig(link: String): JsonObject? {
        return try {
            json.parseToJsonElement(link.trim()).jsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun parseJsonOutbound(jsonStr: String, tag: String): JsonObject {
        val obj = json.parseToJsonElement(jsonStr).jsonObject.toMutableMap()
        obj["tag"] = JsonPrimitive(tag)
        return JsonObject(obj)
    }

    private fun parseVless(link: String, tag: String, settings: org.rigbyfoundation.nuggetvpn.data.models.AppSettings?): JsonObject {
        val uri = URI(link.replace("vless://", "https://"))
        val uuid = link.removePrefix("vless://").substringBefore("@")
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQueryParams(uri.rawQuery ?: "")

        val outbound = buildJsonObject {
            put("type", "vless")
            put("tag", tag)
            put("server", host)
            put("server_port", port)
            put("uuid", uuid)
            put("flow", params["flow"] ?: "")

            val security = params["security"] ?: ""
            if (security == "tls" || security == "reality") {
                putJsonObject("tls") {
                    put("enabled", true)
                    if (security == "reality") {
                        putJsonObject("reality") {
                            put("enabled", true)
                            put("public_key", params["pbk"] ?: "")
                            put("short_id", params["sid"] ?: "")
                        }
                    }
                    val sni = params["sni"] ?: ""
                    if (sni.isNotEmpty()) {
                        put("server_name", applySniSettings(sni, settings))
                    }
                    if (params["fp"]?.isNotEmpty() == true) {
                        putJsonObject("utls") {
                            put("enabled", true)
                            put("fingerprint", params["fp"]!!)
                        }
                    }
                    applyTlsSettings(this, settings)
                }
            }

            addTransport(this, params)
        }
        return outbound
    }

    private fun parseVmess(link: String, tag: String, settings: org.rigbyfoundation.nuggetvpn.data.models.AppSettings?): JsonObject {
        val encoded = link.removePrefix("vmess://").substringBefore("#")
        val padded = when (encoded.length % 4) {
            2 -> "$encoded=="
            3 -> "$encoded="
            else -> encoded
        }
        val decoded = String(Base64.decode(padded, Base64.DEFAULT or Base64.NO_WRAP or Base64.URL_SAFE))
        val vmessObj = json.parseToJsonElement(decoded).jsonObject

        val host = vmessObj["add"]?.jsonPrimitive?.content ?: ""
        val port = vmessObj["port"]?.let {
            when (it) {
                is JsonPrimitive -> if (it.isString) it.content.toIntOrNull() ?: 443 else it.int
                else -> 443
            }
        } ?: 443

        return buildJsonObject {
            put("type", "vmess")
            put("tag", tag)
            put("server", host)
            put("server_port", port)
            put("uuid", vmessObj["id"]?.jsonPrimitive?.content ?: "")
            put("security", vmessObj["scy"]?.jsonPrimitive?.content ?: "auto")
            put("alter_id", vmessObj["aid"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)

            val tls = vmessObj["tls"]?.jsonPrimitive?.content ?: ""
            if (tls == "tls") {
                putJsonObject("tls") {
                    put("enabled", true)
                    val sni = vmessObj["sni"]?.jsonPrimitive?.content ?: vmessObj["host"]?.jsonPrimitive?.content ?: ""
                    if (sni.isNotEmpty()) put("server_name", applySniSettings(sni, settings))
                    applyTlsSettings(this, settings)
                }
            }

            val net = vmessObj["net"]?.jsonPrimitive?.content ?: ""
            if (net == "ws") {
                putJsonObject("transport") {
                    put("type", "ws")
                    put("path", vmessObj["path"]?.jsonPrimitive?.content ?: "/")
                    val wsHost = vmessObj["host"]?.jsonPrimitive?.content ?: ""
                    if (wsHost.isNotEmpty()) {
                        putJsonObject("headers") { put("Host", wsHost) }
                    }
                }
            } else if (net == "grpc") {
                putJsonObject("transport") {
                    put("type", "grpc")
                    put("service_name", vmessObj["path"]?.jsonPrimitive?.content ?: "")
                }
            }
        }
    }

    private fun parseTrojan(link: String, tag: String, settings: org.rigbyfoundation.nuggetvpn.data.models.AppSettings?): JsonObject {
        val uri = URI(link.replace("trojan://", "https://"))
        val password = link.removePrefix("trojan://").substringBefore("@")
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQueryParams(uri.rawQuery ?: "")

        return buildJsonObject {
            put("type", "trojan")
            put("tag", tag)
            put("server", host)
            put("server_port", port)
            put("password", password)

            putJsonObject("tls") {
                put("enabled", true)
                val sni = params["sni"] ?: params["peer"] ?: ""
                if (sni.isNotEmpty()) put("server_name", applySniSettings(sni, settings))
                applyTlsSettings(this, settings)
            }

            addTransport(this, params)
        }
    }

    private fun parseShadowsocks(link: String, tag: String): JsonObject {
        val withoutScheme = link.removePrefix("ss://")
        val mainPart = withoutScheme.substringBefore("#")

        val (method, password, host, port) = if (mainPart.contains("@")) {
            val userInfo = mainPart.substringBefore("@")
            val decoded = try {
                String(Base64.decode(userInfo, Base64.DEFAULT or Base64.NO_WRAP or Base64.URL_SAFE))
            } catch (e: Exception) { userInfo }
            val parts = decoded.split(":", limit = 2)
            val serverPart = mainPart.substringAfter("@")
            val serverHost = serverPart.substringBefore(":")
            val serverPort = serverPart.substringAfter(":").substringBefore("/").substringBefore("?").toIntOrNull() ?: 443
            listOf(parts.getOrElse(0) { "aes-256-gcm" }, parts.getOrElse(1) { "" }, serverHost, serverPort.toString())
        } else {
            val decoded = try {
                String(Base64.decode(mainPart, Base64.DEFAULT or Base64.NO_WRAP or Base64.URL_SAFE))
            } catch (e: Exception) { mainPart }
            val parts = decoded.split("@", limit = 2)
            val methodPass = parts.getOrElse(0) { "" }.split(":", limit = 2)
            val serverPart = parts.getOrElse(1) { "" }
            val serverHost = serverPart.substringBefore(":")
            val serverPort = serverPart.substringAfter(":").substringBefore("/").toIntOrNull() ?: 443
            listOf(methodPass.getOrElse(0) { "" }, methodPass.getOrElse(1) { "" }, serverHost, serverPort.toString())
        }

        return buildJsonObject {
            put("type", "shadowsocks")
            put("tag", tag)
            put("server", host)
            put("server_port", port.toIntOrNull() ?: 443)
            put("method", method)
            put("password", password)
        }
    }

    private fun parseHysteria(link: String, tag: String): JsonObject {
        val uri = URI(link.replace("hysteria://", "https://"))
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQueryParams(uri.rawQuery ?: "")

        return buildJsonObject {
            put("type", "hysteria")
            put("tag", tag)
            put("server", host)
            put("server_port", port)
            put("auth_str", params["auth"] ?: "")
            put("up_mbps", params["upmbps"]?.toIntOrNull() ?: 100)
            put("down_mbps", params["downmbps"]?.toIntOrNull() ?: 100)
            putJsonObject("tls") {
                put("enabled", true)
                val sni = params["peer"] ?: params["sni"] ?: ""
                if (sni.isNotEmpty()) put("server_name", sni)
                if (params["insecure"] == "1") put("insecure", true)
            }
        }
    }

    private fun parseHysteria2(link: String, tag: String, settings: org.rigbyfoundation.nuggetvpn.data.models.AppSettings?): JsonObject {
        val normalized = link.replace("hysteria2://", "https://").replace("hy2://", "https://")
        val uri = URI(normalized)
        val password = normalized.removePrefix("https://").substringBefore("@")
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQueryParams(uri.rawQuery ?: "")

        return buildJsonObject {
            put("type", "hysteria2")
            put("tag", tag)
            put("server", host)
            put("server_port", port)
            put("password", password)
            putJsonObject("tls") {
                put("enabled", true)
                val sni = params["sni"] ?: ""
                if (sni.isNotEmpty()) put("server_name", applySniSettings(sni, settings))
                if (params["insecure"] == "1") put("insecure", true)
                applyTlsSettings(this, settings)
            }
        }
    }

    private fun parseTuic(link: String, tag: String): JsonObject {
        val uri = URI(link.replace("tuic://", "https://"))
        val userInfo = link.removePrefix("tuic://").substringBefore("@")
        val parts = userInfo.split(":", limit = 2)
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val params = parseQueryParams(uri.rawQuery ?: "")

        return buildJsonObject {
            put("type", "tuic")
            put("tag", tag)
            put("server", host)
            put("server_port", port)
            put("uuid", parts.getOrElse(0) { "" })
            put("password", parts.getOrElse(1) { "" })
            put("congestion_control", params["congestion_control"] ?: "bbr")
            putJsonObject("tls") {
                put("enabled", true)
                val sni = params["sni"] ?: ""
                if (sni.isNotEmpty()) put("server_name", sni)
                putJsonArray("alpn") { add("h3") }
            }
        }
    }

    private fun parseWireguard(link: String, tag: String): JsonObject {
        val uri = URI(link.replace("wireguard://", "https://").replace("wg://", "https://"))
        val privateKey = link.substringAfter("://").substringBefore("@")
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 51820
        val params = parseQueryParams(uri.rawQuery ?: "")

        return buildJsonObject {
            put("type", "wireguard")
            put("tag", tag)
            put("server", host)
            put("server_port", port)
            put("private_key", URLDecoder.decode(privateKey, "UTF-8"))
            val pubKey = params["publickey"] ?: params["public_key"] ?: ""
            if (pubKey.isNotEmpty()) put("peer_public_key", pubKey)
            val address = params["address"] ?: ""
            if (address.isNotEmpty()) {
                putJsonArray("local_address") {
                    address.split(",").forEach { add(it.trim()) }
                }
            }
            val mtu = params["mtu"]?.toIntOrNull()
            if (mtu != null) put("mtu", mtu)
        }
    }

    private fun parseSocks(link: String, tag: String): JsonObject {
        val normalized = link.replace("socks5://", "https://").replace("socks://", "https://")
        val uri = URI(normalized)
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 1080
        val userInfo = uri.userInfo?.split(":", limit = 2)

        return buildJsonObject {
            put("type", "socks")
            put("tag", tag)
            put("server", host)
            put("server_port", port)
            put("version", "5")
            if (userInfo != null) {
                put("username", URLDecoder.decode(userInfo[0], "UTF-8"))
                if (userInfo.size > 1) put("password", URLDecoder.decode(userInfo[1], "UTF-8"))
            }
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").associate {
            val parts = it.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], "UTF-8").lowercase()
            val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
            key to value
        }
    }

    private fun addTransport(builder: JsonObjectBuilder, params: Map<String, String>) {
        val type = params["type"] ?: return
        when (type) {
            "ws" -> builder.putJsonObject("transport") {
                put("type", "ws")
                put("path", params["path"] ?: "/")
                val host = params["host"] ?: ""
                if (host.isNotEmpty()) {
                    putJsonObject("headers") { put("Host", host) }
                }
            }
            "grpc" -> builder.putJsonObject("transport") {
                put("type", "grpc")
                put("service_name", params["servicename"] ?: params["path"] ?: "")
            }
            "httpupgrade" -> builder.putJsonObject("transport") {
                put("type", "httpupgrade")
                put("path", params["path"] ?: "/")
                val host = params["host"] ?: ""
                if (host.isNotEmpty()) put("host", host)
            }
            "http", "h2" -> builder.putJsonObject("transport") {
                put("type", "http")
                put("path", params["path"] ?: "/")
                val host = params["host"] ?: ""
                if (host.isNotEmpty()) {
                    putJsonArray("host") { add(host) }
                }
            }
        }
    }

    private fun applySniSettings(sni: String, settings: org.rigbyfoundation.nuggetvpn.data.models.AppSettings?): String {
        if (settings == null) return sni
        return if (settings.sniSpoofEnabled && settings.sniSpoofValue.isNotEmpty()) {
            settings.sniSpoofValue
        } else if (settings.tlsMixedSniCase) {
            sni.mapIndexed { i, c -> if (i % 2 == 0) c.uppercaseChar() else c.lowercaseChar() }.joinToString("")
        } else sni
    }

    private fun applyTlsSettings(builder: JsonObjectBuilder, settings: org.rigbyfoundation.nuggetvpn.data.models.AppSettings?) {
        if (settings == null) return
        if (settings.tlsFragment) {
            builder.putJsonObject("tls_fragment") {
                put("enabled", true)
                val sizes = settings.tlsFragmentSize.split("-")
                put("size_min", sizes.getOrNull(0)?.toIntOrNull() ?: 100)
                put("size_max", sizes.getOrNull(1)?.toIntOrNull() ?: 200)
                val sleeps = settings.tlsFragmentSleep.split("-")
                put("sleep_min", sleeps.getOrNull(0)?.toIntOrNull() ?: 10)
                put("sleep_max", sleeps.getOrNull(1)?.toIntOrNull() ?: 20)
            }
        }
        if (settings.tlsPadding) {
            builder.putJsonArray("tls_padding") {
                add(buildJsonObject {
                    put("length_min", 100)
                    put("length_max", 200)
                })
            }
        }
    }

    fun parseSubscription(content: String, sourceDomain: String): List<Profile> {
        val decoded = try {
            val cleaned = content.trim()
            val base64 = cleaned.replace("-", "+").replace("_", "/")
            val padded = when (base64.length % 4) {
                2 -> "$base64=="
                3 -> "$base64="
                else -> base64
            }
            String(Base64.decode(padded, Base64.DEFAULT or Base64.NO_WRAP))
        } catch (e: Exception) {
            content
        }

        return decoded.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filter { !it.contains(".time:") } // Filter Hiddify fakes
            .mapNotNull { parseProfileFromLink(it, sourceDomain = sourceDomain) }
    }
}
