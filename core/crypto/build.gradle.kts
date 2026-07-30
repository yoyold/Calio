plugins {
    id("calio.multiplatform")
}

kotlin {
    sourceSets {
        // Hashing and randomness are the same code on both targets; only the place a secret is kept
        // differs. The shared source set keeps the first from being written twice.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain").dependsOn(jvmShared)
        getByName("jvmMain").dependsOn(jvmShared)

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.jna.platform)
        }
    }
}
