package com.watube.yard.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.images.WebImage
import com.watube.yard.api.obj.Streams

/**
 * Petit helper Cast : acquisition paresseuse du CastContext et construction du
 * MediaInfo a partir du flux en cours de lecture.
 *
 * Regle de confidentialite : seules les URL https publiquees par l'extracteur
 * (DASH / HLS) sont transmises au recepteur. Aucune URL n'est journalisee.
 */
object CastHelper {

    private const val HLS_CONTENT_TYPE = "application/x-mpegURL"
    private const val DASH_CONTENT_TYPE = "application/dash+xml"

    @Volatile
    private var castContext: CastContext? = null

    /**
     * [CastContext.getSharedInstance] doit etre appele depuis le thread principal
     * et leve IllegalStateException / RuntimeException quand Google Play services
     * ou le module Cast est absent : l'appelant doit catcher et masquer le bouton.
     */
    fun getCastContext(context: Context): CastContext =
        castContext ?: CastContext.getSharedInstance(context.applicationContext)
            .also { castContext = it }

    /**
     * Construit le MediaInfo a partir du flux courant.
     *
     * Priorite : HLS puis DASH pour un live, DASH puis HLS pour une video a la
     * demande (l'extracteur YouTube fournit un manifeste DASH v7 pour les clients
     * Android). Seules les URL https avec hote valide sont retenues ; sinon null
     * (l'appelant affiche toast_cast_unavailable).
     */
    fun buildMediaInfo(streams: Streams): MediaInfo? {
        val candidates: List<Pair<String, String>> = if (streams.isLive) {
            listOfNotNull(
                streams.hls?.to(HLS_CONTENT_TYPE),
                streams.dash?.to(DASH_CONTENT_TYPE)
            )
        } else {
            listOfNotNull(
                streams.dash?.to(DASH_CONTENT_TYPE),
                streams.hls?.to(HLS_CONTENT_TYPE)
            )
        }

        val (contentUrl, contentType) = candidates.firstOrNull { isHttpsUrl(it.first) }
            ?: return null

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
        metadata.putString(MediaMetadata.KEY_TITLE, streams.title)
        if (isHttpsUrl(streams.thumbnailUrl)) {
            runCatching { metadata.addImage(WebImage(Uri.parse(streams.thumbnailUrl))) }
        }

        val builder = MediaInfo.Builder(contentUrl)
            .setStreamType(
                if (streams.isLive) MediaInfo.STREAM_TYPE_LIVE
                else MediaInfo.STREAM_TYPE_BUFFERED
            )
            .setContentType(contentType)
            .setMetadata(metadata)

        if (!streams.isLive && streams.duration > 0) {
            builder.setStreamDuration(streams.duration * 1000)
        }
        return builder.build()
    }

    private fun isHttpsUrl(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }
}
