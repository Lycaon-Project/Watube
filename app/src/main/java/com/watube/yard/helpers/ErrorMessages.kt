package com.watube.yard.helpers

import androidx.annotation.StringRes
import androidx.media3.common.PlaybackException
import com.watube.yard.R
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import retrofit2.HttpException
import java.io.IOException

// region Common Utilities
/**
 * What the user is told when something fails: a string of the app, so in the language the user
 * chose, never the English text of the exception (which may also quote what a server sent).
 * The details stay in the log of development builds.
 */
object ErrorMessages {

    /** Causes looked at, at most: a chain that loops back on itself still ends. */
    private const val MAX_CAUSE_DEPTH = 8

    /**
     * How the extractor reports a premiere or a live stream that has not started yet: the
     * status YouTube gave, quoted in the message of a [ContentNotAvailableException].
     */
    private const val NOT_STARTED_STATUS = "LIVE_STREAM_OFFLINE"

    // groups of PlaybackException.errorCode, as Media3 documents them
    private val SOURCE_ERRORS = 2000..3999 // reading (2xxx) and parsing (3xxx) the media
    private val DECODING_ERRORS = 4000..4999
    private val AUDIO_OUTPUT_ERRORS = 5000..5999
    private val PROTECTED_CONTENT_ERRORS = 6000..6999

    /** The message for [error]: the first cause in its chain that tells more than "an error". */
    @StringRes
    fun of(error: Throwable): Int =
        generateSequence(error) { current -> current.cause?.takeIf { it !== current } }
            .take(MAX_CAUSE_DEPTH)
            .firstNotNullOfOrNull(::messageOf)
            ?: R.string.error

    /** The message for this one exception, or `null` when it says nothing the user can use. */
    @StringRes
    private fun messageOf(error: Throwable): Int? = when (error) {
        is PlaybackException -> playbackMessage(error.errorCode)
        // the subclasses of ContentNotAvailableException first, then the class itself
        is AgeRestrictedContentException -> R.string.error_age_restricted
        is GeographicRestrictionException,
        is UnsupportedContentInCountryException -> R.string.error_not_in_country
        is PrivateContentException -> R.string.error_private
        is PaidContentException,
        is YoutubeMusicPremiumContentException -> R.string.error_paid
        is AccountTerminatedException -> R.string.error_account_terminated
        is ContentNotAvailableException ->
            if (hasNotStarted(error)) R.string.error_not_started else R.string.error_unavailable
        // YouTube slowing down or questioning anonymous visitors
        is ReCaptchaException,
        is SignInConfirmNotBotException -> R.string.error_youtube_limited
        is ExtractionException -> R.string.error_youtube_unreadable
        is HttpException -> R.string.error_server_answer
        is IOException -> R.string.unknown_error
        else -> null
    }

    /** The message for a player error code, or `null` when the code says nothing precise. */
    @StringRes
    internal fun playbackMessage(errorCode: Int): Int? = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> R.string.unknown_error
        in SOURCE_ERRORS, in PROTECTED_CONTENT_ERRORS -> R.string.error_playback_stream
        in DECODING_ERRORS -> R.string.error_playback_decoder
        in AUDIO_OUTPUT_ERRORS -> R.string.error_playback_audio
        else -> null
    }

    private fun hasNotStarted(error: ContentNotAvailableException): Boolean =
        error.message?.contains(NOT_STARTED_STATUS, ignoreCase = true) == true
}
// endregion
