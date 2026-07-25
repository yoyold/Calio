plugins {
    id("calio.multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Entities and calendar arithmetic appear in the public signatures of this module.
            api(projects.core.model)
            api(projects.core.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
