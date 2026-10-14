package com.nova.vpn.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/** Local-only tile: event-driven snapshots and a foreground entry to watch VPN controls. */
class NovaVpnTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val hasProfile = runCatching { !WearProfileVault(this).load().isNullOrBlank() }.getOrDefault(false)
        val model = NovaTilePresentation.model(NovaTileState.read(this), hasProfile, NovaTileState.foregroundService(this))
        return CallbackToFutureAdapter.getFuture { it.set(NovaTileLayout.build(model, requestParams.deviceConfiguration.screenWidthDp, packageName)); "NOVA tile" }
    }
    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder().setVersion(NovaTileLayout.VERSION)
            .addIdToImageMapping("nova", resource(R.drawable.nova_mark))
            .addIdToImageMapping("power", resource(R.drawable.nova_tile_power)).build()
        return CallbackToFutureAdapter.getFuture { it.set(resources); "NOVA tile resources" }
    }
    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) { NovaTileState.requestUpdate(this) }
    private fun resource(id: Int) = ResourceBuilders.ImageResource.Builder().setAndroidResourceByResId(
        ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(id).build()).build()
}

internal object NovaTileLayout {
    const val VERSION = "nova-tile-v1"
    fun build(model: NovaTilePresentation.Model, screenWidth: Int, packageName: String): TileBuilders.Tile {
        val width = (screenWidth - 40).coerceIn(120, 224).toFloat()
        val header = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(image("nova", 22f, 26f)).addContent(gap(6f, 1f))
            .addContent(text("NOVA VPN", 15f, WHITE, true)).build()
        val status = LayoutElementBuilders.Box.Builder().setWidth(dp(width)).setHeight(dp(48f))
            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                .setBackground(background(if (model.active) ACTIVE else SURFACE, 18f))
                .setSemantics(semantics("${model.title}. ${model.detail}")) .build())
            .addContent(LayoutElementBuilders.Column.Builder()
                .addContent(text(model.title, 13f, WHITE, true))
                .addContent(text(model.detail, 10f, MUTED)).build()).build()
        val launch = ActionBuilders.LaunchAction.Builder().setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(MainActivity::class.java.name).build()).build()
        val button = LayoutElementBuilders.Box.Builder().setWidth(dp(width)).setHeight(dp(44f))
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setBackground(background(LAVENDER, 22f))
                .setClickable(ModifiersBuilders.Clickable.Builder().setId("nova-open").setOnClick(launch).build())
                .setSemantics(semantics("Открыть NOVA для управления VPN часов")).build())
            .addContent(LayoutElementBuilders.Row.Builder().setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(image("power", 22f, 22f)).addContent(gap(8f, 1f))
                .addContent(text("Открыть VPN", 13f, INK, true)).build()).build()
        val content = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(header).addContent(gap(1f, 10f)).addContent(status)
            .addContent(gap(1f, 8f)).addContent(button).addContent(gap(1f, 6f))
            .addContent(text("На этих часах", 10f, MUTED)).build()
        val root = LayoutElementBuilders.Box.Builder().setWidth(expand()).setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setBackground(background(BLACK, 0f)).build())
            .addContent(content).build()
        return TileBuilders.Tile.Builder().setResourcesVersion(VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder().setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build()).build()).build())
            // No periodic refresh or network request: updates occur on local state changes / entry.
            .build()
    }
    private fun image(id: String, width: Float, height: Float) = LayoutElementBuilders.Image.Builder()
        .setResourceId(id).setWidth(dp(width)).setHeight(dp(height)).build()
    private fun gap(width: Float, height: Float) = LayoutElementBuilders.Spacer.Builder().setWidth(dp(width)).setHeight(dp(height)).build()
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = LayoutElementBuilders.Text.Builder()
        .setText(value).setMaxLines(1).setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(color))
            .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL).build()).build()
    private fun background(color: Int, radius: Float) = ModifiersBuilders.Background.Builder().setColor(argb(color))
        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(radius)).build()).build()
    private fun semantics(value: String) = ModifiersBuilders.Semantics.Builder().setContentDescription(value).build()
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFF8F2FF.toInt()
    private const val MUTED = 0xFFC8BED9.toInt()
    private const val SURFACE = 0xFF211831.toInt()
    private const val ACTIVE = 0xFF3E2864.toInt()
    private const val LAVENDER = 0xFFE3D3FF.toInt()
    private const val INK = 0xFF271640.toInt()
}
