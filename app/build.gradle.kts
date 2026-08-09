plugins {
    id("com.android.application") version "9.3.1"
}

android {
    namespace = "com.aura.weather"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aura.weather"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Non-deprecated androidx.lifecycle.compose.LocalLifecycleOwner (WeatherOrb.kt) and
    // lifecycle-runtime APIs (Lifecycle, LifecycleEventObserver) used for lifecycle-aware
    // animation. Previously pulled in only transitively via activity-compose /
    // androidx.compose.ui:ui, which is what left the project on the deprecated
    // androidx.compose.ui.platform.LocalLifecycleOwner.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
}
