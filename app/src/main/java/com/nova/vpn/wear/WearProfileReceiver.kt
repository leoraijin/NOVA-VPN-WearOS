package com.nova.vpn.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.Wearable

class WearProfileReceiver : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PROFILE_PATH && messageEvent.path != WearProfileProtocol.PROFILE_PATH) return
        val request = if (messageEvent.path == WearProfileProtocol.PROFILE_PATH) WearProfileProtocol.decode(messageEvent.data) ?: return else null
        var result = "storage_error"
        try {
            require(messageEvent.data.size <= MAX_CONFIG_BYTES) { "Конфигурация слишком велика" }
            val config = request?.config ?: messageEvent.data.toString(Charsets.UTF_8)
            require(WearProfileProtocol.validConfig(config))
            val vault = WearProfileVault(this)
            vault.save(config)
            check(vault.load() == config)
            WatchRuntime.profileSaved()
            WatchStatusSync.publish(this, "profile_ready")
            result = "stored"
        } catch (error: Exception) {
            Log.w(TAG, "Profile storage failed: ${error.javaClass.simpleName}")
            WatchStatusSync.publish(this, "profile_error")
        }
        request?.let {
            runCatching { Wearable.getMessageClient(this).sendMessage(messageEvent.sourceNodeId, WearProfileProtocol.RESULT_PATH, WearProfileProtocol.reply(it.id, result)) }
        }
    }

    private companion object {
        const val PROFILE_PATH = "/nova/profile"
        const val MAX_CONFIG_BYTES = 90 * 1024
        const val TAG = "NovaWearProfile"
    }
}
