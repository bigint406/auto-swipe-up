package com.bhznjns.autoswipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import kotlin.random.Random

fun getScreenDimensions(context: Context): Point {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // API 30+ 使用 WindowMetrics 获取屏幕尺寸
        val metrics = context.resources.displayMetrics
        Point(metrics.widthPixels, metrics.heightPixels)
    } else {
        // API 29 及以下使用旧方法获取屏幕尺寸
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val point = Point()
        display.getRealSize(point)
        point
    }
}
class SwipeService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var swipeTask: Runnable? = null
    private lateinit var sharedPrefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        sharedPrefs = getSharedPreferences("swipe_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("swipe_enabled", false).apply()
        observeSwipeState()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // 不需要处理事件
    }

    override fun onInterrupt() {
        stopAutoSwipe()
    }

    private fun observeSwipeState() {
        swipeTask = object : Runnable {
            override fun run() {
                val isSwipeEnabled = sharedPrefs.getBoolean("swipe_enabled", false)
                val randomDelay = Random.nextLong(240000, 300000)
                handler.postDelayed(this, randomDelay)
                if (isSwipeEnabled) {
                    performSwipe()
                }
            }
        }
        handler.post(swipeTask!!)
    }

    private fun stopAutoSwipe() {
        swipeTask?.let { handler.removeCallbacks(it) }
    }

    private fun performSwipe() {
        Log.i("swipe", "Swiping started")
        val screenDimensions = getScreenDimensions(this) // 直接使用 this
        val screenWidth = screenDimensions.x
        val screenHeight = screenDimensions.y
        val centerX = (screenWidth / 2).toFloat() // 屏幕中央的 X 坐标
        val startY = (screenHeight * 0.7).toFloat() // 起点：屏幕高度的 70%
        val endY = (screenHeight * 0.3).toFloat()   // 终点：屏幕高度的 30%
        val path = Path().apply {
            moveTo(centerX, startY) // 起点
            lineTo(centerX, endY)  // 终点
        }

        val gestureBuilder = GestureDescription.Builder().apply {
            addStroke(GestureDescription.StrokeDescription(path, 0, 300))
        }

        val result = dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("swipe", "Swipe gesture completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d("swipe", "Swipe gesture cancelled")
            }
        }, null)

        if (!result) {
            Log.d("swipe", "Gesture dispatch failed")
        }
    }
}
