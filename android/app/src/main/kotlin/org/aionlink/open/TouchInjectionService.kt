package org.aionlink.open

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
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

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    /** [x]/[y] are already mapped to phone-screen pixels. Called on the main thread. */
    fun touch(action: Int, x: Float, y: Float) {
        when (action and 0xFF) {
            ACTION_DOWN -> stroke = Stroke(Path().apply { moveTo(x, y) }, SystemClock.uptimeMillis(), x, y)
            ACTION_MOVE -> stroke?.let { if (it.lastX != x || it.lastY != y) { it.path.lineTo(x, y); it.lastX = x; it.lastY = y } }
            ACTION_UP -> stroke?.let { current ->
                stroke = null
                if (current.lastX != x || current.lastY != y) current.path.lineTo(x, y)
                val duration = (SystemClock.uptimeMillis() - current.startedAt).coerceIn(1L, GestureDescription.getMaxGestureDuration())
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(current.path, 0, duration)).build()
                dispatchGesture(gesture, null, null)
            }
            ACTION_CANCEL -> stroke = null
        }
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
