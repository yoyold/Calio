plugins {
    id("calio.multiplatform.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // A feature depends on the contracts and on the shared interface building blocks, never
            // on the data layer. That is what keeps a screen testable against fakes.
            api(projects.domain)
            implementation(projects.core.ui)
            implementation(projects.core.model)
            implementation(projects.core.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
