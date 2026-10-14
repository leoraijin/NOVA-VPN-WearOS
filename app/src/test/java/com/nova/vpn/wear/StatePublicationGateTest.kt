package com.nova.vpn.wear

import org.junit.Assert.*
import org.junit.Test

class StatePublicationGateTest {
    @Test fun repeatedStatusDoesNotScheduleMoreRadioWork() {
        val gate = StatePublicationGate()
        assertNotNull(gate.begin("connected"))
        repeat(1000) { assertNull(gate.begin("connected")) }
        assertNotNull(gate.begin("disconnected"))
    }
    @Test fun failedDeliveryCanRetry() {
        val gate = StatePublicationGate()
        gate.failed(gate.begin("connected")!!)
        assertNotNull(gate.begin("connected"))
    }
    @Test fun oldFailureCannotInvalidateNewerPublication() {
        val gate = StatePublicationGate()
        val first = gate.begin("connected")!!
        gate.begin("disconnected")
        gate.begin("connected")
        gate.failed(first)
        assertNull(gate.begin("connected"))
    }
}
