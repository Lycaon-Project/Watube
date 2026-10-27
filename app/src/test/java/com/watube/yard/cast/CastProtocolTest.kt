package com.watube.yard.cast

import com.watube.yard.cast.proto.CastMessage
import com.watube.yard.cast.proto.HashAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Cast v2 framing and device authentication, checked with the test vectors of Open Screen
 * (test/data/cast/common/certificate, BSD-3-Clause): real Chromecast certificate chains and
 * signatures made by a real device key.
 */
class CastProtocolTest {
    private fun resource(name: String) = javaClass.getResourceAsStream("/cast/$name")!!.readBytes()

    private fun pemBlocks(name: String, label: String): List<ByteArray> =
        Regex("-----BEGIN $label-----([\\s\\S]*?)-----END $label-----")
            .findAll(resource(name).toString(Charsets.US_ASCII))
            .map { Base64.getMimeDecoder().decode(it.groupValues[1]) }
            .toList()

    private fun chain(name: String) = pemBlocks(name, "CERTIFICATE").map(CastDeviceAuth::parseCertificate)

    private val anchors = listOf("cast_root_ca.der", "eureka_root_ca.der")
        .map { TrustAnchor(CastDeviceAuth.parseCertificate(resource(it)), null) }
        .toSet()

    private fun date(iso: String) = Date.from(java.time.Instant.parse(iso))

    @Test
    fun framesRoundTrip() {
        val message = CastMessages.text(CastNamespaces.HEARTBEAT, "{\"type\":\"PING\"}", "sender-0", "receiver-0")
        val out = ByteArrayOutputStream()
        CastFraming.write(message, out)
        assertEquals(message, CastFraming.read(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun oversizedOrNegativeFramesAreRefused() {
        for (size in listOf(CastFraming.MAX_MESSAGE_SIZE + 1, -1)) {
            val frame = ByteBuffer.allocate(4).putInt(size).array()
            assertThrows(IOException::class.java) { CastFraming.read(ByteArrayInputStream(frame)) }
        }
        // a frame cut short
        val truncated = ByteBuffer.allocate(6).putInt(10).array()
        assertThrows(IOException::class.java) { CastFraming.read(ByteArrayInputStream(truncated)) }
    }

    @Test
    fun genuineChromecastChainsAreAccepted() {
        val gen1 = chain("chromecast_gen1.pem")
        assertEquals(gen1[0], CastDeviceAuth.verifyChain(gen1, anchors, date("2026-10-13T00:00:00Z")))
        // gen 2 chains through "Chromecast ICA 3", valid until 2025-03-09
        val gen2 = chain("chromecast_gen2.pem")
        CastDeviceAuth.verifyChain(gen2, anchors, date("2024-06-01T00:00:00Z"))
    }

    @Test
    fun chainsOutsideTheCastRootsOrOutOfDateAreRefused() {
        val gen2 = chain("chromecast_gen2.pem")
        assertThrows(CastAuthException::class.java) {
            CastDeviceAuth.verifyChain(gen2, anchors, date("2026-10-13T00:00:00Z"))
        }
        // own test root, and a 1024 bit device key
        val foreign = chain("rsa1024_device_cert.pem")
        assertThrows(CastAuthException::class.java) {
            CastDeviceAuth.verifyChain(foreign, anchors, date("2016-06-01T00:00:00Z"))
        }
        assertThrows(CastAuthException::class.java) {
            CastDeviceAuth.verifyChain(emptyList<X509Certificate>(), anchors, date("2026-10-13T00:00:00Z"))
        }
    }

    @Test
    fun deviceSignaturesAreVerified() {
        val device = chain("chromecast_gen1.pem")[0]
        val data = pemBlocks("2ZZBG9_FA8FCA3EF91A.pem", "MESSAGE").single()
        assertEquals("STRING", data.toString(Charsets.US_ASCII))
        val sha1 = pemBlocks("2ZZBG9_FA8FCA3EF91A.pem", "SIGNATURE SHA1").single()
        val sha256 = pemBlocks("2ZZBG9_FA8FCA3EF91A.pem", "SIGNATURE SHA256").single()

        CastDeviceAuth.verifySignature(device, HashAlgorithm.SHA1, data, sha1)
        CastDeviceAuth.verifySignature(device, HashAlgorithm.SHA256, data, sha256)
        assertThrows(CastAuthException::class.java) {
            CastDeviceAuth.verifySignature(device, HashAlgorithm.SHA256, "STRINH".toByteArray(), sha256)
        }
        assertThrows(CastAuthException::class.java) {
            CastDeviceAuth.verifySignature(device, HashAlgorithm.SHA1, data, sha256)
        }
    }

    @Test
    fun tlsCertificatesMustBeShortLived() {
        val now = date("2026-10-13T12:00:00Z")
        val day = TimeUnit.DAYS.toMillis(1)
        CastDeviceAuth.checkTlsValidity(Date(now.time - day), Date(now.time + 2 * day), now)
        assertThrows(CastAuthException::class.java) {
            CastDeviceAuth.checkTlsValidity(Date(now.time - day), Date(now.time + 5 * day), now)
        }
        assertThrows(CastAuthException::class.java) {
            CastDeviceAuth.checkTlsValidity(Date(now.time + day), Date(now.time + 2 * day), now)
        }
        assertThrows(CastAuthException::class.java) {
            CastDeviceAuth.checkTlsValidity(Date(now.time - 2 * day), Date(now.time - day), now)
        }
    }

    @Test
    fun challengeCarriesAFreshNonce() {
        val first = CastDeviceAuth(anchors).challenge()
        val second = CastDeviceAuth(anchors).challenge()
        assertEquals(CastNamespaces.DEVICE_AUTH, first.namespace)
        assertEquals(CastMessage.PayloadType.BINARY, first.payloadType)
        assert(first.payloadBinary != second.payloadBinary)
    }
}
