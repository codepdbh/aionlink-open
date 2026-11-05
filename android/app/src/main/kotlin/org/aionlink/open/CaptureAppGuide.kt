package org.aionlink.open

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** The system owns the actual capture choice and asks for consent every session. */
@Composable
fun CaptureAppGuide(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir una aplicación") },
        text = { Text("En el selector de Android elige una aplicación y selecciona la ventana que quieras mostrar. La captura de esa app, su sonido y el táctil aún no están verificados en el coche. Al bloquear el teléfono la imagen puede detenerse. Si usas otra app, desactiva el lanzador automático de AionLink.") },
        confirmButton = { TextButton(onClick = onContinue, modifier = Modifier.testTag("capture-app-continue")) { Text("Elegir en Android") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("capture-app-cancel")) { Text("Cancelar") } }
    )
}
