package org.rigbyfoundation.nuggetvpn.util

import kotlinx.coroutines.*
import org.rigbyfoundation.nuggetvpn.data.models.Profile
import org.rigbyfoundation.nuggetvpn.data.models.ProfilePing
import java.net.InetSocketAddress
import java.net.Socket

object PingManager {

    private const val TIMEOUT_MS = 2000
    private const val MAX_CONCURRENT = 12

    suspend fun pingProfiles(profiles: List<Profile>): List<ProfilePing> = withContext(Dispatchers.IO) {
        val semaphore = kotlinx.coroutines.sync.Semaphore(MAX_CONCURRENT)

        profiles.map { profile ->
            async {
                semaphore.acquire()
                try {
                    val ping = pingProfile(profile)
                    ProfilePing(id = profile.id, pingMs = ping)
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll()
    }

    private fun pingProfile(profile: Profile): Long? {
        return try {
            val server = profile.server
            val port = extractPort(profile.configLink, profile.protocol)
            if (server.isEmpty() || server == "Auto") return null

            val startTime = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(server, port), TIMEOUT_MS)
            }
            System.currentTimeMillis() - startTime
        } catch (e: Exception) {
            null
        }
    }

    private fun extractPort(link: String, protocol: String): Int {
        return try {
            when {
                link.startsWith("vmess://") -> {
                    val encoded = link.removePrefix("vmess://").substringBefore("#")
                    val decoded = String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT or android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE))
                    val json = kotlinx.serialization.json.Json.parseToJsonElement(decoded).jsonObject
                    json["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 443
                }
                link.startsWith("{") -> {
                    val json = kotlinx.serialization.json.Json.parseToJsonElement(link).jsonObject
                    json["server_port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 443
                }
                else -> {
                    val withoutScheme = link.substringAfter("://")
                    val hostPart = withoutScheme.substringBefore("#").substringBefore("?")
                    val afterAt = if (hostPart.contains("@")) hostPart.substringAfter("@") else hostPart
                    afterAt.substringAfter(":").substringBefore("/").toIntOrNull() ?: 443
                }
            }
        } catch (e: Exception) {
            443
        }
    }

    private val kotlinx.serialization.json.JsonElement.jsonObject
        get() = this as kotlinx.serialization.json.JsonObject
    private val kotlinx.serialization.json.JsonElement.jsonPrimitive
        get() = this as kotlinx.serialization.json.JsonPrimitive
}
