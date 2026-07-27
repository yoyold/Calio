plugins {
    id("calio.multiplatform.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.domain)
            api(projects.core.sync)
            implementation(projects.core.ui)
            implementation(projects.core.model)
            implementation(projects.core.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
        }
        jvmTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
