import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.shared)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.calio.desktop.MainKt"

        nativeDistributions {
            // Windows is the supported desktop target; the runtime is bundled so the installed
            // application does not depend on a Java installation being present.
            targetFormats(TargetFormat.Msi)
            packageName = "Calio"
            packageVersion = "1.0.0"
            description = "A fast, minimal calendar"
        }
    }
}
