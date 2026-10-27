package com.watube.yard.cast

import android.content.Context
import android.net.InetAddresses
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.core.os.BundleCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.watube.yard.BuildConfig
import com.watube.yard.R
import com.watube.yard.api.obj.Streams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import javax.net.SocketFactory
import kotlin.time.Duration.Companion.milliseconds

// region Stolm
/**
 * Stolm, Watube's own Cast (Cast v2 protocol, no Google library): what the player screen shows
 * about it. Written by the player service, which owns the cast.
 */
object Stolm {
    sealed interface State {
        data object Idle : State
        data class Connecting(val deviceName: String) : State
        data class Casting(val deviceName: String) : State

        /** The TV never reached the phone: [target] can be tried again once allowed to. */
        data class Unreachable(val target: CastTarget) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    internal fun update(state: State) {
        _state.value = state
    }
}

/** The receiver the user picked, as sent from the device picker to the player service. */
data class CastTarget(
    val name: String,
    val address: InetAddress,
    val port: Int,
    /** The network it was found on, to reach it through that one only. */
    val network: Network?,
    /** Its DNS-SD instance name, to ask it for its IPv4 address ([CastMdns]). */
    val serviceName: String
) {
    fun toBundle() = Bundle().apply {
        putString(NAME, name)
        putString(SERVICE, serviceName)
        putString(ADDRESS, address.hostAddress)
        putInt(PORT, port)
        putParcelable(NETWORK, network)
    }

    companion object {
        private const val NAME = "name"
        private const val ADDRESS = "address"
        private const val PORT = "port"
        private const val NETWORK = "network"
        private const val SERVICE = "service"
        private const val MAX_PORT = 65_535

        /**
         * Reads a target back, refusing anything but a numeric address of the local network: no
         * name is ever resolved, no public or loopback address is ever connected to.
         */
        fun fromBundle(bundle: Bundle): CastTarget? {
            val literal = bundle.getString(ADDRESS)?.takeIf(InetAddresses::isNumericAddress) ?: return null
            val address = InetAddresses.parseNumericAddress(literal)
            if (!address.isSiteLocalAddress && !address.isLinkLocalAddress) return null
            val port = bundle.getInt(PORT).takeIf { it in 1..MAX_PORT } ?: return null
            return CastTarget(
                name = bundle.getString(NAME).orEmpty().take(MAX_NAME_LENGTH),
                address = address,
                port = port,
                network = BundleCompat.getParcelable(bundle, NETWORK, Network::class.java),
                serviceName = bundle.getString(SERVICE).orEmpty().take(MAX_NAME_LENGTH)
            )
        }

        /** "fn" is user-chosen text: long enough for any real name, bounded for the UI. */
        const val MAX_NAME_LENGTH = 64
    }
}

/** A failure to show the user, with the reason as a string resource. */
class StolmException(val messageRes: Int, cause: Throwable? = null) : IOException(cause)

/**
 * Developer trace, debug builds only ("Stolm" tag; R8 drops it from release builds): what the
 * receiver answered and asked, never a relay token. JVM tests have no Log: ignored there.
 */
internal inline fun stolmTrace(message: () -> String) {
    if (BuildConfig.DEBUG) runCatching { Log.d("Stolm", message()) }
}

/** A failure and its causes, on one line. */
internal fun Throwable.chain(): String =
    generateSequence(this) { it.cause }.take(5).joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }

/**
 * One cast, from the authenticated connection to the end: the receiver session, the relay
 * serving it the video, the [player] standing for the TV, and the locks keeping the relay
 * awake while the TV plays.
 */
@OptIn(UnstableApi::class)
internal class StolmCast private constructor(
    context: Context,
    val target: CastTarget,
    private val session: CastSession,
    private val server: CastMediaServer,
    val player: StolmPlayer
) {
    private val wakeLock = context.getSystemService<PowerManager>()!!
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "watube:stolm")
        .apply { setReferenceCounted(false) }

    @Suppress("DEPRECATION") // LOW_LATENCY only works with the screen on: the relay must not stall when it is off
    private val wifiLock = context.applicationContext.getSystemService<WifiManager>()!!
        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "watube:stolm")
        .apply { setReferenceCounted(false) }

    /** Completes when the TV side ends: connection lost, app closed on the TV... */
    val finished get() = session.finished

    /** Whether the receiver ever asked the relay for the current video. */
    val receiverReached get() = server.receiverReached

    init {
        // battery: the phone only stays awake while the TV plays or buffers
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val busy = player.playWhenReady && player.playbackState != Player.STATE_IDLE &&
                    player.playbackState != Player.STATE_ENDED
                if (busy) holdLocks() else releaseLocks()
            }
        })
    }

    /**
     * Plays [streams] of [videoId] on the TV from [positionMs]. Only the HLS stream can be cast
     * (a phone-made DASH or SABR stream cannot be read by the receiver).
     */
    suspend fun play(videoId: String, streams: Streams, mediaItem: MediaItem, positionMs: Long) {
        val hls = streams.hls?.takeIf { it.isNotBlank() }
            ?: throw StolmException(R.string.stolm_no_stream)
        holdLocks()
        val (url, formats) = withContext(Dispatchers.IO) {
            server.publish(hls) to runCatching { server.segmentFormats(hls) }
                .onFailure { e -> stolmTrace { "playlist check failed: ${e.chain()}" } }.getOrDefault(HlsFormats.DEFAULT)
        }
        stolmTrace { "play live=${streams.isLive} $formats hls=${CastMediaServer.shape(hls)}" }
        val item = StolmItem(
            videoId = videoId,
            mediaItem = mediaItem,
            title = streams.title,
            isLive = streams.isLive,
            durationMs = streams.duration * 1000
        )
        player.load(item, url, positionMs, formats)
    }

    private fun holdLocks() {
        if (!wakeLock.isHeld) wakeLock.acquire(MAX_LOCK_MS)
        if (!wifiLock.isHeld) wifiLock.acquire()
    }

    private fun releaseLocks() {
        if (wakeLock.isHeld) wakeLock.release()
        if (wifiLock.isHeld) wifiLock.release()
    }

    /** Main thread. Quits the receiver app on the TV, closes the relay, lets the phone sleep again. */
    fun close() {
        releaseLocks()
        player.release()
        // outlives the player service, which may be going away
        closing.launch {
            withTimeoutOrNull(CLOSE_TIMEOUT_MS.milliseconds) { session.close() }
            server.close()
        }
    }

    companion object {
        /** A safety net: a single video plays for less, the lock is taken again at each load. */
        private const val MAX_LOCK_MS = 4 * 60 * 60 * 1000L
        private const val CLOSE_TIMEOUT_MS = 5_000L

        private val closing = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Connects to [target], authenticates it and launches the receiver app. */
        suspend fun connect(
            context: Context,
            target: CastTarget,
            looper: Looper,
            seekIncrementMs: Long,
            onStopRequested: () -> Unit
        ): StolmCast {
            val anchors = withContext(Dispatchers.IO) { CastTrustStore.anchors(context) }
            // IPv4 only: receivers take Cast connections over IPv4 (an IPv6 one may time out), and
            // the relay URL the receiver loads cannot hold the IPv6 link-local address of the phone
            val address = target.address as? Inet4Address
                ?: withContext(Dispatchers.IO) { CastMdns.ipv4Of(target.serviceName, target.network) }
                ?: run {
                    stolmTrace { "no IPv4 address for ${target.serviceName}" }
                    throw StolmException(R.string.stolm_unreachable)
                }
            val channel = try {
                CastChannel.open(
                    address, target.port, anchors,
                    target.network?.socketFactory ?: SocketFactory.getDefault()
                )
            } catch (e: CastAuthException) {
                stolmTrace { "not genuine: ${e.chain()}" }
                throw StolmException(R.string.stolm_not_genuine, e)
            } catch (e: IOException) {
                stolmTrace { "unreachable ${address.hostAddress}:${target.port}: ${e.chain()}" }
                throw StolmException(R.string.stolm_unreachable, e)
            }
            stolmTrace { "authenticated ${address.hostAddress}:${target.port} from ${channel.localAddress.hostAddress}" }
            try {
                val session = CastSession.start(channel)
                val server = withContext(Dispatchers.IO) {
                    CastMediaServer(channel.localAddress, channel.remoteAddress)
                }
                val player = withContext(Dispatchers.Main) {
                    StolmPlayer(looper, session, seekIncrementMs, onStopRequested)
                }
                return withContext(Dispatchers.Main) { StolmCast(context, target, session, server, player) }
            } catch (e: CancellationException) {
                channel.close()
                throw e
            } catch (e: Exception) {
                stolmTrace { "launch failed: ${e.chain()}" }
                channel.close()
                throw e as? StolmException ?: StolmException(R.string.stolm_launch_failed, e)
            }
        }
    }
}
// endregion
