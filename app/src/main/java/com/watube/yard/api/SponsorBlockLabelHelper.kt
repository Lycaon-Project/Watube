package com.watube.yard.api

import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.watube.yard.R
import com.watube.yard.api.obj.VideoLabelData
import com.watube.yard.extensions.sha256Sum
import retrofit2.HttpException
import java.net.HttpURLConnection.HTTP_NOT_FOUND

object SponsorBlockLabelHelper {
    /**
     * Wrapper distinguishing "queried, this video has no label" from "never queried":
     * a plain `null` cache entry would be indistinguishable from a cache miss and the
     * same video would be re-fetched on every single rebind while scrolling.
     */
    private class CachedLabels(val value: VideoLabelData?)

    private val cache = LruCache<String, CachedLabels>(256)

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
        // if we have the response cached, return it (including "no label")
        cache.get(videoId)?.let { return it.value }

        val result = runCatching {
            RetrofitInstance.externalApi.getVideoLabels(
                // use hashed video id for privacy
                // https://wiki.sponsor.ajay.app/w/API_Docs/Draft#GET_/api/videoLabels/:sha256HashPrefix
                videoId.sha256Sum().substring(0, 5),
            ).firstOrNull { it.videoID == videoId }
        }.recoverCatching {
            // 404 is the API's "no labelled video under this prefix", the answer for most videos:
            // taken as a failure, it was never cached and every rebind asked again (battery, data)
            if ((it as? HttpException)?.code() == HTTP_NOT_FOUND) null else throw it
        }

        // cache every completed lookup, including "this video has no label": otherwise
        // the same video would hit the API again on each rebind while scrolling.
        // A failed call is not cached so that a network error stays retryable.
        if (result.isSuccess) cache.put(videoId, CachedLabels(result.getOrNull()))

        return result.getOrNull()
    }

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