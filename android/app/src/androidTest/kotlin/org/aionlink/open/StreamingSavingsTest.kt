package org.aionlink.open

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Runs on the phone's real encoder. Touches brightness only if the app holds the system settings permission. */
class StreamingSavingsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    /** Encoder output per second while a view animates at the display's own rate. */
    private fun encodedFps(format: MediaFormat): Double {
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        encoder.start()
        val running = AtomicBoolean(true)
        val units = AtomicInteger()
        val drain = Thread {
            val info = MediaCodec.BufferInfo()
            while (running.get()) {
                val index = encoder.dequeueOutputBuffer(info, 50_000)
                if (index < 0) continue
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) units.incrementAndGet()
                encoder.releaseOutputBuffer(index, false)
            }
        }.apply { start() }
        var probe: OwnDisplayProbe? = null
        onMain { probe = OwnDisplayProbe(context, target = surface).also { it.start() } }
        Thread.sleep(1000)
        val before = units.get()
        val started = System.nanoTime()
        Thread.sleep(4000)
        val fps = (units.get() - before) / ((System.nanoTime() - started) / 1e9)
        onMain { probe?.close() }
        running.set(false); drain.join()
        encoder.stop(); encoder.release(); surface.release()
        return fps
    }

    @Test fun encoderHonoursTheNegotiatedFrameRate() {
        val uncapped = encodedFps(ScreenEncoder.format(1280, 720, 30).apply { removeKey(MediaFormat.KEY_MAX_FPS_TO_ENCODER) })
        val capped = encodedFps(ScreenEncoder.format(1280, 720, 30))
        android.util.Log.i("AionLinkTest", "encoder fps: uncapped=$uncapped capped=$capped")
        assertTrue("capped=$capped", capped in 5.0..33.0)
    }

    @Test fun ownDisplayRendersFrames() {
        val reports = java.util.Collections.synchronizedList(mutableListOf<String>())
        var probe: OwnDisplayProbe? = null
        onMain { probe = OwnDisplayProbe(context, onReport = { reports += it }).also { it.start() } }
        Thread.sleep(6000)
        val frames = probe!!.framesRendered
        onMain { probe?.close() }
        android.util.Log.i("AionLinkTest", "own display frames=$frames; ${reports.firstOrNull()}")
        assertTrue("frames=$frames", frames > 0)
        assertEquals(1, reports.size)
    }

    @Test fun brightnessIsRestoredAfterTheStreamAndAfterADeath() {
        assumeTrue(Settings.System.canWrite(context))
        val resolver = context.contentResolver
        val level = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        val mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val saved = PhoneSavings(context) { events += it }
        val wasEnabled = saved.enabled.value
        try {
            saved.setEnabled(true)
            onMain { saved.onStreamStarted { 0 } }
            assertEquals(1, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS))
            onMain { saved.onStreamStopped() }
            assertEquals(level, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS))
            assertEquals(mode, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE))
            assertTrue(events.any { it.startsWith("Batería de la sesión") })
            onMain { saved.onStreamStarted { 0 } }
            // A new instance stands in for the process restarted after dying mid-stream.
            PhoneSavings(context) { events += it }.restorePending()
            assertEquals(level, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS))
            onMain { saved.onStreamStopped() }
        } finally {
            saved.setEnabled(wasEnabled)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, level)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
        }
    }
}
