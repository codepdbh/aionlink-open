package org.aionlink.projection

/** The owner's ordered list of launcher entries, stored as one component name per line. */
object CarAppSelection {
    fun decode(stored: String): List<String> = stored.lines().filter { it.isNotBlank() }.distinct()

    fun encode(selection: List<String>): String = selection.joinToString("\n")

    /** Adds [key] at the end, in the order the owner chose, or removes it if already there. */
    fun toggle(selection: List<String>, key: String): List<String> =
        if (key in selection) selection - key else selection + key
}
