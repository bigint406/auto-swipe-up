package com.bhznjns.autoswipper

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var btnStartSwipe: Button
    private lateinit var btnStopSwipe: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStartSwipe = findViewById(R.id.btn_start_swipe)
        btnStopSwipe = findViewById(R.id.btn_stop_swipe)

        btnStartSwipe.setOnClickListener {
            saveSwipeState(true) // 启用滑屏
            Toast.makeText(this, "滑屏功能已启动", Toast.LENGTH_SHORT).show()
        }

        btnStopSwipe.setOnClickListener {
            saveSwipeState(false) // 停止滑屏
            Toast.makeText(this, "滑屏功能已停止", Toast.LENGTH_SHORT).show()
        }

        if (!isAccessibilityServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun saveSwipeState(enabled: Boolean) {
        val sharedPrefs = getSharedPreferences("swipe_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("swipe_enabled", enabled).apply()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/.MyAccessibilityService"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(service) == true
    }
}