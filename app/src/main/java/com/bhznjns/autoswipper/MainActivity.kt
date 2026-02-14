package com.bhznjns.autoswipper

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartSwipe: Button
    private lateinit var btnStopSwipe: Button

    private lateinit var sbStartY: SeekBar
    private lateinit var sbEndY: SeekBar
    private lateinit var sbDuration: SeekBar
    private lateinit var sbInterval: SeekBar

    private lateinit var tvStartY: TextView
    private lateinit var tvEndY: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvInterval: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStartSwipe = findViewById(R.id.btn_start_swipe)
        btnStopSwipe = findViewById(R.id.btn_stop_swipe)

        sbStartY = findViewById(R.id.sb_start_y)
        sbEndY = findViewById(R.id.sb_end_y)
        sbDuration = findViewById(R.id.sb_duration)
        sbInterval = findViewById(R.id.sb_interval)

        tvStartY = findViewById(R.id.tv_start_y)
        tvEndY = findViewById(R.id.tv_end_y)
        tvDuration = findViewById(R.id.tv_duration)
        tvInterval = findViewById(R.id.tv_interval)

        btnStartSwipe.setOnClickListener {
            prefs().edit().putBoolean(KEY_ENABLED, true).apply()
            Toast.makeText(this, "滑屏功能已启动", Toast.LENGTH_SHORT).show()
        }

        btnStopSwipe.setOnClickListener {
            prefs().edit().putBoolean(KEY_ENABLED, false).apply()
            Toast.makeText(this, "滑屏功能已停止", Toast.LENGTH_SHORT).show()
        }

        initSliders()

        if (!isAccessibilityServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    private fun initSliders() {
        val p = prefs()

        val startY = p.getInt(KEY_START_Y_PCT, DEFAULT_START_Y_PCT)
        val endY = p.getInt(KEY_END_Y_PCT, DEFAULT_END_Y_PCT)
        val duration = p.getLong(KEY_DURATION_MS, DEFAULT_DURATION_MS).toInt()
        val intervalSec = p.getInt(KEY_INTERVAL_SEC, DEFAULT_INTERVAL_SEC)

        sbStartY.progress = clampInt(startY, 5, 95)
        sbEndY.progress = clampInt(endY, 5, 95)
        sbDuration.progress = clampInt(duration, 50, 800)

        // SeekBar 的 progress 不方便做偏移，这里直接用 1 到 600 秒
        sbInterval.progress = clampInt(intervalSec, 1, 600)

        ensureOrderAndRender()
        renderInterval()

        sbStartY.setOnSeekBarChangeListener(simpleListener { value ->
            val newStart = clampInt(value, 5, 95)
            p.edit().putInt(KEY_START_Y_PCT, newStart).apply()
            ensureOrderAndRender()
        })

        sbEndY.setOnSeekBarChangeListener(simpleListener { value ->
            val newEnd = clampInt(value, 5, 95)
            p.edit().putInt(KEY_END_Y_PCT, newEnd).apply()
            ensureOrderAndRender()
        })

        sbDuration.setOnSeekBarChangeListener(simpleListener { value ->
            val newDuration = clampInt(value, 50, 800)
            p.edit().putLong(KEY_DURATION_MS, newDuration.toLong()).apply()
            renderLabels()
        })

        sbInterval.setOnSeekBarChangeListener(simpleListener { value ->
            val newInterval = clampInt(value, 1, 600)
            p.edit().putInt(KEY_INTERVAL_SEC, newInterval).apply()
            renderInterval()
        })
    }

    private fun ensureOrderAndRender() {
        val p = prefs()
        var startY = p.getInt(KEY_START_Y_PCT, sbStartY.progress)
        var endY = p.getInt(KEY_END_Y_PCT, sbEndY.progress)

        if (endY >= startY - MIN_GAP_PCT) {
            endY = max(5, startY - MIN_GAP_PCT)
            p.edit().putInt(KEY_END_Y_PCT, endY).apply()
            sbEndY.progress = endY
        }

        renderLabels()
    }

    private fun renderLabels() {
        val p = prefs()
        val startY = p.getInt(KEY_START_Y_PCT, sbStartY.progress)
        val endY = p.getInt(KEY_END_Y_PCT, sbEndY.progress)
        val duration = p.getLong(KEY_DURATION_MS, sbDuration.progress.toLong())

        tvStartY.text = "${startY}%"
        tvEndY.text = "${endY}%"
        tvDuration.text = "${duration}ms"
    }

    private fun renderInterval() {
        val p = prefs()
        val intervalSec = p.getInt(KEY_INTERVAL_SEC, sbInterval.progress)
        tvInterval.text = "${intervalSec}s"
    }

    private fun simpleListener(onStop: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val v = seekBar?.progress ?: return
                onStop(v)
            }
        }

    private fun clampInt(v: Int, lo: Int, hi: Int): Int = min(hi, max(lo, v))

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, SwipeService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    companion object {
        private const val PREFS_NAME = "swipe_prefs"

        private const val KEY_ENABLED = "swipe_enabled"
        private const val KEY_START_Y_PCT = "swipe_start_y_pct"
        private const val KEY_END_Y_PCT = "swipe_end_y_pct"
        private const val KEY_DURATION_MS = "swipe_duration_ms"
        private const val KEY_INTERVAL_SEC = "swipe_interval_sec"

        private const val DEFAULT_START_Y_PCT = 86
        private const val DEFAULT_END_Y_PCT = 18
        private const val DEFAULT_DURATION_MS = 180L
        private const val DEFAULT_INTERVAL_SEC = 120

        private const val MIN_GAP_PCT = 5
    }
}
