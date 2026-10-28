package org.aionlink.open

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Dialog callbacks only: does not request any network. */
class AuthProbeDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun explicitConfirmationIsRequired() {
        var checks = 0
        var dismissals = 0
        compose.setContent { MaterialTheme { AuthProbeDialog({ checks++ }, { dismissals++ }) } }
        compose.onNodeWithText("Comprobar mi vehículo").assertIsDisplayed()
        assertEquals(0, checks)
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1, dismissals)
        assertEquals(0, checks)
        compose.onNodeWithText("Comprobar mi vehículo").performClick()
        assertEquals(1, checks)
    }
}
