package com.watube.yard.ui.fragments

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.watube.yard.R
import com.watube.yard.databinding.FragmentSearchSuggestionsBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.ui.activities.MainActivity
import com.watube.yard.ui.adapters.SearchSuggestionsAdapter
import com.watube.yard.ui.extensions.setOnBackPressed
import com.watube.yard.ui.models.SearchViewModel
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchSuggestionsFragment : Fragment(R.layout.fragment_search_suggestions) {
    private var _binding: FragmentSearchSuggestionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by activityViewModels()
    private val mainActivity get() = activity as MainActivity

    private fun View.hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun View.showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // no SHOW_IMPLICIT: deprecated, and ignored since Android 16
        imm.showSoftInput(this, 0)
    }

    private val suggestionsAdapter = SearchSuggestionsAdapter(
        onRootClickListener = { suggestion ->
            // a tap on a row runs the query, the way the old SearchView submit did
            binding.searchInput.hideKeyboard()
            mainActivity.openSearchResults(suggestion)
        },
        onArrowClickListener = { suggestion ->
            // the arrow only copies the suggestion into the input: the text watcher
            // below turns it into a suggestion query, nothing is submitted yet
            binding.searchInput.setText(suggestion)
            binding.searchInput.setSelection(suggestion.length)
            binding.searchInput.requestFocus()
        },
        onSearchHistoryItemDeleted = { historyItem ->
            lifecycleScope.launch(Dispatchers.IO) {
                DatabaseHolder.Database.searchHistoryDao().delete(historyItem)
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // continuity with the home search pill: it morphs into the input (only used when the
        // navigation shares it), while the rest of the screen fades in
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            scrimColor = Color.TRANSPARENT
            duration = resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        }
        enterTransition = MaterialFadeThrough()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSearchSuggestionsBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
        binding.suggestionsRecycler.adapter = suggestionsAdapter
        setupSearchInput()

        // Back closes the IME first (platform behaviour), then leaves the screen: the
        // chevron of the header is the one press escape from an open keyboard.
        binding.searchBack.setOnClickListener {
            binding.searchInput.hideKeyboard()
            findNavController().popBackStack()
        }
        setOnBackPressed {
            findNavController().popBackStack()
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

    /**
     * The input is the only entry point of the search: every edit is forwarded to the
     * view model, and the trailing clear button follows the text.
     */
    private fun setupSearchInput() {
        val input = binding.searchInput

        // the text itself only arrives in onViewStateRestored(), which seeds again
        syncSuggestionsToInput()

        input.doAfterTextChanged { syncSuggestionsToInput() }

        input.setOnEditorActionListener { _, actionId, event ->
            // a hardware keyboard (or a keyboard sending key events) reports ENTER instead of
            // the IME action: it moved the focus away and never ran the search
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId != EditorInfo.IME_ACTION_SEARCH && !isEnterKey) {
                return@setOnEditorActionListener false
            }
            // ENTER comes as a down and an up event: search once, consume both
            if (event == null || event.action == KeyEvent.ACTION_DOWN) {
                input.hideKeyboard()
                mainActivity.openSearchResults(input.text.toString())
            }
            true
        }

        binding.searchClear.setOnClickListener {
            input.text?.clear()
            input.requestFocus()
        }
    }

    /** The query the input currently holds, or null while it is empty. */
    private fun inputQuery(): String? =
        binding.searchInput.text?.toString()?.takeIf { it.isNotEmpty() }

    /**
     * Mirrors the input into the view model: the suggestions and the clear button
     * always describe exactly what the input holds, whichever wrote the text last.
     */
    private fun syncSuggestionsToInput() {
        val query = inputQuery()
        viewModel.setQuery(query)
        binding.searchClear.isVisible = query != null
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // the view hierarchy state - hence the query - is applied right above this call
        syncSuggestionsToInput()

        if (inputQuery() == null) {
            // a fresh search screen behaves like the old expanded action view: it takes
            // focus and raises the IME. Coming back from a result keeps the keyboard down.
            val input = binding.searchInput
            input.requestFocus()
            input.post { input.showKeyboard() }
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
