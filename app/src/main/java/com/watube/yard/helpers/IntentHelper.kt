package com.watube.yard.helpers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.util.Patterns
import androidx.core.net.toUri
import com.watube.yard.extensions.bundleOf
import androidx.fragment.app.FragmentManager
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.extensions.toastFromMainThread
import com.watube.yard.ui.sheets.IntentChooserSheet
import com.watube.yard.util.TextUtils.toTimeInSeconds

object IntentHelper {
    /**
     * Schemes an in-app link is allowed to open. Anything else (javascript:, intent:,
     * content:, file:, ...) is dropped: links come from untrusted video descriptions.
     */
    private val ALLOWED_SCHEMES = setOf("http", "https", "mailto")

    fun isAllowedLink(link: String): Boolean {
        val scheme = runCatching { link.toUri().scheme?.lowercase() }.getOrNull()
        return scheme != null && scheme in ALLOWED_SCHEMES
    }

    private fun getResolveIntent(link: String) = Intent(Intent.ACTION_VIEW)
        .setData(link.toUri())
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun getResolveInfo(context: Context, link: String): List<ResolveInfo> {
        if (!isAllowedLink(link)) return emptyList()
        val intent = getResolveIntent(link)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager
                .queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
        } else {
            context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
    }

    fun openLinkFromHref(context: Context, fragmentManager: FragmentManager, link: String, forceDefaultOpen: Boolean = false) {
        if (!isAllowedLink(link)) {
            // never hand an unsupported scheme over to another app
            context.toastFromMainThread(R.string.error)
            return
        }
        val resolveInfoList = getResolveInfo(context, link)

        if (resolveInfoList.isEmpty() || forceDefaultOpen) {
            try {
                context.startActivity(getResolveIntent(link))
            } catch (_: Exception) {
                context.toastFromMainThread(R.string.error)
            }
        } else {
            IntentChooserSheet()
                .apply { arguments = bundleOf(IntentData.url to link) }
                .show(fragmentManager)
        }
    }

    /** The link inside a shared text, which apps often wrap ("Title https://youtu.be/..."). */
    fun sharedTextToUri(text: String): Uri =
        (Patterns.WEB_URL.matcher(text).takeIf { it.find() }?.group() ?: text).toUri()

    fun resolveType(uri: Uri) = resolveType(Intent(), uri)

    /**
     * Resolve the uri and return a bundle with the arguments
     */
    fun resolveType(intent: Intent, uri: Uri) = with(intent) {
        // an opaque uri (shared text such as "look: abc") has no path nor query: reading its
        // query parameters throws, so it simply resolves to nothing
        if (!uri.isHierarchical) return@with this
        val lastSegment = uri.lastPathSegment
        val secondLastSegment = uri.pathSegments.getOrNull(uri.pathSegments.size - 2)
        when {
            lastSegment == "results" -> {
                putExtra(IntentData.query, uri.getQueryParameter("search_query"))
            }
            secondLastSegment == "channel" -> {
                putExtra(IntentData.channelId, lastSegment)
            }
            secondLastSegment == "c" || secondLastSegment == "user" -> {
                putExtra(IntentData.channelName, lastSegment)
            }
            lastSegment == "playlist" -> {
                putExtra(IntentData.playlistId, uri.getQueryParameter("list"))
            }
            lastSegment == "watch_videos" -> {
                putExtra(IntentData.playlistName, uri.getQueryParameter("title"))
                val videoIds = uri.getQueryParameter("video_ids")?.split(",")
                putExtra(IntentData.videoIds, videoIds?.toTypedArray())
            }
            else -> {
                val id = if (lastSegment == "watch") uri.getQueryParameter("v") else lastSegment
                putExtra(IntentData.videoId, id)
                putExtra(IntentData.timeStamp, uri.getQueryParameter("t")?.toTimeInSeconds())
            }
        }
    }
}
