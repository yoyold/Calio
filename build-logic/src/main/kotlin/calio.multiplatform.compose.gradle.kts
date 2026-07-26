import org.jetbrains.compose.ComposeExtension

/**
 * Baseline for every module that contains user interface code.
 *
 * It builds on the plain multiplatform convention and adds the Compose plugins plus the dependencies
 * that every screen needs anyway. Declaring them once keeps a feature module free to state only what
 * makes it different from the others.
 */

plugins {
    id("calio.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val compose = extensions.getByType<ComposeExtension>().dependencies

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}
