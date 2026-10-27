package org.aionlink.iccoa

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Own client identity and platform metadata. Anonymized metadata requires explicit consent. */
class ClientInfo(private val id: String, private val model: String, private val pin: String,
                 platformAddress: PlatformP2pAddress, private val supports5Ghz: Boolean,
                 consentToAnonymized: Boolean = false) {
    private val p2pMac = platformAddress.forClientInfo(consentToAnonymized)
    val usesAnonymizedMetadata = platformAddress.disclosure == AddressDisclosure.ANDROID_ANONYMIZED
    init {
        require(id.matches(Regex("[A-Za-z0-9]{6}"))) { "Identidad local no válida" }
        require(model.isNotBlank() && model.toByteArray(Charsets.UTF_8).size <= 64) { "Modelo local no válido" }
        require(pin.matches(Regex("[0-9]{6}"))) { "Se requieren seis dígitos" }
    }
    fun encode(): ByteArray = JSONObject().put("id", id).put("model", model)
        .put("name", "AionLink Open").put("pinCodeOrAuthentication", pin)
        .put("band", if (supports5Ghz) 1 else 0).put("mac", p2pMac).put("type", 1001)
        .toString().toByteArray(Charsets.UTF_8).also { require(it.size <= 512) { "ClientInfo excede el límite GATT" } }
    override fun toString() = "ClientInfo(redacted)"
}

fun validUnicastMac(value: String): Boolean {
    if (!value.matches(Regex("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))) return false
    if (value.equals("02:00:00:00:00:00", true) || value == "00:00:00:00:00:00") return false
    return value.substring(0, 2).toInt(16) and 1 == 0
}

/** Ephemeral network parameters from the selected peer, never diagnostics or persistence. */
enum class NetworkMode(val wireValue: Int) { P2P(1001), ACCESS_POINT(1002) }
enum class ServerInfoFault { SIZE, UTF8, JSON, MODE, REQUIRED_FIELDS, SSID, PSK, MAC, FREQUENCY, PORT }
class ServerInfoException(val fault: ServerInfoFault) : IllegalArgumentException("ServerInfo no válido: $fault; contenido omitido")

class ServerInfo private constructor(val ssid: String, val passphrase: String, val mac: String,
                                    val frequency: Int, val port: Int, val mode: NetworkMode) {
    override fun toString() = "ServerInfo(redacted)"
    companion object {
        fun parse(bytes: ByteArray): ServerInfo {
            checkField(bytes.isNotEmpty() && bytes.size <= 4096, ServerInfoFault.SIZE)
            val text = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: Exception) { throw ServerInfoException(ServerInfoFault.UTF8) }
            val json = try { JSONObject(text) } catch (_: Exception) { throw ServerInfoException(ServerInfoFault.JSON) }
            try {
                val mode = NetworkMode.entries.firstOrNull { it.wireValue == json.optInt("type", 1001) }
                    ?: throw ServerInfoException(ServerInfoFault.MODE)
                checkField(listOf("ssid", "psk", "mac", "freq", "port").all { json.has(it) && !json.isNull(it) }, ServerInfoFault.REQUIRED_FIELDS)
                val ssid = json.get("ssid") as? String ?: throw ServerInfoException(ServerInfoFault.SSID)
                val psk = json.get("psk") as? String ?: throw ServerInfoException(ServerInfoFault.PSK)
                val mac = json.get("mac") as? String ?: throw ServerInfoException(ServerInfoFault.MAC)
                val freq = try { json.getInt("freq") } catch (_: Exception) { throw ServerInfoException(ServerInfoFault.FREQUENCY) }
                val rawPort = json.get("port")
                val port = try { when (rawPort) {
                    is String -> rawPort.toInt()
                    is Int, is Long -> rawPort.toString().toInt()
                    else -> throw IllegalArgumentException()
                } } catch (_: Exception) { throw ServerInfoException(ServerInfoFault.PORT) }
                checkField(ssid.toByteArray(Charsets.UTF_8).size in 1..32 && '\u0000' !in ssid, ServerInfoFault.SSID)
                if (mode == NetworkMode.P2P) checkField(ssid.startsWith("DIRECT-") && ssid.length >= 9, ServerInfoFault.SSID)
                checkField(psk.length in 8..63 && psk.all { it.code in 32..126 }, ServerInfoFault.PSK)
                checkField(validUnicastMac(mac), ServerInfoFault.MAC)
                checkField(freq in 2412..2484 || freq in 5000..5900 || mode == NetworkMode.ACCESS_POINT && freq == 0, ServerInfoFault.FREQUENCY)
                checkField(port in 1..65535, ServerInfoFault.PORT)
                return ServerInfo(ssid, psk, mac, freq, port, mode)
            } catch (error: ServerInfoException) {
                throw error
            } catch (_: Exception) {
                // Never propagate JSON exceptions: their messages can contain credential values.
                throw ServerInfoException(ServerInfoFault.JSON)
            }
        }
        private fun checkField(condition: Boolean, fault: ServerInfoFault) { if (!condition) throw ServerInfoException(fault) }
    }
}
