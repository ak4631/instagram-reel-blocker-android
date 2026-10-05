package com.akash.reelslimiter.monitor

import android.util.Log
import com.akash.reelslimiter.detection.Detection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory bridge between the accessibility service and the debug screen (same process).
 * Nothing here is persisted.
 */
object DetectionMonitor {
    private const val TAG = "ReelsDetector"
    private const val MAX_TRANSITIONS = 100

    data class Transition(val atMillis: Long, val detection: Detection)

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _current = MutableStateFlow<Detection?>(null)
    val current: StateFlow<Detection?> = _current.asStateFlow()

    /** Cost of inspecting Instagram's screen; the PRD budget is under 20 ms per check. */
    data class EvalStats(val lastMicros: Long, val avgMicros: Long, val maxMicros: Long, val checks: Int, val lastNodes: Int)

    private val _evalStats = MutableStateFlow<EvalStats?>(null)
    val evalStats: StateFlow<EvalStats?> = _evalStats.asStateFlow()

    private val _transitions = MutableStateFlow<List<Transition>>(emptyList())
    val transitions: StateFlow<List<Transition>> = _transitions.asStateFlow()

    private val _captureRequested = MutableStateFlow(false)
    val captureRequested: StateFlow<Boolean> = _captureRequested.asStateFlow()

    private val _capture = MutableStateFlow<String?>(null)
    val capture: StateFlow<String?> = _capture.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
        if (!connected) _current.value = null
    }

    /** Records a verdict; only screen changes are added to the transition log. */
    fun report(detection: Detection) {
        val previous = _current.value
        _current.value = detection
        if (previous?.screen == detection.screen) return

        Log.i(TAG, "${detection.screen} (${detection.reason})")
        _transitions.update { (listOf(Transition(System.currentTimeMillis(), detection)) + it).take(MAX_TRANSITIONS) }
    }

    fun recordEvaluation(micros: Long, nodes: Int) {
        _evalStats.update { old ->
            val checks = (old?.checks ?: 0) + 1
            EvalStats(
                lastMicros = micros,
                avgMicros = ((old?.avgMicros ?: 0) * (checks - 1) + micros) / checks,
                maxMicros = maxOf(old?.maxMicros ?: 0, micros),
                checks = checks,
                lastNodes = nodes,
            )
        }
    }

    /** Asks the service to dump the next Instagram screen it evaluates. */
    fun requestCapture() {
        _capture.value = null
        _captureRequested.value = true
    }

    /** Returns true once per request, so the service captures a single screen. */
    fun takeCaptureRequest(): Boolean = _captureRequested.compareAndSet(expect = true, update = false)

    fun publishCapture(detection: Detection, dump: String) {
        _capture.value = "${detection.screen} (${detection.reason})\n\n$dump"
    }

    fun clearTransitions() {
        _transitions.value = emptyList()
    }
}
