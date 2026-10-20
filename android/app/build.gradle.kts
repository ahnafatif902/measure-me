// app/build.gradle.kts — the only module. Dependencies are deliberately few:
//  * compose-bom      → UI toolkit (screens declared once, state-driven)
//  * camera-*         → CameraX preview + still capture (no ARCore, by design)
//  * tasks-vision     → MediaPipe Pose Landmarker, model in assets/ → offline
//  * exifinterface    → rotate gallery photos the right way up
//  * lifecycle/activity-compose → ViewModel + single-activity plumbing
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.measureme.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.measureme.app"
        minSdk = 24          // covers essentially every mid-range Android in use
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"
        ndk {
            // OpenCV's AAR ships native libs for all four ABIs (~112 MB). The
            // app targets real phones only, so the two ARM variants are kept
            // and the APK roughly halves in size.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // MVP: keep it debuggable; enable for store builds
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true // for the debug-only automated test hook (BuildConfig.DEBUG)
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" } // == Kotlin 1.9.24
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.13.1")

    // Camera (preview + still capture). No ARCore, no depth APIs anywhere.
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // On-device pose estimation. MediaPipe Tasks ships the runtime as a
    // library and the model (pose_landmarker_full.task, ~9 MB) lives in
    // assets/ — so detection works fully offline right after install.
    implementation("com.google.mediapipe:tasks-vision:0.10.9")
    implementation("com.google.mediapipe:tasks-core:0.10.9")

    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // OpenCV (official Maven Central build): only used for Mode C's A4 sheet
    // detection (contours + quad fitting). Initialised once in MeasureMeApp
    // via OpenCVLoader.initLocal(); every detector call checks availability
    // and degrades to the Proportions path if native libs are missing.
    implementation("org.opencv:opencv:4.10.0")

    // JVM unit tests for the pure-Kotlin pipeline (GeometryModule etc.).
    testImplementation("junit:junit:4.13.2")
}
