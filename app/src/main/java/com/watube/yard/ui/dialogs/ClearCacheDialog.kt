package com.watube.yard.ui.dialogs

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.watube.yard.R
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.PrivacyHelper
import com.watube.yard.ui.base.SecureAlertDialogBuilder
import kotlinx.coroutines.launch

// region Settings
/**
 * Asks before freeing the storage used by images, http responses and temp files, then empties
 * it (downloads, subscriptions, playlists and history stay) along with the cookies and web
 * storage a WebView may have persisted. Shared by the "Données & maintenance" setting and the
 * library shortcut; [onCleared] runs once the cache is empty.
 */
fun Fragment.showClearCacheDialog(onCleared: () -> Unit) {
    SecureAlertDialogBuilder(requireContext())
        .setTitle(R.string.clear_cache_dialog_title)
        .setMessage(R.string.clear_cache_dialog_message)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.clear_cache) { _, _ ->
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                ImageHelper.clearCache(appContext)
                PrivacyHelper.clearWebViewData()
                onCleared()
                appContext.toastFromMainDispatcher(R.string.cache_cleared)
            }
        }
        .show()
}
// endregion
