package com.watube.yard.util

import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import com.watube.yard.WatubeApp
import com.watube.yard.api.GenericUserAgentInterceptor
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.PreferenceHelper
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// region Networking
/**
 * Encrypted DNS for every request of the app (DNS-over-HTTPS): the names of the hosts Watube
 * contacts no longer travel in clear text on the network, where the provider of the connection
 * could read or alter them. On by default, with Quad9.
 *
 * Strict, like Android's own "Private DNS": when the resolver cannot answer, the lookup fails
 * instead of falling back to the network's DNS, which would send the very same name in clear.
 * Only local names (localhost, *.local...), which no public resolver knows, go to the system.
 *
 * Like the system resolver (AI_ADDRCONFIG), IPv6 addresses are only asked for on a network that
 * can reach them. When both kinds were asked on an IPv4-only network and the IPv4 answer was lost,
 * the lookup still succeeded with IPv6 addresses alone: every connection then failed after its
 * timeout (a video, or the next one of autoplay, never loaded).
 */
object PrivateDns : Dns {
    /**
     * The resolvers offered. [bootstrap] are their own addresses: knowing them, reaching the
     * resolver needs no clear text lookup of its name.
     */
    enum class Resolver(
        val key: String,
        val url: String,
        val bootstrap: List<String>,
        val tls13Only: Boolean
    ) {
        /** Swiss non-profit foundation, keeps no IP address, blocks known malicious domains. */
        QUAD9(
            "quad9",
            "https://dns.quad9.net/dns-query",
            listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9"),
            tls13Only = true
        ),
        MULLVAD(
            "mullvad",
            "https://dns.mullvad.net/dns-query",
            listOf("194.242.2.2", "2a07:e340::2"),
            tls13Only = false
        ),
        CLOUDFLARE(
            "cloudflare",
            "https://cloudflare-dns.com/dns-query",
            listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
            tls13Only = true
        ),

        /** The public resolver backed by the European Union, in its variant without filtering. */
        DNS4EU(
            "dns4eu",
            "https://unfiltered.joindns4.eu/dns-query",
            listOf(
                "86.54.11.100", "86.54.11.200",
                "2a13:1001::86:54:11:100", "2a13:1001::86:54:11:200"
            ),
            tls13Only = false
        );

        companion object {
            val DEFAULT = QUAD9

            fun of(key: String?): Resolver = entries.firstOrNull { it.key == key } ?: DEFAULT
        }
    }

    /** Names that only the local network can resolve. */
    private val LOCAL_SUFFIXES = listOf(".localhost", ".local", ".lan", ".home.arpa", ".internal")

    /** One client per resolver, with and without IPv6 answers. */
    private val clients = ConcurrentHashMap<Pair<Resolver, Boolean>, DnsOverHttps>()

    val isEnabled: Boolean get() = PreferenceHelper.getBoolean(PreferenceKeys.PRIVATE_DNS, true)

    val resolver: Resolver
        get() = Resolver.of(
            PreferenceHelper.getString(PreferenceKeys.PRIVATE_DNS_RESOLVER, Resolver.DEFAULT.key)
        )

    override fun lookup(hostname: String): List<InetAddress> {
        if (!isEnabled || isLocalName(hostname)) return Dns.SYSTEM.lookup(hostname)
        val ipv6 = asksIpv6(defaultRouteAddresses())
        // read once: a resolver changed meanwhile must not get the other one's client
        val resolver = resolver
        return clients.getOrPut(resolver to ipv6) { newClient(resolver, ipv6) }.lookup(hostname)
    }

    /**
     * The addresses of the default routes of the network in use, null when unknown. One of them
     * is IPv6 when the network can reach IPv6 hosts (a VPN included).
     */
    private fun defaultRouteAddresses(): List<InetAddress>? {
        val connectivity = WatubeApp.instance.getSystemService<ConnectivityManager>() ?: return null
        val links = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return null
        return links.routes.filter { it.isDefaultRoute }.map { it.destination.address }
    }

    /** Whether to ask for IPv6 addresses; when the network is unknown, both kinds are asked. */
    internal fun asksIpv6(defaultRouteAddresses: List<InetAddress>?): Boolean =
        defaultRouteAddresses == null || defaultRouteAddresses.any { it is Inet6Address }

    internal fun isLocalName(hostname: String): Boolean {
        val name = hostname.lowercase(Locale.ROOT).trimEnd('.')
        return name == "localhost" || '.' !in name || LOCAL_SUFFIXES.any { name.endsWith(it) }
    }

    private fun newClient(resolver: Resolver, ipv6: Boolean): DnsOverHttps {
        val spec = if (resolver.tls13Only) SharedHttpClient.TLS_1_3_ONLY else ConnectionSpec.RESTRICTED_TLS
        // its own client: the shared one resolves its names through this very object
        val client = OkHttpClient.Builder()
            .connectionSpecs(listOf(spec))
            // the resolver sees the app's generic User-Agent, not the OkHttp version
            .addInterceptor(GenericUserAgentInterceptor())
            // the resolver shows in the network journal of the Dashboard like any other host
            .addNetworkInterceptor(NetworkLedger.interceptor)
            .build()
        return DnsOverHttps.Builder()
            .client(client)
            .url(resolver.url.toHttpUrl())
            .bootstrapDnsHosts(resolver.bootstrap.map { InetAddress.getByName(it) })
            .includeIPv6(ipv6)
            .build()
    }
}
// endregion
