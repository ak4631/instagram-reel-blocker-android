package com.akash.reelslimiter.counting

import org.junit.Assert.assertEquals
import org.junit.Test

class LimitSettingsTest {

    @Test
    fun `defaults are 30 reels and 8 minutes`() {
        val policy = LimitSettings().toPolicy()
        assertEquals(30, policy.sessionLimit)
        assertEquals(8 * 60_000L, policy.cooldownMillis)
    }

    @Test
    fun `edited values flow into the policy`() {
        val policy = LimitSettings(sessionLimit = 3, cooldownMinutes = 1).toPolicy()
        assertEquals(3, policy.sessionLimit)
        assertEquals(60_000L, policy.cooldownMillis)
    }

    @Test
    fun `out of range values are clamped`() {
        assertEquals(LimitSettings(sessionLimit = 1, cooldownMinutes = 1), LimitSettings(0, 0).clamped())
        assertEquals(LimitSettings(sessionLimit = 500, cooldownMinutes = 60), LimitSettings(9_999, 600).clamped())
    }
}
