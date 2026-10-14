package com.nova.vpn.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Local state is authoritative even with no paired phone or Data Layer connection. */
internal object WatchRuntime {
    private val mutableState = MutableStateFlow("watch_disconnected")
    val state = mutableState.asStateFlow()
    private val mutableFailure = MutableStateFlow<String?>(null)
    val failure = mutableFailure.asStateFlow()
    private val mutableProfileRevision = MutableStateFlow(0)
    val profileRevision = mutableProfileRevision.asStateFlow()
    fun profileSaved() { mutableProfileRevision.update { it + 1 } }
    @Volatile var relay: ((String) -> Unit)? = null
    fun update(value: String) { mutableState.value = value; relay?.invoke(value) }
    fun reportFailure(value: String?) { mutableFailure.value = value }
    fun isActive(value: String) = value in setOf("watch_connecting", "watch_connected", "watch_verified", "watch_unconfirmed")
}
