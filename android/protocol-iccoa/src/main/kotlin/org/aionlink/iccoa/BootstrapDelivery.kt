package org.aionlink.iccoa

/** A response may race the write callback. Deliver once, only after the write succeeds. */
class BootstrapDelivery {
    private var acknowledged = false
    private var delivered = false
    private var pending: ServerInfo? = null
    fun response(info: ServerInfo): ServerInfo? {
        if (delivered) return null
        pending = info
        return takeReady()
    }
    fun acknowledge(): ServerInfo? { acknowledged = true; return takeReady() }
    private fun takeReady(): ServerInfo? {
        if (!acknowledged || delivered) return null
        val result = pending ?: return null
        pending = null; delivered = true
        return result
    }
    fun clear() { pending = null; acknowledged = false; delivered = false }
}
