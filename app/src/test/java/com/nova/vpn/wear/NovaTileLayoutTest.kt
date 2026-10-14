package com.nova.vpn.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import org.junit.Assert.*
import org.junit.Test

class NovaTileLayoutTest {
    private fun content(screenWidth: Int, state: String = "watch_disconnected") : LayoutElementBuilders.Column {
        val tile = NovaTileLayout.build(NovaTilePresentation.model(state, true, true), screenWidth, "com.nova.vpn.android")
        assertEquals(NovaTileLayout.VERSION, tile.resourcesVersion)
        assertEquals(0L, tile.freshnessIntervalMillis)
        val root = tile.tileTimeline!!.timelineEntries.single().layout!!.root as LayoutElementBuilders.Box
        return root.contents.single() as LayoutElementBuilders.Column
    }
    @Test fun buttonFitsSmallAndLargeWatchLayouts() {
        for (size in listOf(160, 192, 240, 280)) {
            val column = content(size)
            val status = column.contents[2] as LayoutElementBuilders.Box
            val button = column.contents[4] as LayoutElementBuilders.Box
            val width = (button.width as DimensionBuilders.DpProp).value
            assertTrue(width <= size - 16)
            assertEquals(width, (status.width as DimensionBuilders.DpProp).value)
            assertEquals(44f, (button.height as DimensionBuilders.DpProp).value)
        }
    }
    @Test fun actionOpensLocalWatchControls() {
        val button = content(192).contents[4] as LayoutElementBuilders.Box
        val launch = button.modifiers!!.clickable!!.onClick as ActionBuilders.LaunchAction
        assertEquals("com.nova.vpn.android", launch.androidActivity!!.packageName)
        assertEquals("com.nova.vpn.wear.MainActivity", launch.androidActivity!!.className)
        assertTrue(button.modifiers!!.semantics!!.contentDescription!!.value.contains("часов"))
    }
    @Test fun statusTextUsesLocalTunnelModel() {
        val status = content(192, "watch_verified").contents[2] as LayoutElementBuilders.Box
        val texts = (status.contents.single() as LayoutElementBuilders.Column).contents.map { (it as LayoutElementBuilders.Text).text!!.value }
        assertEquals(listOf("VPN включён", "HTTPS проверен"), texts)
    }
}
