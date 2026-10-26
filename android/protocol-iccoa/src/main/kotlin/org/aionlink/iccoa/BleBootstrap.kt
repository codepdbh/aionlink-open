package org.aionlink.iccoa

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Values belong to this client. Never accept OEM identity or placeholder addresses. */
class ClientInfo(private val id: String, private val model: String, private val pin: String,
                 private val p2pMac: String, private val supports5Ghz: Boolean) {
    init {
        require(id.matches(Regex("[A-Za-z0-9]{6}"))) { "Identidad local no válida" }
        require(model.isNotBlank() && model.toByteArray(Charsets.UTF_8).size <= 64) { "Modelo local no válido" }
        require(pin.matches(Regex("[0-9]{6}"))) { "Se requieren seis dígitos" }
        require(validUnicastMac(p2pMac)) { "Android no proporcionó una dirección P2P válida" }
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
class ServerInfo private constructor(val ssid: String, val passphrase: String, val mac: String,
                                    val frequency: Int, val port: Int) {
    override fun toString() = "ServerInfo(redacted)"
    companion object {
        fun parse(bytes: ByteArray): ServerInfo {
            require(bytes.isNotEmpty() && bytes.size <= 4096) { "Tamaño ServerInfo no válido" }
            try {
                val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                val json = JSONObject(text)
                require(json.optInt("type", 1001) == 1001)
                val ssid = json.getString("ssid")
                val psk = json.getString("psk")
                val mac = json.getString("mac")
                val freq = json.getInt("freq")
                val port = json.getString("port").toInt()
                require(ssid.startsWith("DIRECT-") && ssid.toByteArray(Charsets.UTF_8).size in 9..32)
                require(psk.length in 8..63 && psk.all { it.code in 32..126 })
                require(validUnicastMac(mac))
                require(freq in 2412..2484 || freq in 5000..5900)
                require(port in 1..65535)
                return ServerInfo(ssid, psk, mac, freq, port)
            } catch (_: Exception) {
                // Never propagate JSON exceptions: their messages can contain credential values.
                throw IllegalArgumentException("ServerInfo no válido o modo de red no admitido; contenido omitido")
            }
        }
    }
}
