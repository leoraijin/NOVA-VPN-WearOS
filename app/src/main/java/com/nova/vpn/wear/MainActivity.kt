package com.nova.vpn.wear

import android.net.Uri
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.google.android.gms.wearable.MessageClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private const val STATUS_PATH = "/nova/status"
private const val TOGGLE_PATH = "/nova/toggle"
private const val STATE_KEY = "state"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearVpnRemote() }
        if (BuildConfig.DEBUG && intent?.action == ACTION_DEBUG_COMPANION_PROBE) {
            lifecycleScope.launch {
                try {
                    val connectivity = getSystemService(ConnectivityManager::class.java)
                    val outcome = CompanionGatewayProbe.check(connectivity)
                    android.util.Log.i("NOVA-GATEWAY-CHECK", outcome.name)
                } catch (_: Exception) {
                    android.util.Log.i("NOVA-GATEWAY-CHECK", "HTTPS_FAILED")
                } finally {
                    finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_DEBUG_COMPANION_PROBE = "com.nova.vpn.android.DEBUG_COMPANION_PROBE"
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
    val scope = rememberCoroutineScope()
    var commandTimeout by remember { mutableStateOf<Job?>(null) }
    var pendingCommandId by remember { mutableStateOf<String?>(null) }
    var commandNode by remember { mutableStateOf<String?>(null) }
    val statusUri = remember { Uri.parse("wear://*$STATUS_PATH") }

    fun acceptStatus(item: DataItem) {
        val values = runCatching { DataMapItem.fromDataItem(item).dataMap }.getOrNull() ?: return
        connectionState = values.getString(STATE_KEY) ?: "disconnected"
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


    fun finishCommand(text: String) {
        commandState = text
        sending = false
        pendingCommandId = null
        commandTimeout?.cancel()
    }

    DisposableEffect(messageClient, lifecycleOwner) {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == "/nova/command-result" && event.sourceNodeId == commandNode) {
                WatchCommandReply.read(event.data, event.sourceNodeId, commandNode, pendingCommandId)?.let { state ->
                        connectionState = state
                        finishCommand(when (state) {
                            "connecting" -> "Телефон принял команду подключения."
                            "disconnecting" -> "Телефон принял команду отключения."
                            "connected" -> "VPN на телефоне включён."
                            "permission_required" -> "Откройте NOVA на телефоне и разрешите VPN."
                            "profile_required" -> "Добавьте профиль в NOVA на телефоне."
                            "foreground_required" -> "Android запретил запуск из фона. Откройте NOVA на телефоне и повторите."
                            else -> "Телефон не смог выполнить команду. Проверьте NOVA на телефоне."
                        })
                }
            }
        }
        var listening = false
        fun start() { if (!listening) { listening = true; messageClient.addListener(listener).addOnFailureListener { finishCommand("Нет связи с приложением телефона.") } } }
        fun stop() { if (listening) { listening = false; messageClient.removeListener(listener) } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_START -> start(); Lifecycle.Event.ON_STOP -> stop(); else -> Unit }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { stop(); commandTimeout?.cancel(); lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun sendToggle() {
        if (sending) return
        sending = true
        val id = UUID.randomUUID().toString()
        pendingCommandId = id
        commandState = "Связываюсь с NOVA на телефоне…"
        commandTimeout?.cancel()
        commandTimeout = scope.launch {
            delay(12000)
            if (pendingCommandId == id) finishCommand("Телефон не ответил. Обновите NOVA на обоих устройствах, откройте её на телефоне и проверьте сопряжение.")
        }
        runCatching {
            nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                if (pendingCommandId != id) return@addOnSuccessListener
                val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                if (node == null) { finishCommand("Телефон недоступен. Проверьте сопряжение часов."); return@addOnSuccessListener }
                commandNode = node.id
                runCatching {
                    messageClient.sendMessage(node.id, TOGGLE_PATH, id.toByteArray(Charsets.UTF_8))
                        .addOnSuccessListener {
                            if (pendingCommandId == id) commandState = "Команда доставлена. Ожидаю ответ приложения телефона…"
                        }
                        .addOnFailureListener { if (pendingCommandId == id) finishCommand("Не удалось доставить команду телефону.") }
                }.onFailure { finishCommand("Не удалось отправить команду телефону.") }
            }.addOnFailureListener { finishCommand("Не удалось найти подключённый телефон.") }
        }.onFailure { finishCommand("Сервис связи с телефоном недоступен.") }
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
                    Text("v${BuildConfig.VERSION_NAME} · ${if (android.os.Process.is64Bit()) "64" else "32"} бит", fontSize = 10.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("VPN через телефон", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Text("Связь с телефоном · $statusLabel", fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    commandState?.let { Text(it, fontSize = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Button(onClick = ::sendToggle, enabled = !sending, label = { Text(if (sending) "Отправка…" else buttonLabel + " на телефоне") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    val explanation = when (connectionState) {
                        "permission_required" -> "Откройте NOVA на телефоне, нажмите «Подключить» и подтвердите запрос VPN. Затем повторите здесь."
                        "profile_required" -> "Сначала импортируйте VPN-профиль в приложении на телефоне."
                        else -> "При связи по Bluetooth часы могут использовать интернет телефона. Эта кнопка управляет VPN на телефоне; маршруты приложений часов она не меняет."
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
