plugins {
    id("calio.multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The fakes fulfil the domain contracts, so those contracts are part of what this module
            // hands to its users.
            api(projects.domain)
            api(projects.core.model)
            api(projects.core.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
