package com.nova.vpn.wear

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AndroidTrafficProbeTest {
    @Test fun checkIsAuthenticatedLoopbackAndPrecedesCatchAllBypass() {
        val source = """{"outbounds":[{"type":"vless","tag":"edge"},{"type":"direct","tag":"direct"}],"route":{"final":"edge","rules":[{"outbound":"direct","action":"route"}]}}"""
        val probe = AndroidTrafficProbe.prepare(source)!!
        val config = JSONObject(probe.config)
        val inbound = config.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals("socks", inbound.getString("type"))
        assertTrue(inbound.getInt("listen_port") in 1..65535)
        assertEquals(1, inbound.getJSONArray("users").length())
        assertTrue(inbound.getJSONArray("users").getJSONObject(0).getString("password").length >= 32)
        val rules = config.getJSONObject("route").getJSONArray("rules")
        assertEquals("edge", rules.getJSONObject(0).getString("outbound"))
        assertEquals(AndroidTrafficProbe.TAG, rules.getJSONObject(0).getJSONArray("inbound").getString(0))
        assertEquals("direct", rules.getJSONObject(1).getString("outbound"))
        assertFalse(source.contains(AndroidTrafficProbe.TAG))
        probe.cancel()
    }

    @Test fun directAndSelectorConfigsAreNotCertifiedAsVpnTraffic() {
        for (type in listOf("direct", "selector", "urltest", "block")) {
            assertNull(AndroidTrafficProbe.prepare("""{"outbounds":[{"type":"$type","tag":"edge"}],"route":{"final":"edge"}}"""))
        }
    }

    @Test fun onlySuccessfulHttpBodyCountsAsTraffic() {
        assertTrue(AndroidTrafficProbe.validTrace("HTTP/1.1 200 OK\nContent-Type: text/plain\n\nip=203.0.113.1\n"))
        assertFalse(AndroidTrafficProbe.validTrace("HTTP/1.1 403 Forbidden\n\nip=203.0.113.1\n"))
        assertFalse(AndroidTrafficProbe.validTrace("HTTP/1.1 200 OK\nip=spoofed-header\n\nerror\n"))
        assertFalse(AndroidTrafficProbe.validTrace("HTTP/1.1 200 OK\n\nip=\n"))
    }

    @Test fun cancelledProbeNeverReportsSuccess() {
        val probe = AndroidTrafficProbe.prepare("""{"outbounds":[{"type":"trojan","tag":"edge"}],"route":{"final":"edge"}}""" )!!
        probe.cancel()
        assertFalse(probe.check())
    }
}
