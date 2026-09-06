// Top-level build file. Kotlin support is provided by AGP's built-in Kotlin,
// so org.jetbrains.kotlin.android must NOT be applied (see
// https://developer.android.com/build/migrate-to-built-in-kotlin).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
