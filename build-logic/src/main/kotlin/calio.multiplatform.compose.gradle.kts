import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider

/**
 * Baseline for every module that contains user interface code.
 *
 * It builds on the plain multiplatform convention and adds the Compose plugins plus the dependencies
 * that every screen needs anyway. Declaring them once keeps a feature module free to state only what
 * makes it different from the others.
 *
 * The artifacts are named from the version catalog rather than through the Compose plugin's own
 * shorthand, so Compose versions live where every other version lives.
 */

plugins {
    id("calio.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogLibrary(alias: String): Provider<MinimalExternalModuleDependency> =
    catalog.findLibrary(alias).orElseThrow {
        IllegalStateException("Missing library '$alias' in gradle/libs.versions.toml")
    }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(catalogLibrary("compose-runtime"))
            implementation(catalogLibrary("compose-foundation"))
            implementation(catalogLibrary("compose-material3"))
            implementation(catalogLibrary("compose-ui"))
        }
    }
}
