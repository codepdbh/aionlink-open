package org.aionlink.open

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import org.aionlink.projection.UibcEvent

/**
 * Turns the receiver's touchscreen events into gestures on the phone. Samsung injects with a system-only
 * API; a regular app must use the user-enabled accessibility gesture API instead. Only the first pointer is
 * followed (taps, long presses, swipes and drags); multi-touch pinch is not reproduced. Keys map to global
 * actions (back, home, recents). Nothing is read from the screen or other apps.
 */
class TouchInjectionService : AccessibilityService() {
    companion object {
        @Volatile var instance: TouchInjectionService? = null; private set
        private const val ACTION_DOWN = 0
        private const val ACTION_UP = 1
        private const val ACTION_MOVE = 2
        private const val ACTION_CANCEL = 3
    }

    private class Stroke(val path: Path, val startedAt: Long, var lastX: Float, var lastY: Float)
    private var stroke: Stroke? = null
    private var carButtons: View? = null

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { hideCarButtons(); if (instance === this) instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun openCarLauncher() {
        startActivity(Intent(this, CarLauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Back and home for the car, which has neither button for the phone's apps. Called on the main thread. */
    fun showCarButtons() {
        if (carButtons != null) return
        fun button(text: String, action: () -> Unit) = TextView(this).apply {
            this.text = text; textSize = 28f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xB0000000.toInt()); setPadding(32, 24, 32, 24)
            setOnClickListener { action() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(button("◀") { performGlobalAction(GLOBAL_ACTION_BACK) })
            addView(button("⌂") { openCarLauncher() })
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        getSystemService(WindowManager::class.java).addView(panel, params)
        carButtons = panel
    }

    fun hideCarButtons() {
        carButtons?.let { getSystemService(WindowManager::class.java).removeView(it) }
        carButtons = null
    }

    /**
     * [x]/[y] are pixels of [displayId]: the phone screen (mirror) or AionLink's virtual display, where an embedded
     * Open Headunit lives. Gestures on another display need Android 11 (GestureDescription.setDisplayId).
     * Returns false when the gesture cannot be sent. Called on the main thread.
     */
    fun touch(action: Int, x: Float, y: Float, displayId: Int = android.view.Display.DEFAULT_DISPLAY): Boolean {
        if (displayId != android.view.Display.DEFAULT_DISPLAY && android.os.Build.VERSION.SDK_INT < 30) return false
        when (action and 0xFF) {
            ACTION_DOWN -> stroke = Stroke(Path().apply { moveTo(x, y) }, SystemClock.uptimeMillis(), x, y)
            ACTION_MOVE -> stroke?.let { if (it.lastX != x || it.lastY != y) { it.path.lineTo(x, y); it.lastX = x; it.lastY = y } }
            ACTION_UP -> stroke?.let { current ->
                stroke = null
                if (current.lastX != x || current.lastY != y) current.path.lineTo(x, y)
                val duration = (SystemClock.uptimeMillis() - current.startedAt).coerceIn(1L, GestureDescription.getMaxGestureDuration())
                val builder = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(current.path, 0, duration))
                if (android.os.Build.VERSION.SDK_INT >= 30) builder.setDisplayId(displayId)
                return dispatchGesture(builder.build(), null, null)
            }
            ACTION_CANCEL -> stroke = null
        }
        return true
    }

    fun key(event: UibcEvent.Key) {
        if (event.action != ACTION_UP) return
        when (event.keyCode) {
            4 -> performGlobalAction(GLOBAL_ACTION_BACK)
            3 -> performGlobalAction(GLOBAL_ACTION_HOME)
            187 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }
}
