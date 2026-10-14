package com.nova.vpn.wear

/** Suppress identical in-flight/successful states; allow retry after failure without polling. */
internal class StatePublicationGate {
    private var state: String? = null
    private var revision = 0L
    @Synchronized fun begin(next: String): Long? {
        if (next == state) return null
        state = next
        return ++revision
    }
    @Synchronized fun failed(token: Long) {
        if (token == revision) state = null
    }
}
