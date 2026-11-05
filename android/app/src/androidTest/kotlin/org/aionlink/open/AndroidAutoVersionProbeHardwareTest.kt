package org.aionlink.open

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.aionlink.projection.AndroidAutoVersionProbe
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly selected only; a single loopback request to the owner-started developer server. */
@RunWith(AndroidJUnit4::class)
class AndroidAutoVersionProbeHardwareTest {
    @Test fun localDeveloperServerVersion() {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("allow_local_aa_probe") == "true")
        val result = AndroidAutoVersionProbe.local()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("AA_VERSION_PROBE", "${result.status}; phase=${result.phase}; major=${result.major}; minor=${result.minor}; result=${result.result}")
        })
        org.junit.Assert.assertEquals("Version exchange did not return a response", AndroidAutoVersionProbe.Status.RESPONSE, result.status)
    }
}
