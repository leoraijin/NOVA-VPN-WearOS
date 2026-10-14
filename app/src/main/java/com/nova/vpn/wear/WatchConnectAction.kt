package com.nova.vpn.wear

internal object WatchConnectAction {
    fun perform(active: Boolean, load: () -> String?, stop: () -> Unit, start: (String) -> Unit): String? = try {
        if (active) { stop(); null }
        else {
            val config = load()
            if (config.isNullOrBlank()) "Профиль ещё не передан. На телефоне откройте Серверы и нажмите значок отправки на часы."
            else { start(config); null }
        }
    } catch (error: Exception) {
        when (error.javaClass.simpleName) {
            "ActivityNotFoundException", "UnsupportedOperationException" -> "Система часов не открыла разрешение VPN. Можно использовать нижнюю кнопку для VPN на телефоне."
            "SecurityException" -> "Система не разрешила запуск VPN. Проверьте разрешение на часах."
            else -> "Не удалось выполнить действие (${error.javaClass.simpleName}). Профиль не удалён."
        }
    }
}
