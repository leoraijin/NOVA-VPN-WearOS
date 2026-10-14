package com.nova.vpn.wear

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WatchConfigAdapterTest {
    private fun config(tls: String = """{"reality":{"enabled":true}}""") =
        """{"outbounds":[{"type":"vless","tag":"proxy","server":"vpn.example.invalid","tls":$tls}],"inbounds":[{"type":"tun","stack":"system","mtu":9000,"include_package":["phone.browser"]}],"route":{"final":"proxy","rules":[{"action":"route","outbound":"proxy"}]}}"""

    @Test fun repairsSavedRealityAndBootstrapWithoutMutatingSource() {
        val original = config()
        val result = JSONObject(WatchConfigAdapter.adapt(original, "com.nova.vpn.android"))
        val outbound = result.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("chrome", outbound.getJSONObject("tls").getJSONObject("utls").getString("fingerprint"))
        assertEquals("vpn.example.invalid", outbound.getJSONObject("tls").getString("server_name"))
        assertEquals("nova-bootstrap-dns", outbound.getString("domain_resolver"))
        assertFalse(JSONObject(original).getJSONArray("outbounds").getJSONObject(0).has("domain_resolver"))
        val tun = result.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("gvisor", tun.getString("stack"))
        assertEquals(1500, tun.getInt("mtu"))
        assertFalse(tun.has("include_package"))
        assertEquals("com.nova.vpn.android", tun.getJSONArray("exclude_package").getString(0))
        assertEquals("hijack-dns", result.getJSONObject("route").getJSONArray("rules").getJSONObject(0).getString("action"))
        assertEquals("proxy", result.getJSONObject("dns").getJSONArray("servers").getJSONObject(0).getString("detour"))
    }

    @Test fun preservesExplicitTlsAndRouting() {
        val adapted = JSONObject(WatchConfigAdapter.adapt(config("""{"server_name":"explicit.invalid","utls":{"enabled":false},"reality":{"enabled":true}}"""), "watch"))
        val tls = adapted.getJSONArray("outbounds").getJSONObject(0).getJSONObject("tls")
        assertEquals("explicit.invalid", tls.getString("server_name"))
        assertFalse(tls.getJSONObject("utls").getBoolean("enabled"))
        assertEquals("proxy", adapted.getJSONObject("route").getJSONArray("rules").getJSONObject(1).getString("outbound"))
    }

    @Test fun defaultsFingerprintOnlyWhenEnabled() {
        val adapted = JSONObject(WatchConfigAdapter.adapt(config("""{"utls":{"enabled":true},"reality":{"enabled":true}}"""), "watch"))
        assertEquals("chrome", adapted.getJSONArray("outbounds").getJSONObject(0).getJSONObject("tls").getJSONObject("utls").getString("fingerprint"))
    }

    @Test fun rejectsMissingFinalOutbound() {
        val source = JSONObject(config()).apply { getJSONObject("route").put("final", "absent") }
        assertThrows(IllegalArgumentException::class.java) { WatchConfigAdapter.adapt(source.toString(), "watch") }
    }

    @Test fun createsTunAndRouteForBareOutboundConfig() {
        val result = JSONObject(WatchConfigAdapter.adapt("""{"outbounds":[{"type":"trojan","tag":"vpn","server":"192.0.2.1"}]}""", "watch"))
        assertEquals("vpn", result.getJSONObject("route").getString("final"))
        assertFalse(result.getJSONArray("outbounds").getJSONObject(0).has("domain_resolver"))
        assertEquals("tun", result.getJSONArray("inbounds").getJSONObject(0).getString("type"))
    }

    @Test fun reapplyingDoesNotDuplicateBootstrapServer() {
        val once = WatchConfigAdapter.adapt(config(), "watch")
        val twice = JSONObject(WatchConfigAdapter.adapt(once, "watch"))
        val servers = twice.getJSONObject("dns").getJSONArray("servers")
        assertEquals(1, (0 until servers.length()).count { servers.getJSONObject(it).optString("tag") == "nova-bootstrap-dns" })
    }

    @Test fun neutralStatusStillAllowsDisconnectWithoutPhone() {
        WatchRuntime.update("watch_unconfirmed")
        assertEquals("watch_unconfirmed", WatchRuntime.state.value)
        assertTrue(WatchRuntime.isActive(WatchRuntime.state.value))
        assertTrue(WatchRuntime.isActive("watch_verified"))
        assertFalse(WatchRuntime.isActive("watch_error"))
        WatchRuntime.update("watch_disconnected")
    }
}
