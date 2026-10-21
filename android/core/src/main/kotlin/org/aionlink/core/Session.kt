package org.aionlink.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.aionlink.protocol.*

enum class ConnectionState {
    IDLE, DISCOVERING, DEVICE_FOUND, CONNECTING, PAIRING, NEGOTIATING,
    AUTHENTICATING, SESSION_ESTABLISHED, PROJECTION_ACTIVE, DISCONNECTED,
    TIMEOUT, AUTHENTICATION_FAILED, PROTOCOL_UNSUPPORTED, TRANSPORT_ERROR, PERMISSION_DENIED
}

/** No public setter can turn a discovered/paired device into a verified session. */
class SessionCoordinator(private val adapter: ProtocolAdapter, private val timeoutMs: Long = 5000) {
    private val mutableState = MutableStateFlow(ConnectionState.IDLE)
    val state = mutableState.asStateFlow()
    init { require(timeoutMs > 0) }
    fun startDiscovery() { mutableState.value = ConnectionState.DISCOVERING }
    fun deviceFound() {
        check(mutableState.value in setOf(ConnectionState.DISCOVERING, ConnectionState.DEVICE_FOUND))
        mutableState.value = ConnectionState.DEVICE_FOUND
    }
    fun discoveryFinished() {
        if (mutableState.value == ConnectionState.DISCOVERING) mutableState.value = ConnectionState.IDLE
    }
    fun permissionDenied() { mutableState.value = ConnectionState.PERMISSION_DENIED }
    fun transportError() { mutableState.value = ConnectionState.TRANSPORT_ERROR }
    fun disconnect() { mutableState.value = ConnectionState.DISCONNECTED }
    suspend fun negotiate(peer: Peer) {
        check(mutableState.value == ConnectionState.DEVICE_FOUND)
        mutableState.value = ConnectionState.NEGOTIATING
        try {
            val result = withTimeoutOrNull(timeoutMs) { adapter.negotiate(peer) }
            mutableState.value = if (result == null) ConnectionState.TIMEOUT else ConnectionState.PROTOCOL_UNSUPPORTED
        } catch (e: kotlinx.coroutines.CancellationException) {
            mutableState.value = ConnectionState.DISCONNECTED
            throw e
        } catch (e: Exception) {
            mutableState.value = ConnectionState.TRANSPORT_ERROR
        }
    }
}
