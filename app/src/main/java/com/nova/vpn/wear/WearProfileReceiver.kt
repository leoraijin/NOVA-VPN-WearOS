package com.nova.vpn.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WearProfileReceiver : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PROFILE_PATH) return
        try {
            require(messageEvent.data.size <= MAX_CONFIG_BYTES) { "Конфигурация слишком велика" }
            val config = messageEvent.data.toString(Charsets.UTF_8)
            require(config.startsWith("{") && config.contains("\"outbounds\"")) { "Некорректный sing-box профиль" }
            WearProfileVault(this).save(config)
            WatchStatusSync.publish(this, "profile_ready")
        } catch (error: Exception) {
            Log.w(TAG, "Received profile could not be stored", error)
            WatchStatusSync.publish(this, "profile_error")
        }
    }

    private companion object {
        const val PROFILE_PATH = "/nova/profile"
        const val MAX_CONFIG_BYTES = 90 * 1024
        const val TAG = "NovaWearProfile"
    }
}
