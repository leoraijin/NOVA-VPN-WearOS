package com.nova.vpn.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local state is authoritative even with no paired phone or Data Layer connection. */
internal object WatchRuntime {
    private val mutableState = MutableStateFlow("watch_disconnected")
    val state = mutableState.asStateFlow()
    fun update(value: String) { mutableState.value = value }
    fun isActive(value: String) = value in setOf("watch_connecting", "watch_connected", "watch_verified", "watch_unconfirmed")
}
