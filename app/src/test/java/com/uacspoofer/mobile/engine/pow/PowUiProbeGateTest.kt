package com.uacspoofer.mobile.engine.pow

import org.junit.Assert.assertTrue
import org.junit.Test

class PowUiProbeGateTest {
    @Test
    fun defersUiPingWhileEngineProbeRuns() {
        PowUiProbeGate.beginEngineProbe()
        assertTrue(PowUiProbeGate.shouldDeferUiPing())
        PowUiProbeGate.endEngineProbe()
    }

    @Test
    fun defersUiPingWhileRetuning() {
        PowUiProbeGate.setRetuning(true)
        assertTrue(PowUiProbeGate.shouldDeferUiPing())
        PowUiProbeGate.setRetuning(false)
    }
}
