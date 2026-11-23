package com.watube.yard

import androidx.media3.common.PlaybackException
import com.watube.yard.helpers.ErrorMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// region Common Utilities
/**
 * The user is told why something failed in the language of the app, never with the English
 * text of the exception.
 */
class ErrorMessagesTest {

    @Test
    fun aPremiereThatHasNotStartedIsToldApart() {
        val error = ContentNotAvailableException("Got error LIVE_STREAM_OFFLINE: \"Premieres in 2 hours\"")

        assertEquals(R.string.error_not_started, ErrorMessages.of(error))
    }

    @Test
    fun anUnavailableVideoSaysWhy() {
        assertEquals(R.string.error_unavailable, ErrorMessages.of(ContentNotAvailableException("Got error ERROR")))
        assertEquals(R.string.error_age_restricted, ErrorMessages.of(AgeRestrictedContentException("18+")))
        assertEquals(
            R.string.error_not_in_country,
            ErrorMessages.of(UnsupportedContentInCountryException("not here"))
        )
    }

    @Test
    fun theCauseIsFoundUnderAWrapper() {
        val error = RuntimeException("wrapper", AgeRestrictedContentException("18+"))

        assertEquals(R.string.error_age_restricted, ErrorMessages.of(error))
    }

    @Test
    fun youtubeLimitsAndUnreadableAnswersAreToldApart() {
        assertEquals(R.string.error_youtube_limited, ErrorMessages.of(ReCaptchaException("429", null)))
        assertEquals(R.string.error_youtube_limited, ErrorMessages.of(SignInConfirmNotBotException("bot")))
        assertEquals(
            R.string.error_youtube_unreadable,
            ErrorMessages.of(ParsingException("Could not get visitorData: missing"))
        )
    }

    @Test
    fun networkFailuresSayNetworkError() {
        assertEquals(R.string.unknown_error, ErrorMessages.of(UnknownHostException("www.youtube.com")))
        assertEquals(R.string.unknown_error, ErrorMessages.of(SocketTimeoutException()))
    }

    @Test
    fun anythingElseGivesTheGenericMessage() {
        assertEquals(R.string.error, ErrorMessages.of(IllegalStateException("Something internal")))
    }

    @Test
    fun aLoopingCauseChainStillEnds() {
        val first = LoopingException()
        val second = LoopingException()
        first.next = second
        second.next = first

        assertEquals(R.string.error, ErrorMessages.of(first))
    }

    @Test
    fun playerErrorCodesAreGrouped() {
        assertEquals(
            R.string.unknown_error,
            ErrorMessages.playbackMessage(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        )
        assertEquals(
            R.string.error_playback_stream,
            ErrorMessages.playbackMessage(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        )
        assertEquals(
            R.string.error_playback_decoder,
            ErrorMessages.playbackMessage(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
        )
        assertEquals(
            R.string.error_playback_audio,
            ErrorMessages.playbackMessage(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)
        )
        assertNull(ErrorMessages.playbackMessage(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }

    private class LoopingException : Exception() {
        var next: Throwable? = null
        override val cause: Throwable? get() = next
    }
}
// endregion
