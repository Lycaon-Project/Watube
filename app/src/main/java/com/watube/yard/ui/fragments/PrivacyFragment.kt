package com.watube.yard.ui.fragments

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.databinding.FragmentPrivacyBinding
import com.watube.yard.databinding.ItemPrivacyHostBinding
import com.watube.yard.databinding.ItemPrivacyLegendBinding
import com.watube.yard.databinding.WatubeStatTileBinding
import com.watube.yard.db.DatabaseHolder.Database
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.PrivacyStats
import com.watube.yard.ui.views.DonutChartView
import com.watube.yard.util.NetworkLedger
import com.watube.yard.util.PrivateDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// region Privacy
/**
 * The Dashboard tab, read-only and live: the protections that are on, how much of the traffic
 * is encrypted, where it goes, the live activity, the busiest servers and what is kept on the
 * phone. The settings themselves stay in the settings screen. Every figure is local.
 */
class PrivacyFragment : Fragment(R.layout.fragment_privacy) {
    private var _binding: FragmentPrivacyBinding? = null
    private val binding get() = _binding!!

    /** the charts draw in once per view, later refreshes only update them */
    private var drawnIn = false
    private var shownHosts: List<NetworkLedger.HostStats>? = null
    private var shownProtections = -1

    private enum class Destination(@param:StringRes val label: Int, @param:ColorRes val color: Int) {
        YOUTUBE(R.string.privacy_cat_youtube, R.color.watube_chart_1),
        SERVICES(R.string.privacy_cat_services, R.color.watube_chart_2),
        UPDATES(R.string.privacy_cat_updates, R.color.watube_chart_3),
        OTHER(R.string.privacy_cat_other, R.color.watube_chart_other),
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentPrivacyBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
        labelTiles()

        // real time while the dashboard is on screen, idle as soon as it is not: the network
        // figures and protections every second, the phone storage (database, cache folder)
        // every few seconds
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var tick = 0
                while (isActive) {
                    renderLive()
                    renderProtections()
                    if (tick++ % DEVICE_REFRESH_TICKS == 0) renderDevice()
                    drawnIn = true
                    delay(REFRESH_MS)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        drawnIn = false
        shownHosts = null
        shownProtections = -1
        _binding = null
    }

    private fun labelTiles() {
        binding.statRequests.label(R.string.privacy_stat_requests, R.string.privacy_stat_requests_caption)
        binding.statServers.label(R.string.privacy_stat_servers, R.string.privacy_stat_servers_caption)
        binding.statSkipped.label(R.string.privacy_stat_skipped, R.string.privacy_stat_skipped_caption)
        binding.statSaved.label(R.string.privacy_stat_saved, R.string.privacy_stat_saved_caption)
        binding.statTrackers.label(R.string.privacy_stat_trackers, R.string.privacy_stat_trackers_caption)
        binding.statNetwork.label(R.string.privacy_stat_network, R.string.privacy_stat_network_caption)
        binding.statHistory.label(R.string.privacy_stat_history, R.string.privacy_stat_history_caption)
        binding.statSearches.label(R.string.privacy_stat_searches, R.string.privacy_stat_searches_caption)
        binding.statPositions.label(R.string.privacy_stat_positions, R.string.privacy_stat_positions_caption)
        binding.statCache.label(R.string.privacy_stat_cache, R.string.privacy_stat_cache_caption)
    }

    private fun WatubeStatTileBinding.label(@StringRes label: Int, @StringRes caption: Int) {
        statLabel.setText(label)
        statCaption.setText(caption)
    }

    /** Shows [text] as the tile value and gives the tile one spoken sentence. */
    private fun WatubeStatTileBinding.describe(text: String) {
        root.contentDescription =
            getString(R.string.privacy_tile_description, statLabel.text, text, statCaption.text)
    }

    // Protections
    private fun renderProtections() {
        val binding = _binding ?: return
        val states = listOf(
            PreferenceHelper.getBoolean(PreferenceKeys.PRIVACY_HARDENING, true),
            PreferenceHelper.getBoolean(PreferenceKeys.HIDE_LOCKSCREEN_CONTENT, true),
            PreferenceHelper.getBoolean(PreferenceKeys.HIDE_RECENTS_PREVIEW, true),
            PreferenceHelper.getBoolean(PreferenceKeys.BLOCK_SCREENSHOTS, false),
            PreferenceHelper.isTouristModeEnabled(),
            PrivateDns.isEnabled,
        )
        val on = states.count { it }
        // checked every second: the ring only moves when a protection changed
        if (on == shownProtections) return
        shownProtections = on
        binding.protectionsRing.setFraction(on.toFloat() / states.size, animate = true)
        binding.protectionsRatio.text = getString(R.string.privacy_protections_ratio, on, states.size)
        binding.protections.contentDescription =
            getString(R.string.privacy_protections_description, on, states.size)
    }

    // Live figures
    private fun renderLive() {
        val binding = _binding ?: return
        val hosts = NetworkLedger.snapshot()
        val sent = hosts.sumOf { it.allowed }
        val refused = hosts.sumOf { it.refused }

        val cleartext = NetworkLedger.cleartextRequests
        // never round a single unencrypted request up to 100 %
        val encrypted = if (sent == 0) PERCENT else ((sent - cleartext) * PERCENT / sent)
            .let { if (cleartext > 0) it.coerceAtMost(PERCENT - 1) else it }
            .coerceIn(0, PERCENT)
        countTo(binding.encryptedValue, encrypted) { getString(R.string.privacy_share_percent, it) }
        binding.refusedValue.text =
            resources.getQuantityString(R.plurals.privacy_refused_requests, refused, refused)

        countTo(binding.statRequests.statValue, sent + refused)
        binding.statRequests.describe((sent + refused).toString())
        countTo(binding.statServers.statValue, hosts.size)
        binding.statServers.describe(hosts.size.toString())
        renderSponsorBlock()
        renderShield()

        renderDestinations(hosts)
        renderActivity()
        renderHosts(hosts)
    }

    private fun renderSponsorBlock() {
        val skipped = PrivacyStats.skippedSegments.toInt()
        countTo(binding.statSkipped.statValue, skipped)
        binding.statSkipped.describe(skipped.toString())
        val saved = formatDuration(PrivacyStats.savedTimeMs)
        binding.statSaved.statValue.text = saved
        binding.statSaved.describe(saved)
    }

    private fun renderShield() {
        val trackers = PrivacyStats.trackersRemovedCount.toInt()
        countTo(binding.statTrackers.statValue, trackers)
        binding.statTrackers.describe(trackers.toString())
        val network = PrivacyStats.networkProtectionsCount.toInt()
        countTo(binding.statNetwork.statValue, network)
        binding.statNetwork.describe(network.toString())
    }

    private fun formatDuration(ms: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        return when {
            seconds < SECONDS_PER_MINUTE -> getString(R.string.privacy_duration_s, seconds.toInt())
            seconds < SECONDS_PER_HOUR ->
                getString(R.string.privacy_duration_min, (seconds / SECONDS_PER_MINUTE).toInt())
            else -> getString(
                R.string.privacy_duration_h,
                (seconds / SECONDS_PER_HOUR).toInt(),
                (seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE).toInt()
            )
        }
    }

    // Where the exchanges go
    private fun destinationOf(host: String): Destination {
        fun matches(domains: List<String>) = domains.any { host == it || host.endsWith(".$it") }
        return when {
            matches(YOUTUBE_DOMAINS) -> Destination.YOUTUBE
            matches(SERVICE_DOMAINS) -> Destination.SERVICES
            matches(UPDATE_DOMAINS) -> Destination.UPDATES
            else -> Destination.OTHER
        }
    }

    private fun renderDestinations(hosts: List<NetworkLedger.HostStats>) {
        val totals = Destination.entries.associateWith { 0 }.toMutableMap()
        hosts.forEach { stats ->
            val destination = destinationOf(stats.host)
            totals[destination] = totals.getValue(destination) + stats.allowed + stats.refused
        }
        val total = totals.values.sum()

        val context = requireContext()
        binding.destinationsChart.setSlices(
            Destination.entries.map {
                DonutChartView.Slice(totals.getValue(it).toFloat(), ContextCompat.getColor(context, it.color))
            },
            animate = !drawnIn
        )
        countTo(binding.destinationsTotal, total)
        binding.destinationsUnit.text = resources.getQuantityString(R.plurals.privacy_requests_unit, total)

        // a fixed legend: each category keeps its line and color whatever the traffic
        val legend = binding.legendGroup
        if (legend.childCount == 0) {
            Destination.entries.forEach { ItemPrivacyLegendBinding.inflate(layoutInflater, legend, true) }
        }
        val description = StringBuilder()
        Destination.entries.forEachIndexed { index, destination ->
            val share = if (total == 0) 0 else totals.getValue(destination) * PERCENT / total
            val line = ItemPrivacyLegendBinding.bind(legend.getChildAt(index))
            line.legendDot.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, destination.color))
            line.legendLabel.setText(destination.label)
            line.legendShare.text = getString(R.string.privacy_share_percent, share)
            description.append(getString(destination.label)).append(' ')
                .append(line.legendShare.text).append(", ")
        }
        binding.destinations.contentDescription = description.toString().trimEnd(',', ' ')
    }

    // Live activity
    private fun renderActivity() {
        val activity = NetworkLedger.recentActivity()
        binding.activityChart.setValues(activity, animate = !drawnIn)
        val peak = activity.max()
        binding.activityPeak.text = getString(R.string.privacy_activity_peak, peak)
        binding.activityChart.contentDescription = getString(R.string.privacy_activity_description, peak)
    }

    // Most contacted servers
    private fun renderHosts(hosts: List<NetworkLedger.HostStats>) {
        binding.networkEmpty.isVisible = hosts.isEmpty()
        binding.networkGroup.isVisible = hosts.isNotEmpty()
        val shown = hosts.take(MAX_HOSTS_SHOWN)
        if (shown == shownHosts) return

        val group = binding.networkGroup
        // same servers in the same order: only the figures move, the bars glide to them
        val sameLines = group.childCount == shown.size &&
            shownHosts?.map { it.host } == shown.map { it.host }
        if (!sameLines) group.removeAllViews()
        shownHosts = shown
        val busiest = shown.firstOrNull()?.let { it.allowed + it.refused } ?: return

        shown.forEachIndexed { index, stats ->
            val line = if (sameLines) {
                ItemPrivacyHostBinding.bind(group.getChildAt(index))
            } else {
                ItemPrivacyHostBinding.inflate(layoutInflater, group, true)
            }
            val count = stats.allowed + stats.refused
            line.hostName.text = stats.host
            line.hostCount.text = count.toString()
            line.hostRefused.isVisible = stats.refused > 0
            line.hostRefused.text = resources.getQuantityString(R.plurals.privacy_refused, stats.refused, stats.refused)
            line.hostShare.setProgressCompat(count * SHARE_SCALE / busiest, true)
            line.root.contentDescription = buildString {
                append(stats.host).append(", ")
                append(resources.getQuantityString(R.plurals.privacy_requests, count, count))
                if (stats.refused > 0) append(", ").append(line.hostRefused.text)
            }
        }
    }

    // On this phone
    private fun renderDevice() {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = requireContext()
            val counts = withContext(Dispatchers.IO) {
                listOf(
                    Database.watchHistoryDao().getSize(),
                    Database.searchHistoryDao().getSize(),
                    Database.watchPositionDao().getSize(),
                ) to ImageHelper.getCacheSize(context)
            }
            val binding = _binding ?: return@launch
            val (rows, cacheBytes) = counts
            listOf(binding.statHistory, binding.statSearches, binding.statPositions)
                .zip(rows)
                .forEach { (tile, count) ->
                    countTo(tile.statValue, count)
                    tile.describe(count.toString())
                }
            val cache = Formatter.formatShortFileSize(context, cacheBytes)
            binding.statCache.statValue.text = cache
            binding.statCache.describe(cache)
        }
    }

    /**
     * Counts [view] up (or down) to [target]: a longer roll the first time, a short glide on
     * the live refreshes. The value shown is kept in the view tag to start from it next time.
     */
    private fun countTo(view: TextView, target: Int, format: (Int) -> String = { it.toString() }) {
        val from = view.tag as? Int ?: 0
        view.tag = target
        if (from == target && view.text.isNotEmpty()) return
        ValueAnimator.ofInt(from, target).apply {
            duration = if (drawnIn) GLIDE_MS else ROLL_MS
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { view.text = format(it.animatedValue as Int) }
            start()
        }
    }

    private companion object {
        const val REFRESH_MS = 1_000L
        const val DEVICE_REFRESH_TICKS = 5
        const val ROLL_MS = 900L
        const val GLIDE_MS = 400L
        const val PERCENT = 100
        const val SHARE_SCALE = 1000
        const val MAX_HOSTS_SHOWN = 6
        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3600L

        val YOUTUBE_DOMAINS = listOf(
            "youtube.com", "youtu.be", "googlevideo.com", "ytimg.com", "ggpht.com",
            "youtubei.googleapis.com", "google.com"
        )
        val SERVICE_DOMAINS = listOf("ajay.app", "returnyoutubedislikeapi.com")
        val UPDATE_DOMAINS = listOf("github.com", "githubusercontent.com")
    }
}
// endregion
