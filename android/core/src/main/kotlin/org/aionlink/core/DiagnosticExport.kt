package org.aionlink.core

/** Freeze before Android's document picker can stop the Activity and close transports. */
class DiagnosticExport {
    private var pending: String? = null
    fun capture(content: () -> String) { pending = content() }
    fun consume(): String? = pending.also { pending = null }
}
