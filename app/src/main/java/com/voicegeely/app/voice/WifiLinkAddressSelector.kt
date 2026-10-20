package com.voicegeely.app.voice

import java.net.Inet4Address
import java.net.InetAddress

/** Select only a usable local IPv4; never infer an address from the radio state. */
internal object WifiLinkAddressSelector {
    fun firstIpv4(addresses: List<InetAddress>): String? = addresses
        .firstOrNull { address ->
            address is Inet4Address && !address.isAnyLocalAddress &&
                !address.isLinkLocalAddress && !address.isLoopbackAddress
        }
        ?.hostAddress
}
