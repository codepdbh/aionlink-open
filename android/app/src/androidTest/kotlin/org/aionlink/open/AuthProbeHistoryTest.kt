package org.aionlink.open

import androidx.test.platform.app.InstrumentationRegistry
import org.aionlink.wifi.AuthProbeStage
import org.aionlink.wifi.P2pConnector
import org.junit.Assert.*
import org.junit.Test

/** No active P2P group, network request or socket. */
class AuthProbeHistoryTest {
    @Test fun unavailableResultSurvivesStopAndCannotOpenSocket() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val connector = P2pConnector(instrumentation.targetContext)
            connector.probeAuthChannel()
            assertEquals(1, connector.authProbeCount)
            assertEquals(AuthProbeStage.UNAVAILABLE, connector.authProbe.value.stage)
            val previous = connector.lastAuthProbe
            connector.stop()
            assertEquals(previous, connector.lastAuthProbe)
            assertEquals(1, connector.authProbeCount)
        }
    }
}
