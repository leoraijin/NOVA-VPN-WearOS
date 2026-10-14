package com.nova.vpn.wear

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NovaTilePresentationTest {
    @Test fun staleVerifiedSnapshotAfterServiceCrashIsNotActive() {
        val model = NovaTilePresentation.model("watch_verified", true, false)
        assertFalse(model.active)
        assertEquals("VPN выключен", model.title)
        assertEquals("Профиль готов", model.detail)
    }
    @Test fun unconfirmedTrafficNeverShowsHttpsSuccess() {
        val model = NovaTilePresentation.model("watch_unconfirmed", true, true)
        assertTrue(model.active)
        assertEquals("HTTPS не подтверждён", model.detail)
        assertFalse(NovaTilePresentation.model("connected", true, true).active)
    }
    @Test fun missingProfileAndStartFailureHaveDifferentInstructions() {
        assertEquals("Нет профиля", NovaTilePresentation.model("watch_disconnected", false, false).title)
        assertEquals("Ошибка запуска", NovaTilePresentation.model("watch_error", true, false).title)
        assertEquals("Подключение…", NovaTilePresentation.model("watch_connecting", true, true).title)
    }
    @Test fun tileIsRegisteredWithPlatformPermissionAndPreview() {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val services = document.getElementsByTagName("service")
        val tile = (0 until services.length).map { services.item(it) as org.w3c.dom.Element }
            .single { it.getAttribute("android:name") == ".NovaVpnTileService" }
        assertEquals("true", tile.getAttribute("android:exported"))
        assertEquals("com.google.android.wearable.permission.BIND_TILE_PROVIDER", tile.getAttribute("android:permission"))
        assertEquals("androidx.wear.tiles.action.BIND_TILE_PROVIDER", (tile.getElementsByTagName("action").item(0) as org.w3c.dom.Element).getAttribute("android:name"))
        assertEquals("@drawable/nova_tile_preview", (tile.getElementsByTagName("meta-data").item(0) as org.w3c.dom.Element).getAttribute("android:resource"))
    }
}
