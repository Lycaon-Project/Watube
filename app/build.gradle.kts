import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    // Le plugin Kotlin lui-même n'était jamais appliqué (seuls parcelize/serialization
    // l'étaient) -> le module ne compilait pas en Kotlin. À déclarer explicitement.
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.navigation.safeargs)
    // Plugin baselineprofile : la configuration "com.android.test" du module
    // :baselineprofile plantait la sync Android Studio ("targetProjectPath cannot
    // be null"). Le module est donc retiré du build par défaut (settings.gradle.kts)
    // et l'application ne conserve que profileinstaller, qui lit le profil embarqué
    // app/src/main/baseline-prof.txt si présent.
    // Pour régénérer un jour le profil (nécessite un appareil API 33+) :
    //   ./gradlew :app:generateBaselineProfile -PincludeBaselineProfile=true
    // en décommentant les deux lignes ci-dessous ainsi que le bloc conditionnel
    // dans dependencies{}.
    // if (providers.gradleProperty("includeBaselineProfile").isPresent) {
    //     alias(libs.plugins.baselineprofile)
    // }
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.protobuf)
}

/*
'keystore.properties' should look like the following:

storeFile=my.keystore
storePassword=my_store_password
keyAlias=my_key_alias
keyPassword=my_key_password
 */

val keystoreProperties = Properties()
val keystoreFileExists = rootProject.file("keystore.properties").exists()
if (keystoreFileExists) {
    keystoreProperties.load(rootProject.file("keystore.properties").inputStream())
}

@Suppress("Deprecation")
android {
    compileSdk = 37

    defaultConfig {
        applicationId = "com.watube.yard"
        minSdk = 26
        targetSdk = 37
        versionCode = 821
        // Version en préparation : 27A1 (build 821)
        versionName = "27A1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "app_name", "Watube")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("exportSchema", "true")
    }

    viewBinding {
        enable = true
    }

    signingConfigs {
        if (keystoreFileExists) {
            create("release") {
                storeFile = keystoreProperties["storeFile"]?.let { file(it as String) }
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile != null }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Watube Debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            javaParameters = true
        }
    }

    packaging {
        jniLibs.excludes.add("lib/armeabi-v7a/*_neon.so")
    }

    tasks.register("testClasses") {
        description = "Compiles the test classes for the project"
        group = "verification"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    // language preference for Android 13 and above
    @Suppress("UnstableApiUsage")
    androidResources {
        generateLocaleConfig = true
    }

    namespace = "com.watube.yard"
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
    // Dépendance vers le module :baselineprofile désactivée par défaut (voir le
    // commentaire du bloc plugins{} plus haut). À décommenter en même temps que
    // le plugin, lors d'une génération de profil sur appareil API 33+ :
    // if (providers.gradleProperty("includeBaselineProfile").isPresent) {
    //     baselineProfile(project(":baselineprofile"))
    // }

    /* AndroidX Paging */
    implementation(libs.androidx.paging)

    /* Testing */
    testImplementation(libs.junit)
}

//TODO: exclude from release protobuf
protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                //DSL protobuf-gradle-plugin >= 0.10 : les options se déclarent
                //via builtin "options" (l'ancienne extension `option(...)` n'existe plus)
                create("java") {
                    option("lite")
                }
            }
        }
    }
}