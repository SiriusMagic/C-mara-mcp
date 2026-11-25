plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zirakusa.camara"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zirakusa.camara"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.0"
        // Modo MCP: the same public relay as the rest of the family (one device id per app). Empty = no MCP.
        val relayUrl = (project.findProperty("camaraRelayUrl") as String?).orEmpty().trimEnd('/')
        buildConfigField("String", "RELAY_URL", "\"$relayUrl\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Signed like the rest of the family and Gateway Hub (the hub recognises the same developer).
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Screenshots of the screens land in app/build/screenshots .
        unitTests.all { it.systemProperty("roborazzi.test.record", "true") }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Modo MCP: WebSocket to the relay (same library and version as the rest of the family).
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    // The camera: CameraX's controller (preview, photo, video, zoom, focus), the same version as QR.
    implementation("androidx.camera:camera-camera2:1.7.0")
    implementation("androidx.camera:camera-lifecycle:1.7.0")
    implementation("androidx.camera:camera-view:1.7.0")
    implementation("androidx.camera:camera-video:1.7.0")
    // Retrato, Noche and HDR with the phone maker's own processing, when the phone offers it (CameraX Extensions).
    implementation("androidx.camera:camera-extensions:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.79.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.79.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
