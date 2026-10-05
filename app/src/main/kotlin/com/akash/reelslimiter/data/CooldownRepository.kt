package com.akash.reelslimiter.data

import android.content.Context
import android.content.SharedPreferences
import com.akash.reelslimiter.counting.CooldownState
import com.akash.reelslimiter.counting.CooldownStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** On-device cooldown storage shared by the service and the app screens (same process). */
class CooldownRepository private constructor(private val prefs: SharedPreferences) : CooldownStore {

    private val _state = MutableStateFlow(
        CooldownState(prefs.getLong(KEY_END_AT, 0L), prefs.getString(KEY_MESSAGE, "").orEmpty()),
    )
    val state: StateFlow<CooldownState> = _state.asStateFlow()

    override fun load(): CooldownState = _state.value

    override fun save(state: CooldownState) {
        prefs.edit().putLong(KEY_END_AT, state.endAtMillis).putString(KEY_MESSAGE, state.message).apply()
        _state.value = state
    }

    companion object {
        private const val PREFS = "cooldown"
        private const val KEY_END_AT = "end_at"
        private const val KEY_MESSAGE = "message"

        @Volatile private var instance: CooldownRepository? = null

        fun get(context: Context): CooldownRepository =
            instance ?: synchronized(this) {
                instance ?: CooldownRepository(
                    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
