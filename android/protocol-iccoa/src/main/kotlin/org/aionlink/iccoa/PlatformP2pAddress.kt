package org.aionlink.iccoa

enum class AddressDisclosure { AVAILABLE, ANDROID_ANONYMIZED, UNAVAILABLE }

/** Preserve the platform return value; never synthesize a replacement address. */
class PlatformP2pAddress private constructor(private val value: String?, val disclosure: AddressDisclosure) {
    fun realValueOrNull(): String? = value.takeIf { disclosure == AddressDisclosure.AVAILABLE }
    fun forClientInfo(consentToAnonymized: Boolean): String {
        require(disclosure == AddressDisclosure.AVAILABLE ||
            disclosure == AddressDisclosure.ANDROID_ANONYMIZED && consentToAnonymized) {
            "Dato local no disponible o experimento no autorizado"
        }
        return requireNotNull(value)
    }
    override fun toString() = "PlatformP2pAddress($disclosure; value omitted)"
    companion object {
        fun fromPlatform(returnedValue: String?): PlatformP2pAddress {
            val kind = when {
                returnedValue?.equals("02:00:00:00:00:00", ignoreCase = true) == true -> AddressDisclosure.ANDROID_ANONYMIZED
                returnedValue != null && validUnicastMac(returnedValue) -> AddressDisclosure.AVAILABLE
                else -> AddressDisclosure.UNAVAILABLE
            }
            return PlatformP2pAddress(returnedValue, kind)
        }
    }
}
