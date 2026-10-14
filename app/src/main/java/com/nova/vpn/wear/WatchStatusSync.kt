package com.nova.vpn.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WatchStatusSync {
    const val PATH = "/nova/watch-status"
    fun publish(context: Context, state: String) {
        NovaTileState.changed(context, state)
        if (state.startsWith("watch_")) WatchRuntime.update(state)
        val request = PutDataMapRequest.create(PATH).apply { dataMap.putString("state", state) }
            .asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(context).putDataItem(request) }
            .onFailure { Log.d("NovaWearSync", "Watch status sync unavailable", it) }
    }
}
