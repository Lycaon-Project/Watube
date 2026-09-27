// ============================================================================
// Watube Settings — build system based on LibreTube upstream, tuned for
// Android Studio sync + AGP 9.4.1 / Gradle 9.7 performance.
// ============================================================================

@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        // Order matters for performance: most-used repositories first
        google {
            content {
                // Only fetch Google/Android plugins from here
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Auto-provision JDK toolchains (downloads JDK if needed)
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Fail if any module tries to declare its own repositories
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }

        // Maven Central WITHOUT excluding com.google.* : protobuf et guava y vivent
        mavenCentral {
            content {
                excludeGroupByRegex("com\\.android.*")
                excludeGroupByRegex("androidx.*")
            }
        }

        // JitPack for GitHub-hosted libraries (NewPipeExtractor fork Watube)
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.TeamNewPipe")
                includeGroup("com.github.libre-tube")
                includeGroup("com.github.Lycaon-Project")
                includeGroupByRegex("com\\.github\\..*")
            }
        }

        mavenLocal()
    }
}

// Le nom du projet racine sert de nom de workspace dans Android Studio
rootProject.name = "Watube"

include(":app")
include(":baselineprofile")
