package org.aionlink.iccoa

enum class NormalLinkReadiness { READY, LOCAL_ADDRESS_UNAVAILABLE }

/** Missing local identity must block normal bootstrap, never silently drop the owner's PIN. */
fun normalLinkReadiness(localAddress: String?): NormalLinkReadiness =
    if (localAddress != null && validUnicastMac(localAddress)) NormalLinkReadiness.READY
    else NormalLinkReadiness.LOCAL_ADDRESS_UNAVAILABLE
