package com.bhznjns.autoswipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private fun getScreenDimensions(context: Context): Point {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        Point(bounds.width(), bounds.height())
    } else {
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        val p = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(p)
        p
    }
}

class SwipeService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var swipeTask: Runnable? = null
    private lateinit var sharedPrefs: SharedPreferences

    private var gestureInFlight = false
    private var lastGestureEndUptime = 0L
    private var cancelStreak = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        observeSwipeState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no op
    }

    override fun onInterrupt() {
        stopAutoSwipe()
    }

    private fun observeSwipeState() {
        swipeTask = object : Runnable {
            override fun run() {
                val enabled = sharedPrefs.getBoolean(KEY_ENABLED, false)

                val randomDelay = Random.nextLong(110000, 130000)
                handler.postDelayed(this, randomDelay)

                if (enabled) {
                    performSwipeSafely()
                }
            }
        }
        handler.post(swipeTask!!)
    }

    private fun stopAutoSwipe() {
        swipeTask?.let { handler.removeCallbacks(it) }
    }

    private fun performSwipeSafely() {
        val now = SystemClock.uptimeMillis()
        val minGap = ViewConfiguration.getDoubleTapTimeout().toLong() + EXTRA_COOLDOWN_MS

        if (gestureInFlight) return
        if (now - lastGestureEndUptime < minGap) return

        if (tryScrollForward()) {
            lastGestureEndUptime = SystemClock.uptimeMillis()
            cancelStreak = 0
            Log.d(TAG, "Scroll action succeeded")
            return
        }

        dispatchSwipeGesture()
    }

    private fun tryScrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)

        while (q.isNotEmpty()) {
            val node = q.removeFirst()

            if (node.isScrollable) {
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                if (ok) return true
            }

            val n = node.childCount
            for (i in 0 until n) {
                val child = node.getChild(i)
                if (child != null) q.add(child)
            }
        }
        return false
    }

    private fun dispatchSwipeGesture() {
        val screen = getScreenDimensions(this)
        val w = screen.x.toFloat()
        val h = screen.y.toFloat()

        val startPctRaw = sharedPrefs.getInt(KEY_START_Y_PCT, DEFAULT_START_Y_PCT)
        val endPctRaw = sharedPrefs.getInt(KEY_END_Y_PCT, DEFAULT_END_Y_PCT)
        val durationRaw = sharedPrefs.getLong(KEY_DURATION_MS, DEFAULT_DURATION_MS)

        val startPct = clampInt(startPctRaw, 5, 95)
        val endPct = clampInt(endPctRaw, 5, 95)
        val durationMs = clampLong(durationRaw, 50L, 800L)

        val safeEndPct = if (endPct >= startPct - MIN_GAP_PCT) {
            max(5, startPct - MIN_GAP_PCT)
        } else {
            endPct
        }

        val startY = h * (startPct / 100f)
        val endY = h * (safeEndPct / 100f)

        val baseX = w * 0.45f
        val jitterX = w * 0.06f * (Random.nextFloat() - 0.5f)
        val startX = baseX + jitterX
        val endX = startX + w * 0.02f * (Random.nextFloat() - 0.5f)

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()

        gestureInFlight = true

        Log.i(
            TAG,
            "Swipe startPct=$startPct endPct=$safeEndPct durationMs=$durationMs x=${startX.toInt()}"
        )

        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    markGestureEnd(success = true)
                    Log.d(TAG, "Swipe completed")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    markGestureEnd(success = false)
                    Log.d(TAG, "Swipe cancelled")

                    val enabled = sharedPrefs.getBoolean(KEY_ENABLED, false)
                    if (enabled && cancelStreak <= MAX_CANCEL_RETRY) {
                        val backoff = 350L + 250L * cancelStreak
                        handler.postDelayed({ performSwipeSafely() }, backoff)
                    }
                }
            },
            null
        )

        if (!accepted) {
            markGestureEnd(success = false)
            Log.d(TAG, "dispatchGesture not accepted")
        }
    }

    private fun markGestureEnd(success: Boolean) {
        gestureInFlight = false
        lastGestureEndUptime = SystemClock.uptimeMillis()
        cancelStreak = if (success) 0 else (cancelStreak + 1)
    }

    private fun clampInt(v: Int, lo: Int, hi: Int): Int = min(hi, max(lo, v))
    private fun clampLong(v: Long, lo: Long, hi: Long): Long = min(hi, max(lo, v))

    companion object {
        private const val TAG = "swipe"

        private const val PREFS_NAME = "swipe_prefs"
        private const val KEY_ENABLED = "swipe_enabled"
        private const val KEY_START_Y_PCT = "swipe_start_y_pct"
        private const val KEY_END_Y_PCT = "swipe_end_y_pct"
        private const val KEY_DURATION_MS = "swipe_duration_ms"

        private const val DEFAULT_START_Y_PCT = 86
        private const val DEFAULT_END_Y_PCT = 18
        private const val DEFAULT_DURATION_MS = 180L

        private const val MIN_GAP_PCT = 5

        private const val EXTRA_COOLDOWN_MS = 80L
        private const val MAX_CANCEL_RETRY = 2
    }
}
