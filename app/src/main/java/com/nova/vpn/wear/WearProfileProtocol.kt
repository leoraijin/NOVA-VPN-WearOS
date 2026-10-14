package com.nova.vpn.wear

import org.json.JSONObject
import java.util.UUID

internal object WearProfileProtocol {
    const val PROFILE_PATH = "/nova/profile/v2"
    const val RESULT_PATH = "/nova/profile-result"
    const val CAPABILITY = "nova_vpn_watch_profile_v2"
    const val MAX_BYTES = 90 * 1024
    data class Request(val id: String, val config: String)

    fun encode(id: String, config: String): ByteArray {
        require(validId(id) && validConfig(config))
        return JSONObject().put("requestId", id).put("config", config).toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    }
    fun decode(bytes: ByteArray): Request? = runCatching {
        require(bytes.size <= MAX_BYTES)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val id = json.getString("requestId")
        val config = json.getString("config")
        require(validId(id) && validConfig(config))
        Request(id, config)
    }.getOrNull()
    fun reply(id: String, result: String): ByteArray {
        require(validId(id) && result in listOf("stored", "storage_error"))
        return JSONObject().put("requestId", id).put("result", result).toString().toByteArray(Charsets.UTF_8)
    }
    fun result(bytes: ByteArray, id: String): String? = runCatching {
        require(bytes.size <= 1024)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getString("requestId") == id)
        json.getString("result").also { require(it in listOf("stored", "storage_error")) }
    }.getOrNull()
    fun validConfig(config: String): Boolean = runCatching { config.toByteArray(Charsets.UTF_8).size <= MAX_BYTES && (JSONObject(config).optJSONArray("outbounds")?.length() ?: 0) > 0 }.getOrDefault(false)
    private fun validId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
}
