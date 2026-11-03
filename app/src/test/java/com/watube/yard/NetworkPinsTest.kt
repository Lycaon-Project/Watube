package com.watube.yard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The certificate pins of the third party services (network_security_config.xml): the debug
 * file replaces the main one as a whole, so both must carry the very same pins, and the pins
 * must not have expired already (they would silently stop protecting anything).
 */
class NetworkPinsTest {
    private val module = File(".").absoluteFile
    private val main = pinnedDomains(File(module, "src/main/res/xml/network_security_config.xml"))
    private val debug = pinnedDomains(File(module, "src/debug/res/xml/network_security_config.xml"))

    private class PinSet(val expiration: String, val pins: Set<String>)

    @Test
    fun thirdPartyServicesArePinned() {
        listOf(
            "ajay.app", "returnyoutubedislikeapi.com", "kavin.rocks",
            "github.com", "githubusercontent.com"
        ).forEach { assertTrue("$it is not pinned", it in main) }
    }

    @Test
    fun debugBuildCarriesTheSamePins() {
        assertEquals(main.keys, debug.keys)
        main.forEach { (domain, set) ->
            assertEquals(domain, set.pins, debug.getValue(domain).pins)
            assertEquals(domain, set.expiration, debug.getValue(domain).expiration)
        }
    }

    @Test
    fun pinsAreStillInForce() {
        main.forEach { (domain, set) ->
            assertTrue("$domain pins expired", LocalDate.parse(set.expiration) > LocalDate.now())
            // a single pin leaves no way out when that authority changes: always a backup
            assertTrue("$domain has no backup pin", set.pins.size >= 2)
            // SHA-256 of a public key: a typo would make the pin match nothing
            set.pins.forEach { assertEquals("$domain $it", 32, Base64.getDecoder().decode(it).size) }
        }
    }

    @Test
    fun debugProxyCertificatesOverrideThePins() {
        val file = File(module, "src/debug/res/xml/network_security_config.xml")
        val certificates = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("certificates")
        val user = (0 until certificates.length).map { certificates.item(it) as Element }
            .single { it.getAttribute("src") == "user" }
        assertEquals("true", user.getAttribute("overridePins"))
    }

    @Test
    fun releaseRequiresCertificateTransparency() {
        val file = File(module, "src/main/res/xml/network_security_config.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val base = document.getElementsByTagName("base-config").item(0) as Element
        val transparency = base.getElementsByTagName("certificateTransparency").item(0) as Element?
        assertEquals("true", transparency?.getAttribute("enabled"))
        // no domain may opt out of it
        assertEquals(1, document.getElementsByTagName("certificateTransparency").length)
    }

    private fun pinnedDomains(file: File): Map<String, PinSet> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val configs = document.getElementsByTagName("domain-config")
        return (0 until configs.length).map { configs.item(it) as Element }
            .mapNotNull { config ->
                val pinSet = config.getElementsByTagName("pin-set").item(0) as Element?
                    ?: return@mapNotNull null
                val pins = pinSet.getElementsByTagName("pin")
                val set = PinSet(
                    pinSet.getAttribute("expiration"),
                    (0 until pins.length).map { pins.item(it).textContent.trim() }.toSet()
                )
                val domains = config.getElementsByTagName("domain")
                (0 until domains.length).map { domains.item(it).textContent.trim() to set }
            }
            .flatten()
            .also { pairs ->
                val domains = pairs.map { it.first }
                assertEquals("a domain is pinned twice", domains.distinct(), domains)
            }
            .toMap()
    }
}
