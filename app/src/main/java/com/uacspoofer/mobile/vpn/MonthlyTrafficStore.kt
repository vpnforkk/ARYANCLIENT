package com.uacspoofer.mobile.vpn

import android.content.Context
import com.uacspoofer.mobile.engine.EngineModeStore
import com.uacspoofer.mobile.engine.pow.PowEngineStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MonthlyTrafficStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext
    private val mutableUsage = MutableStateFlow(read())
    val usage: StateFlow<MonthlyTrafficSnapshot> = mutableUsage.asStateFlow()

    @Volatile private var dirty = false
    private var lastPersistMs = 0L

    fun snapshot(): MonthlyTrafficSnapshot = rollover(mutableUsage.value)

    @Synchronized
    fun add(uploadDelta: Long, downloadDelta: Long, nowMs: Long = System.currentTimeMillis()) {
        val next = MonthlyTrafficLedger.apply(
            current = rollover(mutableUsage.value, nowMs),
            nowMonth = MonthlyTrafficLedger.monthKey(nowMs),
            uploadDelta = uploadDelta,
            downloadDelta = downloadDelta,
        )
        if (deferWrites()) {
            mutableUsage.value = next
            dirty = true
            if (lastPersistMs == 0L || nowMs - lastPersistMs >= FLUSH_MS) {
                persist(next, nowMs)
            }
        } else {
            persist(next, nowMs)
        }
    }

    @Synchronized
    fun flush() {
        if (!dirty) return
        persist(mutableUsage.value, System.currentTimeMillis())
    }

    @Synchronized
    private fun rollover(
        current: MonthlyTrafficSnapshot,
        nowMs: Long = System.currentTimeMillis(),
    ): MonthlyTrafficSnapshot {
        val month = MonthlyTrafficLedger.monthKey(nowMs)
        if (current.monthKey == month) return current
        val fresh = MonthlyTrafficSnapshot(month)
        persist(fresh, nowMs)
        return fresh
    }

    private fun read(): MonthlyTrafficSnapshot {
        val month = prefs.getString(KEY_MONTH, null).orEmpty()
        if (month.isBlank()) {
            return MonthlyTrafficSnapshot(MonthlyTrafficLedger.monthKey(System.currentTimeMillis()))
        }
        return MonthlyTrafficSnapshot(
            monthKey = month,
            uploadBytes = prefs.getLong(KEY_UPLOAD, 0L).coerceAtLeast(0L),
            downloadBytes = prefs.getLong(KEY_DOWNLOAD, 0L).coerceAtLeast(0L),
        )
    }

    private fun persist(value: MonthlyTrafficSnapshot, nowMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_MONTH, value.monthKey)
            .putLong(KEY_UPLOAD, value.uploadBytes)
            .putLong(KEY_DOWNLOAD, value.downloadBytes)
            .apply()
        mutableUsage.value = value
        dirty = false
        lastPersistMs = nowMs
    }

    private fun deferWrites(): Boolean {
        if (!EngineModeStore.get(appContext).snapshot().isPow) return false
        return PowEngineStore.get(appContext).snapshot().optimizedMode
    }

    companion object {
        private const val PREFS = "monthly_traffic_v1"
        private const val KEY_MONTH = "month"
        private const val KEY_UPLOAD = "upload"
        private const val KEY_DOWNLOAD = "download"
        private const val FLUSH_MS = 60_000L

        @Volatile private var instance: MonthlyTrafficStore? = null

        fun get(context: Context): MonthlyTrafficStore = instance ?: synchronized(this) {
            instance ?: MonthlyTrafficStore(context.applicationContext).also { store ->
                instance = store
                TrafficStatsStore.monthlySink = { up, down -> store.add(up, down) }
            }
        }
    }
}
