plugins {
    id("calio.multiplatform.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
        }
    }
}
