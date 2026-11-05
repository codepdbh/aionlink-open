package org.aionlink.open

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** Synthetic video only. Does not operate the screen, capture content or connect to a car. */
class DirectEncoderProbeTest {
    @Test fun drawsAndEncodesWithoutCaptureConsent() {
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val probe = DirectEncoderProbe(1280, 720) { errors.add(it) }
        try {
            probe.start()
            Thread.sleep(4000)
            assertTrue("produced=${probe.produced.get()} errors=$errors", probe.produced.get() > 5)
            assertTrue("encoded=${probe.encoded.get()} errors=$errors", probe.encoded.get() > 5)
            assertTrue("timestamp regressions=${probe.timestampRegressions.get()}", probe.timestampRegressions.get() == 0L)
            assertTrue("errors=$errors", errors.isEmpty())
        } finally { probe.close() }
    }
}
