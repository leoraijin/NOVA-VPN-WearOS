package com.nova.vpn.wear

import android.net.Uri
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

private const val STATUS_PATH = "/nova/status"
private const val TOGGLE_PATH = "/nova/toggle"
private const val STATE_KEY = "state"
private const val WATCH_STATUS_PATH = "/nova/watch-status"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearVpnRemote() }
    }
}

@Composable
private fun WearVpnRemote() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dataClient = remember(context) { Wearable.getDataClient(context) }
    val nodeClient = remember(context) { Wearable.getNodeClient(context) }
    val messageClient = remember(context) { Wearable.getMessageClient(context) }
    var connectionState by remember { mutableStateOf("disconnected") }
    var commandState by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val watchState by WatchRuntime.state.collectAsStateWithLifecycle()
    var hasLocalProfile by remember { mutableStateOf(runCatching { WearProfileVault(context).load() != null }.getOrDefault(false)) }
    val watchStatusUri = remember { Uri.parse("wear://*$WATCH_STATUS_PATH") }
    var pendingLocalConnect by remember { mutableStateOf(false) }
    var watchMessage by remember { mutableStateOf<String?>(null) }
    fun startLocalVpn() {
        val config = runCatching { WearProfileVault(context).load() }.getOrNull()
        if (config.isNullOrBlank()) { watchMessage = "Сначала передайте профиль с телефона."; return }
        val intent = Intent(context, WatchVpnService::class.java).setAction(WatchVpnService.ACTION_START)
            .putExtra(WatchVpnService.EXTRA_CONFIG, config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        watchMessage = null
    }
    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (pendingLocalConnect && result.resultCode == Activity.RESULT_OK) startLocalVpn()
        else if (pendingLocalConnect) watchMessage = "Разрешение VPN на часах не выдано."
        pendingLocalConnect = false
    }
    fun toggleLocalVpn() {
        if (WatchRuntime.isActive(watchState)) {
            context.startService(Intent(context, WatchVpnService::class.java).setAction(WatchVpnService.ACTION_STOP))
            return
        }
        val consent = VpnService.prepare(context)
        if (consent == null) startLocalVpn() else { pendingLocalConnect = true; vpnConsent.launch(consent) }
    }
    val statusUri = remember { Uri.parse("wear://*$STATUS_PATH") }

    fun acceptStatus(item: DataItem) {
        val values = runCatching { DataMapItem.fromDataItem(item).dataMap }.getOrNull() ?: return
        connectionState = values.getString(STATE_KEY) ?: "disconnected"
        if (connectionState != "error" && connectionState != "permission_required" && connectionState != "profile_required") {
            commandState = null
        }
    }

    DisposableEffect(dataClient, statusUri, lifecycleOwner) {
        val listener = DataClient.OnDataChangedListener { events ->
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == STATUS_PATH) {
                    acceptStatus(event.dataItem)
                }
            }
        }
        var listening = false
        fun startListening() {
            if (listening) return
            listening = true
            dataClient.addListener(listener, statusUri, DataClient.FILTER_PREFIX)
            dataClient.getDataItems(statusUri, DataClient.FILTER_PREFIX)
                .addOnSuccessListener { items ->
                    try {
                        if (listening) items.forEach(::acceptStatus)
                    } finally {
                        items.release()
                    }
                }
                .addOnFailureListener {
                    if (listening) commandState = "Нет связи с NOVA на телефоне"
                }
        }
        fun stopListening() {
            if (!listening) return
            listening = false
            dataClient.removeListener(listener)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startListening()
                Lifecycle.Event.ON_STOP -> stopListening()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startListening()
        }
        onDispose {
            stopListening()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(dataClient, watchStatusUri, lifecycleOwner) {
        val listener = DataClient.OnDataChangedListener { events ->
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WATCH_STATUS_PATH) {
                    runCatching { DataMapItem.fromDataItem(event.dataItem).dataMap.getString(STATE_KEY) }
                        .getOrNull()?.let { state ->
                            if (state == "profile_ready") hasLocalProfile = true
                            if (state == "watch_connected" || state == "watch_disconnected") watchMessage = null
                        }
                }
            }
        }
        var listening = false
        fun startListening() {
            if (listening) return
            listening = true
            dataClient.addListener(listener, watchStatusUri, DataClient.FILTER_PREFIX)
            dataClient.getDataItems(watchStatusUri, DataClient.FILTER_PREFIX).addOnSuccessListener { items ->
                try { if (listening) items.forEach { item ->
                    runCatching { DataMapItem.fromDataItem(item).dataMap.getString(STATE_KEY) }.getOrNull()?.let { state ->
                        if (state == "profile_ready") hasLocalProfile = true
                    }
                } } finally { items.release() }
            }
        }
        fun stopListening() { if (listening) { listening = false; dataClient.removeListener(listener) } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_START -> startListening(); Lifecycle.Event.ON_STOP -> stopListening(); else -> Unit }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startListening()
        onDispose { stopListening(); lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun sendToggle() {
        if (sending) return
        sending = true
        commandState = null
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    commandState = "Телефон недоступен. Проверьте подключение часов."
                    sending = false
                    return@addOnSuccessListener
                }
                var remaining = nodes.size
                var delivered = false
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, TOGGLE_PATH, byteArrayOf())
                        .addOnSuccessListener {
                            delivered = true
                            remaining -= 1
                            if (remaining == 0) {
                                commandState = "Команда отправлена"
                                sending = false
                            }
                        }
                        .addOnFailureListener {
                            remaining -= 1
                            if (remaining == 0) {
                                commandState = if (delivered) "Команда отправлена" else "Не удалось связаться с телефоном"
                                sending = false
                            }
                        }
                }
            }
            .addOnFailureListener {
                commandState = "Не удалось найти подключённый телефон"
                sending = false
            }
    }

    val statusLabel = when (connectionState) {
        "connected" -> "VPN включён"
        "connecting", "disconnecting" -> "Подключение…"
        "permission_required" -> "Разрешите VPN на телефоне"
        "profile_required" -> "Добавьте профиль на телефоне"
        "error" -> "Ошибка VPN на телефоне"
        else -> "VPN выключен"
    }
    val buttonLabel = when (connectionState) {
        "connected" -> "Отключить"
        "connecting" -> "Отменить"
        else -> "Подключить"
    }
    val localStatusLabel = when (watchState) {
        "watch_connected" -> "Туннель включён · проверка…"
        "watch_verified" -> "HTTPS через VPN подтверждён"
        "watch_unconfirmed" -> "Туннель активен · проверка не подтверждена"
        "watch_connecting" -> "Подключение на часах…"
        "watch_error" -> "Ошибка VPN на часах"
        else -> if (hasLocalProfile) "Профиль сохранён на часах" else "Профиль ещё не передан"
    }

    MaterialTheme {
        AppScaffold {
            val scrollState = rememberScrollState()
            ScreenScaffold(scrollState = scrollState) { contentPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(contentPadding)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("NOVA VPN", fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text("Часы · $localStatusLabel", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = ::toggleLocalVpn,
                        enabled = watchState != "watch_connecting",
                        label = { Text(if (WatchRuntime.isActive(watchState)) "Отключить VPN часов" else "Подключить VPN часов") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    if (watchState == "watch_unconfirmed") {
                        Text("Контрольный адрес не ответил. Это не означает, что VPN остановился; проверьте открытие сайтов.", fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    Text("Связь с телефоном · $statusLabel", fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = ::sendToggle, enabled = !sending, label = { Text(if (sending) "Отправка…" else buttonLabel + " на телефоне") }, modifier = Modifier.fillMaxWidth())
                    val explanation = when (connectionState) {
                        "permission_required" -> "Откройте NOVA на телефоне, нажмите «Подключить» и подтвердите запрос VPN. Затем повторите здесь."
                        "profile_required" -> "Сначала импортируйте VPN-профиль в приложении на телефоне."
                        else -> commandState ?: watchMessage
                    }
                    if (!explanation.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(explanation, fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
