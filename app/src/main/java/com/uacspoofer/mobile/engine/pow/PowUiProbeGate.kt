package com.uacspoofer.mobile.engine.pow

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lets the home-screen ping defer while PoW quality probes or retunes are using
 * the same SOCKS path. Display-only; does not change engine probe behavior.
 */
internal object PowUiProbeGate {
    const val COOLDOWN_MS = 2_500L

    private val probeInFlight = AtomicBoolean(false)
    private val retuning = AtomicBoolean(false)
    @Volatile private var deferUntilMs = 0L

    fun beginEngineProbe() {
        probeInFlight.set(true)
    }

    fun endEngineProbe() {
        probeInFlight.set(false)
        deferUntilMs = System.currentTimeMillis() + COOLDOWN_MS
    }

    fun setRetuning(active: Boolean) {
        retuning.set(active)
        if (active) {
            deferUntilMs = System.currentTimeMillis() + COOLDOWN_MS
        }
    }

    fun shouldDeferUiPing(): Boolean {
        if (probeInFlight.get() || retuning.get()) return true
        return System.currentTimeMillis() < deferUntilMs
    }
}
