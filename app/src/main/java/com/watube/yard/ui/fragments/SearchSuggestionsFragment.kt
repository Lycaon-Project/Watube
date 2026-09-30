package com.watube.yard.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.FragmentSearchSuggestionsBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.ui.activities.MainActivity
import com.watube.yard.ui.adapters.SearchSuggestionsAdapter
import com.watube.yard.ui.extensions.setOnBackPressed
import com.watube.yard.ui.models.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchSuggestionsFragment : Fragment(R.layout.fragment_search_suggestions) {
    private var _binding: FragmentSearchSuggestionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by activityViewModels()
    private val mainActivity get() = activity as MainActivity

    private val suggestionsAdapter = SearchSuggestionsAdapter(
        onRootClickListener = { suggestion ->
            mainActivity.setQuery(suggestion, true)
        },
        onArrowClickListener = { suggestion ->
            mainActivity.setQuery(suggestion, false)
        },
        onSearchHistoryItemDeleted = { historyItem ->
            lifecycleScope.launch(Dispatchers.IO) {
                DatabaseHolder.Database.searchHistoryDao().delete(historyItem)
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setQuery(arguments?.getString(IntentData.query))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSearchSuggestionsBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
        binding.suggestionsRecycler.adapter = suggestionsAdapter

        setOnBackPressed {
            if (!mainActivity.clearSearchViewFocus()) findNavController().popBackStack()
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchSuggestions.collectLatest { result ->
                        suggestionsAdapter.submitSearchSuggestions(
                            result.historyList,
                            result.suggestionList
                        ) {
                            binding.suggestionsRecycler.scrollToPosition(0)
                        }
                        // the .slab "Récents" header belongs to the local list only:
                        // it disappears as soon as online suggestions take over
                        binding.searchSectionLabel.isVisible =
                            result.suggestionList.isNullOrEmpty() &&
                                !result.historyList.isNullOrEmpty()
                    }
                }

                launch {
                    viewModel.shouldShowEmptyHistoryMessage.collectLatest {
                        toggleEmptyHistoryMessageVisibility(it)
                    }
                }
            }
        }
    }

    private fun toggleEmptyHistoryMessageVisibility(show: Boolean) {
        binding.searchSuggestionsContent.isGone = show
        binding.historyEmpty.isVisible = show
    }

    override fun onDestroy() {
        super.onDestroy()

        _binding = null
    }
}
