package com.watube.yard.util

import java.net.Inet6Address
import java.net.InetAddress

// region Networking
/**
 * Tells the addresses of the public internet from those of the phone, of its local networks
 * and of the shared or reserved ranges, which a server could abuse to make the app reach a
 * host it should never talk to (server side request forgery).
 */
object PublicAddress {
    private const val BYTE_MASK = 0xff
    private const val UNIQUE_LOCAL_MASK = 0xfe
    private const val UNIQUE_LOCAL_PREFIX = 0xfc
    private val NAT64_PREFIX = listOf(0x00, 0x64, 0xff, 0x9b)

    // 100.64.0.0/10
    private const val CGNAT_FIRST = 100
    private val CGNAT_SECOND = 64..127

    // 198.18.0.0/15
    private const val BENCHMARK_FIRST = 198
    private val BENCHMARK_SECOND = 18..19

    // 192.0.0.0/24
    private const val IETF_FIRST = 192

    // 240.0.0.0/4 and the broadcast address
    private const val RESERVED_FROM = 240

    /** Names only a local network (or the phone itself) resolves */
    private val LOCAL_SUFFIXES = listOf(".local", ".localhost", ".lan", ".internal", ".home.arpa")

    /**
     * Not a name of the local network nor a non public address literal. A public looking name
     * can still resolve to a local address: see [resolvesToPublic] or a filtering resolver.
     */
    fun isPublicHost(host: String): Boolean {
        // "localhost." is the same name as "localhost"
        val name = host.removeSurrounding("[", "]").trimEnd('.').lowercase()
        val isLiteral = name.all { it.isDigit() || it == '.' } || name.contains(':')
        if (isLiteral) {
            return runCatching { InetAddress.getByName(name) }.getOrNull()?.isPublic() == true
        }
        // a single label ("nas", "router") is resolved through the local search domain
        return name.contains('.') && name != "localhost" && LOCAL_SUFFIXES.none(name::endsWith)
    }

    /**
     * [isPublicHost], and every address the name currently resolves to is public. Blocking:
     * call it off the main thread.
     */
    fun resolvesToPublic(host: String): Boolean = isPublicHost(host) && runCatching {
        InetAddress.getAllByName(host.trimEnd('.')).all { it.isPublic() }
    }.getOrDefault(false)

    /**
     * [InetAddress.isSiteLocalAddress] only knows 10/8, 172.16/12 and 192.168/16: the carrier
     * grade NAT range (also used by Tailscale and ZeroTier networks), the benchmarking and IETF
     * ranges, the reserved block and the NAT64 prefix lead to private hosts as well.
     */
    fun InetAddress.isPublic(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress ||
            isSiteLocalAddress || isMulticastAddress
        ) {
            return false
        }
        val bytes = address.map { it.toInt() and BYTE_MASK }
        return if (this is Inet6Address) {
            // fc00::/7 unique local addresses, 64:ff9b::/96 NAT64 to any IPv4 address
            (bytes[0] and UNIQUE_LOCAL_MASK) != UNIQUE_LOCAL_PREFIX &&
                bytes.take(NAT64_PREFIX.size) != NAT64_PREFIX
        } else {
            val (first, second, third) = bytes
            !(first == CGNAT_FIRST && second in CGNAT_SECOND) &&
                !(first == BENCHMARK_FIRST && second in BENCHMARK_SECOND) &&
                !(first == IETF_FIRST && second == 0 && third == 0) &&
                first < RESERVED_FROM
        }
    }
}
// endregion
