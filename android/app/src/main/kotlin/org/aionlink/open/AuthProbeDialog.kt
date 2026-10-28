package org.aionlink.open

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun AuthProbeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comprobar canal del vehículo") },
        text = { Text("Con tu vehículo estacionado, se abrirá una conexión TCP breve al canal AUTH observado en Samsung y Xiaomi. No se envía clave ni mensaje. El resultado no confirma autenticación ni proyección y el receptor podría cerrar la red.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Comprobar mi vehículo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
