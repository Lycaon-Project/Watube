package com.watube.yard

import com.watube.yard.helpers.PreferenceHelper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A settings row bound to an encrypted preference must read and write it through
 * PreferenceHelper (EncryptedListPreference, EncryptedEditTextPreference): the default rows
 * would show the ciphertext and store the new value in clear text.
 */
class EncryptedPreferenceRowsTest {
    private val xml = File(File(".").absoluteFile, "src/main/res/xml")
    private val appNamespace = "http://schemas.android.com/apk/res-auto"

    @Test
    fun encryptedPreferencesUseEncryptedRows() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val rows = xml.listFiles()!!.filter { it.extension == "xml" }.flatMap { file ->
            val elements = factory.newDocumentBuilder().parse(file).getElementsByTagName("*")
            (0 until elements.length).map { elements.item(it) as Element }
                .filter { it.getAttributeNS(appNamespace, "key") in PreferenceHelper.ENCRYPTED_KEYS }
                .map { file.name to it }
        }
        // both instance lists and the SponsorBlock id: finding none means the test is blind
        assertTrue("no encrypted row found", rows.size >= 3)

        val wrong = rows
            .filterNot { (_, row) -> row.tagName.startsWith("com.watube.yard.ui.preferences.Encrypted") }
            .map { (file, row) -> "$file: ${row.getAttributeNS(appNamespace, "key")} is a ${row.tagName}" }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }
}
