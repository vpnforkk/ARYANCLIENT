package com.uacspoofer.mobile.logging

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportStoreTest {
    @Test
    fun formatIncludesCauseAndLogs() {
        val report = CrashReportStore.format(
            thrown = IllegalStateException("boom"),
            threadName = "main",
            processName = "com.uacspoofer.mobile",
            versionName = "2.0.7",
            versionCode = 350,
            logs = "12:00:00.001 ERROR APP failed",
            atMs = 1_726_444_800_000L,
        )
        assertTrue(report.contains("UAC crash report"))
        assertTrue(report.contains("version=2.0.7 (350)"))
        assertTrue(report.contains("thread=main"))
        assertTrue(report.contains("IllegalStateException: boom"))
        assertTrue(report.contains("12:00:00.001 ERROR APP failed"))
    }
}
