package org.aionlink.bluetooth

data class DiscoveredDevice(
    val id: String, val name: String, val transport: String, val rssi: Int?,
    val observedServiceCount: Int = 0, val recognizedServices: Set<String> = emptySet(),
    val connectable: Boolean? = null
)

/** Preserve first-seen position and accumulated public evidence within one search. */
internal fun mergeObservation(current: List<DiscoveredDevice>, incoming: DiscoveredDevice): List<DiscoveredDevice> {
    val index = current.indexOfFirst { it.id == incoming.id }
    if (index < 0) return current + incoming
    val previous = current[index]
    val merged = incoming.copy(
        name = if (incoming.name == "Sin nombre") previous.name else incoming.name,
        rssi = incoming.rssi ?: previous.rssi,
        connectable = incoming.connectable ?: previous.connectable,
        observedServiceCount = maxOf(previous.observedServiceCount, incoming.observedServiceCount),
        recognizedServices = previous.recognizedServices + incoming.recognizedServices)
    return current.toMutableList().also { it[index] = merged }
}
