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
    /**
     * Two digits of year, two of month, then an optional fix number. Written without a dot, the
     * version only counts glued to a name, as in the tag "Watube2611": a lone number such as the
     * version code in "Watube 26.12 (6212)" would otherwise read as year 62, newer than any
     * release, and the installed version would be offered to itself as an update forever.
     */
    private val VERSION =
        Regex("""(?<!\d)(?:(\d{2})\.(\d{2})|(?<=[A-Za-z_])(\d{2})(\d{2}))(?:\.(\d{1,3}))?(?!\d)""")

    private val MONTHS = 1..12

    /**
     * The version found in [texts] as one comparable number (26.10.4 -> 261004), or null when
     * none of them holds one.
     */
    fun code(vararg texts: String?): Int? = texts.asSequence()
        .filterNotNull()
        .flatMap { VERSION.findAll(it) }
        .mapNotNull { match ->
            val (dottedYear, dottedMonth, gluedYear, gluedMonth, fix) = match.destructured
            val year = dottedYear.ifEmpty { gluedYear }.toInt()
            val month = dottedMonth.ifEmpty { gluedMonth }.toInt()
            if (month !in MONTHS) return@mapNotNull null
            year * 10_000 + month * 100 + (fix.toIntOrNull() ?: 0)
        }
        .maxOrNull()
}
// endregion
