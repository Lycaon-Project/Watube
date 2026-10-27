package com.watube.yard.cast

import android.content.Context
import android.net.Network
import android.net.nsd.DiscoveryRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresExtension
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
 * nothing is announced or queried in the background.
 *
 * Where the system has its own picker ([hasSystemPicker]), [pickWithSystemPicker] is used
 * instead: the user chooses in a system dialog and Watube only learns the chosen device, with
 * no access to the rest of the local network (and no permission to ask for, on Android 17).
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

    /** Shows the system's device picker; null when the user dismisses it. */
    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = PICKER_EXTENSION)
    suspend fun pickWithSystemPicker(): CastDevice? = pickedService()?.let { toDevice(it) }

    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = PICKER_EXTENSION)
    private suspend fun pickedService(): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        val request = DiscoveryRequest.Builder(SERVICE_TYPE)
            .setFlags(DiscoveryRequest.FLAG_SHOW_PICKER)
            .setDisplayNameAttribute("fn")
            .build()
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(info: NsdServiceInfo) {
                if (cont.isActive) cont.resume(info)
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onServiceLost() = Unit

            // also called when the picker is dismissed without a choice
            override fun onServiceInfoCallbackUnregistered() {
                if (cont.isActive) cont.resume(null)
            }
        }
        nsd.registerServiceInfoCallback(request, executor, callback)
        // the system unregisters it after a choice: a second unregistration is harmless
        cont.invokeOnCancellation { runCatching { nsd.unregisterServiceInfoCallback(callback) } }
    }

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
            network = info.network,
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
            withContext(Dispatchers.IO) { CastMdns.ipv4Of(info.serviceName, info.network) }?.let { return it }
        }
        return addresses.firstOrNull()
    }

    companion object {
        private const val SERVICE_TYPE = "_googlecast._tcp"
        private const val PICKER_EXTENSION = 22

        /** The system device picker of NsdManager (Android 17, or an updated Android 13+). */
        val hasSystemPicker: Boolean
            get() = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= PICKER_EXTENSION
    }
}
// endregion
