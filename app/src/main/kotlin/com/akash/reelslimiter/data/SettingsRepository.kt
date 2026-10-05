package com.akash.reelslimiter.data

import android.content.Context
import android.content.SharedPreferences
import com.akash.reelslimiter.counting.LimitSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** On-device limit settings shared by the service and the app screens (same process). */
class SettingsRepository private constructor(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(
        LimitSettings(
            sessionLimit = prefs.getInt(KEY_SESSION_LIMIT, LimitSettings.DEFAULT_SESSION_LIMIT),
            cooldownMinutes = prefs.getInt(KEY_COOLDOWN_MINUTES, LimitSettings.DEFAULT_COOLDOWN_MINUTES),
        ).clamped(),
    )
    val state: StateFlow<LimitSettings> = _state.asStateFlow()

    fun update(settings: LimitSettings) {
        val valid = settings.clamped()
        prefs.edit()
            .putInt(KEY_SESSION_LIMIT, valid.sessionLimit)
            .putInt(KEY_COOLDOWN_MINUTES, valid.cooldownMinutes)
            .apply()
        _state.value = valid
    }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_SESSION_LIMIT = "session_limit"
        private const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"

        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(
                    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
