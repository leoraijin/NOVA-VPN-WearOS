package com.nova.vpn.wear

import kotlinx.coroutines.CancellationException

internal enum class WatchActionStage(val label: String) {
    PROFILE("чтение профиля"), PERMISSION("системное разрешение VPN"),
    PERMISSION_UI("окно разрешения VPN"), START("запуск службы VPN"), STOP("остановка VPN")
}

internal class WatchActionFailure(val stage: WatchActionStage, cause: Exception) : RuntimeException(null, cause)

internal object WatchConnectAction {
    fun <T> at(stage: WatchActionStage, action: () -> T): T = try { action() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { throw WatchActionFailure(stage, error) }

    fun perform(active: Boolean, load: () -> String?, stop: () -> Unit, start: (String) -> Unit): String? = try {
        if (active) { stop(); null }
        else {
            val config = load()
            if (config.isNullOrBlank()) "Профиль ещё не передан. На телефоне откройте Серверы и нажмите значок отправки на часы."
            else { start(config); null }
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        val stage = (error as? WatchActionFailure)?.stage
        val actual = if (error is WatchActionFailure) error.cause ?: error else error
        if (stage == WatchActionStage.PERMISSION && actual is NullPointerException) {
            "Системный запрос разрешения VPN завершился ошибкой. Возможно, VPN API недоступен в прошивке часов. Этап: ${stage.label} (NullPointerException). Профиль сохранён. Нижняя кнопка управляет только VPN телефона, а не трафиком часов."
        } else when (actual.javaClass.simpleName) {
            "ActivityNotFoundException", "UnsupportedOperationException" -> "Система часов не открыла разрешение VPN. Можно использовать нижнюю кнопку для VPN на телефоне."
            "SecurityException" -> "Система не разрешила запуск VPN. Проверьте разрешение на часах."
            else -> "Не удалось выполнить действие (${actual.javaClass.simpleName}). Этап: ${stage?.label ?: "действие VPN"}. Профиль не удалён."
        }
    }
}
