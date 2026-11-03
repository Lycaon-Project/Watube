package com.watube.yard.api

import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.watube.yard.R
import com.watube.yard.api.obj.VideoLabelData
import com.watube.yard.extensions.sha256Sum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.net.HttpURLConnection.HTTP_NOT_FOUND

// region SponsorBlock
object SponsorBlockLabelHelper {
    /**
     * Answers of the API by hash prefix: one answer lists every labelled video under its prefix,
     * so it serves all of them. An empty list is "no labelled video there", a cached answer too.
     */
    private val cache = LruCache<String, List<VideoLabelData>>(256)

    /** Lookups on the way: rows bound at the same time share a single request per prefix. */
    private val inFlight = HashMap<String, Deferred<List<VideoLabelData>?>>()
    private val inFlightLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Returns the full video labels for a video.
     *
     * A full-video label is used, when the entire video is connected to the label,
     * such as a sponsored video, or exclusive-access from a company.
     *
     * See https://wiki.sponsor.ajay.app/w/Full_Video_Labels for more details.
     */
    suspend fun getVideoLabels(
        videoId: String,
    ): VideoLabelData? {
        // use hashed video id for privacy: the shortest prefix the API takes (4, as for the
        // segments), so that each request stands for as many videos as possible
        // https://wiki.sponsor.ajay.app/w/API_Docs/Draft#GET_/api/videoLabels/:sha256HashPrefix
        val prefix = videoId.sha256Sum().substring(0, 4)
        return labelsUnder(prefix)?.firstOrNull { it.videoID == videoId }
    }

    /** The labelled videos under [prefix], or null when the lookup failed (it stays retryable). */
    private suspend fun labelsUnder(prefix: String): List<VideoLabelData>? {
        val lookup = inFlightLock.withLock {
            cache.get(prefix)?.let { return it }
            inFlight.getOrPut(prefix) { scope.async { fetchLabels(prefix) } }
        }
        val labels = lookup.await()
        inFlightLock.withLock {
            if (inFlight[prefix] === lookup) {
                inFlight.remove(prefix)
                if (labels != null) cache.put(prefix, labels)
            }
        }
        return labels
    }

    private suspend fun fetchLabels(prefix: String): List<VideoLabelData>? = runCatching {
        RetrofitInstance.externalApi.getVideoLabels(prefix)
    }.recoverCatching {
        // 404 is the API's "no labelled video under this prefix", the answer for most videos:
        // taken as a failure, it was never cached and every rebind asked again (battery, data)
        if ((it as? HttpException)?.code() == HTTP_NOT_FOUND) emptyList() else throw it
    }.getOrNull()

    /**
     * Returns a suitable drawable to display the category.
     *
     * If there is no matching icon, `null` is returned.
     */
    @DrawableRes
    fun categoryIcon(category: String?): Int? = when (category) {
        "exclusive_access" -> R.drawable.ic_exclusive_content
        "selfpromo" -> R.drawable.ic_selfpromo_content
        "sponsor" -> R.drawable.ic_paid_content
        else -> null
    }

    /**
     * Returns a suitable label to display the category.
     *
     * If there is no matching label, `null` is returned.
     */
    @StringRes
    fun categoryLabel(category: String?): Int? = when (category) {
        "sponsor" -> R.string.category_sponsor
        "exclusive_access" -> R.string.category_exclusive_access
        "selfpromo" -> R.string.category_selfpromo
        else -> null
    }
}
// endregion
