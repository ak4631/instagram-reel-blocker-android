package com.akash.reelslimiter.counting

/** User-editable limits. Values are clamped to sane ranges at the boundary. */
data class LimitSettings(
    val sessionLimit: Int = DEFAULT_SESSION_LIMIT,
    val cooldownMinutes: Int = DEFAULT_COOLDOWN_MINUTES,
) {
    fun clamped() = LimitSettings(
        sessionLimit = sessionLimit.coerceIn(SESSION_LIMIT_RANGE),
        cooldownMinutes = cooldownMinutes.coerceIn(COOLDOWN_MINUTES_RANGE),
    )

    fun toPolicy() = LimitPolicy(sessionLimit = sessionLimit, cooldownMillis = cooldownMinutes * 60_000L)

    companion object {
        const val DEFAULT_SESSION_LIMIT = 30
        const val DEFAULT_COOLDOWN_MINUTES = 8
        val SESSION_LIMIT_RANGE = 1..500
        val COOLDOWN_MINUTES_RANGE = 1..60
    }
}
