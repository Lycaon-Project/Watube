package com.watube.yard.util

// region Updates
/**
 * Values of the `update_check_interval` preference: the minimum delay between two automatic
 * update checks, in hours, as stored by the ListPreference (res/values/array.xml,
 * `updateIntervalValues`).
 */
object UpdateCheckInterval {

    /** Once per start of the app, the default. */
    const val EVERY_LAUNCH = "0"

    private const val MILLIS_PER_HOUR = 60 * 60 * 1000L

    /** The every 6 hours, 12 hours and daily choices, offered up to 26.12. */
    private val RETIRED_VALUES = setOf("6", "12", "24")

    /** Whether [value] is one of the choices the settings no longer offer. */
    fun isRetired(value: String?): Boolean = value in RETIRED_VALUES

    /**
     * The delay of [value] in milliseconds; 0 (every launch) for a missing, malformed or
     * negative value, so a broken preference never stops the checks.
     */
    fun millis(value: String?): Long {
        val hours = value?.toLongOrNull()?.takeIf { it > 0L } ?: return 0L
        return hours * MILLIS_PER_HOUR
    }
}
// endregion
