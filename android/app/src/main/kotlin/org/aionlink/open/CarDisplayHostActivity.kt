package org.aionlink.open

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View

/**
 * Black, empty AionLink activity placed on AionLink's own virtual display. Android only lets a caller open another
 * app's (allowEmbedded) activity on a virtual display if the caller holds the system ACTIVITY_EMBEDDING permission or
 * already has an activity on that display ("Permission Denial ... with launchDisplayId" otherwise, measured on the
 * S25). This host gives AionLink that presence; Open Headunit is then opened on top of it.
 */
class CarDisplayHostActivity : Activity() {
    companion object {
        @Volatile private var current: CarDisplayHostActivity? = null
        val present get() = current != null
        fun finishCurrent() { current?.finish(); current = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this).apply { setBackgroundColor(Color.BLACK) })
        current = this
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }
}
