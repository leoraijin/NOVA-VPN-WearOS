package com.nova.vpn.wear

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WatchRuntimeRelayTest {
    @Test fun serviceStateCanBeRelayedWithoutPhone() {
        val received = mutableListOf<String>()
        try {
            WatchRuntime.relay = { received.add(it) }
            WatchRuntime.update("watch_connecting")
            WatchRuntime.update("watch_verified")
            assertEquals(listOf("watch_connecting", "watch_verified"), received)
            assertEquals("watch_verified", WatchRuntime.state.value)
        } finally { WatchRuntime.relay = null; WatchRuntime.update("watch_disconnected") }
    }
    @Test fun failureIsNotLostWhenServiceReconnects() {
        WatchRuntime.reportFailure("core-start")
        WatchRuntime.update("watch_disconnected")
        assertEquals("core-start", WatchRuntime.failure.value)
        WatchRuntime.reportFailure(null)
        assertNull(WatchRuntime.failure.value)
    }
    @Test fun nativeServiceIsInPrivateProcessAndNotExported() {
        val file = File("src/main/AndroidManifest.xml").takeIf { it.exists() } ?: File("app/src/main/AndroidManifest.xml")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("service")
        val vpn = (0 until nodes.length).map { nodes.item(it) }.first { it.attributes.getNamedItem("android:name").nodeValue == ".WatchVpnService" }
        assertEquals(":vpn", vpn.attributes.getNamedItem("android:process").nodeValue)
        assertEquals("false", vpn.attributes.getNamedItem("android:exported").nodeValue)
        assertEquals("android.permission.BIND_VPN_SERVICE", vpn.attributes.getNamedItem("android:permission").nodeValue)
    }
}
