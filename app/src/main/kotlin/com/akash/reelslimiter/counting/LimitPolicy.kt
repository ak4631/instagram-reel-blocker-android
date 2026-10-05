package com.akash.reelslimiter.counting

import java.time.Instant
import java.time.ZoneId

enum class EnforcementMode {
    /** Full-screen "Reels paused" overlay whenever Reels is on screen. */
    SOFT,

    /** Send Back as soon as Reels appears; overlay only if Back didn't leave Reels. */
    HARD,
}

/** Why and until when Reels is paused. */
data class LimitHit(val endAtMillis: Long, val message: String)

/**
 * Limit rules. Defaults answer the PRD open questions for M3; M4 makes them user settings.
 * A session cooldown resets the session count when it ends; the daily count never resets early.
 */
data class LimitPolicy(
    val sessionLimit: Int? = 30,
    val dailyLimit: Int? = null,
    val cooldownMillis: Long = 8 * 60_000L,
    val mode: EnforcementMode = EnforcementMode.SOFT,
    val resetMinuteOfDay: Int = 0,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** Called right after a reel is counted; returns the cooldown to start, or null if under every limit. */
    fun check(state: CounterState, nowMillis: Long): LimitHit? = when {
        dailyLimit != null && state.dailyCount >= dailyLimit ->
            LimitHit(nextDayStartMillis(nowMillis), "Daily limit of $dailyLimit reels reached")
        sessionLimit != null && state.sessionCount >= sessionLimit ->
            LimitHit(nowMillis + cooldownMillis, "Session limit of $sessionLimit reels reached")
        else -> null
    }

    /** Start of the next counting day, honouring [resetMinuteOfDay]. */
    fun nextDayStartMillis(nowMillis: Long): Long {
        val day = Instant.ofEpochMilli(nowMillis).atZone(zone).minusMinutes(resetMinuteOfDay.toLong()).toLocalDate()
        return day.plusDays(1).atStartOfDay(zone).plusMinutes(resetMinuteOfDay.toLong()).toInstant().toEpochMilli()
    }
}
