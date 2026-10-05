package com.akash.reelslimiter.data

import android.content.Context
import android.content.SharedPreferences
import com.akash.reelslimiter.counting.CounterState
import com.akash.reelslimiter.counting.CounterStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * On-device counter storage shared by the service and the app screens (same process).
 * Also keeps one count per day ("day_2026-10-03") for the M4 history screen.
 */
class CounterRepository private constructor(private val prefs: SharedPreferences) : CounterStore {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<CounterState> = _state.asStateFlow()

    override fun load(): CounterState = _state.value

    override fun save(state: CounterState) {
        prefs.edit()
            .putString(KEY_DAY, state.day)
            .putInt(KEY_DAILY, state.dailyCount)
            .putInt(KEY_SESSION, state.sessionCount)
            .putLong(KEY_LAST_REELS_AT, state.lastReelsAtMillis)
            .apply { if (state.day.isNotEmpty()) putInt(DAY_PREFIX + state.day, state.dailyCount) }
            .apply()
        _state.value = state
    }

    private fun read() = CounterState(
        day = prefs.getString(KEY_DAY, "").orEmpty(),
        dailyCount = prefs.getInt(KEY_DAILY, 0),
        sessionCount = prefs.getInt(KEY_SESSION, 0),
        lastReelsAtMillis = prefs.getLong(KEY_LAST_REELS_AT, 0L),
    )

    companion object {
        private const val PREFS = "counter"
        private const val KEY_DAY = "day"
        private const val KEY_DAILY = "daily_count"
        private const val KEY_SESSION = "session_count"
        private const val KEY_LAST_REELS_AT = "last_reels_at"
        private const val DAY_PREFIX = "day_"

        @Volatile private var instance: CounterRepository? = null

        fun get(context: Context): CounterRepository =
            instance ?: synchronized(this) {
                instance ?: CounterRepository(
                    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
