pluginManagement {
    // Convention plugins live in an included build so they are compiled once and reused by every
    // module without polluting the root build script's classpath.
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Modules must not declare their own repositories; all resolution is configured here.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        google()
        mavenCentral()
    }
}

// Lets modules refer to each other as projects.core.model instead of project(":core:model"),
// so a typo in a module path is a compile error rather than a runtime failure.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "calio"

include(":core:database")
include(":core:datetime")
include(":core:model")
include(":domain")
