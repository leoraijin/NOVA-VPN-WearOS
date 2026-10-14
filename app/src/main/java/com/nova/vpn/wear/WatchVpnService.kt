package com.nova.vpn.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.IBinder
import android.os.Bundle
import android.util.Log
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterface as BoxNetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification as BoxNotification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.InetAddress
import java.net.NetworkInterface as JNetworkInterface
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Independent VPN tunnel hosted on the watch; it never sends commands to the phone. */
class WatchVpnService : VpnService(), PlatformInterface, CommandServerHandler {
    private val connectivity by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val monitors = ConcurrentHashMap<InterfaceUpdateListener, ConnectivityManager.NetworkCallback>()
    private val availableUnderlays = ConcurrentHashMap<Network, NetworkCapabilities>()
    private val queue = Executors.newSingleThreadExecutor { Thread(it, "nova-wear-vpn").apply { isDaemon = true } }
    private val probeQueue = Executors.newSingleThreadExecutor { Thread(it, "nova-wear-probe").apply { isDaemon = true } }
    @Volatile private var trafficProbe: AndroidTrafficProbe? = null
    private var server: CommandServer? = null
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var startIdLatest = 0
    private val uiClients = java.util.concurrent.CopyOnWriteArrayList<Messenger>()
    private val statusBinder = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == WatchServiceConnection.REGISTER && message.replyTo != null) {
            uiClients.addIfAbsent(message.replyTo)
            sendUiStatus(WatchRuntime.state.value)
        }
        true
    })
    override fun onCreate() {
        super.onCreate()
        WatchRuntime.relay = ::sendUiStatus
    }
    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == WatchServiceConnection.ACTION) statusBinder.binder else super.onBind(intent)
    override fun onUnbind(intent: Intent?): Boolean {
        if (intent?.action == WatchServiceConnection.ACTION) uiClients.clear()
        return super.onUnbind(intent)
    }
    private fun sendUiStatus(state: String) {
        uiClients.forEach { client ->
            runCatching { client.send(Message.obtain(null, WatchServiceConnection.STATE).apply { data = Bundle().apply { putString("state", state); putString("failure", WatchRuntime.failure.value) } }) }
                .onFailure { uiClients.remove(client) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startIdLatest = startId
        trafficProbe?.cancel()
        if (intent?.action == ACTION_STOP) { stopTunnel(startId); return START_NOT_STICKY }
        val config = intent?.getStringExtra(EXTRA_CONFIG)
        if (config.isNullOrBlank()) { WatchStatusSync.publish(this, "watch_error"); stopSelfResult(startId); return START_NOT_STICKY }
        WatchStatusSync.publish(this, "watch_connecting")
        WatchRuntime.reportFailure(null)
        WatchStartupDiagnostics.mark(this, "foreground")
        try { enterForeground(notification("Подключение…")) }
        catch (error: Exception) { Log.e(TAG, "Could not enter foreground", error); WatchStatusSync.publish(this, "watch_error"); stopSelfResult(startId); return START_NOT_STICKY }
        queue.execute {
            if (startId != startIdLatest) return@execute
            try {
                check(VpnService.prepare(this) == null) { "Сначала разрешите VPN на часах" }
                WatchStartupDiagnostics.mark(this, "core-setup")
                setupLibbox()
                WatchStartupDiagnostics.mark(this, "config-adapt")
                val adapted = WatchConfigAdapter.adapt(config, packageName)
                val probe = AndroidTrafficProbe.prepare(adapted)
                val runtimeConfig = probe?.config ?: adapted
                WatchStartupDiagnostics.mark(this, "config-check")
                Libbox.checkConfig(runtimeConfig)
                WatchStartupDiagnostics.mark(this, "core-start")
                (server ?: CommandServer(this, this).also { it.start(); server = it })
                    .startOrReloadService(runtimeConfig, OverrideOptions())
                if (startId != startIdLatest) return@execute
                trafficProbe = probe
                WatchStartupDiagnostics.mark(this, "running")
                WatchStatusSync.publish(this, "watch_connected")
                updateNotification("Туннель включён · проверка трафика")
                if (probe == null) {
                    WatchStatusSync.publish(this, "watch_unconfirmed")
                    updateNotification("Туннель активен · трафик не подтверждён")
                } else probeQueue.execute {
                    val verified = probe.check()
                    runCatching {
                        queue.execute {
                            if (startId == startIdLatest && trafficProbe === probe) {
                                WatchStatusSync.publish(this, if (verified) "watch_verified" else "watch_unconfirmed")
                                updateNotification(if (verified) "HTTPS через VPN подтверждён" else "Туннель активен · проверка не подтверждена")
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Watch VPN start failed: ${error.javaClass.simpleName}")
                WatchRuntime.reportFailure(WatchStartupDiagnostics.startupError(this, error))
                WatchStartupDiagnostics.mark(this, "startup-error")
                closeEngine()
                if (startId != startIdLatest) return@execute
                WatchStatusSync.publish(this, "watch_error")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() { stopTunnel(startIdLatest); super.onRevoke() }
    override fun onDestroy() {
        WatchRuntime.relay = null
        uiClients.clear()
        startIdLatest = -1
        trafficProbe?.cancel()
        probeQueue.shutdownNow()
        monitors.values.forEach { runCatching { connectivity.unregisterNetworkCallback(it) } }
        monitors.clear()
        runCatching { queue.execute { closeEngine(); WatchStatusSync.publish(this, "watch_disconnected") }; queue.shutdown() }
        super.onDestroy()
    }

    private fun stopTunnel(id: Int) {
        if (queue.isShutdown) return
        runCatching { queue.execute {
            WatchStartupDiagnostics.mark(this, "stopped")
            closeEngine(); WatchStatusSync.publish(this, "watch_disconnected"); stopForeground(STOP_FOREGROUND_REMOVE)
            if (id > 0) stopSelfResult(id) else stopSelf()
        } }
    }
    private fun closeEngine() {
        trafficProbe?.cancel(); trafficProbe = null
        runCatching { server?.closeService() }; runCatching { server?.close() }; server = null
        runCatching { tun?.close() }; tun = null
    }
    private fun setupLibbox() {
        synchronized(LIBBOX_LOCK) {
            if (libboxReady) return
            val options = io.nekohasekai.libbox.SetupOptions().apply {
                basePath = filesDir.absolutePath; workingPath = (getExternalFilesDir(null) ?: filesDir).absolutePath
                tempPath = cacheDir.absolutePath; fixAndroidStack = true; appVersion = BuildConfig.VERSION_CODE.toString(); appMarketingVersion = BuildConfig.VERSION_NAME; logMaxLines = 500
            }
            Libbox.setup(options); libboxReady = true
        }
    }
    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(text))
    }
    private fun notification(text: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "NOVA VPN на часах", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, WatchVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("NOVA VPN · Часы").setContentText(text).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Отключить", stop).build()).build()
    }
    @android.annotation.TargetApi(29)
    private fun enterForeground(value: Notification) {
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, value, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, value, 0)
        else startForeground(NOTIFICATION_ID, value)
    }

    override fun localDNSTransport(): io.nekohasekai.libbox.LocalDNSTransport = AndroidLocalDnsTransport(::selectUnderlay)
    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun autoDetectInterfaceControl(fd: Int) {
        check(protect(fd))
        selectUnderlay()?.let { network ->
            ParcelFileDescriptor.fromFd(fd).use { network.bindSocket(it.fileDescriptor) }
        }
    }
    override fun useProcFS() = false
    override fun openTun(options: TunOptions): Int {
        WatchStartupDiagnostics.mark(this, "tun-open")
        check(VpnService.prepare(this) == null) { "Android VPN permission is missing" }
        val builder = Builder().setSession("NOVA VPN · Часы").setMtu(1500)
        builder.addDisallowedApplication(packageName)
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)
        val has4Address = addAddresses(builder, options.inet4Address)
        val has6Address = addAddresses(builder, options.inet6Address)
        if (options.autoRoute) {
            val dns = options.dnsServerAddress
            if (options.dnsMode.value != Libbox.DNSModeDisabled) while (dns.hasNext()) builder.addDnsServer(dns.next())
            var has4 = false; var has6 = false
            if (Build.VERSION.SDK_INT >= 33) {
                has4 = addRoutes(builder, options.inet4RouteAddress); has6 = addRoutes(builder, options.inet6RouteAddress)
                addExcluded(builder, options.inet4RouteExcludeAddress); addExcluded(builder, options.inet6RouteExcludeAddress)
            } else { has4 = addRoutes(builder, options.inet4RouteRange); has6 = addRoutes(builder, options.inet6RouteRange) }
            if (shouldAddFallbackDefaultRoute(has4, has4Address)) builder.addRoute("0.0.0.0", 0)
            if (shouldAddFallbackDefaultRoute(has6, has6Address)) builder.addRoute("::", 0)
        }
        tun?.close(); tun = builder.establish() ?: error("Android не создал TUN-интерфейс часов")
        return tun!!.fd
    }
    private fun addAddresses(b: Builder, it: RoutePrefixIterator): Boolean {
        var added = false
        while (it.hasNext()) it.next().let { prefix ->
            b.addAddress(prefix.address(), prefix.prefix())
            added = true
        }
        return added
    }
    private fun addRoutes(b: Builder, it: RoutePrefixIterator): Boolean { var added = false; while (it.hasNext()) it.next().let { p -> b.addRoute(p.address(), p.prefix()); added = true }; return added }
    @android.annotation.TargetApi(33)
    private fun addExcluded(b: Builder, it: RoutePrefixIterator) { while (it.hasNext()) it.next().let { p -> b.excludeRoute(android.net.IpPrefix(InetAddress.getByName(p.address()), p.prefix())) } }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String?,
        sourcePort: Int,
        destinationAddress: String?,
        destinationPort: Int
    ): ConnectionOwner = ConnectionOwner().apply {
        // libbox expects a non-null owner; Android can legitimately fail to resolve a UID.
        userId = -1
        userName = ""
        processPath = ""
        setAndroidPackageNames(StringIteratorImpl(emptyList()))
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivity.getNetworkCapabilities(network)?.let { availableUnderlays[network] = it }
                publishDefaultInterface(listener, selectUnderlay())
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                availableUnderlays[network] = caps
                publishDefaultInterface(listener, selectUnderlay())
            }
            override fun onLost(network: Network) {
                availableUnderlays.remove(network)
                publishDefaultInterface(listener, selectUnderlay())
            }
        }
        monitors.put(listener, callback)?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivity.registerNetworkCallback(request, callback)
        connectivity.activeNetwork?.let { network ->
            connectivity.getNetworkCapabilities(network)?.let { availableUnderlays[network] = it }
        }
        publishDefaultInterface(listener, selectUnderlay())
    }

    private fun selectUnderlay(): Network? = availableUnderlays.entries
        .filter { it.value.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            it.value.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) }
        .maxByOrNull { (_, capabilities) ->
            val validated = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 100 else 0
            validated + when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 3
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 2
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 1
                else -> 0
            }
        }?.key

    private fun publishDefaultInterface(listener: InterfaceUpdateListener, network: Network?) {
        val name = network?.let { connectivity.getLinkProperties(it)?.interfaceName }.orEmpty()
        val index = runCatching { JNetworkInterface.getByName(name)?.index ?: 0 }.getOrDefault(0)
        runCatching { setUnderlyingNetworks(network?.let { arrayOf(it) }) }
        if (name.isNotBlank() && index > 0) listener.updateDefaultInterface(name, index, false, false)
        else listener.updateDefaultInterface("", 0, false, false)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        monitors.remove(listener)?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        if (monitors.isEmpty()) {
            availableUnderlays.clear()
            runCatching { setUnderlyingNetworks(null) }
        }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val list = runCatching {
            Collections.list(JNetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .map { networkInterface ->
                    val addresses = networkInterface.interfaceAddresses.mapNotNull { address ->
                        formatLibboxInterfaceAddress(address.address, address.networkPrefixLength.toInt())
                    }
                    BoxNetworkInterface().apply {
                        index = networkInterface.index
                        mtu = 1500
                        name = networkInterface.name
                        this.addresses = StringIteratorImpl(addresses)
                        flags = 0
                        type = 0
                        dnsServer = StringIteratorImpl(emptyList())
                        gateway = StringIteratorImpl(emptyList())
                        metered = false
                    }
                }
        }.getOrDefault(emptyList())
        return NetworkIteratorImpl(list)
    }
    override fun underNetworkExtension() = false
    override fun includeAllNetworks() = false
    override fun readWIFIState(): WIFIState? = null
    override fun clearDNSCache() = Unit
    override fun sendNotification(notification: BoxNotification) = Unit
    override fun cancelNotification(identifier: String, typeID: Int) = Unit
    override fun startNeighborMonitor(listener: NeighborUpdateListener) = throw UnsupportedOperationException()
    override fun closeNeighborMonitor(listener: NeighborUpdateListener) = Unit
    override fun registerMyInterface(name: String) = Unit
    override fun usePlatformShell() = false
    override fun checkPlatformShell() = Unit
    override fun openShellSession(user: PlatformUser, command: String, environ: StringIterator, term: String, rows: Int, cols: Int): ShellSession = throw UnsupportedOperationException()
    override fun lookupUser(username: String): PlatformUser = throw UnsupportedOperationException()
    override fun lookupSFTPServer() = ""
    override fun readSystemSSHHostKey() = ""
    override fun tailscaleHostname() = ""
    override fun usePlatformBridge() = false
    override fun createBridge(options: BridgeOptions): BridgeSession = throw UnsupportedOperationException()
    override fun serviceStop() = stopTunnel(startIdLatest)
    override fun serviceReload() = Unit
    override fun getSystemProxyStatus() = SystemProxyStatus().apply { available=false; enabled=false }
    override fun setSystemProxyEnabled(enabled: Boolean) = Unit
    override fun triggerNativeCrash() = throw UnsupportedOperationException()
    // Native messages may contain profile identifiers. Never turn one failed request into a global VPN error.
    override fun writeDebugMessage(message: String?) = Unit
    override fun connectSSHAgent() = -1

    private class StringIteratorImpl(private val values: List<String>) : StringIterator {
        private var i=0; override fun len()=values.size; override fun hasNext()=i<values.size; override fun next()=values[i++]
    }
    private class NetworkIteratorImpl(private val values: List<BoxNetworkInterface>) : NetworkInterfaceIterator {
        private var i=0; override fun hasNext()=i<values.size; override fun next()=values[i++]
    }
    companion object {
        const val ACTION_START="com.nova.vpn.wear.START"; const val ACTION_STOP="com.nova.vpn.wear.STOP"; const val EXTRA_CONFIG="config"
        private const val TAG="NovaWatchVpn"; private const val CHANNEL="nova-watch-vpn"; private const val NOTIFICATION_ID=702
        private val LIBBOX_LOCK=Any(); @Volatile private var libboxReady=false
    }
}

internal fun formatLibboxInterfaceAddress(address: InetAddress, prefixLength: Int): String? {
    val maxPrefixLength = if (address.address.size == 4) 32 else 128
    if (prefixLength !in 0..maxPrefixLength) return null
    val hostAddress = address.hostAddress?.substringBefore('%') ?: return null
    return "$hostAddress/$prefixLength"
}

internal fun shouldAddFallbackDefaultRoute(routesAdded: Boolean, hasTunnelAddress: Boolean): Boolean =
    !routesAdded && hasTunnelAddress
