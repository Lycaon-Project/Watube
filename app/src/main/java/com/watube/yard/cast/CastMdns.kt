package com.watube.yard.cast

import android.net.Network
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom

// region Stolm
/**
 * The IPv4 address of one Cast receiver, asked to the receiver itself over mDNS.
 *
 * Before Android 14 the system resolver gives a single address, often the IPv6 link-local one:
 * receivers take Cast connections over IPv4 only (seen with a Chromecast), and a link-local
 * address cannot go in the relay URL the receiver loads. One query for the receiver's SRV
 * record, from an ordinary port so that the answer comes back to it alone (RFC 6762, 6.7): no
 * multicast group is joined, nothing else on the network is listened to.
 */
internal object CastMdns {
    private val GROUP = InetSocketAddress(InetAddress.getByAddress(byteArrayOf(224.toByte(), 0, 0, 251.toByte())), 5353)
    private const val SERVICE_SUFFIX = "._googlecast._tcp.local"
    private const val TYPE_A = 1
    private const val TYPE_SRV = 33
    private const val CLASS_IN = 1
    private const val CLASS_MASK = 0x7fff
    private const val FLAG_RESPONSE = 0x8000
    /** A receiver in Wi-Fi power save may answer late: each wait is longer than the previous one. */
    private val WAITS_MS = intArrayOf(700, 1_000, 1_500)
    private const val MAX_PACKET = 9_000
    private const val MAX_LABEL = 63
    private const val MAX_NAME = 255
    private const val MAX_POINTER_JUMPS = 16
    private val random = SecureRandom()

    /** Blocking. Null when the receiver does not answer, or only with a non-local address. */
    fun ipv4Of(serviceName: String, network: Network?): Inet4Address? {
        val name = serviceName + SERVICE_SUFFIX
        val id = random.nextInt(0xffff) + 1
        val query = query(id, name) ?: return null
        return runCatching {
            DatagramSocket().use { socket ->
                network?.bindSocket(socket)
                val buffer = ByteArray(MAX_PACKET)
                for (waitMs in WAITS_MS) {
                    socket.soTimeout = waitMs
                    stolmTrace { "mDNS: query $name from port ${socket.localPort} on $network" }
                    socket.send(DatagramPacket(query, query.size, GROUP))
                    val deadline = System.nanoTime() + waitMs * 1_000_000L
                    while (System.nanoTime() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (e: SocketTimeoutException) {
                            break
                        }
                        val answer = runCatching { answerIn(buffer, packet.length, id, name) }.getOrNull()
                        stolmTrace { "mDNS: ${packet.length} bytes from ${packet.address.hostAddress}:${packet.port}, ours=${answer != null}, a=${answer?.address}" }
                        if (answer == null) continue
                        // the A record of the SRV target, or the receiver itself answering over IPv4
                        val address = answer.address ?: (packet.address as? Inet4Address)
                        if (address != null && address.isLocal()) return@use address
                    }
                }
                null
            }
        }.onFailure { e -> stolmTrace { "mDNS lookup failed: ${e.chain()}" } }.getOrNull()
    }

    private fun Inet4Address.isLocal() = isSiteLocalAddress || isLinkLocalAddress

    /** A standard query for the SRV record of [name] (null if [name] cannot be encoded). */
    internal fun query(id: Int, name: String): ByteArray? {
        val out = ByteArrayOutputStream()
        fun u16(value: Int) {
            out.write(value shr 8 and 0xff)
            out.write(value and 0xff)
        }
        u16(id)
        u16(0) // flags: standard query
        u16(1) // one question
        u16(0)
        u16(0)
        u16(0)
        var total = 0
        for (label in name.split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            if (bytes.isEmpty() || bytes.size > MAX_LABEL) return null
            total += bytes.size + 1
            if (total > MAX_NAME) return null
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        u16(TYPE_SRV)
        u16(CLASS_IN)
        return out.toByteArray()
    }

    /** A response to our query: the IPv4 address of the SRV target, when the packet carries it. */
    internal class Answer(val address: Inet4Address?)

    /** Reads a response to query [id]: null when it holds no SRV record for [name] (not ours). */
    internal fun answerIn(data: ByteArray, length: Int, id: Int, name: String): Answer? {
        val reader = Reader(data, length)
        if (reader.u16() != id) return null
        if (reader.u16() and FLAG_RESPONSE == 0) return null
        val questions = reader.u16()
        val records = reader.u16() + reader.u16() + reader.u16()
        repeat(questions) {
            reader.name()
            reader.skip(4)
        }
        var target: String? = null
        val addresses = mutableMapOf<String, Inet4Address>()
        repeat(records) {
            val owner = reader.name()
            val type = reader.u16()
            val cls = reader.u16() and CLASS_MASK
            reader.skip(4) // TTL
            val size = reader.u16()
            val end = reader.position + size
            if (end > length) throw IllegalArgumentException("record past the packet")
            if (cls == CLASS_IN) when (type) {
                TYPE_SRV -> if (owner.equals(name, ignoreCase = true)) {
                    reader.skip(6) // priority, weight, port
                    target = reader.name()
                }
                TYPE_A -> if (size == 4) {
                    addresses[owner.lowercase()] = InetAddress.getByAddress(reader.bytes(4)) as Inet4Address
                }
            }
            reader.position = end
        }
        val srvTarget = target ?: return null
        return Answer(addresses[srvTarget.lowercase()])
    }

    /** Bounded reader of a DNS message, compressed names included. */
    private class Reader(private val data: ByteArray, private val length: Int) {
        var position = 0

        fun u8(): Int {
            if (position >= length) throw IllegalArgumentException("truncated")
            return data[position++].toInt() and 0xff
        }

        fun u16() = u8() shl 8 or u8()

        fun skip(count: Int) {
            if (position + count > length) throw IllegalArgumentException("truncated")
            position += count
        }

        fun bytes(count: Int): ByteArray {
            if (position + count > length) throw IllegalArgumentException("truncated")
            return data.copyOfRange(position, position + count).also { position += count }
        }

        fun name(): String {
            val labels = mutableListOf<String>()
            var cursor = position
            var jumps = 0
            var resume = -1
            var total = 0
            while (true) {
                if (cursor >= length) throw IllegalArgumentException("truncated name")
                val size = data[cursor].toInt() and 0xff
                when {
                    size == 0 -> {
                        cursor++
                        break
                    }
                    size and 0xc0 == 0xc0 -> {
                        if (cursor + 1 >= length || ++jumps > MAX_POINTER_JUMPS) {
                            throw IllegalArgumentException("bad name pointer")
                        }
                        if (resume < 0) resume = cursor + 2
                        cursor = (size and 0x3f) shl 8 or (data[cursor + 1].toInt() and 0xff)
                    }
                    size > MAX_LABEL -> throw IllegalArgumentException("bad label")
                    else -> {
                        if (cursor + 1 + size > length) throw IllegalArgumentException("truncated label")
                        total += size + 1
                        if (total > MAX_NAME) throw IllegalArgumentException("name too long")
                        labels += String(data, cursor + 1, size, Charsets.UTF_8)
                        cursor += size + 1
                    }
                }
            }
            position = if (resume >= 0) resume else cursor
            return labels.joinToString(".")
        }
    }
}
// endregion
