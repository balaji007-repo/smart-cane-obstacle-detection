plugins {
    id("com.android.application") version "8.9.2"
    id("org.jetbrains.kotlin.android") version "2.1.20"
}

android {
    namespace = "com.smartcane.prototype"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartcane.prototype"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    androidResources {
        noCompress += "tflite"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    val cameraX = "1.4.2"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("com.google.mediapipe:tasks-vision:0.10.26")
    testImplementation("junit:junit:4.13.2")
}
