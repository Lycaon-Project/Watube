import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    // WATUBE FIX (sync Android Studio) : le plugin Kotlin android est declare
    // explicitement avec sa version via le version catalog. C'est LA condition
    // pour qu'Android Studio reconnaisse le module comme une application Android
    // complete (sourceSets Kotlin, extension kotlin{}, tache IDE
    // prepareKotlinBuildScriptModel). La version reste alignee sur celle
    // embarquee par AGP (voir commentaire dans libs.versions.toml).
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.navigation.safeargs)
    // Plugin baselineprofile : identique à main amont. L'erreur de sync
    // "targetProjectPath cannot be null in test project baselineprofile" était
    // causée par une déclaration TARDIVE de targetProjectPath dans le DSL android{}
    // du module :baselineprofile (AGP lit cette propriété dès afterEvaluate -> null).
    // Corrigé dans baselineprofile/build.gradle.kts (déclaration en tête de bloc).
    alias(libs.plugins.baselineprofile)
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
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("exportSchema", "true")
    }

    buildFeatures {
        viewBinding = true
        // CORRECTION "Unresolved reference 'BuildConfig'" : AGP 9 ne genere plus
        // BuildConfig par defaut -> reactivation explicite (classe generee dans
        // com.watube.yard.BuildConfig, utilisee par ~15 fichiers).
        buildConfig = true
        // Le plugin safeargs (genereur de code NavDirections/NavArgs) n'expose
        // AUCUNE propriete dans buildFeatures{} -> rien a declarer ici. Les
        // classes generees (ex. NavDirections.openChannel) le sont des que le
        // plugin est applique ci-dessus dans le bloc plugins{}.
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
            // WATUBE: resValue etait desactive sous AGP9/newDsl -> nom "Watube Debug"
            // porte par values/strings.xml dans le sourceSet debug (equivalent, propre).
        }
    }

    // CORRECTION "Unresolved reference: kotlinOptions" :
    // sans le plugin org.jetbrains.kotlin.android applique nommement, l'extension
    // KotlinAndroidProjectExtension n'existe pas (AGP 9 embarque le compilateur
    // mais n'enregistre PAS l'extension DSL). On configure donc le jvmTarget via
    // la task compileOptions commune + options de compilation Kotlin standard.
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // javaParameters est une option JAVA pure -> passe par les tasks JavaCompile.
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }

    tasks.register("testClasses") {
        description = "Compiles the test classes for the project"
        group = "verification"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
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

// WATUBE FIX (sync Android Studio) : extension Kotlin officielle. Le plugin
// org.jetbrains.kotlin.android etant desormais applique nommement dans le bloc
// plugins{}, cette DSL est resolue par l'IDE -> le module est reconnu comme
// application Android complete (plus de "prepareKotlinBuildScriptModel not
// found", plus de module Java orphelin). Remplace le hack par reflection.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
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
    baselineProfile(project(":baselineprofile"))

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