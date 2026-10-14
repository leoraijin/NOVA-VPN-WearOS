package com.nova.vpn.wear

internal object NovaTilePresentation {
    data class Model(val title: String, val detail: String, val active: Boolean)
    fun model() = Model("VPN на телефоне", "Нажмите, чтобы управлять", false)
}
