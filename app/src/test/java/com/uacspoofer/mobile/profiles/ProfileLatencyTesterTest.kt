package com.uacspoofer.mobile.profiles

import com.uacspoofer.mobile.vpn.LivePing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLatencyTesterTest {
    @Test
    fun medianIgnoresAHighOutlier() {
        assertEquals(
            205L,
            ProfileLatencyTester.medianSuccessful(listOf(180L, 220L, 205L, 800L, 198L)),
        )
    }

    @Test
    fun medianSupportsEvenSuccessfulSampleCount() {
        assertEquals(212L, ProfileLatencyTester.medianSuccessful(listOf(180L, 220L, 205L, 800L)))
    }

    @Test
    fun connectionCloseHeaderDisablesReuse() {
        val headers = "HTTP/1.1 204 No Content\r\nConnection: keep-alive, close\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
        assertEquals(true, ProfileLatencyTester.hasConnectionClose(headers))
    }

    @Test
    fun configDelayUsesTheSameLivePingHttp80Path() {
        assertEquals("cp.cloudflare.com", LivePing.TARGETS[0].host)
        assertEquals(80, LivePing.TARGETS[0].port)
        assertEquals("/generate_204", LivePing.TARGETS[0].path)
        assertTrue(LivePing.TARGETS.none { it.port == 443 })
        assertEquals(5_000, LivePing.TIMEOUT_MS)
    }
}
