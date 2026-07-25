// Plugins are declared here without being applied, so their implementations are loaded once for the
// whole build instead of separately per module. Modules get them through the convention plugins in
// build-logic/, and no module depends on the root project.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.sqldelight) apply false
}
