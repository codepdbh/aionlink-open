package org.aionlink.core

import org.junit.Assert.*
import org.junit.Test

class BackgroundHandoffTest {
    @Test fun activeBleAndNetworkSurviveBriefBackgroundHandoffs() {
        assertTrue(preserveBackgroundHandoff(bleActive = true, networkConnectingOrConnected = false))
        assertTrue(preserveBackgroundHandoff(bleActive = false, networkConnectingOrConnected = true))
        assertTrue(preserveBackgroundHandoff(bleActive = true, networkConnectingOrConnected = true))
    }
    @Test fun idleOrPreparationDoesNotKeepRadioResourcesInBackground() {
        assertFalse(preserveBackgroundHandoff(bleActive = false, networkConnectingOrConnected = false))
    }
}
