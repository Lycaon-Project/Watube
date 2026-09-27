package com.watube.yard.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import com.watube.yard.databinding.SuggestionRowBinding
import com.watube.yard.ui.adapters.callbacks.DiffUtilItemCallback
import com.watube.yard.ui.viewholders.SuggestionsViewHolder
import com.watube.yard.R
import com.watube.yard.db.obj.SearchHistoryItem
import com.watube.yard.enums.SearchDataType
import com.watube.yard.obj.SearchDataItem
import kotlin.collections.plus

class SearchSuggestionsAdapter(
    private val onRootClickListener: (String) -> Unit,
    private val onArrowClickListener: (String) -> Unit,
    private val onSearchHistoryItemDeleted: (SearchHistoryItem) -> Unit,
) : ListAdapter<SearchDataItem, SuggestionsViewHolder>(
    // the list gets de-duplicated by query already, so the query identifies an entry
    DiffUtilItemCallback<SearchDataItem>(areItemsTheSame = { oldItem, newItem ->
        oldItem.query == newItem.query && oldItem.type == newItem.type
    })
) {

    /**
     *  Allow submit list partially, either [historyList] only or [suggestionList] only, without
     *  updating the whole list.
     */
    fun submitSearchSuggestions(
        historyList: List<SearchDataItem>?,
        suggestionList: List<SearchDataItem>?,
        commitCallback: Runnable? = null,
    ) {
        if (historyList == null && suggestionList == null) return

        val oldList = currentList.toList()
        val histories = historyList ?: oldList.filter { it.type == SearchDataType.HISTORY }
        val suggestions = suggestionList ?: oldList.filter { it.type == SearchDataType.SUGGESTION }
        val newList = (histories + suggestions).distinctBy { it.query }

        super.submitList(newList, commitCallback)
    }

    /**
     * @see [submitSearchSuggestions]
     */
    @Deprecated("Use `submitSearchSuggestions()` instead.")
    override fun submitList(list: List<SearchDataItem>?) {}

    /**
     * @see [submitSearchSuggestions]
     */
    @Deprecated("Use `submitSearchSuggestions()` instead.")
    override fun submitList(list: List<SearchDataItem>?, commitCallback: Runnable?) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionsViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = SuggestionRowBinding.inflate(layoutInflater, parent, false)
        return SuggestionsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionsViewHolder, position: Int) {
        val item = getItem(holder.bindingAdapterPosition)
        val suggestion = item.query

        holder.binding.apply {
            when (item.type) {
                SearchDataType.HISTORY -> {
                    deleteHistory.setOnClickListener {
                        onSearchHistoryItemDeleted(SearchHistoryItem(suggestion))
                    }
                    suggestionText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_history, 0, 0, 0
                    )
                }

                SearchDataType.SUGGESTION -> {
                    suggestionText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_search, 0, 0, 0
                    )
                }
            }
            deleteHistory.isVisible = item.type == SearchDataType.HISTORY
            suggestionText.text = suggestion
            root.setOnClickListener {
                onRootClickListener(suggestion)
            }
            arrow.setOnClickListener {
                onArrowClickListener(suggestion)
            }
        }
    }
}
