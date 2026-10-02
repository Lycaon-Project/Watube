// Fichier : /app/build.gradle.kts
// ⚠️ IMPORTANT : Le bloc plugins {} DOIT être la TOUTE PREMIÈRE instruction du fichier
// Il ne doit PAS être à l'intérieur d'un bloc android {}, dependencies {}, ou autre

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.androidx.navigation.safeargs)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.protobuf)
}

// WATUBE: parcelize is already on the build classpath (shipped by AGP) -> it can only be
// applied WITHOUT a version (a versioned request fails with "already on the classpath with
// an unknown version"). Serialization is not, hence its `apply false` declaration at the
// root project, so this bare apply can find it.
apply(plugin = "org.jetbrains.kotlin.plugin.parcelize")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

android {
    namespace = "com.watube.yard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.watube.yard"
        minSdk = 28
        targetSdk = 37

        versionCode = 1407
        versionName = "26.10.3"
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
            vcsInfo { include = false }
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
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Configuration du jvmTarget pour Kotlin
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // javaParameters via les tâches Java
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs.excludes.add("lib/armeabi-v7a/*_neon.so")
        // keep the dex files compressed: on this project they are the bulk of the APK
        // (~80%), so the install/download size drops by ~21 MB at no functional cost
        dex {
            useLegacyPackaging = true
        }
        // The protobuf lite runtime never reads .proto sources nor descriptor sets (they only
        // serve the full runtime / protoc), and DebugProbesKt.bin is only loaded by the
        // kotlinx-coroutines debug agent: none of them is used at runtime.
        resources.excludes += listOf(
            "google/protobuf/*.proto",
            "src/google/protobuf/*.proto",
            "**/java_features_proto-descriptor-set.proto.bin",
            "DebugProbesKt.bin"
        )
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
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

// WATUBE: Room schema export. AppDatabase has exportSchema = true, without this KSP
// fails the build ("Schema import directory was not provided").
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

    /* Google Cast */
    implementation(libs.google.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)

    /* Retrofit and Kotlinx Serialization */
    implementation(libs.square.retrofit)
    implementation(libs.logging.interceptor)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.datetime)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.google.protobuf.javalite)
    implementation(libs.google.protobuf.kotlin.lite)

    /* NewPipe Extractor */
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
}

// Protobuf configuration
protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}