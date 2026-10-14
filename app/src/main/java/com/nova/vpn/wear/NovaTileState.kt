package com.nova.vpn.wear

import android.app.ActivityManager
import android.content.Context
import android.util.AtomicFile
import androidx.wear.tiles.TileService
import java.io.File

/** Stores only a fixed local tunnel state, never a profile, hostname or key. */
internal object NovaTileState {
    private val states = setOf("watch_disconnected", "watch_connecting", "watch_connected", "watch_verified", "watch_unconfirmed", "watch_error")
    private fun file(context: Context) = AtomicFile(File(context.filesDir, "watch-tile-state.txt"))
    fun read(context: Context): String = runCatching { file(context).readFully().toString(Charsets.UTF_8).takeIf { it in states } }.getOrNull() ?: "watch_disconnected"
    fun changed(context: Context, state: String) {
        // Profile reception still refreshes readiness even when tunnel state is unchanged.
        if (state in states && read(context) == state) return
        if (state in states) runCatching {
            if (read(context) != state) {
                val target = file(context)
                val stream = target.startWrite()
                try { stream.write(state.toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
                catch (error: Exception) { target.failWrite(stream); throw error }
            }
        }
        requestUpdate(context)
    }
    fun requestUpdate(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(NovaVpnTileService::class.java) }
    }
    @Suppress("DEPRECATION")
    fun foregroundService(context: Context): Boolean = runCatching {
        // Android exposes this app's own services. Idle UI-bound service is not an active VPN.
        context.getSystemService(ActivityManager::class.java).getRunningServices(32)
            .any { it.service.className == WatchVpnService::class.java.name && it.foreground }
    }.getOrDefault(false)
}
