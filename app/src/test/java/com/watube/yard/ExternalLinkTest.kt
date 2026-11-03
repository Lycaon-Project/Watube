package com.watube.yard

import com.watube.yard.util.ExternalLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the confirmation of an external link shows: the real host, look-alike names and the
 * trackers taken out of the link.
 */
class ExternalLinkTest {
    @Test
    fun plainHttpsLink() {
        val link = ExternalLink.parse("https://shop.example.com/item?id=4")!!
        assertEquals("shop.example.com", link.host)
        assertEquals("example.com", link.registrableDomain)
        assertEquals("shop.", link.subdomainPrefix)
        assertTrue(link.encrypted)
        assertFalse(link.lookalike)
        assertEquals("https://shop.example.com/item?id=4", link.url)
        assertTrue(link.removedParameters.isEmpty())
    }

    @Test
    fun theHostIsTheRealOneWhateverTheLinkSays() {
        // user info before the @ is what a careless reader takes for the site
        val link = ExternalLink.parse("https://www.youtube.com@evil.example/watch")!!
        assertEquals("evil.example", link.host)
    }

    @Test
    fun lookAlikeNamesShowInPunycode() {
        // a Cyrillic "о" in place of the Latin one
        val link = ExternalLink.parse("https://gооgle.com/login")!!
        assertTrue(link.lookalike)
        assertTrue(link.host, link.host.startsWith("xn--"))
        assertEquals("gооgle.com", link.unicodeHost)
    }

    @Test
    fun plainHttpIsNotEncrypted() {
        assertFalse(ExternalLink.parse("http://example.org/")!!.encrypted)
    }

    @Test
    fun trackersAreTakenOutAndListed() {
        val link = ExternalLink.parse("https://example.org/a?utm_source=yt&id=1&fbclid=abc")!!
        assertEquals("https://example.org/a?id=1", link.url)
        assertEquals(listOf("utm_source", "fbclid"), link.removedParameters)
    }

    @Test
    fun registrableDomainUnderACountryCode() {
        assertEquals("example.co.uk", ExternalLink.registrableDomain("shop.example.co.uk"))
        assertEquals("example.fr", ExternalLink.registrableDomain("a.b.example.fr"))
        assertEquals("192.168.1.10", ExternalLink.registrableDomain("192.168.1.10"))
    }

    @Test
    fun emailLinks() {
        val link = ExternalLink.parse("mailto:contact@example.org?subject=hi")!!
        assertEquals("contact@example.org", link.email)
        assertEquals("example.org", link.host)
    }

    @Test
    fun otherSchemesAreNotLinks() {
        assertNull(ExternalLink.parse("javascript:alert(1)"))
        assertNull(ExternalLink.parse("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(ExternalLink.parse("not a link"))
    }

    @Test
    fun namesTheBrowserWouldReadOtherwiseAreRefused() {
        // shown as accounts.google.com%2eevil.com, opened as accounts.google.com.evil.com
        assertNull(ExternalLink.parse("https://accounts.google.com%2eevil.com/"))
        // IDNA 2003 turns ß into ss, a browser opens xn--fa-hia.de
        assertNull(ExternalLink.parse("https://faß.de/"))
    }
}
