package com.nova.vpn.wear

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NovaTilePresentationTest {
    @Test fun tileClearlyRepresentsPhoneVpnRemote() {
        val model = NovaTilePresentation.model()
        assertFalse(model.active)
        assertEquals("VPN на телефоне", model.title)
        assertEquals("Нажмите, чтобы управлять", model.detail)
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
        val names = (0 until services.length).map { (services.item(it) as org.w3c.dom.Element).getAttribute("android:name") }
        assertFalse(names.contains(".WatchVpnService"))
    }
}
