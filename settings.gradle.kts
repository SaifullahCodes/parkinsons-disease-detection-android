pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS ensures these repositories are always used
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral() // This is REQUIRED for FFmpeg Kit
        maven { url = uri("https://jitpack.io") } // This is for TarsosDSP if used via JitPack
    }
}

rootProject.name = "Parkinson's Disease Detection System"
include(":app")