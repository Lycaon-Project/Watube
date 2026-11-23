plugins {
    // Every plugin is loaded once, on the shared classpath of the root project, so that its DSL
    // extensions are visible in the modules that apply it (:app, :baselineprofile).
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.protobuf) apply false
    // The serialization plugin is not shipped by AGP (unlike the Kotlin Android and Parcelize
    // plugins, which AGP 9 puts on the classpath itself): it is loaded here with its version,
    // and :app applies it by alias.
    alias(libs.plugins.kotlin.serialization) apply false
    // androidx.navigation.safeargs.kotlin is a code generator with no DSL extension of its own:
    // it is applied directly in :app, its only user.
}

tasks.register<Delete>("clean") {
    description = "Deletes the build directory of the root project"
    group = "build"
    delete(rootProject.layout.buildDirectory)
}
