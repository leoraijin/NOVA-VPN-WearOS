package com.nova.vpn.wear

import android.net.DnsResolver
import android.net.Network
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.LocalDNSTransport
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Resolve on the physical network, never on the VPN being bootstrapped. */
internal class AndroidLocalDnsTransport(private val underlay: () -> Network?) : LocalDNSTransport {
    override fun raw() = Build.VERSION.SDK_INT >= 29

    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        check(Build.VERSION.SDK_INT >= 29) { "Raw DNS requires Android 10" }
        val network = underlay() ?: run {
            logDebug("raw DNS has no physical underlay")
            error("Нет доступной Wi-Fi или мобильной сети")
        }
        logDebug("raw DNS query bytes=${message.size}")
        val signal = CancellationSignal()
        val complete = AtomicBoolean(false)
        val done = CountDownLatch(1)
        fun finish(action: () -> Unit) {
            if (complete.compareAndSet(false, true)) {
                try { action() } finally { done.countDown() }
            }
        }
        ctx.onCancel { signal.cancel(); finish { } }
        DnsResolver.getInstance().rawQuery(network, message, DnsResolver.FLAG_NO_RETRY,
            Executor { it.run() }, signal, object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) = finish {
                    logDebug("raw DNS answer bytes=${answer.size} rcode=$rcode")
                    if (rcode == 0) ctx.rawSuccess(answer) else ctx.errorCode(rcode)
                }
                override fun onError(error: DnsResolver.DnsException) = finish {
                    logDebug("raw DNS failed: ${error.javaClass.simpleName} code=${error.code}")
                    ctx.errorCode(2)
                }
            })
        if (!done.await(8, TimeUnit.SECONDS)) {
            signal.cancel()
            finish {
                logDebug("raw DNS timed out")
                ctx.errorCode(2)
            }
        }
    }

    override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
        val physicalNetwork = underlay() ?: error("Нет доступной Wi-Fi или мобильной сети")
        try {
            val addresses = physicalNetwork.getAllByName(domain).filter {
                when {
                    network.endsWith("4") -> it.address.size == 4
                    network.endsWith("6") -> it.address.size == 16
                    else -> true
                }
            }
            logDebug("local DNS lookup succeeded addressCount=${addresses.size}")
            ctx.success(addresses.mapNotNull { it.hostAddress?.substringBefore('%') }.joinToString("\n"))
        } catch (_: UnknownHostException) {
            logDebug("local DNS lookup returned no address")
            ctx.errorCode(3)
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "NovaLocalDns"
    }
}
