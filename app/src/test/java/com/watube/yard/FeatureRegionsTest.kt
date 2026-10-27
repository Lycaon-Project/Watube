package com.watube.yard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// region Build
/**
 * Keeps the feature index of FEATURES.md true: every Kotlin file and layout sits under a
 * `region <Feature>` of that list, and every region is closed.
 */
class FeatureRegionsTest {
    private val module = File(".").absoluteFile
    private val main = File(module, "src/main")

    private val features: Set<String> = File(module, "../FEATURES.md").readLines()
        .mapNotNull { Regex("""^\| \*\*(.+?)\*\* \|""").find(it)?.groupValues?.get(1) }
        .toSet()

    private val open = Regex("""^\s*(?://|#|<!--) region (.+?)(?: -->)?$""")
    private val close = Regex("""^\s*(?://|#|<!--) endregion\b""")

    private fun problems(file: File, mustHaveRegion: Boolean): List<String> {
        val name = file.relativeTo(main).path
        var depth = 0
        var regions = 0
        val found = mutableListOf<String>()
        file.readLines().forEachIndexed { index, line ->
            open.find(line)?.let {
                regions++
                depth++
                val feature = it.groupValues[1]
                if (feature !in features) found += "$name:${index + 1}: \"$feature\" is not in FEATURES.md"
            }
            if (close.containsMatchIn(line) && --depth < 0) found += "$name:${index + 1}: endregion without region"
        }
        if (depth > 0) found += "$name: $depth region(s) never closed"
        if (mustHaveRegion && regions == 0) found += "$name: no feature region"
        return found
    }

    @Test
    fun everyFileSitsUnderAKnownFeature() {
        assertTrue("FEATURES.md lists no feature", features.size > 10)
        val kotlin = File(main, "java").walk().filter { it.extension == "kt" }
        val layouts = File(main, "res").listFiles()!!
            .filter { it.name.startsWith("layout") || it.name in setOf("menu", "navigation", "xml") }
            .flatMap { dir -> dir.listFiles()!!.filter { it.extension == "xml" } }
        val found = (kotlin + layouts + File(main, "AndroidManifest.xml"))
            .flatMap { problems(it, mustHaveRegion = true) }
            .toList()
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }
}
// endregion
