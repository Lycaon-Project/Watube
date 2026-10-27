package com.watube.yard.cast

import com.watube.yard.api.GenericUserAgentInterceptor
import com.watube.yard.helpers.ProxyHelper
import com.watube.yard.util.PublicAddress
import com.watube.yard.util.PublicAddress.isPublic
import com.watube.yard.util.SharedHttpClient
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

// region Stolm
/**
 * Serves the video being cast, from the phone: the receiver only ever talks to this server,
 * never to YouTube. YouTube's HLS playlists are fetched here and rewritten so that every
 * playlist and segment points back to this server, which relays them; Google only sees the
 * phone, like for a video played on it.
 *
 * Locked down: it listens on the Wi-Fi address only, on a random port, for one authenticated
 * receiver ([allowedPeer]; any other client is dropped without an answer), only answers GET /
 * HEAD (and the CORS preflight) under a 256-bit random path token compared in constant time,
 * bounds request size, connections and idle time, and allows CORS for the receiver's origin
 * only. Upstream, it only fetches public https addresses, each one through [route] (the
 * Piped instance's proxy in pure Piped mode): never the local network, never the phone itself.
 */
internal class CastMediaServer(
    private val bindAddress: InetAddress,
    private val allowedPeer: InetAddress,
    private val client: OkHttpClient = defaultUpstreamClient(),
    private val route: (String) -> String? = ::defaultRoute
) : Closeable {
    private val serverSocket = ServerSocket(0, BACKLOG, bindAddress)
    private val workers: ExecutorService = Executors.newFixedThreadPool(MAX_CONNECTIONS + 1)
    private val slots = Semaphore(MAX_CONNECTIONS)

    @Volatile
    private var source: Source? = null

    /**
     * Whether the receiver has requested the current video: a LOAD that fails while this is
     * still false means the receiver could not reach the phone at all (firewall, isolated
     * Wi-Fi, local network access refused).
     */
    @Volatile
    var receiverReached = false
        private set

    /** The upstream address of every relayed resource, by id. Bounded: the oldest are dropped. */
    private val targets = object : LinkedHashMap<Int, Relayed>(INITIAL_TARGETS, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Relayed>) = size > MAX_TARGETS
    }

    /** [audio]: an audio rendition playlist, or one of its segments (rewrapped into MPEG-TS). */
    private class Relayed(val url: String, val audio: Boolean)
    private var nextId = 0

    private class Source(val token: String, val playlistUrl: String)

    init {
        workers.execute(::acceptLoop)
    }

    /**
     * Serves [hlsUrl] under a new token (the previous video stops being reachable) and returns
     * the address the receiver loads.
     */
    fun publish(hlsUrl: String): String {
        val token = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        synchronized(targets) { targets.clear() }
        prefetched.clear()
        receiverReached = false
        source = Source(token, hlsUrl)
        return "${baseUrl(token)}/$MASTER"
    }

    /**
     * The segment formats of [hlsUrl] as the relay serves them, to tell the receiver: it reads
     * every segment as MPEG-TS otherwise and stalls on anything else. Audio in its own segments is
     * MPEG-TS or fragmented MP4 (packed AAC, YouTube's, is rewrapped into MPEG-TS: [PackedAudioTs]).
     * Blocking: reads the master, its first variant and audio playlists (served once to the
     * receiver afterwards, not fetched again).
     */
    fun segmentFormats(hlsUrl: String): HlsFormats {
        val (masterUrl, master) = fetchPlaylist(hlsUrl) ?: return HlsFormats.DEFAULT
        val castable = HlsVariants.castable(master)
        // a media playlist, without variants
        if (!castable.contains(HlsVariants.STREAM_INF)) {
            return if (castable.contains(MAP_TAG)) HlsFormats.FMP4 else HlsFormats.DEFAULT
        }
        val (tag, uri) = HlsVariants.firstVariant(castable) ?: return HlsFormats.DEFAULT
        val videoFmp4 = masterUrl.resolve(uri)?.let { fetchPlaylist(it.toString()) }?.second?.contains(MAP_TAG) == true
        val audioUrl = HlsVariants.audioUri(castable, tag)?.let(masterUrl::resolve)
            // audio and video in the same segments
            ?: return if (videoFmp4) HlsFormats.FMP4 else HlsFormats.DEFAULT
        val audio = fetchPlaylist(audioUrl.toString())?.second ?: return HlsFormats(null, video(videoFmp4))
        return HlsFormats(if (audio.contains(MAP_TAG)) HlsFormats.FMP4_FORMAT else HlsFormats.TS, video(videoFmp4))
    }

    private fun video(fmp4: Boolean) = if (fmp4) HlsFormats.FMP4_FORMAT else HlsFormats.MPEG2_TS


    private fun fetchPlaylist(url: String): Pair<HttpUrl, String>? {
        val target = route(url)?.toHttpUrlOrNull() ?: return null
        return client.newCall(Request.Builder().url(target).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body.byteStream().readBounded(MAX_PLAYLIST_BYTES).toString(Charsets.UTF_8)
            prefetched[target.toString()] = Prefetched(text, System.nanoTime())
            target to text
        }
    }

    /** A playlist [segmentFormats] read: the receiver gets it once, if still fresh. */
    private class Prefetched(val text: String, val atNanos: Long)

    private val prefetched = ConcurrentHashMap<String, Prefetched>()

    private fun takePrefetched(url: String): String? = prefetched.remove(url)
        ?.takeIf { System.nanoTime() - it.atNanos < PREFETCH_TTL_NANOS }?.text

    private fun baseUrl(token: String) =
        "http://${bindAddress.hostAddress}:${serverSocket.localPort}/$token"

    private fun acceptLoop() {
        while (!serverSocket.isClosed) {
            val socket = try {
                serverSocket.accept()
            } catch (e: IOException) {
                return
            }
            // anyone else on the network gets nothing, not even an error page
            if (socket.inetAddress != allowedPeer || !slots.tryAcquire()) {
                stolmTrace { "relay: dropped ${socket.inetAddress.hostAddress} (receiver ${allowedPeer.hostAddress})" }
                socket.closeQuietly()
                continue
            }
            workers.execute {
                try {
                    socket.soTimeout = IDLE_TIMEOUT_MS
                    handle(socket)
                } catch (e: IOException) {
                    // the receiver went away or sent nonsense: nothing to answer
                    stolmTrace { "relay: connection ended: ${e.chain()}" }
                } catch (e: RuntimeException) {
                    // a malformed request must end its connection, never the process
                    stolmTrace { "relay: request refused: ${e.chain()}" }
                } finally {
                    socket.closeQuietly()
                    slots.release()
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        val request = HttpRequest.read(socket.getInputStream()) ?: return
        val out = HeadTrackingStream(socket.getOutputStream())
        val cors = request.headers["origin"]?.takeIf(::isReceiverOrigin)
        stolmTrace {
            val kind = request.path.trimStart('/').split('/').drop(1).joinToString("/")
            "relay: ${request.method} $kind origin=${request.headers["origin"]} range=${request.headers["range"]} " +
                request.headers.filterKeys { it.startsWith("access-control-request") }
        }

        val current = source ?: return respond(out, 404, cors)
        val segments = request.path.trimStart('/').split('/')
        if (segments.size < 2 || !tokenMatches(segments[0], current.token)) return respond(out, 404, cors)

        val upstream = when {
            segments.size == 2 && segments[1] == MASTER -> Relayed(current.playlistUrl, audio = false)
            segments.size == 3 && segments[1] == RELAY ->
                segments[2].toIntOrNull()?.let { synchronized(targets) { targets[it] } }
            else -> null
        } ?: return respond(out, 404, cors)

        when (request.method) {
            "GET", "HEAD" -> Unit
            // the receiver's player may ask before a ranged request
            "OPTIONS" -> return respond(out, if (cors != null) 204 else 403, cors)
            else -> return respond(out, 405, cors)
        }
        receiverReached = true

        val target = route(upstream.url) ?: return respond(out, 403, cors)
        try {
            if (upstream.audio) {
                relayAudio(target, request, out, cors, baseUrl(current.token))
            } else {
                relay(target, request, out, cors, baseUrl(current.token))
            }
        } catch (e: IOException) {
            // upstream failed before anything was sent: tell the receiver instead of hanging up
            stolmTrace { "relay: upstream failed: ${e.chain()}" }
            if (!out.wroteHead) respond(out, 502, cors) else throw e
        }
    }

    private fun relay(url: String, request: HttpRequest, out: HeadTrackingStream, cors: String?, base: String) {
        val headOnly = request.method == "HEAD"
        if (!headOnly) takePrefetched(url)?.let { return servePlaylist(it, url, out, cors, base, headOnly, audio = false) }
        // a HEAD stays a HEAD upstream: a segment is not downloaded only to be dropped...
        var response = fetch(url, request, if (headOnly) "HEAD" else "GET")
        // ...but the length of a playlist is the one of its rewritten text, which has to be read
        if (headOnly && response.isSuccessful && response.isPlaylistType()) {
            response.close()
            response = fetch(url, request, "GET")
        }

        response.use {
            val body = response.body
            val isGet = response.request.method == "GET"
            // by its type or its mandatory first tag, never by its URL: segment URLs of YouTube's
            // HLS streams hold "playlist_type/DVR"
            val isPlaylist = response.isPlaylistType() || (isGet && body.source().startsWithPlaylistTag())
            stolmTrace { "relay: upstream ${response.request.method} ${response.code} ${body.contentType()} playlist=$isPlaylist ${shape(url)}" }
            if (!response.isSuccessful) return respond(out, response.code, cors)

            if (isPlaylist) {
                servePlaylist(body.byteStream().readBounded(MAX_PLAYLIST_BYTES).toString(Charsets.UTF_8), url, out, cors, base, headOnly, audio = false)
            } else {
                val length = if (isGet) body.contentLength() else response.header("Content-Length")?.toLongOrNull()
                writeHead(
                    out, response.code, cors,
                    response.header("Content-Type") ?: "application/octet-stream",
                    length?.takeIf { it >= 0 },
                    response.header("Content-Range")
                )
                if (!headOnly) body.byteStream().copyTo(out)
            }
            out.flush()
        }
    }

    /**
     * Serves a playlist, its castable variants only, every URI pointing back to the relay. The
     * audio renditions of a master, and everything an [audio] playlist lists, are marked audio.
     */
    private fun servePlaylist(
        text: String, url: String, out: HeadTrackingStream, cors: String?, base: String, headOnly: Boolean,
        audio: Boolean
    ) {
        val rewritten = HlsRewriter.rewrite(HlsVariants.castable(text), url) { target, audioRendition ->
            "$base/$RELAY/${register(Relayed(target, audio || audioRendition))}"
        }.toByteArray(Charsets.UTF_8)
        writeHead(out, 200, cors, PLAYLIST_TYPE, rewritten.size.toLong(), null)
        if (!headOnly) out.write(rewritten)
        out.flush()
    }

    /**
     * An audio playlist, or an audio segment: packed AAC is rewrapped into MPEG-TS ([PackedAudioTs]),
     * so the whole segment is read (a HEAD too, for its new length) and served without ranges.
     */
    private fun relayAudio(url: String, request: HttpRequest, out: HeadTrackingStream, cors: String?, base: String) {
        val headOnly = request.method == "HEAD"
        if (!headOnly) takePrefetched(url)?.let { return servePlaylist(it, url, out, cors, base, headOnly, audio = true) }
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return respond(out, response.code, cors)
            val source = response.body.source()
            if (response.isPlaylistType() || source.startsWithPlaylistTag()) {
                val text = response.body.byteStream().readBounded(MAX_PLAYLIST_BYTES).toString(Charsets.UTF_8)
                return servePlaylist(text, url, out, cors, base, headOnly, audio = true)
            }
            val segment = response.body.byteStream().readBounded(MAX_AUDIO_SEGMENT_BYTES)
            val ts = if (PackedAudioTs.isPackedAudio(segment)) PackedAudioTs.toTs(segment) else null
            stolmTrace { "relay: audio segment ${segment.size} bytes, as MPEG-TS: ${ts?.size}" }
            val body = ts ?: segment
            writeHead(out, 200, cors, if (ts != null) TS_TYPE else response.header("Content-Type"), body.size.toLong(), null)
            if (!headOnly) out.write(body)
            out.flush()
        }
    }

    private fun fetch(url: String, request: HttpRequest, method: String) = client.newCall(
        Request.Builder().url(url).method(method, null).apply {
            // only a well-formed byte range is forwarded: OkHttp throws on a non ASCII value,
            // which escaped the worker and took the whole app down
            request.headers["range"]?.takeIf(BYTE_RANGE::matches)?.let { header("Range", it) }
        }.build()
    ).execute()

    private fun register(target: Relayed): Int = synchronized(targets) {
        val id = nextId++
        targets[id] = target
        id
    }

    private fun respond(out: HeadTrackingStream, code: Int, cors: String?) {
        stolmTrace { "relay: answered $code cors=$cors" }
        writeHead(out, code, cors, null, 0, null)
        out.flush()
    }

    private fun writeHead(
        out: HeadTrackingStream, code: Int, cors: String?, type: String?, length: Long?, range: String?
    ) {
        val head = buildString {
            append("HTTP/1.1 $code ${reason(code)}\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            type?.let { append("Content-Type: $it\r\n") }
            length?.let { append("Content-Length: $it\r\n") }
            range?.let { append("Content-Range: $it\r\n") }
            cors?.let {
                append("Access-Control-Allow-Origin: $it\r\n")
                append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
                append("Access-Control-Allow-Headers: Range\r\n")
                append("Access-Control-Expose-Headers: Content-Length, Content-Range\r\n")
                append("Access-Control-Max-Age: 600\r\n")
                append("Vary: Origin\r\n")
            }
            append("\r\n")
        }
        out.wroteHead = true
        out.write(head.toByteArray(Charsets.US_ASCII))
    }

    override fun close() {
        source = null
        prefetched.clear()
        runCatching { serverSocket.close() }
        workers.shutdownNow()
        synchronized(targets) { targets.clear() }
    }

    /** The response stream, remembering whether a status line went out already. */
    private class HeadTrackingStream(out: OutputStream) : BufferedOutputStream(out) {
        var wroteHead = false
    }

    /** One request line and its headers, bounded in size and count. */
    private class HttpRequest(val method: String, val path: String, val headers: Map<String, String>) {
        companion object {
            fun read(input: InputStream): HttpRequest? {
                val lines = mutableListOf<String>()
                var total = 0
                while (true) {
                    val line = readLine(input) ?: return null
                    total += line.length
                    if (total > MAX_HEADER_BYTES || lines.size > MAX_HEADERS) return null
                    if (line.isEmpty()) break
                    lines += line
                }
                val requestLine = lines.firstOrNull()?.split(' ') ?: return null
                if (requestLine.size != 3 || !requestLine[2].startsWith("HTTP/1.")) return null
                val headers = lines.drop(1).mapNotNull { header ->
                    val colon = header.indexOf(':').takeIf { it > 0 } ?: return@mapNotNull null
                    header.substring(0, colon).trim().lowercase() to header.substring(colon + 1).trim()
                }.toMap()
                return HttpRequest(requestLine[0], requestLine[1].substringBefore('?'), headers)
            }

            private fun readLine(input: InputStream): String? {
                val line = StringBuilder()
                while (line.length <= MAX_HEADER_BYTES) {
                    when (val byte = input.read()) {
                        -1 -> return null
                        '\n'.code -> return line.toString().trimEnd('\r')
                        else -> line.append(byte.toChar())
                    }
                }
                return null
            }
        }
    }

    internal companion object {
        private const val BACKLOG = 8
        private const val MAX_CONNECTIONS = 4
        private const val IDLE_TIMEOUT_MS = 15_000
        private const val TOKEN_BYTES = 32
        private const val MAX_HEADER_BYTES = 8 * 1024
        private const val MAX_HEADERS = 50
        private const val MAX_PLAYLIST_BYTES = 2 * 1024 * 1024
        // least recently used first out: a live stream keeps only its recent segments, and the
        // map stays a few megabytes even with long googlevideo addresses
        private const val MAX_TARGETS = 5_000
        private val BYTE_RANGE = Regex("bytes=\\d*-\\d*")
        private const val INITIAL_TARGETS = 256
        private const val LOAD_FACTOR = 0.75f
        private const val MASTER = "master.m3u8"
        private const val RELAY = "r"
        private const val MAP_TAG = "#EXT-X-MAP"
        private const val TS_TYPE = "video/mp2t"
        /** An audio segment of a few seconds weighs well under this. */
        private const val MAX_AUDIO_SEGMENT_BYTES = 8 * 1024 * 1024
        /** A prefetched playlist older than this is fetched again (a live one moves on). */
        private const val PREFETCH_TTL_NANOS = 10_000_000_000L
        private const val PLAYLIST_TYPE = "application/vnd.apple.mpegurl"

        /**
         * Google's receiver pages are served from gstatic.com (the Default Media Receiver from
         * www.gstatic.com): the only origins allowed to read the relayed media from a script.
         * DÉDUIT, to confirm with a real receiver; the peer address and the token guard the
         * media anyway.
         */
        private const val RECEIVER_SITE = "gstatic.com"

        fun isReceiverOrigin(origin: String): Boolean {
            val url = origin.toHttpUrlOrNull() ?: return false
            return url.scheme == "https" && url.encodedPath == "/" &&
                (url.host == RECEIVER_SITE || url.host.endsWith(".$RECEIVER_SITE"))
        }

        /** Every relayed address is public https, in pure Piped mode through the instance proxy. */
        fun defaultRoute(url: String): String? {
            val target = ProxyHelper.streamUrl(url)?.toHttpUrlOrNull() ?: return null
            if (target.scheme != "https" || !isPublicHost(target.host)) return null
            return target.toString()
        }

        /** Not an address of the local network or of the phone (names are checked by [PublicOnlyDns]). */
        fun isPublicHost(host: String): Boolean = PublicAddress.isPublicHost(host)

        /** Resolutions to a local address are refused: a public name cannot lead to the LAN. */
        private object PublicOnlyDns : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                Dns.SYSTEM.lookup(hostname).filter { it.isPublic() }
                    .ifEmpty { throw UnknownHostException("$hostname has no public address") }
        }

        fun defaultUpstreamClient(): OkHttpClient = SharedHttpClient.base.newBuilder()
            .dns(PublicOnlyDns)
            .addInterceptor(GenericUserAgentInterceptor())
            // every hop, redirects included, must be an address the relay may fetch as it is
            .addNetworkInterceptor { chain ->
                val url = chain.request().url.toString()
                if (defaultRoute(url) != url) throw IOException("relay target refused")
                chain.proceed(chain.request())
            }
            .build()

        /** Host and first path segments of [url], for the debug trace: no query, no token. */
        fun shape(url: String): String = url.toHttpUrlOrNull()?.let { "${it.host}/${it.pathSegments.take(3).joinToString("/")}" }
            ?: "?"

        fun tokenMatches(given: String, expected: String) =
            MessageDigest.isEqual(given.toByteArray(Charsets.US_ASCII), expected.toByteArray(Charsets.US_ASCII))

        private val PLAYLIST_TAG = "#EXTM3U".encodeUtf8()

        private fun Response.isPlaylistType() = body.contentType()?.subtype?.contains("mpegurl") == true

        /** RFC 8216: every playlist starts with #EXTM3U. Peeked, not consumed. */
        private fun BufferedSource.startsWithPlaylistTag() =
            request(PLAYLIST_TAG.size.toLong()) && rangeEquals(0, PLAYLIST_TAG)

        private fun InputStream.readBounded(limit: Int): ByteArray {
            val bytes = readNBytes(limit + 1)
            if (bytes.size > limit) throw IOException("playlist too large")
            return bytes
        }

        private fun reason(code: Int) = when (code) {
            200 -> "OK"
            204 -> "No Content"
            206 -> "Partial Content"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            502 -> "Bad Gateway"
            else -> "Status"
        }

        private fun Socket.closeQuietly() = try {
            close()
        } catch (e: IOException) {
            Unit
        }
    }
}

/**
 * Keeps the variants of a master playlist that every Cast receiver plays: H.264 video with AAC-LC
 * audio. YouTube lists H.264 variants in MPEG-TS next to VP9 ones in fragmented MP4; a receiver
 * cannot switch from one segment format to the other (it stalls on the first VP9 segment) and
 * older ones do not decode VP9 at all. HE-AAC variants are left out too: only AAC-LC was seen
 * playing on a Chromecast. A playlist without any such variant is left as it is.
 */
internal object HlsVariants {
    const val STREAM_INF = "#EXT-X-STREAM-INF:"
    private const val I_FRAME_STREAM_INF = "#EXT-X-I-FRAME-STREAM-INF:"
    private val codecs = Regex("CODECS=\"([^\"]*)\"")
    private val castableCodec = Regex("^(avc1\\..+|avc3\\..+|mp4a\\.40\\.2)$")

    private fun isCastable(tag: String): Boolean {
        val list = codecs.find(tag)?.groupValues?.get(1) ?: return false
        return list.split(',').map { it.trim() }.all { castableCodec.matches(it) }
    }

    private val audioGroup = Regex("AUDIO=\"([^\"]*)\"")
    private val uri = Regex("URI=\"([^\"]*)\"")

    /** The first variant of a master playlist: its tag and its URI. */
    fun firstVariant(master: String): Pair<String, String>? {
        val lines = master.lines()
        val index = lines.indexOfFirst { it.startsWith(STREAM_INF) }.takeIf { it >= 0 } ?: return null
        val uri = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: return null
        return lines[index] to uri.trim()
    }

    /** The URI of the audio rendition of [variantTag], when its audio is apart from the video. */
    fun audioUri(master: String, variantTag: String): String? {
        val group = audioGroup.find(variantTag)?.groupValues?.get(1) ?: return null
        return master.lines().firstOrNull {
            it.startsWith("#EXT-X-MEDIA:") && "TYPE=AUDIO" in it && "GROUP-ID=\"$group\"" in it
        }?.let { uri.find(it)?.groupValues?.get(1) }
    }

    fun castable(playlist: String): String {
        if (!playlist.contains(STREAM_INF)) return playlist
        val lines = playlist.lines()
        if (lines.none { it.startsWith(STREAM_INF) && isCastable(it) }) return playlist
        val kept = ArrayList<String>(lines.size)
        var dropUri = false
        for (line in lines) {
            when {
                line.startsWith(STREAM_INF) -> {
                    dropUri = !isCastable(line)
                    if (!dropUri) kept += line
                }
                line.startsWith(I_FRAME_STREAM_INF) -> if (isCastable(line)) kept += line
                // the URI line of the variant above
                line.isNotBlank() && !line.startsWith("#") -> {
                    if (!dropUri) kept += line
                    dropUri = false
                }
                else -> kept += line
            }
        }
        return kept.joinToString("\n")
    }
}

/** Points every URI of an HLS playlist (lines and URI="..." attributes) through the relay. */
internal object HlsRewriter {
    private val uriAttribute = Regex("URI=\"([^\"]*)\"")

    /** [relay] gets each absolute URI, and whether it is an audio rendition of a master playlist. */
    fun rewrite(playlist: String, playlistUrl: String, relay: (uri: String, audioRendition: Boolean) -> String): String {
        val base = playlistUrl.toHttpUrlOrNull() ?: throw IOException("bad playlist url")
        fun absolute(uri: String) = base.resolve(uri)?.toString() ?: throw IOException("bad uri in playlist")

        return playlist.lineSequence().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#") -> {
                    val audioRendition = line.startsWith("#EXT-X-MEDIA:") && "TYPE=AUDIO" in line
                    uriAttribute.replace(line) { match ->
                        "URI=\"${relay(absolute(match.groupValues[1]), audioRendition)}\""
                    }
                }
                else -> relay(absolute(line.trim()), false)
            }
        }
    }
}
// endregion
