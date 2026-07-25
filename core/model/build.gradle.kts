plugins {
    id("calio.multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Date and time types appear in the public signatures of this module, so they are api().
            api(libs.kotlinx.datetime)
        }
    }
}
