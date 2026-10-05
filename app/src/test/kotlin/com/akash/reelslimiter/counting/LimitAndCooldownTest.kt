package com.akash.reelslimiter.counting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class LimitAndCooldownTest {

    private val zone = ZoneOffset.UTC
    private val t0 = LocalDateTime.of(2026, 10, 3, 12, 0).toInstant(zone).toEpochMilli()

    private class FakeCooldownStore(var saved: CooldownState = CooldownState()) : CooldownStore {
        override fun load() = saved
        override fun save(state: CooldownState) {
            saved = state
        }
    }

    // LimitPolicy

    @Test
    fun `under the limit there is no cooldown`() {
        assertNull(LimitPolicy(zone = zone).check(CounterState(dailyCount = 29, sessionCount = 29), t0))
    }

    @Test
    fun `session limit starts an 8 minute cooldown`() {
        val hit = LimitPolicy(zone = zone).check(CounterState(dailyCount = 50, sessionCount = 30), t0)
        assertEquals(t0 + 8 * 60_000, hit?.endAtMillis)
    }

    @Test
    fun `daily limit pauses until the next day and wins over the session limit`() {
        val policy = LimitPolicy(sessionLimit = 30, dailyLimit = 60, zone = zone)
        val hit = policy.check(CounterState(dailyCount = 60, sessionCount = 30), t0)
        assertEquals(LocalDateTime.of(2026, 10, 4, 0, 0).toInstant(zone).toEpochMilli(), hit?.endAtMillis)
    }

    @Test
    fun `next day start honours the reset time`() {
        val policy = LimitPolicy(resetMinuteOfDay = 4 * 60, zone = zone)
        val at0130 = LocalDateTime.of(2026, 10, 4, 1, 30).toInstant(zone).toEpochMilli()
        assertEquals(LocalDateTime.of(2026, 10, 4, 4, 0).toInstant(zone).toEpochMilli(), policy.nextDayStartMillis(at0130))
    }

    @Test
    fun `no limits configured means no cooldown`() {
        assertNull(LimitPolicy(sessionLimit = null, dailyLimit = null, zone = zone).check(CounterState(dailyCount = 999, sessionCount = 999), t0))
    }

    // CooldownManager

    @Test
    fun `cooldown is active until its end time then expires once`() {
        val manager = CooldownManager(FakeCooldownStore())
        manager.start(LimitHit(t0 + 1_000, "limit"))
        assertTrue(manager.isActive(t0 + 999))
        assertEquals(1L, manager.remainingMillis(t0 + 999))
        assertFalse(manager.expireIfDue(t0 + 999))
        assertTrue(manager.expireIfDue(t0 + 1_000))
        assertFalse(manager.expireIfDue(t0 + 2_000))
        assertFalse(manager.isActive(t0 + 2_000))
    }

    @Test
    fun `cooldown survives a restart through the store`() {
        val store = FakeCooldownStore()
        CooldownManager(store).start(LimitHit(t0 + 60_000, "Session limit of 30 reels reached"))
        val restarted = CooldownManager(store)
        assertTrue(restarted.isActive(t0 + 30_000))
        assertEquals("Session limit of 30 reels reached", restarted.state.message)
    }

    // Counter + policy together

    @Test
    fun `the 30th reel triggers the cooldown and a finished cooldown starts a new session`() {
        val counterStore = object : CounterStore {
            var saved = CounterState()
            override fun load() = saved
            override fun save(state: CounterState) {
                saved = state
            }
        }
        val engine = CounterEngine(counterStore, zone)
        val policy = LimitPolicy(zone = zone)
        var now = t0
        var hit: LimitHit? = null
        for (reel in 1..30) {
            engine.observe(now, isReels = true, reelKey = reel)
            now += 2_000
            engine.observe(now, isReels = true, reelKey = reel)
            assertTrue(engine.tick(now))
            hit = policy.check(engine.current, now)
            if (reel < 30) assertNull(hit)
        }
        assertEquals(now + 8 * 60_000, hit?.endAtMillis)

        engine.startNewSession()
        assertEquals(0, engine.current.sessionCount)
        assertEquals(30, engine.current.dailyCount)
    }
}
