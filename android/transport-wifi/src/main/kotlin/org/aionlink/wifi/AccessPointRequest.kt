package org.aionlink.wifi

import android.net.MacAddress
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import org.aionlink.iccoa.NetworkMode
import org.aionlink.iccoa.ServerInfo

/** Only the exact selected receiver, without requesting Internet or changing global routing. */
fun accessPointRequest(info: ServerInfo): NetworkRequest {
    require(info.mode == NetworkMode.ACCESS_POINT)
    val specifier = WifiNetworkSpecifier.Builder().setSsid(info.ssid)
        .setBssid(MacAddress.fromString(info.mac)).setWpa2Passphrase(info.passphrase).build()
    return NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(specifier).build()
}
