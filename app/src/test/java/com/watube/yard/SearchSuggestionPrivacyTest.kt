package com.watube.yard

import com.watube.yard.ui.models.SearchViewModel.Companion.isSafeForOnlineSuggestions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionPrivacyTest {
    @Test
    fun ordinarySearchesAreSent() {
        assertTrue(isSafeForOnlineSuggestions("lofi hip hop"))
        assertTrue(isSafeForOnlineSuggestions("minecraft 1.21 update"))
        assertTrue(isSafeForOnlineSuggestions("olympics 2024"))
        assertTrue(isSafeForOnlineSuggestions("  rammstein  "))
    }

    @Test
    fun identifyingFragmentsAreNeverSent() {
        // too short to be worth a request
        assertFalse(isSafeForOnlineSuggestions(""))
        assertFalse(isSafeForOnlineSuggestions("a"))
        // links and domains
        assertFalse(isSafeForOnlineSuggestions("https://example.org/private"))
        assertFalse(isSafeForOnlineSuggestions("www.example.org"))
        assertFalse(isSafeForOnlineSuggestions("example.org"))
        assertFalse(isSafeForOnlineSuggestions("youtu.be/dQw4w9WgXcQ"))
        // e-mail address
        assertFalse(isSafeForOnlineSuggestions("jane doe jane.doe@example.org"))
        // phone, card or account numbers
        assertFalse(isSafeForOnlineSuggestions("06 12 34 56 78"))
        assertFalse(isSafeForOnlineSuggestions("4970 1012 3456 7890"))
    }
}
