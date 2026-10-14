package com.nova.vpn.wear

import org.junit.Assert.*
import org.junit.Test

class InterfaceUpdateGateTest {
    @Test fun signalChangesDoNotReloadTheSameInterface() {
        val gate = InterfaceUpdateGate<DefaultInterfaceSnapshot>()
        var deliveries = 0
        repeat(1000) { gate.deliver(DefaultInterfaceSnapshot(1L, "wlan0", 7)) { deliveries++ } }
        assertEquals(1, deliveries)
    }
    @Test fun handoverLinkChangeLossAndRestoreAreDelivered() {
        val gate = InterfaceUpdateGate<DefaultInterfaceSnapshot>()
        val states = listOf(DefaultInterfaceSnapshot(1L, "wlan0", 7), DefaultInterfaceSnapshot(2L, "wlan0", 7),
            DefaultInterfaceSnapshot(2L, "rmnet0", 8), DefaultInterfaceSnapshot(null, "", 0), DefaultInterfaceSnapshot(2L, "rmnet0", 8))
        val received = mutableListOf<DefaultInterfaceSnapshot>()
        states.forEach { state -> gate.deliver(state) { received.add(state) } }
        assertEquals(states, received)
    }
    @Test fun failedCallbackDoesNotPreventRetry() {
        val gate = InterfaceUpdateGate<String>()
        runCatching { gate.deliver("wifi") { error("delivery failed") } }
        var retried = false
        gate.deliver("wifi") { retried = true }
        assertTrue(retried)
    }
}
