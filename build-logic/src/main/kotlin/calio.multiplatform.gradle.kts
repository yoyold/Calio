import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Baseline configuration for every Kotlin Multiplatform module in the project.
 *
 * Modules applying this plugin get the Android and JVM targets, the shared toolchain, the common
 * compiler settings and the common test dependencies. Keeping this in one place is what allows a
 * module build script to stay a handful of lines long.
 */

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// Type-safe catalog accessors are not generated inside precompiled script plugins, so the catalog is
// looked up explicitly.
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogVersion(alias: String): String =
    versionCatalog.findVersion(alias).orElseThrow {
        IllegalStateException("Missing version '$alias' in gradle/libs.versions.toml")
    }.requiredVersion

val jvmToolchainVersion = catalogVersion("jvmToolchain")

kotlin {
    jvmToolchain(jvmToolchainVersion.toInt())

    android {
        // Derived from the Gradle path so the namespace can never drift from the module location:
        // ":core:model" becomes "app.calio.core.model".
        namespace = "app.calio" + project.path.replace(':', '.').replace('-', '.')
        compileSdk = catalogVersion("androidCompileSdk").toInt()
        minSdk = catalogVersion("androidMinSdk").toInt()

        withHostTestBuilder {}
    }

    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    compilerOptions {
        // Warnings are design feedback; letting them accumulate hides the ones that matter.
        allWarningsAsErrors.set(true)
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmToolchainVersion))
}
