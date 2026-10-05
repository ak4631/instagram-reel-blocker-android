package com.akash.reelslimiter.counting

/** Persisted cooldown: an absolute end time (0 = none) plus the message shown on the overlay. */
data class CooldownState(val endAtMillis: Long = 0L, val message: String = "")

interface CooldownStore {
    fun load(): CooldownState
    fun save(state: CooldownState)
}

/**
 * Holds the cooldown as an absolute wall-clock end time, so it survives a service restart or reboot.
 */
class CooldownManager(private val store: CooldownStore) {
    var state: CooldownState = store.load()
        private set

    fun isActive(nowMillis: Long): Boolean = state.endAtMillis > nowMillis

    fun remainingMillis(nowMillis: Long): Long = (state.endAtMillis - nowMillis).coerceAtLeast(0L)

    fun start(hit: LimitHit) {
        state = CooldownState(hit.endAtMillis, hit.message)
        store.save(state)
    }

    /** Clears a cooldown whose end time has passed. Returns true exactly once per finished cooldown. */
    fun expireIfDue(nowMillis: Long): Boolean {
        if (state.endAtMillis == 0L || nowMillis < state.endAtMillis) return false
        state = CooldownState()
        store.save(state)
        return true
    }
}
