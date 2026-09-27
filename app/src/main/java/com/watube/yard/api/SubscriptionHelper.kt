package com.watube.yard.api

import android.os.SystemClock
import android.util.LruCache
import com.watube.yard.api.obj.Subscription
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.db.obj.SubscriptionsFeedItem
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.repo.AccountSubscriptionsRepository
import com.watube.yard.repo.FeedProgress
import com.watube.yard.repo.FeedRepository
import com.watube.yard.repo.LocalFeedRepository
import com.watube.yard.repo.LocalSubscriptionsRepository
import com.watube.yard.repo.PipedAccountFeedRepository
import com.watube.yard.repo.PipedLocalSubscriptionsRepository
import com.watube.yard.repo.PipedNoAccountFeedRepository
import com.watube.yard.repo.SubscriptionsRepository

object SubscriptionHelper {
    /**
     * The maximum number of channel IDs that can be passed via a GET request for fetching
     * the subscriptions list and the feed
     */
    const val GET_SUBSCRIPTIONS_LIMIT = 100

    /** How long a known subscription state is reused before being re-checked. */
    private const val SUBSCRIPTION_STATE_TTL_MS = 60_000L

    private class CachedSubscription(val value: Boolean, val expiresAt: Long)

    private val subscribedState = LruCache<String, CachedSubscription>(512)

    private val localFeedExtraction
        get() = PreferenceHelper.getBoolean(
            PreferenceKeys.LOCAL_FEED_EXTRACTION,
            true
        )
    private val token get() = PreferenceHelper.getToken()
    private val subscriptionsRepository: SubscriptionsRepository
        get() = when {
            token.isNotEmpty() -> AccountSubscriptionsRepository()
            localFeedExtraction -> LocalSubscriptionsRepository()
            else -> PipedLocalSubscriptionsRepository()
        }
    private val feedRepository: FeedRepository
        get() = when {
            localFeedExtraction -> LocalFeedRepository()
            token.isNotEmpty() -> PipedAccountFeedRepository()
            else -> PipedNoAccountFeedRepository()
        }

    suspend fun subscribe(
        channelId: String, name: String, uploaderAvatar: String?, verified: Boolean
    ) = subscriptionsRepository.subscribe(channelId, name, uploaderAvatar, verified)
        .also { invalidateSubscribedState(channelId) }

    suspend fun unsubscribe(channelId: String) {
        subscriptionsRepository.unsubscribe(channelId)
        invalidateSubscribedState(channelId)
        // remove videos from (local) feed
        feedRepository.removeChannel(channelId)
    }

    /**
     * Known subscription state of a channel, kept for a short moment so that list rows
     * don't fire one request per line while scrolling (a search result page would
     * otherwise ask the API about every channel it renders).
     *
     * The entry is dropped as soon as the state changes through [subscribe] or
     * [unsubscribe] and expires on its own, so a change made elsewhere (import, another
     * device) can only stay stale for [SUBSCRIPTION_STATE_TTL_MS].
     */
    suspend fun isSubscribed(channelId: String): Boolean? {
        val now = SystemClock.elapsedRealtime()
        subscribedState.get(channelId)?.let { cached ->
            if (now < cached.expiresAt) return cached.value
            subscribedState.remove(channelId)
        }

        val result = subscriptionsRepository.isSubscribed(channelId) ?: return null
        subscribedState.put(
            channelId,
            CachedSubscription(result, now + SUBSCRIPTION_STATE_TTL_MS)
        )
        return result
    }

    private fun invalidateSubscribedState(channelId: String) {
        subscribedState.remove(channelId)
    }

    suspend fun importSubscriptions(newChannels: List<String>) =
        subscriptionsRepository.importSubscriptions(newChannels).also {
            subscribedState.evictAll()
        }

    suspend fun getSubscriptions() =
        subscriptionsRepository.getSubscriptions().sortedBy { it.name.lowercase() }

    suspend fun getSubscriptionChannelIds() = subscriptionsRepository.getSubscriptionChannelIds()
    suspend fun getFeed(forceRefresh: Boolean, onProgressUpdate: (FeedProgress) -> Unit = {}) =
        feedRepository.getFeed(forceRefresh, onProgressUpdate)

    suspend fun submitFeedItemChange(feedItem: SubscriptionsFeedItem) =
        feedRepository.submitFeedItemChange(feedItem)

    suspend fun submitSubscriptionChannelInfosChanged(subscriptions: List<Subscription>) =
        subscriptionsRepository.submitSubscriptionChannelInfosChanged(subscriptions)
}
