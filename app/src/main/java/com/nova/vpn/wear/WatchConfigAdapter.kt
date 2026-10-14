package com.nova.vpn.wear

import org.json.JSONArray
import org.json.JSONObject

/** Adapt a runtime copy; the encrypted profile and phone configuration stay unchanged. */
internal object WatchConfigAdapter {
    private const val BOOTSTRAP = "nova-bootstrap-dns"

    fun adapt(source: String, ownPackage: String): String {
        val config = JSONObject(source)
        val outbounds = config.getJSONArray("outbounds")
        require(outbounds.length() > 0) { "Профиль не содержит подключения" }
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.getJSONObject(i)
            val tls = outbound.optJSONObject("tls")
            if (tls?.optJSONObject("reality")?.optBoolean("enabled") == true) {
                if (!tls.has("utls")) tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", "chrome"))
                tls.optJSONObject("utls")?.let {
                    if (it.optBoolean("enabled") && it.optString("fingerprint").isBlank()) it.put("fingerprint", "chrome")
                }
                if (tls.optString("server_name").isBlank()) {
                    tls.put("server_name", outbound.optString("server").removeSurrounding("[", "]"))
                }
            }
            val host = outbound.optString("server").removeSurrounding("[", "]")
            if (host.isNotBlank() && !host.contains(':') && !Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(host)
                && !outbound.has("domain_resolver")) outbound.put("domain_resolver", BOOTSTRAP)
        }
        val inbounds = config.optJSONArray("inbounds") ?: JSONArray()
        if ((0 until inbounds.length()).none { inbounds.optJSONObject(it)?.optString("type") == "tun" }) {
            inbounds.put(JSONObject().put("type", "tun").put("tag", "nova-watch-tun"))
        }
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.getJSONObject(i)
            if (inbound.optString("type") != "tun") continue
            inbound.put("mtu", 1500).put("stack", "gvisor").put("auto_route", true)
            if (!inbound.has("address")) inbound.put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
            // Phone package policy is not valid on the watch. Route policy remains in sing-box.
            inbound.remove("include_package")
            inbound.put("exclude_package", JSONArray().put(ownPackage))
        }
        config.put("inbounds", inbounds)
        val route = config.optJSONObject("route") ?: JSONObject()
        if (route.optString("final").isBlank()) {
            val proxy = (0 until outbounds.length()).map(outbounds::getJSONObject)
                .firstOrNull { it.optString("type") !in setOf("direct", "block", "dns") }
                ?: error("В профиле нет VPN-outbound")
            require(proxy.optString("tag").isNotBlank()) { "У VPN-outbound отсутствует tag" }
            route.put("final", proxy.getString("tag"))
        }
        require((0 until outbounds.length()).any { outbounds.getJSONObject(it).optString("tag") == route.optString("final") })
        val oldRules = route.optJSONArray("rules") ?: JSONArray()
        val rules = JSONArray().put(JSONObject().put("protocol", JSONArray().put("dns")).put("action", "hijack-dns"))
        for (i in 0 until oldRules.length()) rules.put(oldRules.get(i))
        route.put("rules", rules).put("auto_detect_interface", true).put("default_domain_resolver", BOOTSTRAP)
        config.put("route", route)
        val dns = config.optJSONObject("dns") ?: JSONObject()
            .put("servers", JSONArray().put(JSONObject().put("type", "https").put("tag", "remote-dns")
                .put("server", "1.1.1.1").put("path", "/dns-query").put("detour", route.getString("final"))
                .put("tls", JSONObject().put("enabled", true).put("server_name", "cloudflare-dns.com"))))
            .put("final", "remote-dns").put("strategy", "prefer_ipv4")
        val oldServers = dns.optJSONArray("servers") ?: JSONArray()
        val servers = JSONArray()
        for (i in 0 until oldServers.length()) {
            val item = oldServers.getJSONObject(i)
            if (item.optString("tag") != BOOTSTRAP) servers.put(item)
        }
        servers.put(JSONObject().put("type", "local").put("tag", BOOTSTRAP))
        config.put("dns", dns.put("servers", servers))
        return config.toString()
    }
}
