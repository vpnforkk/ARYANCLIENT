package com.uacspoofer.mobile

import android.app.Application
import com.uacspoofer.mobile.engine.EngineModeStore
import com.uacspoofer.mobile.logging.CrashReportStore

class UacApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReportStore.install(this)
        EngineModeStore.get(this)
    }
}
