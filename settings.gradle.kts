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

rootProject.name = "calio"

include(":core:model")
