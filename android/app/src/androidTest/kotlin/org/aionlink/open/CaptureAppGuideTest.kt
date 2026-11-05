package org.aionlink.open

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CaptureAppGuideTest {
    @get:Rule val compose = createComposeRule()

    @Test fun continueAndCancelAreExplicit() {
        var continued = 0
        var dismissed = 0
        compose.setContent { MaterialTheme { CaptureAppGuide({ continued++ }, { dismissed++ }) } }
        compose.onNodeWithTag("capture-app-cancel").performClick()
        assertEquals(0, continued)
        assertEquals(1, dismissed)
        compose.onNodeWithTag("capture-app-continue").performClick()
        assertEquals(1, continued)
    }
}
