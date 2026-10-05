package com.akash.reelslimiter.counting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class CounterEngineTest {

    private class FakeStore(var saved: CounterState = CounterState()) : CounterStore {
        var saves = 0
        override fun load() = saved
        override fun save(state: CounterState) {
            saved = state
            saves++
        }
    }

    private val zone = ZoneOffset.UTC
    private val t0 = LocalDateTime.of(2026, 10, 3, 12, 0).toInstant(zone).toEpochMilli()
    private val store = FakeStore()
    private val engine = CounterEngine(store, zone)

    /** Shows [key] from [at] and runs the tick when the engine asks for it. */
    private fun watch(key: Int, at: Long, forMillis: Long): Boolean {
        val deadline = engine.observe(at, isReels = true, reelKey = key) ?: return false
        if (deadline > at + forMillis) return false
        engine.observe(deadline, isReels = true, reelKey = key)
        return engine.tick(deadline)
    }

    @Test
    fun `a reel counts after 1_5 s on screen`() {
        assertTrue(watch(key = 1, at = t0, forMillis = 2_000))
        assertEquals(1, engine.current.dailyCount)
        assertEquals(1, engine.current.sessionCount)
        assertEquals(1, store.saves)
    }

    @Test
    fun `a fast flick does not count`() {
        assertFalse(watch(key = 1, at = t0, forMillis = 800))
        assertTrue(watch(key = 2, at = t0 + 800, forMillis = 3_000))
        assertEquals(1, engine.current.dailyCount)
    }

    @Test
    fun `tick fails if the reel was swiped away before the dwell ended`() {
        engine.observe(t0, isReels = true, reelKey = 1)
        engine.observe(t0 + 1_000, isReels = true, reelKey = 2)
        assertFalse(engine.tick(t0 + 1_500))
        assertEquals(0, engine.current.dailyCount)
    }

    @Test
    fun `replaying the same reel counts once`() {
        assertTrue(watch(key = 1, at = t0, forMillis = 2_000))
        assertNull(engine.observe(t0 + 30_000, isReels = true, reelKey = 1))
        assertFalse(engine.tick(t0 + 31_500))
        assertEquals(1, engine.current.dailyCount)
    }

    @Test
    fun `leaving Reels and coming back to the same reel does not recount`() {
        assertTrue(watch(key = 1, at = t0, forMillis = 2_000))
        engine.observe(t0 + 5_000, isReels = false, reelKey = null)
        assertNull(engine.observe(t0 + 60_000, isReels = true, reelKey = 1))
        assertEquals(1, engine.current.dailyCount)
    }

    @Test
    fun `leaving Reels cancels a reel that has not finished its dwell`() {
        engine.observe(t0, isReels = true, reelKey = 1)
        engine.observe(t0 + 1_000, isReels = false, reelKey = null)
        assertFalse(engine.tick(t0 + 1_500))
        assertEquals(0, engine.current.dailyCount)
    }

    @Test
    fun `a mid-swipe frame does not reset the dwell of the same reel`() {
        engine.observe(t0, isReels = true, reelKey = 1)
        engine.observe(t0 + 700, isReels = true, reelKey = null)
        val deadline = engine.observe(t0 + 900, isReels = true, reelKey = 1)
        assertEquals(t0 + 1_500, deadline)
        assertTrue(engine.tick(t0 + 1_500))
    }

    @Test
    fun `daily count resets on a new day and session continues`() {
        val lateNight = LocalDateTime.of(2026, 10, 3, 23, 59, 50).toInstant(zone).toEpochMilli()
        assertTrue(watch(key = 1, at = lateNight, forMillis = 2_000))
        assertTrue(watch(key = 2, at = lateNight + 15_000, forMillis = 2_000))
        assertEquals("2026-10-04", engine.current.day)
        assertEquals(1, engine.current.dailyCount)
        assertEquals(2, engine.current.sessionCount)
    }

    @Test
    fun `a 20 minute gap starts a new session`() {
        assertTrue(watch(key = 1, at = t0, forMillis = 2_000))
        engine.observe(t0 + 3_000, isReels = false, reelKey = null)
        assertTrue(watch(key = 2, at = t0 + 25 * 60_000, forMillis = 2_000))
        assertEquals(2, engine.current.dailyCount)
        assertEquals(1, engine.current.sessionCount)
    }

    @Test
    fun `counts survive a restart through the store`() {
        assertTrue(watch(key = 1, at = t0, forMillis = 2_000))
        val restarted = CounterEngine(store, zone)
        restarted.observe(t0 + 10_000, isReels = true, reelKey = 7)
        assertTrue(restarted.tick(t0 + 11_500))
        assertEquals(2, restarted.current.dailyCount)
    }

    @Test
    fun `reset time moves the day boundary`() {
        val at0130 = LocalDateTime.of(2026, 10, 4, 1, 30).toInstant(zone).toEpochMilli()
        assertEquals("2026-10-04", CounterEngine.dayKey(at0130, zone, resetMinuteOfDay = 0))
        assertEquals("2026-10-03", CounterEngine.dayKey(at0130, zone, resetMinuteOfDay = 4 * 60))
    }
}
