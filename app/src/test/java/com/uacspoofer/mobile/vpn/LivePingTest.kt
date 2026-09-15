package com.uacspoofer.mobile.vpn

import com.uacspoofer.mobile.engine.EngineMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePingTest {
    @Test
    fun homeChipPrefersHttpsGoogleGenerate204() {
        assertEquals("https://www.google.com/generate_204", LivePing.CHIP_URLS[0])
        assertTrue(LivePing.CHIP_URLS.all { it.startsWith("https://") })
        assertTrue(LivePing.CHIP_URLS.any { it.contains("generate_204") })
    }

    @Test
    fun triesGenerate204OnPort80WithHostnameInsideSocks() {
        assertEquals("cp.cloudflare.com", LivePing.TARGETS[0].host)
        assertEquals("/generate_204", LivePing.TARGETS[0].path)
        assertEquals(80, LivePing.TARGETS[0].port)
        assertEquals("www.gstatic.com", LivePing.TARGETS[1].host)
        assertEquals(80, LivePing.TARGETS[1].port)
        assertTrue(LivePing.TARGETS.any { it.host == "www.google.com" })
        assertTrue(LivePing.TARGETS.any { it.host == "1.1.1.1" })
        assertFalse(LivePing.TARGETS.any { it.port == 443 })
    }

    @Test
    fun domainConnectUsesSocksAtyp3SoDnsStaysInTheTunnel() {
        val request = LivePing.connectRequest("www.google.com", 80)
        assertEquals(0x05.toByte(), request[0])
        assertEquals(0x01.toByte(), request[1])
        assertEquals(0x03.toByte(), request[3])
        assertEquals("www.google.com".length.toByte(), request[4])
        assertEquals(0.toByte(), request[request.lastIndex - 1])
        assertEquals(80.toByte(), request[request.lastIndex])
    }

    @Test
    fun ipv4FallbackUsesSocksAtyp1() {
        assertTrue(LivePing.isIpv4Literal("1.1.1.1"))
        assertFalse(LivePing.isIpv4Literal("www.google.com"))
        val request = LivePing.connectRequest("1.1.1.1", 80)
        assertArrayEquals(
            byteArrayOf(0x05, 0x01, 0x00, 0x01, 1, 1, 1, 1, 0, 80),
            request,
        )
    }

    @Test
    fun acceptsHttpSuccessStatusWindow() {
        assertEquals(204, LivePing.statusLineCode("HTTP/1.1 204 No Content"))
        assertEquals(200, LivePing.statusLineCode("HTTP/1.1 200 OK"))
        assertEquals(-1, LivePing.statusLineCode("HTTP/1.1"))
    }

    @Test
    fun matchesForegroundCadenceAndTimeouts() {
        assertEquals(5_000L, LivePing.INTERVAL_MS)
        assertEquals(1_200L, LivePing.SETTLE_MS)
        assertEquals(400L, LivePing.QUIET_MS)
        assertEquals(5_000, LivePing.TIMEOUT_MS)
        assertEquals(12_000, LivePing.timeoutMs(EngineMode.TOR_WEBTUNNEL))
        assertEquals(5_000, LivePing.timeoutMs(EngineMode.UAC_POW))
    }
}
