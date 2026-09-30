import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Debug-only module: the app consumes it with `debugImplementation(project(":localpush-debug"))`,
// so neither its code nor its <receiver> ever reach the release APK/AAB.
plugins {
    id("com.android.library")
    // With AGP 9+ (Kotlin built into AGP) remove this line.
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.localpush.debug"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
