package com.watube.yard.api

import com.watube.yard.api.obj.DeArrowBody
import com.watube.yard.api.obj.DeArrowContent
import com.watube.yard.api.obj.PipedConfig
import com.watube.yard.api.obj.SegmentData
import com.watube.yard.api.obj.SubmitSegmentResponse
import com.watube.yard.api.obj.VideoLabelData
import com.watube.yard.api.obj.VoteInfo
import com.watube.yard.obj.update.UpdateInfo
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

private const val GITHUB_API_URL = "https://api.github.com/repos/Lycaon-Project/Watube/releases/latest"
private const val SB_API_URL = "https://sponsor.ajay.app"
// Watube 26.9 fix: the former endpoint (ryd-proxy.kavin.rocks, a Piped side proxy) answers
// 502 for every request, so the dislike counter could never load and stayed hidden.
// returnyoutubedislikeapi.com is the official Return YouTube Dislike API and is the very
// URL announced by the setting summary (res/values*/strings.xml "local_ryd_summary").
private const val RYD_API_URL = "https://returnyoutubedislikeapi.com"

interface ExternalApi {
    @GET("config")
    suspend fun getInstanceConfig(@Url url: String): PipedConfig

    // fetch latest version info
    @GET(GITHUB_API_URL)
    suspend fun getLatestRelease(): UpdateInfo

    @GET("$RYD_API_URL/votes")
    suspend fun getVotes(@Query("videoId") videoId: String): VoteInfo

    @POST("$SB_API_URL/api/skipSegments")
    suspend fun submitSegment(
        @Query("videoID") videoId: String,
        @Query("userID") userID: String,
        @Query("userAgent") userAgent: String,
        @Query("startTime") startTime: Float,
        @Query("endTime") endTime: Float,
        @Query("category") category: String,
        @Query("duration") duration: Float? = null,
        @Query("description") description: String = ""
    ): List<SubmitSegmentResponse>

    @GET("$SB_API_URL/api/skipSegments/{videoId}")
    suspend fun getSegments(
        @Path("videoId") videoId: String,
        @Query("category") category: List<String>,
        @Query("actionType") actionType: List<String>? = null
    ): List<SegmentData>

    @GET("$SB_API_URL/api/videoLabels/{videoId}")
    suspend fun getVideoLabels(
        @Path("videoId") videoId: String,
    ): List<VideoLabelData>

    @POST("$SB_API_URL/api/branding")
    suspend fun submitDeArrow(@Body body: DeArrowBody)

    /**
     * @param score: 0 for downvote, 1 for upvote, 20 for undoing previous vote (if existent)
     */
    @POST("$SB_API_URL/api/voteOnSponsorTime")
    suspend fun voteOnSponsorTime(
        @Query("UUID") uuid: String,
        @Query("userID") userID: String,
        @Query("type") score: Int
    )

    @GET("$SB_API_URL/api/branding/{videoId}")
    suspend fun getDeArrowContent(@Path("videoId") videoId: String): Map<String, DeArrowContent>
}
