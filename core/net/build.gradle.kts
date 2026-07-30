plugins {
    id("calio.multiplatform")
}

kotlin {
    sourceSets {
        // Both targets are the JVM, so platform code they have in common has one home instead of
        // being written out twice. The default source set layout has no name for that relationship,
        // so it is drawn here.
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
    }
}
