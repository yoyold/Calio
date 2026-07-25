plugins {
    `kotlin-dsl`
}

dependencies {
    // Convention plugins apply these plugins, so their implementations must be on the classpath.
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.android.gradlePlugin)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}
