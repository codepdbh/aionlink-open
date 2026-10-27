package org.aionlink.open

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI fixtures only: no Bluetooth, car identity, Wi-Fi operation or real PIN. */
class ConnectionCodeDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun blockedPreflightStillShowsCodeFieldButCannotSend() {
        var sends = 0
        compose.setContent { MaterialTheme {
            ConnectionCodeDialog(CodeReadiness.BLOCKED, "Requisito local no disponible", { _, _ -> sends++ }, {})
        } }
        compose.onNodeWithTag("connection-code").assertIsDisplayed().performTextInput("123456")
        compose.onNodeWithTag("submit-code").assertIsNotEnabled()
        assertEquals(0, sends)
    }
    @Test fun readyRequiresSixDigitsAndTransmitsOnlyOnExplicitConfirmation() {
        var submitted: String? = null
        compose.setContent { MaterialTheme {
            ConnectionCodeDialog(CodeReadiness.READY, "Datos locales listos", { value, experimental -> submitted = value; assertFalse(experimental) }, {})
        } }
        compose.onNodeWithTag("connection-code").performTextInput("12345")
        compose.onNodeWithTag("submit-code").assertIsNotEnabled()
        compose.onNodeWithTag("connection-code").performTextInput("6")
        compose.onNodeWithTag("submit-code").assertIsEnabled().performClick()
        assertEquals("123456", submitted)
    }
    @Test fun preparingShowsFieldWithoutAllowingPrematureSend() {
        compose.setContent { MaterialTheme {
            ConnectionCodeDialog(CodeReadiness.PREPARING, "Comprobando requisitos", { _, _ -> }, {})
        } }
        compose.onNodeWithTag("connection-code").assertIsDisplayed().performTextInput("123456")
        compose.onNodeWithTag("submit-code").assertIsNotEnabled()
    }
    @Test fun closingAndReopeningDiscardsTheCode() {
        compose.setContent { MaterialTheme {
            var visible by remember { mutableStateOf(true) }
            if (visible) ConnectionCodeDialog(CodeReadiness.BLOCKED, "Requisito pendiente", { _, _ -> }, { visible = false })
            else TextButton(onClick = { visible = true }, modifier = Modifier.testTag("reopen")) { Text("Reabrir prueba") }
        } }
        compose.onNodeWithTag("connection-code").performTextInput("123456")
        compose.onNodeWithTag("close-code").performClick()
        compose.onNodeWithTag("reopen").performClick()
        compose.onNodeWithTag("connection-code").assertTextContains("", substring = false)
        compose.onNodeWithTag("submit-code").assertIsNotEnabled()
    }
    @Test fun anonymousExperimentRequiresSeparateExplicitConsent() {
        var sends = 0
        compose.setContent { MaterialTheme {
            ConnectionCodeDialog(CodeReadiness.ANONYMIZED_EXPERIMENT, ANONYMIZED_EXPERIMENT_DETAIL,
                { value, optedIn -> assertEquals("123456", value); assertTrue(optedIn); sends++ }, {})
        } }
        compose.onNodeWithTag("anonymous-consent").assertIsDisplayed()
        compose.onNodeWithTag("connection-code").performTextInput("123456")
        compose.onNodeWithTag("submit-code").assertIsNotEnabled()
        compose.onNodeWithTag("anonymous-consent").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag("submit-code").assertIsEnabled().performClick()
        assertEquals(1, sends)
    }
    @Test fun anonymousConsentIsNotRetainedAfterClosing() {
        compose.setContent { MaterialTheme {
            var visible by remember { mutableStateOf(true) }
            if (visible) ConnectionCodeDialog(CodeReadiness.ANONYMIZED_EXPERIMENT, "Prueba de interfaz",
                { _, _ -> }, { visible = false })
            else TextButton(onClick = { visible = true }, modifier = Modifier.testTag("reopen")) { Text("Reabrir prueba") }
        } }
        compose.onNodeWithTag("anonymous-consent").performScrollTo().performClick()
        compose.onNodeWithTag("close-code").performClick()
        compose.onNodeWithTag("reopen").performClick()
        compose.onNodeWithTag("anonymous-consent").performScrollTo().assertIsOff()
        compose.onNodeWithTag("submit-code").assertIsNotEnabled()
    }
}
