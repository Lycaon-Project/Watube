package com.watube.yard.ui.models

import android.util.Log
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watube.yard.api.MediaServiceRepository
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.enums.SearchDataType
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.obj.SearchDataItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlin.collections.map

// region Search
class SearchViewModel : ViewModel() {
    data class Result(
        val historyList: List<SearchDataItem>? = null,
        val suggestionList: List<SearchDataItem>? = null,
    )

    private val searchQuery = MutableStateFlow<String?>(null)
    private val suggestionCache = LruCache<String, List<String>>(MAX_CACHED_SUGGESTIONS)
    private val sharingStarted = SharingStarted.WhileSubscribed(replayExpirationMillis = 0L)

    // online suggestions are opt-in: by default nothing typed leaves the device
    private val isSearchSuggestionEnabled = flow {
        emit(PreferenceHelper.getBoolean(PreferenceKeys.SEARCH_SUGGESTIONS, false))
    }
        .stateIn(viewModelScope, sharingStarted, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchHistoryList = DatabaseHolder.Database.searchHistoryDao()
        .getAllNewestFirstFlow()
        .mapLatest { it.map { it.query } }
        .stateIn(viewModelScope, sharingStarted, emptyList())

    /**
     * Emits a [Pair] of ([SearchDataType.HISTORY] and the results ([List]))
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredSearchHistory = combine(searchQuery, searchHistoryList) { query, list ->
        val result =
            if (query.isNullOrBlank()) list
            else list.take(MAX_FILTERED_SEARCH_HISTORY).filter { it.startsWith(query) }

        SearchDataType.HISTORY to result
    }

    /**
     * Emits a [Pair] of ([SearchDataType.SUGGESTION]  and the results ([List]))
     *
     * The query is debounced and de-duplicated so that typing doesn't fire one network
     * request per keystroke.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val onlineSearchSuggestions =
        combine(
            searchQuery.debounce(SUGGESTIONS_DEBOUNCE_MS).distinctUntilChanged(),
            isSearchSuggestionEnabled
        ) { query, suggestionsEnabled ->
            if (!suggestionsEnabled || query == null || !isSafeForOnlineSuggestions(query)) {
                return@combine emptyList<String>()
            }
            // going back to a text already typed (fixing a typo) asks nothing again
            suggestionCache.get(query)?.let { return@combine it }
            try {
                MediaServiceRepository.instance.getSuggestions(query)
                    .also { suggestionCache.put(query, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("search", "failed to fetch search suggestions", e)
                emptyList<String>()
            }
        }.mapLatest { SearchDataType.SUGGESTION to it }

    /**
     * Get filtered search history and search suggestions combined. Both the histories and
     * suggestions are being collected concurrently. Whichever completes the collection first,
     * it will emit ([Result]) immediately with the other list being `null`. Once both sides
     * complete their collections, it will emit another result ([Result]) with both list ready.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val searchSuggestions = merge(filteredSearchHistory, onlineSearchSuggestions)
        .runningFold(Result()) { prev, new ->
            when (new.first) {
                SearchDataType.HISTORY -> {
                    prev.copy(
                        historyList = new.second.map {
                            SearchDataItem(it, SearchDataType.HISTORY)
                        }
                    )
                }

                SearchDataType.SUGGESTION -> {
                    prev.copy(
                        suggestionList = new.second.map {
                            SearchDataItem(it, SearchDataType.SUGGESTION)
                        }
                    )
                }
            }
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val shouldShowEmptyHistoryMessage = combine(
        searchHistoryList,
        searchQuery,
        isSearchSuggestionEnabled
    ) { histories, query, suggestionsEnabled ->
        histories.isEmpty() && (query.isNullOrBlank() || !suggestionsEnabled)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, sharingStarted, true)

    fun setQuery(query: String?) {
        this.searchQuery.value = query
    }

    companion object {
        private const val MAX_FILTERED_SEARCH_HISTORY = 5

        /** Delay before an online suggestion request is actually fired. */
        private const val SUGGESTIONS_DEBOUNCE_MS = 300L

        private const val MAX_CACHED_SUGGESTIONS = 32

        private const val MIN_ONLINE_QUERY_LENGTH = 2

        /** A phone, card or account number has more digits than any search for a title. */
        private const val MAX_ONLINE_QUERY_DIGITS = 5

        /** One word with a dot inside: a domain, a file name or an address. */
        private val DOMAIN_LIKE = Regex("""\S+\.\p{L}{2,}\S*""")

        /**
         * Online suggestions send what is typed as it is typed, before the user decides to search
         * for it: never a fragment that could identify or locate them (a pasted link, an e-mail
         * address, a phone or account number).
         */
        fun isSafeForOnlineSuggestions(query: String): Boolean {
            val text = query.trim()
            return text.length >= MIN_ONLINE_QUERY_LENGTH &&
                '@' !in text &&
                "://" !in text &&
                !text.startsWith("www.", ignoreCase = true) &&
                !DOMAIN_LIKE.containsMatchIn(text) &&
                text.count(Char::isDigit) <= MAX_ONLINE_QUERY_DIGITS
        }
    }
}
// endregion
