package com.watube.yard.helpers

import org.schabi.newpipe.extractor.utils.PrivacyEvents
import java.util.concurrent.atomic.AtomicLong

// region Privacy
/**
 * Totals of the privacy tab that outlive the app process: how many sponsor segments
 * SponsorBlock skipped and how much time that gave back. Two numbers kept on the phone,
 * never sent anywhere.
 */
object PrivacyStats {
    private const val SKIPPED_SEGMENTS = "stats_sb_skipped"
    private const val SAVED_MS = "stats_sb_saved_ms"

    /** A skip seeks from where the player is to the segment end: [savedMs] is that distance. */
    fun recordSkippedSegment(savedMs: Long) {
        PreferenceHelper.putLong(SKIPPED_SEGMENTS, skippedSegments + 1)
        PreferenceHelper.putLong(SAVED_MS, savedTimeMs + savedMs.coerceAtLeast(0))
    }

    val skippedSegments: Long get() = PreferenceHelper.getLong(SKIPPED_SEGMENTS, 0)

    val savedTimeMs: Long get() = PreferenceHelper.getLong(SAVED_MS, 0)

    private const val TRACKERS_REMOVED = "stats_trackers_removed"
    private const val NETWORK_PROTECTIONS = "stats_network_protections"

    /** Read once, then counted in memory: the extractor reports from several threads. */
    private val trackersRemoved by lazy { AtomicLong(PreferenceHelper.getLong(TRACKERS_REMOVED, 0)) }
    private val networkProtections by lazy {
        AtomicLong(PreferenceHelper.getLong(NETWORK_PROTECTIONS, 0))
    }

    /**
     * What the extractor did for the user (see [PrivacyEvents]): a tracker taken out of a link,
     * or an address refused or moved to https. Only the count is kept.
     */
    @Synchronized
    fun recordExtractorEvent(event: PrivacyEvents.Event) {
        val (counter, key) = when (event) {
            PrivacyEvents.Event.TRACKER_REMOVED -> trackersRemoved to TRACKERS_REMOVED
            PrivacyEvents.Event.STREAM_URL_REFUSED,
            PrivacyEvents.Event.REDIRECT_BLOCKED,
            PrivacyEvents.Event.HTTPS_UPGRADED,
            PrivacyEvents.Event.INSECURE_REQUEST_REFUSED -> networkProtections to NETWORK_PROTECTIONS
        }
        // synchronized: the stored values never go back to an older count
        PreferenceHelper.putLong(key, counter.incrementAndGet())
    }

    /** Tracking parameters removed from links, and YouTube redirect links bypassed. */
    val trackersRemovedCount: Long get() = trackersRemoved.get()

    /** Addresses refused (streams, redirects, plain http requests) and links moved to https. */
    val networkProtectionsCount: Long get() = networkProtections.get()
}
// endregion
