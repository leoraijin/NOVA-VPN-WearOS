package com.nova.vpn.wear

/** Commit only after delivery succeeds; handover/loss must never be suppressed. */
internal class InterfaceUpdateGate<T> {
    private var previous: T? = null
    @Synchronized fun deliver(snapshot: T, update: () -> Unit) {
        if (snapshot == previous) return
        update()
        previous = snapshot
    }
}

internal data class DefaultInterfaceSnapshot(val network: Long?, val name: String, val index: Int)
