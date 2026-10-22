package org.aionlink.iccoa

enum class GattStage { IDLE, CONNECTING, LINK_CONNECTED, DISCOVERING_SERVICES, INSPECTED, DISCONNECTED, TIMEOUT, ERROR }
data class KnownGattCharacteristic(val uuid: String, val properties: Int)
data class GattInspection(
    val stage: GattStage = GattStage.IDLE,
    val peerId: String? = null,
    val serviceCount: Int = 0,
    val unionSharePresent: Boolean = false,
    val knownCharacteristics: List<KnownGattCharacteristic> = emptyList(),
    val statusCode: Int? = null,
    val error: String? = null
)

/** Filters remote metadata without treating a link as protocol acceptance. */
object GattEvidence {
    fun summarize(peerId: String, serviceIds: List<String>, characteristicsInUnionShare: List<Pair<String, Int>>): GattInspection {
        val hasService = serviceIds.any { it.equals(AdvertisementEvidence.GATT_SERVICE, ignoreCase = true) }
        val known = if (!hasService) emptyList() else characteristicsInUnionShare.mapNotNull { (uuid, properties) ->
            val normalized = uuid.lowercase(java.util.Locale.ROOT)
            if (normalized in AdvertisementEvidence.knownCharacteristics) KnownGattCharacteristic(normalized, properties) else null
        }.distinctBy { it.uuid }
        return GattInspection(GattStage.INSPECTED, peerId, serviceIds.size, hasService, known)
    }
}
