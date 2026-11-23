// region Build
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.androidx.navigation.safeargs)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.protobuf)
    alias(libs.plugins.kotlin.serialization)
    // shipped by AGP, already on the classpath "with an unknown version": requested bare
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.watube.yard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.watube.yard"
        minSdk = 31
        targetSdk = 37

        // The express release after 26.11 carries the name of its mascot, VireoBoo: shown next to
        // the version ("Watube 26.12 · Vireo", About and settings), never in versionName, which
        // the in-app update check compares with the name of the latest GitHub release.
        versionCode = 6212
        versionName = "26.12"
        buildConfigField("String", "RELEASE_NAME", "\"Vireo\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            // Hardening: never ship a debuggable release, and keep the source control
            // revision (commit hash / branch) out of the packaged APK.
            isDebuggable = false
            @Suppress("UnstableApiUsage")
            vcsInfo { include = false }
            // 64-bit ARM only, the processor of every phone Watube targets: the other ABIs only
            // shipped more copies of the SQLCipher library (x86 / x86_64 for emulators, 32-bit
            // ARM for old phones), 5.3 MB of the 15.5 MB APK. Debug builds keep every ABI.
            ndk { abiFilters += "arm64-v8a" }
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile != null }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }

        // Measures (memory, CPU, frames) on the release code itself: shrunk by R8 and not
        // debuggable, but signed with the debug key and installed next to the real release.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".bench"
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs.excludes.add("lib/armeabi-v7a/*_neon.so")
        // SQLCipher compressed in the APK (2.0 -> 0.97 MB), the user's choice to stay under 9 MB:
        // Android extracts it once at install (2 MB on disk), nothing changes at run time.
        jniLibs.useLegacyPackaging = true
        // keep the dex files compressed: on this project they are the bulk of the APK
        // (~80%), so the install/download size drops by ~21 MB at no functional cost
        dex {
            useLegacyPackaging = true
        }
        // The protobuf lite runtime never reads .proto sources nor descriptor sets (they only
        // serve the full runtime / protoc), and DebugProbesKt.bin is only loaded by the
        // kotlinx-coroutines debug agent: none of them is used at runtime.
        resources.excludes += listOf(
            // translations of Rhino's internal error messages (never shown, the English base
            // stays) and of a BouncyCastle certificate path reviewer Watube does not use
            "org/mozilla/javascript/resources/Messages_*.properties",
            "org/bouncycastle/x509/CertPathReviewerMessages*.properties",
            "google/protobuf/*.proto",
            "src/google/protobuf/*.proto",
            "**/java_features_proto-descriptor-set.proto.bin",
            "DebugProbesKt.bin"
        )
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        // arm64-v8a only on purpose (see the release ndk filter above): x86_64 Chromebooks are
        // not a target, and their ABI would add another copy of the SQLCipher library
        disable += "ChromeOsAbiSupport"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    @Suppress("UnstableApiUsage")
    androidResources {
        generateLocaleConfig = true
        // Ship only the languages Watube itself is translated into: the libraries bundle
        // translations for ~30 more locales the app UI never uses (they only grew
        // resources.arsc). https://developer.android.com/build/shrink-code#unused-alt-resources
        localeFilters += listOf(
            "en", "af", "ar", "as", "ast", "az", "azb", "b+en+Shaw", "b+es+419", "be", "bg",
            "bn", "ca", "ckb", "cs", "da", "de", "el", "eo", "es", "et", "eu", "fa", "fi", "fil",
            "fr", "gu", "haw", "hi", "hr", "hu", "hy", "ia", "in", "is", "it", "iw", "ja", "km",
            "ko", "lt", "lv", "ml", "mr", "ms", "nb-rNO", "ne", "nl", "or", "pa", "pl", "pt-rBR",
            "pt", "ro", "ru", "si", "sk", "so", "sr", "sv", "ta", "th", "ti", "tk", "tr", "ug",
            "uk", "ur", "uz", "vi", "yue", "zh-rCN", "zh-rTW"
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

// method parameter names kept in the Java bytecode (reflection on parameter names)
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

// Room schema export: AppDatabase has exportSchema = true, without this KSP fails the build
// ("Schema import directory was not provided").
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("exportSchema", "true")
}

dependencies {
    /* Android Core */
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    // app lock (biometric or device credential), AOSP library: no Play Services involved
    implementation(libs.androidx.biometric)
    // the Room database is stored encrypted, see db/DatabaseEncryption.kt
    implementation(libs.sqlcipher.android)
    // backups are encrypted with a key derived by Argon2id, see helpers/BackupCrypto.kt
    implementation(libs.bouncycastle.bcprov)
    // downloads are encrypted, see helpers/DownloadEncryption.kt
    implementation(libs.tink.android)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.media)
    implementation(libs.androidx.swiperefreshlayout)

    /* Android Lifecycle */
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.service)

    /* Design */
    implementation(libs.material)

    /* ExoPlayer */
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    /* Retrofit and Kotlinx Serialization */
    implementation(libs.square.retrofit)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.datetime)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.google.protobuf.javalite)
    implementation(libs.google.protobuf.kotlin.lite)

    /* NewPipe Extractor */
    // also the JSON code the app reads the player response with (org.schabi.newpipe.extractor.json)
    implementation(libs.newpipeextractor)

    /* Coil */
    coreLibraryDesugaring(libs.desugaring)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    /* Room */
    ksp(libs.room.compiler)
    implementation(libs.room)

    /* Baseline profile generation */
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    /* AndroidX Paging */
    implementation(libs.androidx.paging)

    /* Testing */
    testImplementation(libs.junit)

    /* Security patches of transitive dependencies */
    constraints {
        implementation(libs.google.guava) {
            because("CVE-2026-102554: Media3 still pulls a vulnerable Guava")
        }
    }
}

// Protobuf: protoc is a build tool, not a dependency of the app, hence a plain coordinate built
// from the catalog version (shared with the protobuf runtime) rather than a library alias.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            // java is a generator built into protoc, not an external protoc-gen-* plugin
            task.builtins {
                maybeCreate("java").option("lite")
            }
        }
    }
}
// endregion
// region Privacy
// Privacy guard: no proprietary Google library (Play Services, Firebase, datatransport, Play Core)
// may be resolved, not even as a transitive dependency of something else.
val proprietaryGoogleGroups =
    Regex("""com\.google\.(android\.gms|firebase|android\.datatransport|android\.play)(\..+)?""")
configurations.configureEach {
    resolutionStrategy.eachDependency {
        check(!proprietaryGoogleGroups.matches(requested.group)) {
            "Proprietary Google dependency refused: ${requested.group}:${requested.name}"
        }
    }
}

// Privacy guard: the release build fails when proprietary Google code (Play Services, Firebase,
// datatransport) or telemetry is back in the dex, e.g. pulled in again by a dependency, or after
// a library update made the Media3 rule of proguard-rules.pro stale (re-audit it then).
tasks.matching { it.name == "minifyReleaseWithR8" }.configureEach {
    doLast {
        val markers = listOf(
            "com/google/android/gms/", // Google Play Services (proprietary)
            "com/google/firebase/", // Firebase
            "com/google/android/datatransport/", // Google datatransport (Firelog uploads)
            "IClientTelemetryService", // Play Services client telemetry
            "createPlaybackSession" // Media3 platform diagnostics
        )
        val dex = outputs.files.asFileTree.filter { it.extension == "dex" }
            .joinToString("") { it.readText(Charsets.ISO_8859_1) }
        check(dex.isNotEmpty()) { "Privacy guard: no dex found in the R8 output" }
        val found = markers.filter { it in dex }
        check(found.isEmpty()) { "Telemetry code survived R8: $found, re-audit proguard-rules.pro" }
    }
}
// endregion
