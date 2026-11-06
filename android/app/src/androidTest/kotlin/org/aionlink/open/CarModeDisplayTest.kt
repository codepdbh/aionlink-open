package org.aionlink.open

import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.aionlink.projection.TsRtpStreamer
import org.aionlink.projection.WfdFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Car mode on the S25 without the car: dashboard → H.264 → TS/RTP in memory, plus a touch on the dashboard. */
class CarModeDisplayTest {
    @Test fun dashboardStreamsAtCarSizeAndAcceptsTouches() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val units = AtomicLong(); val keyframes = AtomicLong(); val datagrams = AtomicInteger()
        val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
        val streamer = TsRtpStreamer({ datagrams.incrementAndGet() })
        val car = CarModeDisplay(instrumentation.targetContext, 1920, 960, 30, WfdFormats.videoBitrate(1920, 960, 30),
            onAccessUnit = { unit, pts, key ->
                units.incrementAndGet(); if (key) keyframes.incrementAndGet()
                synchronized(streamer) { streamer.onAccessUnit(unit, pts, key) }
            }, onError = { errors += it })
        try {
            instrumentation.runOnMainSync { car.start() }
            Thread.sleep(5000)
            // Static dashboard: frames come from clock updates plus encoder repeats. On the S25 (6 Nov 2026) this gave
            // 58 units in 5 s (~12 fps), never above the negotiated 30 fps; any redraw is encoded immediately.
            assertTrue("units=${units.get()}", units.get() in 25..200)
            assertTrue("keyframes=${keyframes.get()}", keyframes.get() >= 1)
            assertTrue("datagrams=${datagrams.get()}", datagrams.get() >= 25)
            instrumentation.runOnMainSync {
                car.touch(MotionEvent.ACTION_DOWN, 960f, 700f)
                car.touch(MotionEvent.ACTION_UP, 960f, 700f)
                car.requestKeyframe()
            }
            Thread.sleep(1500)
            assertEquals(emptyList<String>(), errors)
        } finally { instrumentation.runOnMainSync { car.close() } }
    }
}
