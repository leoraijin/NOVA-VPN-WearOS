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
    @Test fun unsupportedLocalVpnServiceIsNotRegistered() {
        val file = File("src/main/AndroidManifest.xml").takeIf { it.exists() } ?: File("app/src/main/AndroidManifest.xml")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("service")
        val names = (0 until nodes.length).mapNotNull { nodes.item(it).attributes.getNamedItem("android:name")?.nodeValue }
        assertFalse(names.contains(".WatchVpnService"))
    }
}
