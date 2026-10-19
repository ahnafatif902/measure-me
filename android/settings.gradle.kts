// Top-level settings. Two repositories are enough: Google (AGP, AndroidX, ML Kit)
// and Maven Central (Kotlin stdlib). Everything else is intentionally omitted.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MeasureMe"
include(":app")
