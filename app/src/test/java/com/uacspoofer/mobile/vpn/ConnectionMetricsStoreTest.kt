package com.uacspoofer.mobile.vpn

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionMetricsStoreTest {
    @After fun cleanup() = ConnectionMetricsStore.reset()

    @Test
    fun warmupHidesEarlyOutlierAndPublishesMedian() {
        ConnectionMetricsStore.beginLatencyMeasurement()
        ConnectionMetricsStore.addLatencySample(989L)
        assertTrue(ConnectionMetricsStore.metrics.value.isMeasuringLatency)
        assertNull(ConnectionMetricsStore.metrics.value.latencyMs)

        ConnectionMetricsStore.addLatencySample(190L)
        ConnectionMetricsStore.addLatencySample(210L)
        ConnectionMetricsStore.finishLatencyMeasurement()

        assertFalse(ConnectionMetricsStore.metrics.value.isMeasuringLatency)
        assertEquals(210L, ConnectionMetricsStore.metrics.value.latencyMs)
    }

    @Test
    fun liveChipShowsTheLastPingNotTheMedian() {
        ConnectionMetricsStore.publishLivePing(2_980L)
        ConnectionMetricsStore.publishLivePing(118L)
        assertEquals(118L, ConnectionMetricsStore.metrics.value.latencyMs)
        assertEquals(2_980L, ConnectionMetricsStore.metrics.value.maximumLatencyMs)
    }
}
