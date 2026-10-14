package com.nova.vpn.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WatchStatusSync {
    private val publications = StatePublicationGate()
    const val PATH = "/nova/watch-status"
    fun publish(context: Context, state: String) {
        NovaTileState.changed(context, state)
        if (state.startsWith("watch_")) WatchRuntime.update(state)
        val token = publications.begin(state) ?: return
        val request = PutDataMapRequest.create(PATH).apply { dataMap.putString("state", state) }
            .asPutDataRequest()
        // Routine verification is batched by Data Layer, not an immediate radio wakeup.
        // User-visible connection transitions remain urgent; command replies use MessageClient.
        if (state != "watch_verified" && state != "watch_unconfirmed") request.setUrgent()
        runCatching { Wearable.getDataClient(context).putDataItem(request) }
            .onFailure { publications.failed(token); Log.d("NovaWearSync", "Watch status sync unavailable", it) }
            .onSuccess { task -> task.addOnFailureListener { publications.failed(token) } }
    }
}
