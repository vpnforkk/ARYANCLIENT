package com.uacspoofer.mobile.engine.pow

import com.uacspoofer.mobile.logging.AppLogRepository
import com.uacspoofer.mobile.logging.LogSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Page Turbo — warms mapdns after connect. Optional keepalive probes
 * keep the first real navigation primed. Isolated to PoW SOCKS.
 */
internal object PowPageTurbo {
    @Volatile private var job: Job? = null

    fun kick(scope: CoroutineScope, socksPort: Int, keepWarm: Boolean = true) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            delay(900)
            runCatching {
                val ok = PowPathProbe.warmMapDns(socksPort)
                if (ok) AppLogRepository.info(LogSource.POW, "Page Turbo mapdns warm")
                else AppLogRepository.debug(LogSource.POW, "Page Turbo warm miss")
            }
            if (keepWarm) {
                repeat(2) {
                    delay(1500)
                    runCatching { PowPathProbe.measureMs(socksPort, timeoutMs = 1_500) }
                }
                AppLogRepository.debug(LogSource.POW, "Page Turbo primed")
            }
        }
    }

    fun cancel() {
        job?.cancel(); job = null
    }
}
