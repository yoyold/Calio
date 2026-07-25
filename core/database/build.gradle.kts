plugins {
    id("calio.multiplatform")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Generated query classes are part of this module's public surface.
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.jvm)
        }
    }
}

sqldelight {
    databases {
        create("CalioDatabase") {
            packageName.set("app.calio.database")
            // A snapshot of the released schema is kept in the repository. Every build replays the
            // migrations against it, so a change that would break an existing installation fails
            // here instead of on a user's device. Regenerate it with
            // `generateCommonMainCalioDatabaseSchema` after adding a migration.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
