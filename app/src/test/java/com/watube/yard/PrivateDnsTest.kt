package com.watube.yard

import com.watube.yard.util.PrivateDns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet6Address
import java.net.InetAddress

/**
 * What the encrypted DNS (PrivateDns) sends where: public names to the resolver, local names to
 * the system, and the resolvers themselves reached without any clear text lookup.
 */
class PrivateDnsTest {
    @Test
    fun publicNamesGoToTheResolver() {
        listOf(
            "www.youtube.com", "rr3---sn-25ge7nsk.googlevideo.com", "sponsor.ajay.app",
            "localhost.example.com"
        ).forEach { assertFalse(it, PrivateDns.isLocalName(it)) }
    }

    @Test
    fun localNamesStayOnTheLocalNetwork() {
        listOf("localhost", "LOCALHOST.", "piped.localhost", "nas.local", "piped.lan", "box.home.arpa", "tv")
            .forEach { assertTrue(it, PrivateDns.isLocalName(it)) }
    }

    @Test
    fun ipv6AddressesOnlyAskedWhereTheNetworkReachesThem() {
        val ipv4Route = InetAddress.getByName("0.0.0.0")
        val ipv6Route = InetAddress.getByName("::")
        // an IPv4-only network got IPv6 answers alone when the IPv4 one was lost
        assertFalse(PrivateDns.asksIpv6(listOf(ipv4Route)))
        assertFalse(PrivateDns.asksIpv6(emptyList()))
        assertTrue(PrivateDns.asksIpv6(listOf(ipv4Route, ipv6Route)))
        assertTrue(PrivateDns.asksIpv6(listOf(ipv6Route)))
        // unknown network: both kinds, as before
        assertTrue(PrivateDns.asksIpv6(null))
    }

    @Test
    fun quad9ByDefaultAndForAnUnknownChoice() {
        assertEquals(PrivateDns.Resolver.QUAD9, PrivateDns.Resolver.DEFAULT)
        assertEquals(PrivateDns.Resolver.QUAD9, PrivateDns.Resolver.of(null))
        assertEquals(PrivateDns.Resolver.QUAD9, PrivateDns.Resolver.of("removed-resolver"))
        assertEquals(PrivateDns.Resolver.MULLVAD, PrivateDns.Resolver.of("mullvad"))
    }

    @Test
    fun resolversAreReachedOverHttpsAtKnownAddresses() {
        PrivateDns.Resolver.entries.forEach { resolver ->
            assertTrue(resolver.url, resolver.url.startsWith("https://"))
            val addresses = resolver.bootstrap.map { address ->
                // literal addresses only: a name here would need a clear text lookup first
                assertTrue(address, address.all { it.isDigit() || it in ".:abcdef" })
                InetAddress.getByName(address)
            }
            // IPv4 and IPv6, so that either kind of network finds the resolver
            assertTrue(resolver.name, addresses.any { it is Inet6Address })
            assertTrue(resolver.name, addresses.any { it !is Inet6Address })
        }
    }

    @Test
    fun keysMatchTheSettingsList() {
        // res/values/array.xml, privateDnsResolverValues
        assertEquals(
            listOf("quad9", "mullvad", "cloudflare", "dns4eu"),
            PrivateDns.Resolver.entries.map { it.key }
        )
    }
}
