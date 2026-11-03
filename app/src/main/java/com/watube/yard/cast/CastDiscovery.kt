package com.watube.yard.cast

import android.content.Context
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume

// region Stolm
/**
 * The system refused to search (Wi-Fi off, no network...). Its own type: a coroutine's
 * cancellation is an IllegalStateException too, and must not be taken for it.
 */
class DiscoveryFailedException(errorCode: Int) : IOException("Cast discovery failed: $errorCode")

/** A Cast receiver found on the local network. */
data class CastDevice(
    /** "fn": the name the user gave it */
    val name: String,
    /** "md": the model ("Chromecast", "Google TV Streamer"...) */
    val model: String,
    val address: InetAddress,
    val port: Int,
    /** The network it was found on. */
    val network: Network?,
    /** The DNS-SD instance name. */
    val serviceName: String
) {
    fun toTarget() = CastTarget(name, address, port, network, serviceName)
}

/**
 * Finds Cast receivers on the Wi-Fi with DNS-SD (`_googlecast._tcp`), through the system's
 * own resolver. Only runs while [devices] is collected, i.e. while the device picker is open:
 * nothing is announced or queried in the background. On Android 17 this needs the local network
 * permission, asked by the picker before it searches.
 */
class CastDiscovery(context: Context) {
    private val nsd = context.getSystemService<NsdManager>()!!
    private val executor = ContextCompat.getMainExecutor(context)

    /** One resolution at a time: NsdManager refuses parallel ones before Android 14. */
    private val resolveLock = Mutex()

    val devices: Flow<List<CastDevice>> = callbackFlow {
        // filled from the NSD thread and from the resolutions: every access holds its lock
        val found = linkedMapOf<String, CastDevice>()
        fun publish() = synchronized(found) { trySend(found.values.sortedBy { it.name.lowercase() }) }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                launch {
                    val info = resolveLock.withLock { resolve(service) } ?: return@launch
                    val device = toDevice(info) ?: return@launch
                    synchronized(found) { found[service.serviceName] = device }
                    publish()
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                if (synchronized(found) { found.remove(service.serviceName) } != null) publish()
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(DiscoveryFailedException(errorCode))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        publish()
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }.distinctUntilChanged()

    private suspend fun resolve(service: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        fun deliver(info: NsdServiceInfo?) {
            if (cont.isActive) cont.resume(info)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(info: NsdServiceInfo) {
                    // the first answer is enough: unregister right away
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                    deliver(info)
                }

                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = deliver(null)
                override fun onServiceLost() = deliver(null)
                override fun onServiceInfoCallbackUnregistered() = deliver(null)
            }
            nsd.registerServiceInfoCallback(service, executor, callback)
            cont.invokeOnCancellation { runCatching { nsd.unregisterServiceInfoCallback(callback) } }
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) = deliver(info)
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = deliver(null)
            })
        }
    }

    private suspend fun toDevice(info: NsdServiceInfo): CastDevice? {
        val address = address(info) ?: return null
        stolmTrace { "found ${info.serviceName} at ${address.hostAddress}" }

        fun text(key: String) = info.attributes[key]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
        return CastDevice(
            name = (text("fn") ?: info.serviceName).take(CastTarget.MAX_NAME_LENGTH),
            model = text("md").orEmpty().take(CastTarget.MAX_NAME_LENGTH),
            address = address,
            port = info.port.takeIf { it > 0 } ?: CastChannel.PORT,
            network = info.networkOrNull(),
            serviceName = info.serviceName.take(CastTarget.MAX_NAME_LENGTH)
        )
    }

    /**
     * The receiver's IPv4 address of the local network: receivers take Cast connections over IPv4
     * only, and the relay URL they load cannot hold an IPv6 link-local address. IPv6 is kept as a
     * last resort. A resolved public address is not a Cast device: refused.
     */
    private suspend fun address(info: NsdServiceInfo): InetAddress? {
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(info.host)
        }.filter { it.isSiteLocalAddress || it.isLinkLocalAddress }
        addresses.firstOrNull { it is Inet4Address }?.let { return it }
        // before Android 14 the system gives one address: ask the receiver for its IPv4 one
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            withContext(Dispatchers.IO) { CastMdns.ipv4Of(info.serviceName, info.networkOrNull()) }?.let { return it }
        }
        return addresses.firstOrNull()
    }

    companion object {
        private const val SERVICE_TYPE = "_googlecast._tcp"
        /** NsdServiceInfo.getNetwork: Android 13 with its T extension 3, not Android 12L. */
        private const val NETWORK_EXTENSION = 3

        /** The network the receiver was found on, or null where the system cannot tell it. */
        private fun NsdServiceInfo.networkOrNull(): Network? =
            if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= NETWORK_EXTENSION) {
                network
            } else {
                null
            }
    }
}
// endregion
