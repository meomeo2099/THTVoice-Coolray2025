package com.voicegeely.app.voice

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiLinkAddressSelectorTest {
    @Test fun choosesUsableIpv4AfterIpv6AndLocalOnlyAddresses() {
        val addresses = listOf("::1", "0.0.0.0", "169.254.2.1", "127.0.0.1", "192.168.1.8")
            .map(InetAddress::getByName)
        assertEquals("192.168.1.8", WifiLinkAddressSelector.firstIpv4(addresses))
    }

    @Test fun noIpv4DoesNotInventAnAddress() {
        assertNull(WifiLinkAddressSelector.firstIpv4(listOf(InetAddress.getByName("::1"))))
    }
}
