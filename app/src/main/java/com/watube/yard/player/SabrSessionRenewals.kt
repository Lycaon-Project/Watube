package com.watube.yard.player

import java.io.IOException

// region SABR Streaming
/**
 * YouTube flags some SABR streaming sessions as needing attestation (StreamProtectionStatus 2,
 * then 3), or asks for a player reload, after which that session is never served again: it then
 * serves only about the first minute of media, at best. No token of ours satisfies it for
 * the visionOS client the stream comes from, whereas a new session usually is not flagged at all.
 * Seen on device: video bound and session bound web tokens both refused, so none is made any more
 * (no BotGuard script of Google's runs, no identifier ties sessions or videos together).
 */
class SabrAttestationException : IOException(MESSAGE) {
    companion object {
        /** Survives the trip to the MediaController, unlike the exception class itself. */
        const val MESSAGE = "Attestation required"
    }
}

/**
 * Fresh sessions the player may still ask for, for the video it plays. Downloads don't register
 * here, so their streams keep the previous behaviour.
 */
object SabrSessionRenewals {
    private const val MAX_RENEWALS = 3

    private var videoId: String? = null
    private var left = 0

    /** A video started by the player gets a full budget. */
    @Synchronized
    fun start(videoId: String) {
        this.videoId = videoId
        left = MAX_RENEWALS
    }

    @Synchronized
    fun canRenew(videoId: String) = videoId == this.videoId && left > 0

    /** Takes one renewal and returns its rank (1 for the first), 0 when none is left for [videoId]. */
    @Synchronized
    fun consume(videoId: String): Int {
        if (!canRenew(videoId)) return 0
        left--
        return MAX_RENEWALS - left
    }
}
// endregion
