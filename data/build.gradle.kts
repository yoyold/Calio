plugins {
    id("calio.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The composition root constructs these implementations, so both the contracts they
            // fulfil and the database they take are part of this module's public surface.
            api(projects.domain)
            api(projects.core.database)
            implementation(projects.core.model)
            implementation(projects.core.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.coroutines)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.sqldelight.driver.jvm)
        }
    }
}
