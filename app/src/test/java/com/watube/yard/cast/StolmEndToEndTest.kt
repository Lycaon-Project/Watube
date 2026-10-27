package com.watube.yard.cast

import androidx.media3.common.Player
import com.google.protobuf.ByteString
import com.watube.yard.cast.proto.AuthResponse
import com.watube.yard.cast.proto.CastMessage
import com.watube.yard.cast.proto.DeviceAuthMessage
import com.watube.yard.cast.proto.HashAlgorithm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.concurrent.thread

/**
 * Stolm from the authenticated connection to the relayed video, against a fake receiver that
 * behaves like the Default Media Receiver. Its device PKI (root, intermediate, device) and its
 * short-lived TLS certificate are made at run time with the JDK's keytool: no private key is
 * kept in the repository.
 */
class StolmEndToEndTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val http = OkHttpClient()

    @Test
    fun genuineReceiverIsAuthenticatedAndPlaysThroughTheRelay() = runBlocking {
        FakeOrigin().use { origin ->
            FakeReceiver(signsFor = { it }).use { receiver ->
                val channel = CastChannel.open(loopback, receiver.port, pki.anchors)
                val session = CastSession.start(channel)
                val relay = CastMediaServer(channel.localAddress, channel.remoteAddress, http) { it }

                val url = relay.publish(origin.masterUrl)
                val formats = relay.segmentFormats(origin.masterUrl)
                assertEquals(HlsFormats.FMP4, formats)
                session.load(url, StolmPlayer.CONTENT_TYPE_HLS, false, "Title", 12.0, formats)

                // the receiver read the whole video from the phone, never from the origin
                assertArrayEquals(FakeOrigin.SEGMENT, receiver.fetchedSegment.poll(5, TimeUnit.SECONDS))
                assertTrue(relay.receiverReached)
                val load = receiver.lastOf("LOAD")
                assertEquals(url, load["media"]!!.jsonObject["contentId"]!!.jsonPrimitive.content)
                assertEquals("fmp4", load["media"]!!.jsonObject["hlsSegmentFormat"]!!.jsonPrimitive.content)
                assertEquals("fmp4", load["media"]!!.jsonObject["hlsVideoSegmentFormat"]!!.jsonPrimitive.content)
                assertTrue("no artwork sent", load["media"]!!.jsonObject["metadata"]!!.jsonObject["images"] == null)
                assertEquals("PLAYING", session.mediaStatus.value?.playerState)

                session.pause()
                assertEquals("PAUSED", withTimeout(5_000) { session.mediaStatus.first { it?.playerState == "PAUSED" } }?.playerState)
                session.seek(30.0)
                assertEquals(30.0, withTimeout(5_000) { session.mediaStatus.first { it?.positionSeconds == 30.0 } }?.positionSeconds)
                session.setVolume(0.3)
                assertEquals(0.3, withTimeout(5_000) { session.volume.first { it?.level == 0.3 } }?.level)

                session.close()
                assertEquals("s1", receiver.lastOf("STOP")["sessionId"]?.jsonPrimitive?.content)
                assertNull(withTimeout(5_000) { session.finished.await() })
                relay.close()
            }
        }
    }

    @Test
    fun receiverOfAnotherPkiIsRefusedBeforeAnythingElseIsSent() {
        FakeReceiver(signsFor = { it }).use { receiver ->
            assertThrows(CastAuthException::class.java) {
                runBlocking { CastChannel.open(loopback, receiver.port, castRoots) }
            }
            // only the challenge went out: no CONNECT, no LAUNCH
            assertEquals(listOf(CastNamespaces.DEVICE_AUTH), receiver.namespacesSeen())
        }
    }

    @Test
    fun signatureMadeForAnotherConnectionIsRefused() {
        // a relay in the middle: the genuine device signed for its own TLS certificate
        FakeReceiver(signsFor = { pki.otherTlsCert }).use { receiver ->
            assertThrows(CastAuthException::class.java) {
                runBlocking { CastChannel.open(loopback, receiver.port, pki.anchors) }
            }
        }
    }

    @Test
    fun receiverAppStoppedOnTheTvEndsTheSession() = runBlocking {
        FakeReceiver(signsFor = { it }).use { receiver ->
            val session = CastSession.start(CastChannel.open(loopback, receiver.port, pki.anchors))
            receiver.stopApp()
            assertNotNull(withTimeout(5_000) { session.finished.await() })
        }
    }

    @Test
    fun receiverStatusesMapToPlayerStates() {
        fun status(state: String, idle: String? = null) = CastMediaStatus(state, 0.0, null, idle, 1.0, 0)
        assertEquals(Player.STATE_BUFFERING, StolmStatus.playbackStateOf(null, isLoading = false))
        assertEquals(Player.STATE_BUFFERING, StolmStatus.playbackStateOf(status("PLAYING"), isLoading = true))
        assertEquals(Player.STATE_READY, StolmStatus.playbackStateOf(status("PAUSED"), isLoading = false))
        assertEquals(Player.STATE_BUFFERING, StolmStatus.playbackStateOf(status("BUFFERING"), isLoading = false))
        assertEquals(Player.STATE_ENDED, StolmStatus.playbackStateOf(status("IDLE", "FINISHED"), isLoading = false))
        assertEquals(Player.STATE_IDLE, StolmStatus.playbackStateOf(status("IDLE", "ERROR"), isLoading = false))
        assertTrue(StolmStatus.playWhenReadyOf(status("BUFFERING")))
        assertFalse(StolmStatus.playWhenReadyOf(status("PAUSED")))
    }

    /** A Cast receiver speaking the Default Media Receiver's protocol, on the loopback. */
    private inner class FakeReceiver(
        /** The TLS certificate the device signs for, given its own. */
        private val signsFor: (X509Certificate) -> X509Certificate
    ) : Closeable {
        private val server = SSLContext.getInstance("TLS").apply {
            init(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(pki.tlsStore, PASSWORD)
            }.keyManagers, null, null)
        }.serverSocketFactory.createServerSocket(0, 1, loopback) as SSLServerSocket
        val port get() = server.localPort

        private val received = LinkedBlockingQueue<CastMessage>()
        val fetchedSegment = LinkedBlockingQueue<ByteArray>()
        private var output: java.io.OutputStream? = null
        private var senderId = ""

        init {
            thread(isDaemon = true) {
                runCatching {
                    server.accept().use { socket ->
                        output = socket.outputStream
                        val input = socket.inputStream.buffered()
                        while (true) handle(CastFraming.read(input))
                    }
                }
            }
        }

        fun namespacesSeen(): List<String> {
            Thread.sleep(300)
            return received.map { it.namespace }
        }

        fun lastOf(type: String): JsonObject {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                received.lastOrNull { CastMessages.type(CastMessages.json(it)) == type }
                    ?.let { return CastMessages.json(it)!! }
                Thread.sleep(20)
            }
            throw AssertionError("no $type received")
        }

        fun stopApp() = reply(CastNamespaces.RECEIVER, "receiver-0", buildJsonObject {
            put("type", "RECEIVER_STATUS")
            put("requestId", 0)
            putJsonObject("status") { putJsonObject("volume") { put("level", 0.5); put("muted", false) } }
        })

        private fun handle(message: CastMessage) {
            if (message.namespace == CastNamespaces.HEARTBEAT) {
                reply(CastNamespaces.HEARTBEAT, message.destinationId, buildJsonObject { put("type", "PONG") })
                return
            }
            received += message
            if (message.namespace == CastNamespaces.DEVICE_AUTH) return answerChallenge(message)

            val json = CastMessages.json(message) ?: return
            senderId = message.sourceId
            val requestId = json["requestId"]?.jsonPrimitive?.intOrNull ?: 0
            when (CastMessages.type(json)) {
                "LAUNCH" -> reply(CastNamespaces.RECEIVER, "receiver-0", receiverStatus(requestId, running = true, 0.5))
                "SET_VOLUME" -> reply(
                    CastNamespaces.RECEIVER, "receiver-0",
                    receiverStatus(requestId, running = true, json["volume"]!!.jsonObject["level"]!!.jsonPrimitive.doubleOrNull!!)
                )
                "STOP" -> if (message.namespace == CastNamespaces.RECEIVER) {
                    reply(CastNamespaces.RECEIVER, "receiver-0", receiverStatus(requestId, running = false, 0.5))
                }
                "LOAD" -> {
                    playAsTheReceiverWould(json["media"]!!.jsonObject["contentId"]!!.jsonPrimitive.content)
                    val start = json["currentTime"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    reply(CastNamespaces.MEDIA, "t1", mediaStatus(requestId, "PLAYING", start))
                }
                "PAUSE" -> reply(CastNamespaces.MEDIA, "t1", mediaStatus(requestId, "PAUSED", 12.0))
                "PLAY" -> reply(CastNamespaces.MEDIA, "t1", mediaStatus(requestId, "PLAYING", 12.0))
                "SEEK" -> reply(
                    CastNamespaces.MEDIA, "t1",
                    mediaStatus(requestId, "PLAYING", json["currentTime"]!!.jsonPrimitive.doubleOrNull!!)
                )
            }
        }

        /** Reads the master playlist, the first variant and its first segment, as players do. */
        private fun playAsTheReceiverWould(masterUrl: String) {
            fun get(url: String) = http.newCall(
                Request.Builder().url(url).header("Origin", "https://www.gstatic.com").build()
            ).execute().use { it.body.bytes() }

            val variant = get(masterUrl).toString(Charsets.UTF_8).lines().first { it.startsWith("http") }
            val segment = get(variant).toString(Charsets.UTF_8).lines().first { it.startsWith("http") }
            fetchedSegment += get(segment)
        }

        private fun answerChallenge(message: CastMessage) {
            val nonce = DeviceAuthMessage.parseFrom(message.payloadBinary).challenge.senderNonce.toByteArray()
            val tlsCert = pki.tlsStore.getCertificate(TLS) as X509Certificate
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(pki.deviceKey)
                update(nonce + signsFor(tlsCert).encoded)
                sign()
            }
            val response = AuthResponse.newBuilder()
                .setSignature(ByteString.copyFrom(signature))
                .setClientAuthCertificate(ByteString.copyFrom(pki.deviceCert.encoded))
                .addIntermediateCertificate(ByteString.copyFrom(pki.intermediateCert.encoded))
                .setSenderNonce(ByteString.copyFrom(nonce))
                .setHashAlgorithm(HashAlgorithm.SHA256)
            val payload = DeviceAuthMessage.newBuilder().setResponse(response).build().toByteArray()
            write(CastMessages.binary(CastNamespaces.DEVICE_AUTH, payload).toBuilder()
                .setSourceId("receiver-0").setDestinationId("sender-0").build())
        }

        private fun receiverStatus(requestId: Int, running: Boolean, level: Double) = buildJsonObject {
            put("type", "RECEIVER_STATUS")
            put("requestId", requestId)
            putJsonObject("status") {
                if (running) {
                    putJsonArray("applications") {
                        add(buildJsonObject {
                            put("appId", "CC1AD845")
                            put("sessionId", "s1")
                            put("transportId", "t1")
                        })
                    }
                }
                putJsonObject("volume") { put("level", level); put("muted", false) }
            }
        }

        private fun mediaStatus(requestId: Int, state: String, position: Double) = buildJsonObject {
            put("type", "MEDIA_STATUS")
            put("requestId", requestId)
            put("status", buildJsonArray {
                add(buildJsonObject {
                    put("mediaSessionId", 1)
                    put("playerState", state)
                    put("currentTime", position)
                    putJsonObject("media") { put("duration", 120.0) }
                })
            })
        }

        private fun reply(namespace: String, source: String, payload: JsonObject) =
            write(CastMessages.text(namespace, payload.toString(), source, senderId.ifEmpty { "sender-0" }))

        @Synchronized
        private fun write(message: CastMessage) {
            output?.let { CastFraming.write(message, it) }
        }

        override fun close() = server.close()
    }

    /** An HLS origin: master, a fragmented MP4 variant and one segment. */
    private class FakeOrigin : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val masterUrl = "http://127.0.0.1:${server.localPort}/hls/master.m3u8"

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    socket.use {
                        val path = it.getInputStream().bufferedReader().readLine().split(' ')[1]
                        val body = when (path) {
                            "/hls/master.m3u8" -> "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv1/index.m3u8\n".toByteArray()
                            "/hls/v1/index.m3u8" ->
                                "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:5,\nseg1.m4s\n#EXT-X-ENDLIST\n".toByteArray()
                            "/hls/v1/seg1.m4s" -> SEGMENT
                            else -> ByteArray(0)
                        }
                        it.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }

        override fun close() = server.close()

        companion object {
            val SEGMENT = ByteArray(4096) { (it * 7).toByte() }
        }
    }

    /** Root → intermediate → device certificates, and the receiver's TLS identity. */
    private class TestPki(dir: File) {
        val anchors: Set<TrustAnchor>
        val intermediateCert: X509Certificate
        val deviceCert: X509Certificate
        val deviceKey: PrivateKey
        val tlsStore: KeyStore
        val otherTlsCert: X509Certificate

        init {
            val store = File(dir, "pki.p12").path
            val tls = File(dir, "tls.p12").path
            fun gen(alias: String, cn: String, file: String, days: Int, vararg ext: String) = keytool(
                "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=$cn",
                "-validity", days.toString(), "-keystore", file, *ext
            )
            fun sign(issuer: String, subject: String, vararg ext: String): X509Certificate {
                val csr = File(dir, "$subject.csr").path
                val cer = File(dir, "$subject.cer")
                keytool("-certreq", "-alias", subject, "-file", csr, "-keystore", store)
                keytool(
                    "-gencert", "-alias", issuer, "-infile", csr, "-outfile", cer.path, "-validity", "3650",
                    "-keystore", store, *ext
                )
                return cer.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) } as X509Certificate
            }

            gen("root", "Stolm Test Root", store, 3650, "-ext", "bc:c", "-ext", "ku:c=keyCertSign,cRLSign")
            gen("ica", "Stolm Test ICA", store, 3650)
            gen("device", "Stolm Test Device", store, 3650)
            intermediateCert = sign("root", "ica", "-ext", "bc:c=ca:true,pathlen:0", "-ext", "ku:c=keyCertSign")
            deviceCert = sign("ica", "device", "-ext", "ku:c=digitalSignature")
            // receivers renew a self-signed TLS certificate that lives two days
            gen("tls", "receiver", tls, 2)
            gen("other", "receiver", File(dir, "other.p12").path, 2)

            val pki = KeyStore.getInstance("PKCS12").apply { File(store).inputStream().use { load(it, PASSWORD) } }
            anchors = setOf(TrustAnchor(pki.getCertificate("root") as X509Certificate, null))
            deviceKey = pki.getKey("device", PASSWORD) as PrivateKey
            tlsStore = KeyStore.getInstance("PKCS12").apply { File(tls).inputStream().use { load(it, PASSWORD) } }
            otherTlsCert = KeyStore.getInstance("PKCS12")
                .apply { File(dir, "other.p12").inputStream().use { load(it, PASSWORD) } }
                .getCertificate("other") as X509Certificate
        }

        private fun keytool(vararg args: String) {
            val exe = File(System.getProperty("java.home"), "bin/keytool").path
            val process = ProcessBuilder(listOf(exe) + args + listOf("-storetype", "PKCS12", "-storepass", String(PASSWORD), "-noprompt"))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
            check(process.waitFor() == 0) { "keytool ${args.first()} failed: $output" }
        }
    }

    companion object {
        private val PASSWORD = "stolm-test".toCharArray()
        private const val TLS = "tls"
        private lateinit var dir: File
        private lateinit var pki: TestPki

        /** The real Cast roots: a receiver of the test PKI must fail against them. */
        private val castRoots: Set<TrustAnchor> by lazy {
            listOf("cast_root_ca.der", "eureka_root_ca.der").map { name ->
                val cert = StolmEndToEndTest::class.java.getResourceAsStream("/cast/$name")!!.use {
                    CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
                }
                TrustAnchor(cert, null)
            }.toSet()
        }

        @JvmStatic
        @BeforeClass
        fun createPki() {
            dir = Files.createTempDirectory("stolm-pki").toFile()
            pki = TestPki(dir)
        }

        @JvmStatic
        @AfterClass
        fun deletePki() {
            dir.deleteRecursively()
        }
    }
}
