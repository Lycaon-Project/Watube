
plugins {
    // Identique au dépôt amont (Lycaon-Project/Watube @ main) : le plugin
    // com.android.test partage la classe d'agents commune chargée à la racine
    // (alias androidTest -> même version que AGP, embarquée dans AGP).
    alias(libs.plugins.androidTest)
    alias(libs.plugins.baselineprofile)
}

// ✅ CORRECTION 1 : Suppression du warning de dépréciation pour android {}
@Suppress("Deprecation")
android {
    // CORRECTION CRITIQUE DE LA SYNC ANDROID STUDIO :
    // "targetProjectPath cannot be null in test project baselineprofile"
    // AGP lit cette propriété dès la phase afterEvaluate ; elle doit donc être
    // déclarée EN TÊTE du bloc android{}, avant toute autre configuration.
    targetProjectPath = ":app"

    namespace = "com.watube.yard.baselineprofile"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 28
        // ✅ CORRECTION 3 : Mise à jour vers targetSdk 37 pour éliminer le warning
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

}

// CORRECTION "Unresolved reference: kotlinOptions / jvmTarget" :
// sans org.jetbrains.kotlin.android applique nommement, seule la compilation
// embarquee AGP 9 est disponible -> reglage du jvmTarget par reflection.
tasks.withType<AbstractCompile>().configureEach {
    if (name.startsWith("compile") && name.contains("Kotlin")) {
        try {
            val co = javaClass.getMethod("getCompilerOptions").invoke(this)
            val jt = co.javaClass.getMethod("getJvmTarget").invoke(co)
            val v17 = jt.javaClass.getMethod("fromString", String::class.java).invoke(null, "17")
            jt.javaClass.getMethod("set", Object::class.java).invoke(jt, v17)
        } catch (_: Throwable) { }
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.espressoCore)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// ✅ CORRECTION 4 : Suppression du warning @Incubating pour getTestedApks()
@Suppress("UnstableApiUsage")
androidComponents {
    onVariants { v ->
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { v.artifacts.getBuiltArtifactsLoader().load(it)?.applicationId }
        )
    }
}