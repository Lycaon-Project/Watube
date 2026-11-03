package com.watube.yard.util

import com.watube.yard.api.obj.StreamItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which video comes next: the queue first (playlist, channel, videos added by hand), then the
 * autoplay suggestion of the current video.
 */
class PlayingQueueTest {
    private fun video(id: String, duration: Long = 60) =
        StreamItem(url = "/watch?v=$id", title = "video $id", duration = duration)

    private fun ids(streams: List<StreamItem>) = streams.map { it.url!!.substringAfter("v=") }

    @After
    fun emptyQueue() = PlayingQueue.clear()

    @Test
    fun theQueueComesBeforeAutoplay() {
        val queue = listOf(video("a"), video("b"))
        assertEquals("b", nextVideoId(queue, 0, repeatAll = false, autoplay = video("s")))
    }

    @Test
    fun autoplayGoesOnOnceTheQueueIsOver() {
        // a video opened on its own: the queue only holds it, the next button used to stay off
        val queue = listOf(video("a"))
        assertEquals("s", nextVideoId(queue, 0, repeatAll = false, autoplay = video("s")))
    }

    @Test
    fun nothingNextWithoutAutoplay() {
        assertNull(nextVideoId(listOf(video("a")), 0, repeatAll = false, autoplay = null))
    }

    @Test
    fun aRepeatedQueueStartsAgainBeforeAutoplay() {
        val queue = listOf(video("a"), video("b"))
        assertEquals("a", nextVideoId(queue, 1, repeatAll = true, autoplay = video("s")))
    }

    @Test
    fun autoplaySkipsLiveStreamsAndVideosAlreadyPlayed() {
        val queue = listOf(video("a"), video("b"))
        val suggestions = listOf(video("live", duration = 0), video("a"), video("c"), video("d"))
        assertEquals(listOf("c"), ids(listOfNotNull(pickAutoplayStream(suggestions, queue))))
    }

    @Test
    fun noAutoplayWhenEverySuggestionWasPlayed() {
        assertNull(pickAutoplayStream(listOf(video("a")), listOf(video("a"))))
        assertNull(pickAutoplayStream(emptyList(), listOf(video("a"))))
    }

    @Test
    fun theSuggestionDoesNotCountAsAQueuedVideo() {
        // otherwise the playlist or channel of the video would never be loaded into the queue
        PlayingQueue.updateCurrent(video("a"))
        PlayingQueue.setAutoplaySuggestions(listOf(video("s")))
        assertFalse(PlayingQueue.hasQueuedNext())

        PlayingQueue.add(video("b"))
        assertTrue(PlayingQueue.hasQueuedNext())
    }

    @Test
    fun aVideoOpenedElsewhereReplacesWhatFollowsTheCurrentOne() {
        PlayingQueue.setStreams(listOf(video("a"), video("b"), video("c")))
        PlayingQueue.updateCurrent(video("b"))

        PlayingQueue.clearAfterCurrent()
        PlayingQueue.updateCurrent(video("x"))

        assertEquals(listOf("a", "b", "x"), ids(PlayingQueue.getStreams()))
    }

    @Test
    fun aPlaylistStartedWhileAnotherVideoPlaysKeepsItsQueue() {
        // the playlist screen sets the queue, then the service switches from the old video
        PlayingQueue.updateCurrent(video("old"))
        PlayingQueue.setStreams(listOf(video("p1"), video("p2"), video("p3")))

        // keepQueue: no trimming, the first video of the playlist becomes the current one
        PlayingQueue.updateCurrent(video("p1"))

        assertEquals(listOf("p1", "p2", "p3"), ids(PlayingQueue.getStreams()))
        assertTrue(PlayingQueue.hasQueuedNext())
    }

    @Test
    fun clearingTheQueueDropsTheSuggestion() {
        PlayingQueue.updateCurrent(video("a"))
        PlayingQueue.setAutoplaySuggestions(listOf(video("s")))
        PlayingQueue.clear()
        PlayingQueue.updateCurrent(video("b"))

        assertFalse(PlayingQueue.hasQueuedNext())
    }
}
