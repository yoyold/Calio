plugins {
    `kotlin-dsl`
}

dependencies {
    // Convention plugins apply these plugins, so their implementations must be on the classpath.
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.composeCompilerGradlePlugin)
    implementation(libs.android.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}
