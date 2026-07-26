plugins {
    id("calio.multiplatform.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The composition root is the only place that sees both the implementations and the
            // screens, so everything it wires is part of its public surface.
            api(projects.data)
            api(projects.domain)
            api(projects.core.database)
            api(projects.core.designsystem)
            api(projects.core.ui)
            api(projects.feature.calendar)
            api(projects.feature.eventEditor)
            api(projects.feature.tasks)
            implementation(projects.core.model)
            implementation(projects.core.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.sqldelight.driver.jvm)
        }
    }
}
