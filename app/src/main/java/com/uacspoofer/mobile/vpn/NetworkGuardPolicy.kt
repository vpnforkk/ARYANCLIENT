package com.uacspoofer.mobile.vpn

import com.uacspoofer.mobile.core.ConnectionState

object NetworkGuardPolicy {
    fun holdOnFailure(killSwitch: Boolean, proxyMode: Boolean): Boolean =
        killSwitch && !proxyMode

    fun stayAliveOnSwipe(state: ConnectionState): Boolean =
        state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING

    fun canAutoStart(
        autoConnect: Boolean,
        state: ConnectionState,
        userStopped: Boolean,
        hasConsent: Boolean,
    ): Boolean {
        if (!autoConnect || userStopped || !hasConsent) return false
        return state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR
    }
}
