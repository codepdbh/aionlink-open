package org.aionlink.open

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.aionlink.bluetooth.BootstrapStage
import org.aionlink.bluetooth.BootstrapStatus
import org.aionlink.core.preserveBackgroundHandoff
import org.aionlink.iccoa.NetworkMode
import org.aionlink.wifi.NetworkStage
import org.aionlink.wifi.P2pStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual stage classifications used by the Activity, without radio operations. */
@RunWith(AndroidJUnit4::class)
class BackgroundHandoffStateTest {
    @Test fun keepsWaitingForServerAndBothConnectedNetworkModes() {
        val ble = BootstrapStatus(stage = BootstrapStage.WAITING_SERVER)
        assertTrue(preserveBackgroundHandoff(ble.busy, P2pStatus(stage = NetworkStage.READY).handoffActive))
        for (mode in NetworkMode.entries) {
            for (stage in listOf(NetworkStage.CONNECTING, NetworkStage.CONNECTED)) {
                val network = P2pStatus(stage = stage, mode = mode)
                assertTrue(preserveBackgroundHandoff(false, network.handoffActive))
            }
        }
    }
    @Test fun noHandoffForFinishedOrPreparedStates() {
        for (stage in listOf(NetworkStage.IDLE, NetworkStage.PREPARING, NetworkStage.READY, NetworkStage.ERROR, NetworkStage.CANCELLED)) {
            assertFalse(preserveBackgroundHandoff(false, P2pStatus(stage = stage).handoffActive))
        }
        assertFalse(preserveBackgroundHandoff(BootstrapStatus(stage = BootstrapStage.ERROR).busy, false))
    }
}
