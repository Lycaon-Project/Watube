package com.watube.yard.helpers

/**
 * Open source libraries bundled inside the Watube APK.
 *
 * The list is hand maintained: keep it in sync with `gradle/libs.versions.toml`
 * (runtime entries only, test/benchmark dependencies are not shipped in the APK)
 * and with `app/build.gradle.kts`.
 *
 * It backs the "Open source licenses" card of the About screen so that every
 * user can reach the license of each bundled component (GPL-3.0 compliance for
 * NewPipeExtractor, attribution for the Apache-2.0 and BSD-3-Clause parts).
 */
data class LibraryInfo(
    val name: String,
    val version: String,
    val license: String,
    val licenseUrl: String,
) {
    /** Single line shown in the licenses dialog */
    val label: String get() = if (version.isBlank()) "$name · $license" else "$name $version · $license"

    companion object {
        private const val APACHE = "Apache-2.0"
        private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
        private const val GPL3 = "GPL-3.0"
        private const val GPL3_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
        private const val BSD3 = "BSD-3-Clause"
        private const val BSD3_URL = "https://opensource.org/license/bsd-3-clause"

        val libraries: List<LibraryInfo> = listOf(
            // --- Google / Android ---
            LibraryInfo("AndroidX Activity", "1.13.0", APACHE, APACHE_URL),
            LibraryInfo("AndroidX AppCompat", "1.7.1", APACHE, APACHE_URL),
            LibraryInfo("AndroidX Core", "1.18.0", APACHE, APACHE_URL),
            LibraryInfo("AndroidX Fragment", "1.8.9", APACHE, APACHE_URL),
            LibraryInfo("AndroidX Lifecycle", "2.10.0", APACHE, APACHE_URL),
            LibraryInfo("AndroidX Navigation", "2.9.8", APACHE, APACHE_URL),
            LibraryInfo("AndroidX Preference", "1.2.1", APACHE, APACHE_URL),
            LibraryInfo("AndroidX Room", "2.8.4", APACHE, APACHE_URL),
            LibraryInfo("AndroidX Paging", "3.5.0", APACHE, APACHE_URL),
            LibraryInfo("AndroidX WorkManager", "2.11.2", APACHE, APACHE_URL),
            LibraryInfo("AndroidX Media", "1.8.0", APACHE, APACHE_URL),
            LibraryInfo("AndroidX SwipeRefreshLayout", "1.2.0", APACHE, APACHE_URL),
            LibraryInfo("AndroidX SplashScreen", "1.2.0", APACHE, APACHE_URL),
            LibraryInfo("AndroidX Collection", "1.6.0", APACHE, APACHE_URL),
            LibraryInfo("AndroidX DocumentFile", "1.1.0", APACHE, APACHE_URL),
            LibraryInfo("AndroidX ProfileInstaller", "1.4.1", APACHE, APACHE_URL),
            LibraryInfo("AndroidX ConstraintLayout", "2.2.1", APACHE, APACHE_URL),
            LibraryInfo("AndroidX Media3 / ExoPlayer", "1.9.2", APACHE, APACHE_URL),
            LibraryInfo("Material Components for Android", "1.14.0", APACHE, APACHE_URL),

            // --- Networking / serialization ---
            LibraryInfo("OkHttp (+ logging-interceptor)", "5.3.2", APACHE, APACHE_URL),
            LibraryInfo("Retrofit (+ kotlinx-serialization converter)", "3.0.0", APACHE, APACHE_URL),
            LibraryInfo("Kotlin Stdlib", "2.3.20", APACHE, APACHE_URL),
            LibraryInfo("kotlinx.serialization", "1.11.0", APACHE, APACHE_URL),
            LibraryInfo("kotlinx.datetime", "0.8.0", APACHE, APACHE_URL),
            LibraryInfo("Google Protocol Buffers (javalite / kotlin-lite)", "4.33.5", BSD3, BSD3_URL),

            // --- Extraction / storage / images ---
            // Copyleft: the only GPL component of the app besides Watube itself
            LibraryInfo("NewPipeExtractor", "3e863d7", GPL3, GPL3_URL),
            LibraryInfo("Coil", "3.4.0", APACHE, APACHE_URL),
            LibraryInfo("desugar_jdk_libs_nio", "2.1.5", APACHE, APACHE_URL),

            // --- Watube itself ---
            LibraryInfo("GNU General Public License v3.0", "", GPL3, GPL3_URL),
        )
    }
}
