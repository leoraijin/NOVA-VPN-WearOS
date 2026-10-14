package com.nova.vpn.wear

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One-shot HTTPS diagnostic over the Wear OS phone-companion network. */
internal object CompanionGatewayProbe {
    enum class Outcome { HTTPS_OK, NO_BLUETOOTH_NETWORK, HTTPS_TIMEOUT, REQUEST_REJECTED, TLS_OR_IO_FAILURE, BAD_TRACE }

    suspend fun check(connectivity: ConnectivityManager): Outcome {
        val existing = connectivity.allNetworks.firstOrNull { candidate ->
            connectivity.getNetworkCapabilities(candidate)?.let {
                it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH) &&
                    it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true
        }
        val network = try {
            existing ?: withTimeout(10_000) {
                suspendCancellableCoroutine<Network> { continuation ->
                    val request = NetworkRequest.Builder()
                        .addTransportType(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH)
                        .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            if (!continuation.isActive) return
                            runCatching { connectivity.unregisterNetworkCallback(this) }
                            continuation.resume(network)
                        }

                        override fun onUnavailable() {
                            if (!continuation.isActive) return
                            runCatching { connectivity.unregisterNetworkCallback(this) }
                            continuation.resumeWithException(NoBluetoothNetworkException())
                        }
                    }
                    try {
                        connectivity.requestNetwork(request, callback, 8_000)
                        continuation.invokeOnCancellation {
                            runCatching { connectivity.unregisterNetworkCallback(callback) }
                        }
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            return Outcome.HTTPS_TIMEOUT
        } catch (_: NoBluetoothNetworkException) {
            return Outcome.NO_BLUETOOTH_NETWORK
        } catch (_: Exception) {
            return Outcome.REQUEST_REJECTED
        }

        return withContext(Dispatchers.IO) {
            val connection = network.openConnection(URL(TRACE_URL)) as HttpsURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                val status = connection.responseCode
                if (status != 200) Outcome.TLS_OR_IO_FAILURE
                else if (connection.inputStream.bufferedReader().useLines(::hasAddressField)) Outcome.HTTPS_OK
                else Outcome.BAD_TRACE
            } catch (error: javax.net.ssl.SSLException) {
                Outcome.TLS_OR_IO_FAILURE
            } catch (error: java.io.IOException) {
                if (error is java.net.SocketTimeoutException) Outcome.HTTPS_TIMEOUT else Outcome.TLS_OR_IO_FAILURE
            } finally {
                connection.disconnect()
            }
        }
    }

    internal fun validTrace(statusCode: Int, lines: Sequence<String>): Boolean = statusCode == 200 && hasAddressField(lines)

    private fun hasAddressField(lines: Sequence<String>): Boolean = lines.any { it.startsWith("ip=") && it.length > 3 }

    private class NoBluetoothNetworkException : Exception()

    private const val TRACE_URL = "https://1.1.1.1/cdn-cgi/trace"
}
