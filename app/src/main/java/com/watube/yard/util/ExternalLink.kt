package com.watube.yard.util

import org.schabi.newpipe.extractor.utils.UrlPrivacy
import java.net.IDN
import java.net.URI
import java.util.Locale

// region Privacy
/**
 * What a link of a description or a comment really leads to, shown before it leaves the app
 * (ExternalLinkSheet): the text of a link can say anything, its host cannot.
 *
 * @property url the link to open, without the tracking parameters that were left in it
 * @property host the host in plain ASCII, as the network sees it: a name written with letters
 * of other alphabets shows here in its punycode form ("xn--..."), which cannot pass for a
 * well-known site
 * @property unicodeHost how such a name looks when written out, or null for a plain name
 * @property registrableDomain the part of [host] someone registered, the one that tells who
 * runs the site (example.co.uk in shop.example.co.uk)
 * @property encrypted whether the link uses https
 * @property removedParameters the tracking parameters taken out of [url]
 * @property email the address of a mailto: link, null otherwise
 */
data class ExternalLink(
    val url: String,
    val host: String,
    val unicodeHost: String?,
    val registrableDomain: String,
    val encrypted: Boolean,
    val removedParameters: List<String>,
    val email: String? = null
) {
    /** Letters of other alphabets in the name: it may imitate the name of a known site. */
    val lookalike: Boolean get() = unicodeHost != null

    /** [host] before [registrableDomain] ("shop." in shop.example.com), may be empty. */
    val subdomainPrefix: String get() = host.removeSuffix(registrableDomain)

    companion object {
        private const val ASCII_MAX = 127
        private val PLAIN_HOST = Regex("[a-z0-9-]+(\\.[a-z0-9-]+)*")
        private val IP_LITERAL = Regex("\\[[0-9a-f:.]+]")

        /** Second level names under a country code that are not someone's own domain. */
        private val GENERIC_SECOND_LEVELS = setOf(
            "co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go", "gob", "nic"
        )

        /** Null when the link cannot be read as an http(s) or mailto link. */
        fun parse(link: String): ExternalLink? {
            val trimmed = link.trim()
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            return when (uri.scheme?.lowercase(Locale.ROOT)) {
                "mailto" -> parseEmail(trimmed, uri)
                "http", "https" -> parseWeb(trimmed, uri)
                else -> null
            }
        }

        private fun parseEmail(link: String, uri: URI): ExternalLink? {
            val address = uri.schemeSpecificPart?.substringBefore('?')?.takeIf { '@' in it }
                ?: return null
            val domain = address.substringAfterLast('@').lowercase(Locale.ROOT)
            return ExternalLink(
                url = link,
                host = domain,
                unicodeHost = null,
                registrableDomain = domain,
                encrypted = false,
                removedParameters = emptyList(),
                email = address
            )
        }

        private fun parseWeb(link: String, uri: URI): ExternalLink? {
            // java.net.URI leaves host null for names with letters of other alphabets
            val rawHost = uri.host
                ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')
                ?: return null
            val name = rawHost.trimEnd('.')
            val host = runCatching { IDN.toASCII(name, IDN.ALLOW_UNASSIGNED) }
                .getOrNull()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
            // only a plain name or address can be shown as the browser will read it: "%2e",
            // "_" and the like are decoded differently by each of them
            if (!PLAIN_HOST.matches(host) && !IP_LITERAL.matches(host)) return null
            val unicode = IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED)
            // "ß" or "ς" become "ss" or "σ" here but stay as they are in a browser, which then
            // opens another domain than the one shown: such a name is refused
            if (unicode != name.lowercase(Locale.ROOT) && name.any { it.code > ASCII_MAX }) {
                return null
            }

            val cleaned = UrlPrivacy.removeTrackingParameters(link) ?: link
            val removed = parameterNames(link) - parameterNames(cleaned).toSet()
            return ExternalLink(
                url = cleaned,
                host = host,
                unicodeHost = unicode.takeIf { it != host },
                registrableDomain = registrableDomain(host),
                encrypted = uri.scheme.equals("https", ignoreCase = true),
                removedParameters = removed.distinct()
            )
        }

        private fun parameterNames(link: String): List<String> {
            val query = link.substringAfter('?', "").substringBefore('#')
            if (query.isEmpty()) return emptyList()
            return query.split('&').filter { it.isNotEmpty() }.map { it.substringBefore('=') }
        }

        /**
         * The last two labels, or three under a generic second level of a country code
         * (example.co.uk). An address is shown whole.
         */
        internal fun registrableDomain(host: String): String {
            if (host.all { it.isDigit() || it == '.' } || ':' in host) return host
            val labels = host.split('.')
            if (labels.size <= 2) return host
            val countryCode = labels.last().length == 2
            val keep = if (countryCode && labels[labels.size - 2] in GENERIC_SECOND_LEVELS) 3 else 2
            return labels.takeLast(keep).joinToString(".")
        }
    }
}
// endregion
