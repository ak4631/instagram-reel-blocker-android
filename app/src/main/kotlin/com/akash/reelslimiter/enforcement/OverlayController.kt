package com.akash.reelslimiter.enforcement

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen "Reels paused" cover drawn as an accessibility overlay: no "Display over other apps"
 * permission needed, and it is not focusable, so Back and system gestures still reach Instagram.
 * Plain Views instead of Compose keep it light and quick to show.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val onBackToChats: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val audioManager = service.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    // Asking for transient audio focus nudges Instagram to pause the reel playing underneath.
    private val audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build()

    private var root: View? = null
    private var countdown: TextView? = null
    private var title: TextView? = null
    private var endAtMillis = 0L

    val isShowing: Boolean get() = root != null

    private val tick = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1_000)
        }
    }

    /** Shows (or refreshes) the overlay. Returns false if the window could not be added. */
    fun show(endAtMillis: Long, message: String): Boolean {
        this.endAtMillis = endAtMillis
        if (root == null) {
            val view = build()
            try {
                windowManager.addView(view, layoutParams())
            } catch (e: RuntimeException) {
                return false
            }
            root = view
            audioManager.requestAudioFocus(audioFocus)
            handler.post(tick)
        }
        title?.text = message
        updateCountdown()
        return true
    }

    fun hide() {
        val view = root ?: return
        handler.removeCallbacks(tick)
        runCatching { windowManager.removeView(view) }
        audioManager.abandonAudioFocusRequest(audioFocus)
        root = null
        countdown = null
        title = null
    }

    private fun updateCountdown() {
        val remainingSeconds = ((endAtMillis - System.currentTimeMillis()).coerceAtLeast(0L) + 999) / 1_000
        val h = remainingSeconds / 3_600
        val m = remainingSeconds % 3_600 / 60
        val s = remainingSeconds % 60
        countdown?.text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /**
     * Covers the app area only: the status bar (notification shade) and the navigation bar
     * (Home / Back / Recents) stay uncovered and usable, so the phone itself is never blocked.
     */
    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = WindowInsets.Type.systemBars()
        }
    }

    private fun build(): View {
        val context = service
        fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
        fun text(size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

        val heading = text(30f, Color.WHITE, bold = true).apply { text = "Reels paused" }
        val subtitle = text(16f, Color.argb(200, 255, 255, 255)).also { title = it }
        val timer = text(64f, Color.WHITE, bold = true).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, dp(24), 0, dp(24))
        }.also { countdown = it }
        val hint = text(14f, Color.argb(170, 255, 255, 255)).apply {
            text = "DMs, feed and stories still work."
            setPadding(0, 0, 0, dp(32))
        }
        val back = Button(context).apply {
            text = "Back to chats"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setOnClickListener { onBackToChats() }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(245, 12, 12, 18))
            setPadding(dp(32), dp(32), dp(32), dp(32))
            // Swallow touches so the reel underneath can't be scrolled.
            isClickable = true
            addView(heading)
            addView(subtitle)
            addView(timer)
            addView(hint)
            addView(back, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }
}
