package com.watube.yard.ui.models

import android.content.Context
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watube.yard.R
import com.watube.yard.api.SubscriptionHelper
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.api.obj.Subscription
import com.watube.yard.db.obj.SubscriptionGroup
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.repo.FeedProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

// region Feed
class SubscriptionsViewModel : ViewModel() {
    val videoFeed = MutableLiveData<List<StreamItem>?>()

    val subscriptions = MutableLiveData<List<Subscription>?>()
    val feedProgress = MutableLiveData<FeedProgress?>()

    var subFeedRecyclerViewState: Parcelable? = null

    /** channel id picked in the subscriptions rail, null to show every channel of the group */
    var selectedRailChannel: String? = null

    /** when the user had last seen the feed before this app session: newer videos are "new" */
    var feedSeenBefore: Long? = null

    val groups = MutableLiveData<List<SubscriptionGroup>>()
    var groupToEdit: SubscriptionGroup? = null

    private var feedLoad: Deferred<List<StreamItem>>? = null

    /**
     * Loads the feed, one load at a time: a caller arriving while it runs (new videos badge at
     * startup, home, subscriptions tab, refresh) shares it. Side by side loads each requested
     * every channel again and all drove the progress bar, which jumped back and forth.
     * Synchronized: home calls it from a background thread.
     */
    @Synchronized
    fun fetchFeed(context: Context, forceRefresh: Boolean): Deferred<List<StreamItem>> =
        feedLoad?.takeIf { it.isActive } ?: viewModelScope.async(Dispatchers.IO) {
            val videoFeed = try {
                SubscriptionHelper.getFeed(forceRefresh = forceRefresh) { feedProgress ->
                    this@SubscriptionsViewModel.feedProgress.postValue(feedProgress)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toastFromMainDispatcher(R.string.server_error)
                Log.e(TAG(), e.toString())
                // an empty feed leaves the loading state: the spinner and the progress bar
                // would stay visible forever otherwise
                emptyList()
            }
            // close the progress bar even when the repository short-circuited (cached feed,
            // no subscriptions) and never reported a final "n/n" update
            this@SubscriptionsViewModel.feedProgress.postValue(null)
            this@SubscriptionsViewModel.videoFeed.postValue(videoFeed)
            videoFeed.firstOrNull { !it.isUpcoming }?.uploaded?.let {
                PreferenceHelper.updateLastFeedWatchedTime(it, false)
            }
            videoFeed
        }.also { feedLoad = it }

    fun fetchSubscriptions(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val subscriptions = try {
                SubscriptionHelper.getSubscriptions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toastFromMainDispatcher(R.string.server_error)
                Log.e(TAG(), e.toString())
                return@launch
            }
            this@SubscriptionsViewModel.subscriptions.postValue(subscriptions)
        }
    }
}
// endregion
