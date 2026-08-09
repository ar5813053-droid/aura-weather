/*
 * Aura Weather - Gradle settings
 *
 * This file configures plugin resolution and dependency repositories for the
 * whole build, and declares which modules are part of it.
 */

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Aura Weather"

// The ":app" module has not been created yet - this repository currently
// only contains build tooling/infrastructure. Including a module whose
// directory does not exist would fail Gradle configuration, so the include
// is guarded until the module is actually added. Once an "app/" directory
// with its own build.gradle.kts is created, this will pick it up
// automatically with no further changes required here.
if (file("app").exists()) {
    include(":app")
}
