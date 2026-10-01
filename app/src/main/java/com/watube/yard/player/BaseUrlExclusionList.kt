/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.watube.yard.player

import android.os.SystemClock
import android.util.Pair
import androidx.annotation.VisibleForTesting
import androidx.media3.common.util.UnstableApi
import com.watube.yard.player.manifest.BaseUrl
import java.util.Random
import kotlin.math.max

/**
 * Holds the state of [excluded][exclude] base URLs to be used to [select][selectBaseUrl] a base
 * URL based on these exclusions.
 */
@UnstableApi
class BaseUrlExclusionList @VisibleForTesting constructor(private val random: Random) {

    private val excludedServiceLocations = HashMap<String, Long>()
    private val excludedPriorities = HashMap<Int, Long>()
    private val selectionsTaken = HashMap<List<Pair<String, Int>>, BaseUrl>()

    /** Creates an instance. */
    constructor() : this(Random())

    /**
     * Excludes the given base URL.
     *
     * @param baseUrlToExclude The base URL to exclude.
     * @param exclusionDurationMs The duration of exclusion, in milliseconds.
     */
    fun exclude(baseUrlToExclude: BaseUrl, exclusionDurationMs: Long) {
        val excludeUntilMs = SystemClock.elapsedRealtime() + exclusionDurationMs
        addExclusion(baseUrlToExclude.serviceLocation, excludeUntilMs, excludedServiceLocations)
        if (baseUrlToExclude.priority != BaseUrl.PRIORITY_UNSET) {
            addExclusion(baseUrlToExclude.priority, excludeUntilMs, excludedPriorities)
        }
    }

    /**
     * Selects the base URL to use from the given list.
     *
     * The list is reduced by service location and priority of base URLs that have been passed to
     * [exclude]. The base URL to use is then selected from the remaining base URLs by priority and
     * weight.
     *
     * @param baseUrls The list of [base URLs][BaseUrl] to select from.
     * @return The selected base URL after exclusion or null if all elements have been excluded.
     */
    fun selectBaseUrl(baseUrls: List<BaseUrl>): BaseUrl? {
        // Sort by priority and service location to make the sort order of the candidates deterministic.
        val includedBaseUrls = applyExclusions(baseUrls)
            .sortedWith(compareBy({ it.priority }, { it.serviceLocation }))
        if (includedBaseUrls.size < 2) {
            return includedBaseUrls.firstOrNull()
        }

        // Get candidates of the lowest priority from the head of the sorted list.
        val candidateKeys = ArrayList<Pair<String, Int>>()
        val lowestPriority = includedBaseUrls[0].priority
        for (baseUrl in includedBaseUrls) {
            if (lowestPriority != baseUrl.priority) {
                // Only a single candidate of lowest priority; no choice.
                if (candidateKeys.size == 1) return includedBaseUrls[0]
                break
            }
            candidateKeys.add(Pair(baseUrl.serviceLocation, baseUrl.weight))
        }

        // Reuse a selection already taken for the same candidate set, otherwise draw a new one.
        return selectionsTaken.getOrPut(candidateKeys) {
            // Weighted random selection from multiple candidates of the same priority.
            selectWeighted(includedBaseUrls.subList(0, candidateKeys.size))
        }
    }

    /**
     * Returns the number of priority levels for the given list of base URLs after exclusion.
     */
    fun getPriorityCountAfterExclusion(baseUrls: List<BaseUrl>): Int =
        applyExclusions(baseUrls).mapTo(HashSet()) { it.priority }.size

    /** Resets the state. */
    fun reset() {
        excludedServiceLocations.clear()
        excludedPriorities.clear()
        selectionsTaken.clear()
    }

    // Internal methods.

    private fun applyExclusions(baseUrls: List<BaseUrl>): List<BaseUrl> {
        val nowMs = SystemClock.elapsedRealtime()
        removeExpiredExclusions(nowMs, excludedServiceLocations)
        removeExpiredExclusions(nowMs, excludedPriorities)
        return baseUrls.filter {
            !excludedServiceLocations.containsKey(it.serviceLocation) &&
                !excludedPriorities.containsKey(it.priority)
        }
    }

    private fun selectWeighted(candidates: List<BaseUrl>): BaseUrl {
        val randomChoice = random.nextInt(candidates.sumOf { it.weight })
        var cumulativeWeight = 0
        for (baseUrl in candidates) {
            cumulativeWeight += baseUrl.weight
            if (randomChoice < cumulativeWeight) return baseUrl
        }
        return candidates.last()
    }

    companion object {
        /**
         * Returns the number of priority levels of the given list of base URLs.
         *
         * @param baseUrls The list of base URLs.
         * @return The number of priority levels before exclusion.
         */
        @JvmStatic
        fun getPriorityCount(baseUrls: List<BaseUrl>): Int =
            baseUrls.mapTo(HashSet()) { it.priority }.size

        private fun <T> addExclusion(
            toExclude: T,
            excludeUntilMs: Long,
            currentExclusions: MutableMap<T, Long>
        ) {
            val existing = currentExclusions[toExclude]
            currentExclusions[toExclude] =
                if (existing != null) max(excludeUntilMs, existing) else excludeUntilMs
        }

        private fun <T> removeExpiredExclusions(nowMs: Long, exclusions: MutableMap<T, Long>) {
            exclusions.entries.removeAll { it.value <= nowMs }
        }
    }
}
