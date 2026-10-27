package com.watube.yard.cast

import android.annotation.SuppressLint
import com.watube.yard.cast.proto.CastMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

// region Stolm
/**
 * An authenticated Cast v2 connection to one receiver: TLS 1.2 / 1.3, device authentication
 * ([CastDeviceAuth]) before any other message, then length-prefixed protobuf frames and a
 * heartbeat. Closed when the receiver stays silent, the network drops or [close] is called.
 */
internal class CastChannel private constructor(
    private val socket: SSLSocket,
    /** The receiver, as authenticated. */
    val remoteAddress: InetAddress,
    /** This device's address on the network the receiver is reached through. */
    val localAddress: InetAddress
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val output = socket.outputStream.buffered()
    private val writeLock = Mutex()
    // monotonic and paused while the device sleeps: a nap is not taken for a silent receiver
    private val lastReceived = AtomicLong(System.nanoTime())

    private val _messages = MutableSharedFlow<CastMessage>(extraBufferCapacity = MESSAGE_BUFFER)

    /** Every message but the heartbeat, answered here. */
    val messages: SharedFlow<CastMessage> = _messages

    /** Completes once closed, with the failure that closed it (null for [close]). */
    val closed = CompletableDeferred<Throwable?>()

    private fun start() {
        scope.launch { readLoop() }
        scope.launch { heartbeatLoop() }
    }

    suspend fun send(message: CastMessage) = withContext(Dispatchers.IO) {
        writeLock.withLock { CastFraming.write(message, output) }
    }

    private suspend fun readLoop() {
        try {
            val input = socket.inputStream.buffered()
            while (scope.isActive) {
                val message = CastFraming.read(input)
                lastReceived.set(System.nanoTime())
                if (message.namespace == CastNamespaces.HEARTBEAT) {
                    if (CastMessages.type(CastMessages.json(message)) == "PING") {
                        send(heartbeat(PONG, message.destinationId, message.sourceId))
                    }
                    continue
                }
                _messages.emit(message)
            }
        } catch (e: IOException) {
            close(e)
        }
    }

    private suspend fun heartbeatLoop() {
        while (scope.isActive) {
            delay(HEARTBEAT_INTERVAL_MS)
            if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastReceived.get()) > HEARTBEAT_TIMEOUT_MS) {
                close(IOException("Cast receiver silent"))
                return
            }
            runCatching { send(heartbeat(PING, CastMessages.PLATFORM_SENDER, CastMessages.PLATFORM_RECEIVER)) }
        }
    }

    override fun close() = close(null)

    private fun close(cause: Throwable?) {
        if (!closed.complete(cause)) return
        scope.cancel()
        runCatching { socket.close() }
    }

    companion object {
        const val PORT = 8009

        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val AUTH_TIMEOUT_MS = 10_000
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val HEARTBEAT_TIMEOUT_MS = 15_000L
        private const val MESSAGE_BUFFER = 32
        private const val PING = "{\"type\":\"PING\"}"
        private const val PONG = "{\"type\":\"PONG\"}"
        private val PROTOCOLS = arrayOf("TLSv1.3", "TLSv1.2")

        private fun heartbeat(payload: String, source: String, destination: String) =
            CastMessages.text(CastNamespaces.HEARTBEAT, payload, source, destination)

        /**
         * Connects to [address] and authenticates the receiver: [CastAuthException] when it
         * cannot prove it is a genuine Cast device owning this connection. [socketFactory] binds
         * the connection to the network the receiver was found on (not a VPN, for instance).
         */
        suspend fun open(
            address: InetAddress,
            port: Int,
            anchors: Set<TrustAnchor>,
            socketFactory: SocketFactory = SocketFactory.getDefault()
        ): CastChannel = withContext(Dispatchers.IO) {
            val context = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(HandshakeTrustManager), SecureRandom())
            }
            val plain: Socket = socketFactory.createSocket()
            try {
                plain.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                val socket = context.socketFactory.createSocket(plain, address.hostAddress, port, true) as SSLSocket
                try {
                    socket.enabledProtocols = PROTOCOLS.filter { it in socket.supportedProtocols }.toTypedArray()
                    socket.soTimeout = AUTH_TIMEOUT_MS
                    socket.startHandshake()
                    val peerCert = socket.session.peerCertificates.firstOrNull() as? X509Certificate
                        ?: throw CastAuthException("no TLS certificate")

                    val auth = CastDeviceAuth(anchors)
                    CastFraming.write(auth.challenge(), socket.outputStream)
                    auth.verify(CastFraming.read(socket.inputStream), peerCert)

                    // from now on silence is detected by the heartbeat
                    socket.soTimeout = 0
                    CastChannel(socket, address, plain.localAddress).also { it.start() }
                } catch (e: Exception) {
                    runCatching { socket.close() }
                    throw e
                }
            } catch (e: Exception) {
                runCatching { plain.close() }
                throw e
            }
        }
    }

    /**
     * Receivers present a self-signed certificate: it is accepted for the handshake only, and
     * nothing but the authentication challenge is sent before [CastDeviceAuth] has proven,
     * by a signature over it, that it belongs to a genuine Cast device.
     */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private object HandshakeTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            throw CertificateException("no client role")

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) throw CertificateException("no receiver certificate")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
// endregion
