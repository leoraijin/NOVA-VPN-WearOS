package com.nova.vpn.wear

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger

/** App-private IPC; no polling and no dependence on phone or Google Data Layer. */
internal class WatchServiceConnection(private val context: Context) : ServiceConnection {
    private var bound = false
    private val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == STATE) {
            message.data.getString("failure")?.let(WatchRuntime::reportFailure)
            message.data.getString("state")?.let(WatchRuntime::update)
        }
        true
    })
    fun bind(create: Boolean = false) {
        if (bound) return
        runCatching {
            // Opening controls must not create an idle native-VPN process.
            bound = context.bindService(Intent(context, WatchVpnService::class.java).setAction(ACTION), this, if (create) Context.BIND_AUTO_CREATE else 0)
            if (!bound && create) WatchRuntime.reportFailure("Не удалось связаться со службой VPN часов.")
            if (!bound && !create && WatchRuntime.isActive(WatchRuntime.state.value)) failed()
        }.onFailure { WatchRuntime.reportFailure("Система не разрешила запуск службы VPN часов.") }
    }
    fun unbind() { if (bound) { bound = false; runCatching { context.unbindService(this) } } }
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        runCatching { Messenger(binder).send(Message.obtain(null, REGISTER).apply { replyTo = replies }) }
            .onFailure { failed() }
    }
    override fun onServiceDisconnected(name: ComponentName) = failed()
    override fun onBindingDied(name: ComponentName) { failed(); unbind() }
    override fun onNullBinding(name: ComponentName) { failed(); unbind() }
    private fun failed() {
        WatchRuntime.reportFailure(WatchStartupDiagnostics.describe(context))
        WatchRuntime.update("watch_error")
    }
    companion object {
        const val ACTION = "com.nova.vpn.wear.UI_STATUS"
        const val REGISTER = 1
        const val STATE = 2
    }
}
