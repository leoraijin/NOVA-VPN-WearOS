package com.nova.vpn.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionGatewayProbeTest {
    @Test fun acceptsOnlySuccessfulTraceWithAnAddressField() {
        assertTrue(CompanionGatewayProbe.validTrace(200, sequenceOf("ip=203.0.113.1", "loc=PL")))
        assertFalse(CompanionGatewayProbe.validTrace(403, sequenceOf("ip=203.0.113.1")))
        assertFalse(CompanionGatewayProbe.validTrace(200, sequenceOf("ip=")))
        assertFalse(CompanionGatewayProbe.validTrace(200, sequenceOf("error=upstream")))
    }
}
