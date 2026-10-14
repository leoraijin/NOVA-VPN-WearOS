package com.nova.vpn.wear

import org.json.JSONObject

internal object WatchCommandReply {
    fun read(data: ByteArray, sourceNode: String, expectedNode: String?, expectedId: String?): String? {
        if (expectedId == null || sourceNode != expectedNode || data.size > 2048) return null
        return runCatching {
            val reply = JSONObject(data.toString(Charsets.UTF_8))
            if (reply.optString("requestId") != expectedId) null
            else reply.optString("state").takeIf { it in setOf("connecting", "disconnecting", "connected", "permission_required", "profile_required", "foreground_required", "error") }
        }.getOrNull()
    }
}
