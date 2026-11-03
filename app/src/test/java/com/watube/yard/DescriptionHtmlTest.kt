package com.watube.yard

import com.watube.yard.ui.views.DescriptionLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The extractor escapes descriptions: one with only "&amp;" in it is still shown as HTML. */
class DescriptionHtmlTest {

    @Test
    fun `tags or escaped characters make a description html`() {
        listOf(
            "line one<br>line two",
            "<a href=\"https://example.org\">example.org</a>",
            "Tom &amp; Jerry",
            "5 &lt; 6",
            "caf&#233; &#x2665;",
        ).forEach { assertTrue(it, DescriptionLayout.isHtml(it)) }
    }

    @Test
    fun `plain text stays plain text`() {
        listOf(
            "Tom & Jerry",
            "a & b; c",
            "see https://example.org/?a=1&b=2",
            "",
        ).forEach { assertFalse(it, DescriptionLayout.isHtml(it)) }
    }
}
