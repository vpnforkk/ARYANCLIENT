package com.uacspoofer.mobile.engine.pow

/**
 * After UAC PoW is up, a lost carrier or dropped internet is not a live retune.
 * The path is treated as dead only by the watchdog; reconnect waits on backoff
 * until ConnectivityManager says a network is available again.
 */
internal object PowNetworkRestore {
    val BACKOFF_MS = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L, 120_000L)

    fun delayMs(attemptIndex: Int): Long {
        val index = attemptIndex.coerceAtLeast(0).coerceAtMost(BACKOFF_MS.lastIndex)
        return BACKOFF_MS[index]
    }

    fun shouldActOnLost(): Boolean = false

    fun shouldRetryOnAvailable(
        waiting: Boolean,
        stopRequested: Boolean,
        pathReady: Boolean,
    ): Boolean = waiting && !stopRequested && !pathReady

    fun statusDetail(reason: String): String {
        val clean = reason.trim().ifBlank { "the connection dropped" }
        return "Reconnecting after $clean…"
    }
}
