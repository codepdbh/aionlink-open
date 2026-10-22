package org.aionlink.iccoa

/** Static client evidence only. A match does not identify or authenticate a vehicle. */
object AdvertisementEvidence {
    const val ADVERTISED_SERVICE = "0000fcfb-0000-1000-8000-00805f9b34fb"
    const val GATT_SERVICE = "2abcc850-9935-4f8a-ba84-123456789100"
    const val CLIENT_INFO = "2abcc850-9935-4f8a-ba84-123456789101"
    const val SERVER_INFO = "2abcc850-9935-4f8a-ba84-123456789102"
    const val CLIENT_AUTH_INFO = "2abcc850-9935-4f8a-ba84-123456789104"
    val knownCharacteristics = setOf(CLIENT_INFO, SERVER_INFO, CLIENT_AUTH_INFO)
    fun recognizedServices(advertised: Collection<String>): Set<String> =
        advertised.map { it.lowercase(java.util.Locale.ROOT) }
            .filter { it == ADVERTISED_SERVICE || it == GATT_SERVICE }.toSet()
}
