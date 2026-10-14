package com.nova.vpn.wear

import org.junit.Assert.*
import org.junit.Test

class WatchConnectActionTest {
    @Test fun permissionNullDoesNotStartVpnOrMisreportProfileFailure() {
        var started = false
        val message = WatchConnectAction.perform(false, { "test-profile" }, {}, {
            WatchConnectAction.at(WatchActionStage.PERMISSION) { throw NullPointerException() }
            started = true
        })!!
        assertFalse(started)
        assertTrue(message.contains("системное разрешение VPN"))
        assertTrue(message.contains("Профиль сохранён"))
        assertTrue(message.contains("а не трафиком часов"))
    }
    @Test fun profileAndServiceNullFailuresRemainDistinct() {
        for (stage in listOf(WatchActionStage.PROFILE, WatchActionStage.START)) {
            val message = WatchConnectAction.perform(false, {
                if (stage == WatchActionStage.PROFILE) WatchConnectAction.at(stage) { throw NullPointerException() }
                "test"
            }, {}, { WatchConnectAction.at(stage) { throw NullPointerException() } })!!
            assertTrue(message.contains(stage.label))
            assertFalse(message.contains("VPN API недоступен"))
        }
    }
    @Test fun cancellationIsNotReportedAsVpnFailure() {
        try {
            WatchConnectAction.perform(false, { WatchConnectAction.at(WatchActionStage.PROFILE) { throw kotlinx.coroutines.CancellationException() } }, {}, {})
            fail("Expected cancellation")
        } catch (expected: kotlinx.coroutines.CancellationException) { }
    }
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
