package org.aionlink.open

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualDisplayVideoProbeTest {
    @Test fun privateOwnContentReachesH264Encoder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val reports = java.util.Collections.synchronizedList(mutableListOf<String>())
        val probe = VirtualDisplayVideoProbe(instrumentation.targetContext) { reports.add(it) }
        try {
            instrumentation.runOnMainSync { probe.start() }
            Thread.sleep(6000)
            assertTrue("drawn=${probe.drawnFrames}; reports=$reports", probe.drawnFrames > 5)
            assertTrue("encoded=${probe.encodedFrames}; reports=$reports", probe.encodedFrames > 5)
            assertTrue("muxed=${probe.muxedFrames}; reports=$reports", probe.muxedFrames > 5)
            assertTrue("rtp=${probe.rtpPackets}; reports=$reports", probe.rtpPackets > 5)
            assertTrue("malformed=${probe.invalidPackets}", probe.invalidPackets == 0L)
            assertTrue("errors=$reports", reports.none { it.contains("error H264") })
            assertTrue("missing interval/display report: $reports", reports.any { it.contains("intervalo ms=") && it.contains("pantalla principal=") })
        } finally { instrumentation.runOnMainSync { probe.close() } }
    }
}
