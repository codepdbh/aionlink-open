package org.aionlink.open

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuthExchangeDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun requiresSixDigitsAndExplicitClick() {
        var submitted: String? = null
        compose.setContent { MaterialTheme {
            var visible by remember { mutableStateOf(true) }
            if (visible) AuthExchangeDialog({ submitted = it; visible = false }, { visible = false })
        } }
        compose.onNodeWithTag("auth-exchange-send").assertIsNotEnabled()
        compose.onNodeWithTag("auth-exchange-pin").performTextInput("12345")
        compose.onNodeWithTag("auth-exchange-send").assertIsNotEnabled()
        compose.onNodeWithTag("auth-exchange-pin").performTextInput("6")
        assertNull(submitted)
        compose.onNodeWithTag("auth-exchange-send").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals("123456", submitted)
    }
}
