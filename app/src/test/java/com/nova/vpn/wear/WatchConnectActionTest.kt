package com.nova.vpn.wear

import org.junit.Assert.*
import org.junit.Test

class WatchConnectActionTest {
    @Test fun missingProfileNeverRequestsConsentOrStartsVpn() {
        var started = false
        val message = WatchConnectAction.perform(false, { null }, {}, { started = true })
        assertFalse(started)
        assertTrue(message!!.contains("Профиль ещё не передан"))
    }
    @Test fun corruptVaultIsReportedWithoutThrowingOrRemovingProfile() {
        val message = WatchConnectAction.perform(false, { throw IllegalStateException() }, {}, { fail() })
        assertTrue(message!!.contains("Профиль не удалён"))
    }
    @Test fun unsupportedPermissionUiDoesNotCrash() {
        val message = WatchConnectAction.perform(false, { "test" }, {}, { throw UnsupportedOperationException() })
        assertTrue(message!!.contains("нижнюю кнопку"))
    }
    @Test fun foregroundAndSecurityFailuresAreCaught() {
        assertNotNull(WatchConnectAction.perform(false, { "test" }, {}, { throw SecurityException() }))
        assertNotNull(WatchConnectAction.perform(false, { "test" }, {}, { throw IllegalStateException() }))
    }
    @Test fun disconnectWorksWithoutLoadingProfile() {
        var stopped = false
        assertNull(WatchConnectAction.perform(true, { fail(); null }, { stopped = true }, { fail() }))
        assertTrue(stopped)
    }
    @Test fun validProfileReachesStartUnchanged() {
        var value = ""
        assertNull(WatchConnectAction.perform(false, { "test-profile" }, {}, { value = it }))
        assertEquals("test-profile", value)
    }
}
