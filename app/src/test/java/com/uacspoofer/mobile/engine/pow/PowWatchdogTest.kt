package com.uacspoofer.mobile.engine.pow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowWatchdogTest {
    @Test
    fun firstTickOnlyBaselines() {
        val verdict = PowWatchdog.judge(
            processesAlive = true,
            txBytes = 10L,
            rxBytes = 20L,
            previousTx = -1L,
            previousRx = -1L,
            screenOn = true,
            idleStrikes = 0,
        )
        assertEquals(PowWatchdog.Verdict.Healthy, verdict)
    }

    @Test
    fun downstreamBytesAreAlwaysHealthy() {
        val verdict = PowWatchdog.judge(
            processesAlive = true,
            txBytes = 10L,
            rxBytes = 40L,
            previousTx = 10L,
            previousRx = 20L,
            screenOn = true,
            idleStrikes = 1,
        )
        assertEquals(PowWatchdog.Verdict.Healthy, verdict)
    }

    @Test
    fun screenOffIdleIsNotDead() {
        val verdict = PowWatchdog.judge(
            processesAlive = true,
            txBytes = 10L,
            rxBytes = 20L,
            previousTx = 10L,
            previousRx = 20L,
            screenOn = false,
            idleStrikes = 0,
        )
        assertEquals(PowWatchdog.Verdict.Healthy, verdict)
    }

    @Test
    fun processDeathIsImmediate() {
        val verdict = PowWatchdog.judge(
            processesAlive = false,
            txBytes = 10L,
            rxBytes = 20L,
            previousTx = 10L,
            previousRx = 20L,
            screenOn = true,
            idleStrikes = 0,
        )
        assertTrue(verdict is PowWatchdog.Verdict.Dead)
        assertEquals("engine process exited", (verdict as PowWatchdog.Verdict.Dead).reason)
    }

    @Test
    fun screenOnIdleNeedsTwoStrikes() {
        val first = PowWatchdog.judge(
            processesAlive = true,
            txBytes = 10L,
            rxBytes = 20L,
            previousTx = 10L,
            previousRx = 20L,
            screenOn = true,
            idleStrikes = 0,
        )
        assertTrue(first is PowWatchdog.Verdict.Strike)
        assertEquals(1, (first as PowWatchdog.Verdict.Strike).nextStrikes)

        val second = PowWatchdog.judge(
            processesAlive = true,
            txBytes = 10L,
            rxBytes = 20L,
            previousTx = 10L,
            previousRx = 20L,
            screenOn = true,
            idleStrikes = 1,
        )
        assertTrue(second is PowWatchdog.Verdict.Dead)
        assertEquals("the tunnel stopped passing traffic", (second as PowWatchdog.Verdict.Dead).reason)
    }

    @Test
    fun txWithoutRxIsNotAPass() {
        val first = PowWatchdog.judge(
            processesAlive = true,
            txBytes = 80L,
            rxBytes = 20L,
            previousTx = 10L,
            previousRx = 20L,
            screenOn = true,
            idleStrikes = 0,
        )
        assertTrue(first is PowWatchdog.Verdict.Strike)
        assertEquals(
            "the tunnel accepted traffic but answered nothing",
            (first as PowWatchdog.Verdict.Strike).reason,
        )
    }
}
