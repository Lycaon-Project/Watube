plugins {
    // CORRECTION "Unresolved reference: navigationArgs" (Android Studio) :
    // les plugins doivent etre CHARGES au classpath commun des la racine pour
    // que leurs extensions DSL soient visibles dans les sous-modules.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.androidTest) apply false
    // NOTE : le plugin androidx.navigation.safeargs.kotlin est UN GENERATEUR
    // de code (KSP/annotation-processor style) : il n'ajoute AUCUNE propriete
    // au bloc buildFeatures{} -> `safeArgs`/`navigationArgs` y sont inconnus.
    // Il est donc applique directement dans :app (seul consommateur).
    // WATUBE FIX (sync Android Studio) : les plugins Kotlin (android/parcelize/
    // serialization) sont desormais declares AVEC version dans :app via le
    // version catalog (alias libs.plugins.kotlin.*). Ils ne doivent plus etre
    // pre-resolus ici sans version coherentes, sinon conflits de classpath.
    // KSP et protobuf idem : classes chargees une seule fois, a la racine.
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.protobuf) apply false
}

// ✅ CORRECTION 1 : Ajout description et groupe pour la tâche clean
tasks.register<Delete>("clean") {
    description = "Deletes the build directory and all generated files"
    group = "build"
    delete(rootProject.layout.buildDirectory)
}

// ✅ CORRECTION 2 : Ajout description et groupe pour la tâche buildLanguages
// this builds the list of languages to use for Android versions below 13
tasks.register("buildLanguages") {
    description = "Generates the languages.xml file with all available locales"
    group = "build"

    val projectDirectory = layout.projectDirectory

    // reference: https://docs.gradle.org/current/userguide/working_with_files.html
    val resPath = projectDirectory.file("app/src/main/res")
    val locales = resPath.asFile.listFiles()
        .filter {
            it.nameWithoutExtension.startsWith("values-") && File(
                it,
                "strings.xml"
            ).exists()
        }
        .map {
            it.nameWithoutExtension.removePrefix("values-")
        } + "en" // en is the default locale, its values file has no -en suffix

    val localesConfig =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "<string-array name=\"languageCodes\">\n" +
                locales.joinToString("\n") { "  <item>$it</item>" } + "\n" +
                "</string-array>\n" +
                "</resources>"

    val outputFile = projectDirectory.file("app/src/main/res/values/languages.xml").asFile
    if (!outputFile.exists()) outputFile.createNewFile()
    outputFile.bufferedWriter().use {
        it.write(localesConfig)
    }
}