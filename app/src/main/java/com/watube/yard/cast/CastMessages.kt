package com.watube.yard.cast

import android.content.Context
import com.google.protobuf.ByteString
import com.watube.yard.R
import com.watube.yard.cast.proto.CastMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.cert.TrustAnchor

// region Stolm
/** Namespaces of the Cast v2 protocol used by Watube. */
internal object CastNamespaces {
    const val DEVICE_AUTH = "urn:x-cast:com.google.cast.tp.deviceauth"
    const val CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
    const val HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
    const val RECEIVER = "urn:x-cast:com.google.cast.receiver"
    const val MEDIA = "urn:x-cast:com.google.cast.media"
}

internal object CastMessages {
    /** The platform endpoints of a channel. */
    const val PLATFORM_SENDER = "sender-0"
    const val PLATFORM_RECEIVER = "receiver-0"

    fun binary(namespace: String, payload: ByteArray): CastMessage = base(namespace, PLATFORM_SENDER, PLATFORM_RECEIVER)
        .setPayloadType(CastMessage.PayloadType.BINARY)
        .setPayloadBinary(ByteString.copyFrom(payload))
        .build()

    fun text(namespace: String, payload: String, source: String, destination: String): CastMessage =
        base(namespace, source, destination)
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8(payload)
            .build()

    /** The JSON payload of a text message, null when it is not a JSON object. */
    fun json(message: CastMessage): JsonObject? =
        if (!message.hasPayloadUtf8()) null
        else runCatching { Json.parseToJsonElement(message.payloadUtf8).jsonObject }.getOrNull()

    /** The "type" of a text message ("PING", "RECEIVER_STATUS"...). */
    fun type(json: JsonObject?): String? = runCatching { json?.get("type")?.jsonPrimitive?.content }.getOrNull()

    private fun base(namespace: String, source: String, destination: String) = CastMessage.newBuilder()
        .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
        .setSourceId(source)
        .setDestinationId(destination)
        .setNamespace(namespace)
}

/**
 * The Cast root certificates, bundled as data (res/raw, from Open Screen, BSD-3-Clause): the
 * only anchors a receiver's device certificate may chain up to.
 */
internal object CastTrustStore {
    @Volatile
    private var anchors: Set<TrustAnchor>? = null

    fun anchors(context: Context): Set<TrustAnchor> = anchors ?: listOf(R.raw.cast_root_ca, R.raw.eureka_root_ca)
        .map { id ->
            val der = context.resources.openRawResource(id).use { it.readBytes() }
            TrustAnchor(CastDeviceAuth.parseCertificate(der), null)
        }
        .toSet()
        .also { anchors = it }
}
// endregion
