package com.uacspoofer.mobile.engine.pow

/**
 * Connected-session liveness for UAC PoW.
 * Local process flags plus byte movement. No HTTP.
 * A dark idle phone is not a dead tunnel.
 */
internal object PowWatchdog {
    const val INTERVAL_MS = 30_000L
    const val SETTLE_MS = 8_000L
    const val STRIKES_BEFORE_RECONNECT = 2

    sealed class Verdict {
        data object Healthy : Verdict()
        data class Strike(val nextStrikes: Int, val reason: String) : Verdict()
        data class Dead(val reason: String) : Verdict()
    }

    fun judge(
        processesAlive: Boolean,
        txBytes: Long,
        rxBytes: Long,
        previousTx: Long,
        previousRx: Long,
        screenOn: Boolean,
        idleStrikes: Int,
    ): Verdict {
        if (!processesAlive) return Verdict.Dead("engine process exited")
        if (previousTx < 0L || previousRx < 0L) return Verdict.Healthy
        val downstreamMoved = rxBytes != previousRx
        val upstreamMoved = txBytes != previousTx
        if (downstreamMoved) return Verdict.Healthy
        if (!screenOn) return Verdict.Healthy
        val next = idleStrikes + 1
        val reason = if (upstreamMoved) {
            "the tunnel accepted traffic but answered nothing"
        } else {
            "the tunnel stopped passing traffic"
        }
        return if (next < STRIKES_BEFORE_RECONNECT) {
            Verdict.Strike(next, reason)
        } else {
            Verdict.Dead(reason)
        }
    }
}
