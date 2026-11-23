package com.watube.yard.util

// region Updates
/**
 * Watube versions are dates, year then month, with an optional fix number: 26.11, 26.10.4.
 *
 * The GitHub releases do not write them one way: in the tag ("Watube2611", "Watube_26.10"), in
 * the title ("Watube 26.10.3 Major Fix", "... (version 26.10.4)"), or not at all in the title
 * ("Your update is ready"). The version is looked for in each text given, and the most precise
 * one found is kept: a title saying 26.10.4 wins over a tag saying 26.10.
 */
object ReleaseVersion {
    /** Two digits of year, two of month (with or without a dot), then an optional fix number. */
    private val VERSION = Regex("""(?<!\d)(\d{2})\.?(\d{2})(?:\.(\d{1,3}))?(?!\d)""")

    private val MONTHS = 1..12

    /**
     * The version found in [texts] as one comparable number (26.10.4 -> 261004), or null when
     * none of them holds one.
     */
    fun code(vararg texts: String?): Int? = texts.asSequence()
        .filterNotNull()
        .flatMap { VERSION.findAll(it) }
        .mapNotNull { match ->
            val (year, month, fix) = match.destructured
            val monthNumber = month.toInt()
            if (monthNumber !in MONTHS) return@mapNotNull null
            year.toInt() * 10_000 + monthNumber * 100 + (fix.toIntOrNull() ?: 0)
        }
        .maxOrNull()
}
// endregion
