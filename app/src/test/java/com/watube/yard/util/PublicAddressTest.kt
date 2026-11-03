package com.watube.yard.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// region Networking
class PublicAddressTest {

    @Test
    fun `local names and private ranges are refused`() {
        listOf(
            "localhost", "printer.local", "app.localhost",
            // trailing dot, single label (local search domain), local network suffixes
            "localhost.", "printer.local.", "nas", "router.lan", "box.home.arpa", "db.internal",
            // other spellings of 127.0.0.1 and an IPv4-mapped IPv6 loopback
            "2130706433", "127.1", "[::ffff:127.0.0.1]",
            "127.0.0.1", "0.0.0.0", "10.0.2.2", "172.16.4.1", "192.168.1.10", "169.254.1.1",
            // carrier grade NAT, also used by Tailscale and ZeroTier networks
            "100.64.0.1", "100.100.100.100", "100.127.255.254",
            "198.18.0.1", "198.19.255.1", "192.0.0.8", "240.0.0.1", "255.255.255.255",
            "[::1]", "[fd12:3456::1]", "[fe80::1]", "[64:ff9b::a00:1]"
        ).forEach { assertFalse(it, PublicAddress.isPublicHost(it)) }
    }

    @Test
    fun `public addresses and names are allowed`() {
        listOf(
            "rr3---sn-25ge7nsk.googlevideo.com", "pipedproxy.example.org",
            "8.8.8.8", "100.63.255.255", "100.128.0.1", "198.20.0.1",
            "[2001:4860:4860::8888]"
        ).forEach { assertTrue(it, PublicAddress.isPublicHost(it)) }
    }
}
// endregion
