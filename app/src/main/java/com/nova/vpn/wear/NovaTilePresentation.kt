package com.nova.vpn.wear

internal object NovaTilePresentation {
    data class Model(val title: String, val detail: String, val active: Boolean)
    fun model(state: String, hasProfile: Boolean, foregroundService: Boolean): Model = when {
        foregroundService && WatchRuntime.isActive(state) -> Model(
            if (state == "watch_connecting") "Подключение…" else "VPN включён",
            when (state) { "watch_verified" -> "HTTPS проверен"; "watch_unconfirmed" -> "HTTPS не подтверждён"; else -> "Туннель на часах" }, true)
        state == "watch_error" -> Model("Ошибка запуска", "Откройте NOVA", false)
        foregroundService -> Model("Проверьте VPN", "Откройте NOVA", false)
        hasProfile -> Model("VPN выключен", "Профиль готов", false)
        else -> Model("Нет профиля", "Передайте с телефона", false)
    }
}
