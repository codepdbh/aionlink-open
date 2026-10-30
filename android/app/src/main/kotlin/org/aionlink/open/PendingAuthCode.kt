package org.aionlink.open

/** One attempt only; all discarded mutable copies are wiped. Main-thread use. */
internal class PendingAuthCode {
    private var value: ByteArray? = null
    val pending: Boolean get() = value != null
    fun set(code: ByteArray) { clear(); value = code }
    fun take(): ByteArray? = value.also { value = null }
    fun clear(): Boolean {
        val old = value ?: return false
        value = null
        old.fill(0)
        return true
    }
}
