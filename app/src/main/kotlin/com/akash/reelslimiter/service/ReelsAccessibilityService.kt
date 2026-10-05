package com.akash.reelslimiter.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.akash.reelslimiter.counting.CooldownManager
import com.akash.reelslimiter.counting.CounterEngine
import com.akash.reelslimiter.counting.EnforcementMode
import com.akash.reelslimiter.counting.LimitPolicy
import com.akash.reelslimiter.data.CooldownRepository
import com.akash.reelslimiter.data.CounterRepository
import com.akash.reelslimiter.data.SettingsRepository
import com.akash.reelslimiter.detection.Detection
import com.akash.reelslimiter.detection.ReelsDetector
import com.akash.reelslimiter.detection.ReelsRules
import com.akash.reelslimiter.detection.Screen
import com.akash.reelslimiter.enforcement.OverlayController
import com.akash.reelslimiter.monitor.DetectionMonitor

/**
 * Decides Reels vs Not Reels for Instagram, feeds [CounterEngine], and while a cooldown is
 * active covers Reels (and only Reels) with the overlay or sends Back.
 */
class ReelsAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    /** Uptime at which the queued check will run, or 0 when nothing is queued. */
    private var scheduledAt = 0L
    private var lastEvaluationAt = 0L
    private val evaluate = Runnable {
        scheduledAt = 0L
        lastEvaluationAt = SystemClock.uptimeMillis()
        evaluateActiveWindow()
    }

    private val settings by lazy { SettingsRepository.get(this) }

    /** Read on every use, so edits on the Settings screen apply immediately. */
    private val policy: LimitPolicy get() = settings.state.value.toPolicy()
    private val counter by lazy { CounterEngine(CounterRepository.get(this)) }
    private val cooldown by lazy { CooldownManager(CooldownRepository.get(this)) }
    private val overlay by lazy { OverlayController(this) { performGlobalAction(GLOBAL_ACTION_BACK) } }
    private var lastHardBackAt = 0L

    private val countTick = Runnable { onCountTick() }

    /** Re-applies the latest verdict when a cooldown ends, so the overlay goes and a new session starts. */
    private val cooldownEnd = Runnable {
        onDetection(DetectionMonitor.current.value ?: Detection(Screen.NOT_REELS, "cooldown ended"))
    }

    /**
     * Safety net while the overlay is up: re-checks every second that Instagram Reels is really
     * what's on screen, so the overlay can never linger over another app or the home screen.
     */
    private val overlayWatchdog = Runnable { if (overlay.isShowing) evaluateActiveWindow() }

    /** Never leave the overlay over the lock screen. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onDetection(Detection(Screen.NOT_REELS, "screen off"))
        }
    }

    override fun onServiceConnected() {
        DetectionMonitor.setServiceConnected(true)
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // A cooldown stored before a restart or reboot resumes from its absolute end time.
        val now = System.currentTimeMillis()
        if (cooldown.expireIfDue(now)) counter.startNewSession()
        scheduleCooldownEnd(now)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        when {
            // Our own overlay fires events too; reacting to them would make it flicker.
            pkg == packageName -> Unit
            pkg == ReelsRules.INSTAGRAM_PACKAGE ->
                // Swipes, taps and window switches are real navigation; content changes are mostly
                // video progress ticking during playback, so those are rate-limited.
                scheduleEvaluation(urgent = event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Another app (or system UI) came to the front; its content is never inspected.
                handler.removeCallbacks(evaluate)
                scheduledAt = 0L
                onDetection(Detection(Screen.NOT_REELS, "left Instagram"))
            }
        }
    }

    /** Single sink for every verdict: debug monitor, cooldown enforcement, then the counter. */
    private fun onDetection(detection: Detection) {
        DetectionMonitor.report(detection)
        val now = System.currentTimeMillis()
        val onReels = detection.screen == Screen.REELS

        if (cooldown.expireIfDue(now)) {
            Log.i(TAG, "Cooldown over; new session")
            counter.startNewSession()
        }

        handler.removeCallbacks(countTick)
        if (cooldown.isActive(now)) {
            // Nothing counts during a pause; pending dwell is dropped.
            counter.observe(now, isReels = false, reelKey = null)
            enforce(onReels, now)
            return
        }

        overlay.hide()
        val tickAt = counter.observe(now, onReels, detection.reelKey)
        if (tickAt != null) handler.postDelayed(countTick, maxOf(0L, tickAt - now) + TICK_SLACK_MS)
    }

    private fun onCountTick() {
        val now = System.currentTimeMillis()
        if (!counter.tick(now)) return
        val hit = policy.check(counter.current, now) ?: return

        Log.i(TAG, "${hit.message}; paused until ${hit.endAtMillis}")
        cooldown.start(hit)
        scheduleCooldownEnd(now)
        enforce(DetectionMonitor.current.value?.screen == Screen.REELS, now)
    }

    /** Covers Reels during a cooldown. Never acts outside Reels, so chat is untouched. */
    private fun enforce(onReels: Boolean, now: Long) {
        if (!onReels) {
            overlay.hide()
            return
        }
        if (policy.mode == EnforcementMode.HARD && now - lastHardBackAt > HARD_BACK_RETRY_MS) {
            lastHardBackAt = now
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
        val state = cooldown.state
        // If the overlay can't be drawn, fall back to leaving Reels.
        if (!overlay.show(state.endAtMillis, state.message)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
        handler.removeCallbacks(overlayWatchdog)
        handler.postDelayed(overlayWatchdog, WATCHDOG_INTERVAL_MS)
    }

    private fun scheduleCooldownEnd(now: Long) {
        handler.removeCallbacks(cooldownEnd)
        if (cooldown.isActive(now)) handler.postDelayed(cooldownEnd, cooldown.remainingMillis(now) + TICK_SLACK_MS)
    }

    private fun scheduleEvaluation(urgent: Boolean) {
        val now = SystemClock.uptimeMillis()
        val runAt = if (urgent) {
            now + EVALUATION_DELAY_MS
        } else {
            maxOf(now + EVALUATION_DELAY_MS, lastEvaluationAt + CONTENT_CHANGE_INTERVAL_MS)
        }
        if (scheduledAt != 0L && scheduledAt <= runAt) return
        handler.removeCallbacks(evaluate)
        handler.postAtTime(evaluate, runAt)
        scheduledAt = runAt
    }

    private fun evaluateActiveWindow() {
        val root = rootInActiveWindow
        if (root?.packageName?.toString() != ReelsRules.INSTAGRAM_PACKAGE) {
            onDetection(Detection(Screen.NOT_REELS, "Instagram not in front"))
            return
        }

        val started = SystemClock.elapsedRealtimeNanos()
        val snapshot = root.toUiNode(ReelsRules::isRelevantDescription)
        val detection = ReelsDetector.detect(snapshot)
        DetectionMonitor.recordEvaluation((SystemClock.elapsedRealtimeNanos() - started) / 1_000, snapshot.nodeCount())
        onDetection(detection)

        if (DetectionMonitor.takeCaptureRequest()) {
            DetectionMonitor.publishCapture(detection, root.toUiNode { true }.dump())
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOffReceiver) }
        overlay.hide()
        DetectionMonitor.setServiceConnected(false)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ReelsLimiter"
        const val EVALUATION_DELAY_MS = 150L
        const val CONTENT_CHANGE_INTERVAL_MS = 1_000L

        /** Runs timed callbacks just after their deadline so wall-clock rounding never misses it. */
        const val TICK_SLACK_MS = 20L

        /** Hard mode: if Reels is still on screen this long after Back, show the overlay instead. */
        const val HARD_BACK_RETRY_MS = 1_500L

        const val WATCHDOG_INTERVAL_MS = 1_000L
    }
}
