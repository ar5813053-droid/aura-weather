/*
 * Aura Weather - top-level (project) build file.
 *
 * Plugins are declared here with "apply false" so that their versions are
 * resolved once, centrally, and only actually applied in the modules that
 * need them (e.g. the future ":app" module).
 *
 * Note on Kotlin: Android Gradle Plugin 9.x compiles Kotlin sources using
 * its built-in Kotlin support. The classic "org.jetbrains.kotlin.android"
 * plugin is no longer required (and is not compatible with AGP 9's new
 * DSL), so it is deliberately NOT declared here or in any module.
 */

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
