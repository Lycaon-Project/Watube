plugins {
    // com.android.test shares the AGP classes loaded at the root (same version as AGP)
    alias(libs.plugins.androidTest)
    alias(libs.plugins.baselineprofile)
    // The module has Kotlin sources (BaselineProfileGenerator): AGP 9 compiles them itself,
    // declaring org.jetbrains.kotlin.android fails ("no longer required for Kotlin support").
}

@Suppress("Deprecation")
android {
    // read by AGP as early as afterEvaluate: declared first, before any other setting
    // ("targetProjectPath cannot be null in test project baselineprofile" otherwise)
    targetProjectPath = ":app"

    namespace = "com.watube.yard.baselineprofile"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

// Kotlin jvmTarget aligned with compileOptions (AGP 9 built-in Kotlin has no kotlin {} block here)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

// Runs the generators on the connected devices (no Gradle managed device).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.espressoCore)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// getTestedApks() is @Incubating
@Suppress("UnstableApiUsage")
androidComponents {
    onVariants { v ->
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { v.artifacts.getBuiltArtifactsLoader().load(it)?.applicationId }
        )
    }
}
