package com.watube.yard.cast

import com.watube.yard.cast.proto.CastMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

// region Stolm
/** What the receiver reports about the media it plays. */
data class CastMediaStatus(
    /** IDLE, LOADING, BUFFERING, PLAYING or PAUSED */
    val playerState: String,
    val positionSeconds: Double,
    val durationSeconds: Double?,
    /** Why the player is IDLE: FINISHED, CANCELLED, INTERRUPTED or ERROR */
    val idleReason: String?,
    val playbackRate: Double,
    /** When the status arrived ([System.nanoTime]): the position goes on from there. */
    val receivedAtNanos: Long
)

/**
 * The segment formats of an HLS stream, for the receiver (null: its default, MPEG-TS): [audio] is
 * the audio segments' one ("aac", "ts", "fmp4"), [video] the video segments' one ("mpeg2_ts",
 * "fmp4"), told apart when the audio is in its own segments.
 */
data class HlsFormats(val audio: String?, val video: String?) {
    companion object {
        const val FMP4_FORMAT = "fmp4"
        const val MPEG2_TS = "mpeg2_ts"
        const val TS = "ts"
        val DEFAULT = HlsFormats(null, null)
        val FMP4 = HlsFormats(FMP4_FORMAT, FMP4_FORMAT)
    }
}

/** The volume of the receiver itself (the TV), not of the stream. */
data class CastVolume(val level: Double, val muted: Boolean)

/**
 * The Default Media Receiver session on one device, over an authenticated [CastChannel]:
 * launch, load a stream, control it, and [close] (which also quits the receiver app on the TV).
 */
internal class CastSession private constructor(
    private val channel: CastChannel,
    private val transportId: String,
    private val receiverSessionId: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var mediaSessionId: Int? = null

    private val _mediaStatus = MutableStateFlow<CastMediaStatus?>(null)
    val mediaStatus: StateFlow<CastMediaStatus?> = _mediaStatus

    private val _volume = MutableStateFlow<CastVolume?>(null)
    val volume: StateFlow<CastVolume?> = _volume

    private val _finished = CompletableDeferred<Throwable?>()

    /**
     * Completes when the session is over: the connection dropped (with its failure), the
     * receiver app was closed on the TV or by another sender, or [close] was called (null).
     */
    val finished: Deferred<Throwable?> = _finished

    init {
        scope.launch {
            channel.messages.collect { message ->
                val json = CastMessages.json(message) ?: return@collect
                stolmTrace { "<- ${message.namespace.substringAfterLast(':')} ${message.sourceId}: ${redacted(json)}" }
                when (CastMessages.type(json)) {
                    "MEDIA_STATUS" -> if (message.sourceId == transportId) onMediaStatus(json)
                    "RECEIVER_STATUS" -> onReceiverStatus(json)
                    // the receiver app closed the virtual connection
                    "CLOSE" -> if (message.sourceId == transportId) finish(IOException("Cast receiver app closed"))
                }
            }
        }
        scope.launch { finish(channel.closed.await() ?: IOException("Cast connection closed")) }
    }

    /** Statuses come from the collector and from [load]: one at a time, never an older media's. */
    @Synchronized
    private fun onMediaStatus(json: JsonObject) {
        val status = json["status"]?.jsonArray?.firstOrNull()?.jsonObject ?: run {
            // an empty status: the media is gone from the receiver
            _mediaStatus.value = _mediaStatus.value?.copy(playerState = "IDLE", idleReason = "CANCELLED")
            return
        }
        val id = status["mediaSessionId"]?.jsonPrimitive?.intOrNull
        // the end of the previous media (INTERRUPTED by a LOAD) may arrive after the new one
        if (id != null && (mediaSessionId ?: Int.MIN_VALUE) > id) return
        id?.let { mediaSessionId = it }
        _mediaStatus.value = parseMediaStatus(status, _mediaStatus.value, System.nanoTime())
    }

    private fun onReceiverStatus(json: JsonObject) {
        receiverVolume(json)?.let { _volume.value = it }
        // another sender (or the TV remote) replaced or quit the receiver app: no "applications"
        // at all means nothing runs anymore
        val status = json["status"]?.jsonObject ?: return
        val stillRunning = status["applications"]?.jsonArray.orEmpty().any {
            it.jsonObject["sessionId"]?.jsonPrimitive?.contentOrNull == receiverSessionId
        }
        if (!stillRunning) finish(IOException("Cast receiver app stopped"))
    }

    private fun finish(cause: Throwable?) {
        if (!_finished.complete(cause)) return
        scope.cancel()
        channel.close()
    }

    /**
     * Plays [url] on the TV. No artwork is sent: the receiver would fetch it itself from
     * Google's image servers. [formats] tells the receiver how the HLS segments are made: it
     * reads them all as MPEG-TS otherwise.
     */
    suspend fun load(
        url: String,
        contentType: String,
        isLive: Boolean,
        title: String,
        positionSeconds: Double,
        formats: HlsFormats
    ) {
        val requestId = nextRequestId()
        val load = buildJsonObject {
            put("type", "LOAD")
            put("requestId", requestId)
            putJsonObject("media") {
                put("contentId", url)
                put("contentUrl", url)
                put("contentType", contentType)
                put("streamType", if (isLive) "LIVE" else "BUFFERED")
                formats.audio?.let { put("hlsSegmentFormat", it) }
                formats.video?.let { put("hlsVideoSegmentFormat", it) }
                putJsonObject("metadata") {
                    put("metadataType", 0)
                    put("title", title)
                }
            }
            put("autoplay", true)
            if (!isLive) put("currentTime", positionSeconds)
        }
        // the old media's status must not be taken for the new one
        _mediaStatus.value = null
        stolmTrace { "-> LOAD ${redacted(load)}" }
        val reply = channel.request(text(CastNamespaces.MEDIA, transportId, load)) { _, json ->
            json["requestId"]?.jsonPrimitive?.intOrNull == requestId &&
                (CastMessages.type(json) == "MEDIA_STATUS" || CastMessages.type(json) in MEDIA_ERRORS)
        }
        stolmTrace { "<- LOAD reply ${redacted(reply)}" }
        if (CastMessages.type(reply) in MEDIA_ERRORS) throw IOException("Cast LOAD refused: ${CastMessages.type(reply)}")
        onMediaStatus(reply)
    }

    suspend fun play() = mediaCommand("PLAY")
    suspend fun pause() = mediaCommand("PAUSE")
    suspend fun seek(positionSeconds: Double) = mediaCommand("SEEK") { put("currentTime", positionSeconds) }

    suspend fun setVolume(level: Double) = receiverCommand("SET_VOLUME") {
        putJsonObject("volume") { put("level", level.coerceIn(0.0, 1.0)) }
    }

    suspend fun setMuted(muted: Boolean) = receiverCommand("SET_VOLUME") {
        putJsonObject("volume") { put("muted", muted) }
    }

    private suspend fun receiverCommand(type: String, extra: JsonObjectBuilder.() -> Unit) = channel.send(
        text(CastNamespaces.RECEIVER, CastMessages.PLATFORM_RECEIVER, buildJsonObject {
            put("type", type)
            put("requestId", nextRequestId())
            extra()
        })
    )

    private suspend fun mediaCommand(type: String, extra: JsonObjectBuilder.() -> Unit = {}) {
        val id = mediaSessionId ?: return
        channel.send(text(CastNamespaces.MEDIA, transportId, buildJsonObject {
            put("type", type)
            put("requestId", nextRequestId())
            put("mediaSessionId", id)
            extra()
        }))
    }

    /** Quits the receiver app on the TV and closes the connection. */
    suspend fun close() {
        if (_finished.isCompleted) return
        runCatching {
            withTimeout(REPLY_TIMEOUT_MS) {
                receiverCommand("STOP") { put("sessionId", receiverSessionId) }
            }
        }
        finish(null)
    }

    private fun text(namespace: String, destination: String, payload: JsonObject): CastMessage =
        CastMessages.text(namespace, payload.toString(), senderId, destination)

    companion object {
        /** Google's Default Media Receiver */
        private const val DEFAULT_RECEIVER_APP = "CC1AD845"
        private const val REPLY_TIMEOUT_MS = 15_000L
        private val MEDIA_ERRORS = setOf("LOAD_FAILED", "LOAD_CANCELLED", "INVALID_REQUEST")

        private val relayToken = Regex("[0-9a-f]{64}")

        /** A message for the debug trace: relay tokens masked, bounded. */
        private fun redacted(json: JsonObject) = json.toString().replace(relayToken, "<token>").take(1500)

        private val requestIds = AtomicInteger(1)
        private fun nextRequestId() = requestIds.getAndIncrement()

        /** A fresh sender id per app run: no stable identifier is given to the receiver. */
        private val senderId = "sender-" + ByteArray(6).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }

        internal fun parseMediaStatus(status: JsonObject, previous: CastMediaStatus?, now: Long): CastMediaStatus {
            fun string(key: String) = status[key]?.jsonPrimitive?.contentOrNull
            fun number(key: String) = status[key]?.jsonPrimitive?.doubleOrNull
            return CastMediaStatus(
                playerState = string("playerState") ?: previous?.playerState ?: "IDLE",
                positionSeconds = number("currentTime") ?: previous?.positionSeconds ?: 0.0,
                durationSeconds = status["media"]?.jsonObject?.get("duration")?.jsonPrimitive?.doubleOrNull
                    ?: previous?.durationSeconds,
                idleReason = string("idleReason"),
                playbackRate = number("playbackRate") ?: 1.0,
                receivedAtNanos = now
            )
        }

        private fun receiverVolume(json: JsonObject): CastVolume? {
            val volume = json["status"]?.jsonObject?.get("volume")?.jsonObject ?: return null
            val level = volume["level"]?.jsonPrimitive?.doubleOrNull ?: return null
            return CastVolume(level, volume["muted"]?.jsonPrimitive?.booleanOrNull ?: false)
        }

        /** Launches the Default Media Receiver on the device of [channel] and joins it. */
        suspend fun start(channel: CastChannel): CastSession {
            channel.send(CastMessages.text(CastNamespaces.CONNECTION, CONNECT, senderId, CastMessages.PLATFORM_RECEIVER))

            val launch = buildJsonObject {
                put("type", "LAUNCH")
                put("requestId", nextRequestId())
                put("appId", DEFAULT_RECEIVER_APP)
            }
            val status = channel.request(
                CastMessages.text(CastNamespaces.RECEIVER, launch.toString(), senderId, CastMessages.PLATFORM_RECEIVER)
            ) { _, json -> CastMessages.type(json) == "RECEIVER_STATUS" && runningApp(json) != null }
            val (transportId, sessionId) = runningApp(status)!!
            stolmTrace { "launched: ${redacted(status)}" }

            channel.send(CastMessages.text(CastNamespaces.CONNECTION, CONNECT, senderId, transportId))
            return CastSession(channel, transportId, sessionId).also { session ->
                receiverVolume(status)?.let { session._volume.value = it }
            }
        }

        /**
         * Sends [message] and returns the first reply [accept] takes. The reply is subscribed to
         * before sending, so a fast receiver cannot answer before anyone listens.
         */
        private suspend fun CastChannel.request(
            message: CastMessage,
            accept: (CastMessage, JsonObject) -> Boolean
        ): JsonObject = withTimeout(REPLY_TIMEOUT_MS) {
            coroutineScope {
                val reply = async(start = CoroutineStart.UNDISPATCHED) {
                    messages.first { reply -> CastMessages.json(reply)?.let { accept(reply, it) } == true }
                }
                send(message)
                CastMessages.json(reply.await())!!
            }
        }

        private const val CONNECT = "{\"type\":\"CONNECT\"}"

        /** transportId and sessionId of the Default Media Receiver, when running. */
        private fun runningApp(json: JsonObject): Pair<String, String>? = json["status"]?.jsonObject
            ?.get("applications")?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["appId"]?.jsonPrimitive?.contentOrNull == DEFAULT_RECEIVER_APP }
            ?.let { app ->
                val transport = app["transportId"]?.jsonPrimitive?.contentOrNull ?: return@let null
                val session = app["sessionId"]?.jsonPrimitive?.contentOrNull ?: return@let null
                transport to session
            }
    }
}
// endregion
