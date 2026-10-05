package com.akash.reelslimiter.counting

import java.time.Instant
import java.time.ZoneId

/** Persisted counter values. [day] is the local counting day, e.g. "2026-10-03". */
data class CounterState(
    val day: String = "",
    val dailyCount: Int = 0,
    val sessionCount: Int = 0,
    val lastReelsAtMillis: Long = 0L,
)

interface CounterStore {
    fun load(): CounterState
    fun save(state: CounterState)
}

/**
 * Turns detector verdicts into a reel count. Pure Kotlin with an explicit clock, so it is unit tested.
 *
 * Rule: a reel counts once it has stayed on screen for [dwellMillis]. Fast flicks never reach it,
 * replays keep the same reel identity, and the last reel before leaving still counts.
 */
class CounterEngine(
    private val store: CounterStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val dwellMillis: Long = DEFAULT_DWELL_MILLIS,
    private val sessionGapMillis: Long = DEFAULT_SESSION_GAP_MILLIS,
    private val resetMinuteOfDay: Int = 0,
) {
    private var state = store.load()
    private var onReels = false
    private var visibleKey: Int? = null
    private var pendingKey: Int? = null
    private var pendingSince = 0L
    private var lastCountedKey: Int? = null

    val current: CounterState get() = state

    /**
     * Feeds one detector verdict. Returns the wall-clock time at which [tick] should run
     * to finish the dwell for the reel on screen, or null when nothing is waiting.
     */
    fun observe(nowMillis: Long, isReels: Boolean, reelKey: Int?): Long? {
        rollOver(nowMillis)
        if (!isReels) {
            if (onReels) store.save(state)
            onReels = false
            visibleKey = null
            pendingKey = null
            return null
        }

        if (state.lastReelsAtMillis > 0 && nowMillis - state.lastReelsAtMillis > sessionGapMillis) {
            state = state.copy(sessionCount = 0)
        }
        state = state.copy(lastReelsAtMillis = nowMillis)
        onReels = true
        visibleKey = reelKey

        return when (reelKey) {
            null -> null // mid-swipe or no clear reel; keep any pending dwell
            pendingKey -> pendingSince + dwellMillis
            lastCountedKey -> {
                pendingKey = null
                null
            }
            else -> {
                pendingKey = reelKey
                pendingSince = nowMillis
                nowMillis + dwellMillis
            }
        }
    }

    /** Counts the pending reel if it is still on screen and its dwell is complete. Returns true when counted. */
    fun tick(nowMillis: Long): Boolean {
        val key = pendingKey ?: return false
        if (!onReels || visibleKey != key || nowMillis - pendingSince < dwellMillis) return false

        rollOver(nowMillis)
        state = state.copy(
            dailyCount = state.dailyCount + 1,
            sessionCount = state.sessionCount + 1,
            lastReelsAtMillis = nowMillis,
        )
        lastCountedKey = key
        pendingKey = null
        store.save(state)
        return true
    }

    /** A finished cooldown is a real break: the next reel starts a fresh session. */
    fun startNewSession() {
        state = state.copy(sessionCount = 0)
        store.save(state)
    }

    private fun rollOver(nowMillis: Long) {
        val today = dayKey(nowMillis, zone, resetMinuteOfDay)
        if (state.day != today) state = state.copy(day = today, dailyCount = 0)
    }

    companion object {
        const val DEFAULT_DWELL_MILLIS = 1_500L
        const val DEFAULT_SESSION_GAP_MILLIS = 20 * 60 * 1_000L

        /** Local counting day; [resetMinuteOfDay] shifts the day boundary away from midnight. */
        fun dayKey(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault(), resetMinuteOfDay: Int = 0): String =
            Instant.ofEpochMilli(nowMillis).atZone(zone).minusMinutes(resetMinuteOfDay.toLong()).toLocalDate().toString()
    }
}
