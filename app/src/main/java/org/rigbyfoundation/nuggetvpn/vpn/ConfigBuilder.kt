package org.rigbyfoundation.nuggetvpn.vpn

import kotlinx.serialization.json.*
import org.rigbyfoundation.nuggetvpn.data.models.AppSettings
import org.rigbyfoundation.nuggetvpn.data.models.Profile
import org.rigbyfoundation.nuggetvpn.util.ProxyParser

object ConfigBuilder {

    fun buildConfig(
        profile: Profile,
        settings: AppSettings,
        allProfiles: List<Profile> = emptyList()
    ): String {
        // If profile is a full sing-box config, patch it for library mode
        if (ProxyParser.isFullSingboxConfig(profile.configLink)) {
            return patchConfigForLibraryMode(profile.configLink.trim())
        }

        val outbounds = mutableListOf<JsonObject>()
        val routeRules = mutableListOf<JsonObject>()

        // Build main proxy outbound
        if (settings.proxyChainEnabled && settings.proxyChain.isNotEmpty()) {
            buildProxyChain(settings, allProfiles, outbounds)
        } else {
            val mainOutbound = ProxyParser.parseOutbound(profile, settings, "proxy")
            if (mainOutbound != null) outbounds.add(mainOutbound)
        }

        // Add direct and block outbounds
        outbounds.add(buildJsonObject {
            put("type", "direct")
            put("tag", "direct")
        })
        outbounds.add(buildJsonObject {
            put("type", "block")
            put("tag", "block")
        })
        outbounds.add(buildJsonObject {
            put("type", "dns")
            put("tag", "dns-out")
        })

        // DNS route rule
        routeRules.add(buildJsonObject {
            put("protocol", "dns")
            put("outbound", "dns-out")
        })

        // Split tunneling rules
        buildSplitTunnelingRules(settings, routeRules)

        val config = buildJsonObject {
            putJsonObject("log") {
                put("level", "info")
                put("timestamp", true)
            }

            putJsonObject("dns") {
                putJsonArray("servers") {
                    add(buildJsonObject {
                        put("tag", "dns-remote")
                        put("address", "https://1.1.1.1/dns-query")
                        put("detour", "proxy")
                    })
                    add(buildJsonObject {
                        put("tag", "dns-local")
                        put("address", "local")
                    })
                }
                putJsonArray("rules") {
                    add(buildJsonObject {
                        putJsonArray("outbound") { add("any") }
                        put("server", "dns-local")
                    })
                }
                put("final", "dns-remote")
                put("independent_cache", true)
            }

            putJsonArray("inbounds") {
                add(buildJsonObject {
                    put("type", "tun")
                    put("tag", "tun-in")
                    putJsonArray("inet4_address") { add("172.19.0.1/30") }
                    putJsonArray("inet6_address") { add("fdfe:dcba:9876::1/126") }
                    put("mtu", settings.mtu)
                    put("auto_route", true)
                    put("strict_route", true)
                    put("stack", "mixed")
                    put("sniff", true)
                    put("sniff_override_destination", true)
                })
            }

            putJsonArray("outbounds") {
                outbounds.forEach { add(it) }
            }

            putJsonObject("route") {
                putJsonArray("rules") {
                    routeRules.forEach { add(it) }
                }
                put("final", "proxy")
            }
        }

        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), config)
    }

    /**
     * Patches a raw sing-box JSON config for library mode compatibility:
     * - Removes auto_detect_interface (platform handles this via PlatformInterface)
     * DNS settings are left as-is — the user's config should work
     * since autoDetectInterfaceControl(fd) protects outbound sockets from TUN.
     */
    private fun patchConfigForLibraryMode(raw: String): String {
        val json = Json.parseToJsonElement(raw).jsonObject.toMutableMap()

        // Remove auto_detect_interface from route (redundant in library mode,
        // platform's usePlatformAutoDetectInterfaceControl() handles this)
        val route = json["route"]?.jsonObject
        if (route != null) {
            val routeMap = route.toMutableMap()
            routeMap.remove("auto_detect_interface")
            json["route"] = JsonObject(routeMap)
        }

        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(json))
    }

    private fun buildProxyChain(
        settings: AppSettings,
        allProfiles: List<Profile>,
        outbounds: MutableList<JsonObject>
    ) {
        val chainIds = settings.proxyChain
        val profileMap = allProfiles.associateBy { it.id }
        var prevTag = "direct"

        chainIds.forEachIndexed { index, id ->
            val profile = profileMap[id] ?: return@forEachIndexed
            val tag = if (index == chainIds.lastIndex) "proxy" else "chain-$index"
            val outbound = ProxyParser.parseOutbound(profile, settings, tag)
            if (outbound != null) {
                val modified = outbound.toMutableMap()
                if (index > 0) modified["detour"] = JsonPrimitive(prevTag)
                outbounds.add(JsonObject(modified))
                prevTag = tag
            }
        }
    }

    private fun buildSplitTunnelingRules(settings: AppSettings, rules: MutableList<JsonObject>) {
        when (settings.routingMode) {
            "apps" -> {
                if (settings.routingApps.isNotEmpty()) {
                    rules.add(buildJsonObject {
                        putJsonArray("package_name") {
                            settings.routingApps.forEach { add(it) }
                        }
                        put("outbound", "proxy")
                    })
                    rules.add(buildJsonObject {
                        put("outbound", "direct")
                        // All other traffic goes direct
                    })
                }
            }
            "domains" -> {
                if (settings.routingDomains.isNotEmpty()) {
                    rules.add(buildJsonObject {
                        putJsonArray("domain_suffix") {
                            settings.routingDomains.forEach { add(it) }
                        }
                        put("outbound", "proxy")
                    })
                }
            }
            "apps_domains", "selected" -> {
                if (settings.routingApps.isNotEmpty()) {
                    rules.add(buildJsonObject {
                        putJsonArray("package_name") {
                            settings.routingApps.forEach { add(it) }
                        }
                        put("outbound", "proxy")
                    })
                }
                if (settings.routingDomains.isNotEmpty()) {
                    rules.add(buildJsonObject {
                        putJsonArray("domain_suffix") {
                            settings.routingDomains.forEach { add(it) }
                        }
                        put("outbound", "proxy")
                    })
                }
            }
            // "all" - default, everything goes through proxy via route.final
        }
    }
}
