package com.nova.vpn.wear

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A bounded HTTPS check explicitly through the core, not the excluded app's direct route. */
internal class AndroidTrafficProbe private constructor(
    val config: String, private val port: Int, private val username: String, private val password: String
) {
    private val cancelled = AtomicBoolean()
    @Volatile private var socket: Socket? = null

    fun cancel() { cancelled.set(true); runCatching { socket?.close() } }

    fun check(): Boolean {
        val deadline = java.util.Timer("nova-probe-deadline", true)
        deadline.schedule(object : java.util.TimerTask() { override fun run() { cancelProbe() } }, 18000)
        return try {
        check(!cancelled.get())
        Socket().use { raw ->
            socket = raw
            check(!cancelled.get())
            raw.connect(InetSocketAddress("127.0.0.1", port), 3000)
            raw.soTimeout = 7000
            val input = raw.getInputStream()
            val output = raw.getOutputStream()
            // SOCKS5 username/password: another local app cannot use this test listener.
            output.write(byteArrayOf(5, 1, 2)); output.flush()
            check(input.read() == 5 && input.read() == 2)
            val user = username.toByteArray(StandardCharsets.UTF_8)
            val secret = password.toByteArray(StandardCharsets.UTF_8)
            output.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(secret.size.toByte()) + secret)
            output.flush(); check(input.read() == 1 && input.read() == 0)
            // A numeric destination avoids bootstrap dependence on Android/browser DNS.
            output.write(byteArrayOf(5, 1, 0, 1, 1, 1, 1, 1, 1, -69)); output.flush()
            check(input.read() == 5 && input.read() == 0 && input.read() == 0)
            val addressBytes = when (input.read()) { 1 -> 4; 4 -> 16; 3 -> input.read().also { check(it >= 0) }; else -> error("SOCKS address") }
            repeat(addressBytes + 2) { check(input.read() >= 0) }
            check(!cancelled.get())
            ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, "1.1.1.1", 443, false) as SSLSocket).use { tls ->
                socket = tls
                check(!cancelled.get())
                tls.soTimeout = 7000
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.startHandshake()
                tls.getOutputStream().apply {
                    write("GET /cdn-cgi/trace HTTP/1.1\r\nHost: 1.1.1.1\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                    flush()
                }
                val reader = BufferedReader(InputStreamReader(tls.getInputStream(), StandardCharsets.UTF_8))
                val response = StringBuilder()
                while (response.length < 8192) {
                    check(!cancelled.get())
                    val line = reader.readLine() ?: break
                    response.append(line).append('\n')
                    if (line.startsWith("ip=")) break
                }
                validTrace(response.toString())
            }
        }
        } catch (_: Exception) { false } finally { deadline.cancel(); socket = null }
    }

    private fun cancelProbe() = cancel()

    companion object {
        internal const val TAG = "nova-local-health"
        internal fun validTrace(response: String): Boolean =
            response.lineSequence().firstOrNull()?.matches(Regex("HTTP/1\\.[01] 200(?: .*)?")) == true &&
                response.contains("\n\n") && response.substringAfter("\n\n").lineSequence().any { it.startsWith("ip=") && it.length > 3 }

        fun prepare(config: String): AndroidTrafficProbe? {
            val json = JSONObject(config)
            val route = json.optJSONObject("route") ?: return null
            val finalTag = route.optString("final")
            val outbounds = json.optJSONArray("outbounds") ?: return null
            val final = (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject)
                .firstOrNull { it.optString("tag") == finalTag } ?: return null
            // Arbitrary selectors/full configs may contain a direct fallback. Do not certify them.
            if (final.optString("type") !in setOf("vless", "trojan", "shadowsocks", "vmess", "hysteria2", "tuic")) return null
            val inbounds = json.optJSONArray("inbounds") ?: JSONArray()
            if ((0 until inbounds.length()).any { inbounds.optJSONObject(it)?.optString("tag") == TAG }) return null
            val port = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }
            val user = UUID.randomUUID().toString(); val password = UUID.randomUUID().toString()
            inbounds.put(JSONObject().put("type", "socks").put("tag", TAG).put("listen", "127.0.0.1")
                .put("listen_port", port).put("users", JSONArray().put(JSONObject().put("username", user).put("password", password))))
            json.put("inbounds", inbounds)
            val original = route.optJSONArray("rules") ?: JSONArray()
            val rules = JSONArray().put(JSONObject().put("inbound", JSONArray().put(TAG)).put("action", "route").put("outbound", finalTag))
            for (index in 0 until original.length()) rules.put(original.get(index))
            route.put("rules", rules)
            return AndroidTrafficProbe(json.toString(), port, user, password)
        }
    }
}
