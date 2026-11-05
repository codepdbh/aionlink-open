package org.aionlink.open

import android.content.Context
import java.time.Instant

/** Last user-started virtual-display test only; contains no screen pixels, app names or network data. */
internal class VirtualProbeJournal(context: Context, name: String = "virtual-probe-journal") {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun start() {
        preferences.edit().putBoolean("active", true).putString("records", stamp("Inicio de prueba local")).apply()
    }

    fun sample(message: String) {
        if (!preferences.getBoolean("active", false)) return
        val safe = message.replace('\n', ' ').replace('\r', ' ').take(500)
        val records = (preferences.getString("records", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList() + stamp(safe)).takeLast(150)
        preferences.edit().putString("records", records.joinToString("\n")).apply()
    }

    fun finish(message: String) {
        sample(message)
        preferences.edit().putBoolean("active", false).apply()
    }

    fun snapshot(): String {
        val records = preferences.getString("records", "").orEmpty()
        if (records.isEmpty()) return "Sin prueba virtual guardada"
        val state = if (preferences.getBoolean("active", false)) "EN_CURSO_O_INTERRUMPIDA" else "FINALIZADA"
        return "Última prueba virtual: $state; solo datos sintéticos\n$records"
    }

    private fun stamp(message: String) = "${Instant.now()} $message"
}
