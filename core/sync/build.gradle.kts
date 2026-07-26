plugins {
    id("calio.multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Revisions and identifiers appear in the public contracts of this module.
            api(projects.core.model)
            api(projects.domain)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
