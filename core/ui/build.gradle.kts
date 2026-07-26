plugins {
    id("calio.multiplatform.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.designsystem)
            api(projects.core.datetime)
            implementation(projects.core.model)
        }
    }
}
