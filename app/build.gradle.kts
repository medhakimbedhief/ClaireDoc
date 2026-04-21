plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.clairedoc.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.clairedoc.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Only arm64-v8a: covers Galaxy A52s 5G (Snapdragon 778G).
        // Omitting x86_64 avoids UnsatisfiedLinkError if litertlm-android
        // does not ship x86_64 binaries.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            // No obfuscation for hackathon build — avoids ProGuard rules for
            // LiteRT/MediaPipe native bindings.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // tasks-genai:0.10.24 AAR was compiled targeting JVM 11.
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        // litertlm-android:0.10.2 was compiled with Kotlin 2.3.x (metadata 2.3.0).
        // Our Kotlin 2.1.0 compiler understands metadata up to 2.1.0.
        // This flag tells the compiler to accept forward-compatible metadata
        // rather than failing hard on the version mismatch.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    buildFeatures {
        compose = true
        // composeOptions { kotlinCompilerExtensionVersion } is removed.
        // kotlin.plugin.compose (added above) handles this automatically for
        // Kotlin 2.0+ — it picks the correct Compose compiler version.
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Both litertlm-android and tasks-genai ship these .so files.
            // pickFirsts prevents "More than one file was found" build error.
            pickFirsts += "**/libOpenCL.so"
            pickFirsts += "**/libvndksupport.so"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.activity)
    implementation(libs.compose.navigation)
    implementation(libs.compose.lifecycle.viewmodel)
    implementation(libs.compose.lifecycle.runtime)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Protobuf conflict: tasks-genai brings protobuf-java (full runtime) while
    // litertlm-android and localagents-fc use protobuf-javalite (Android-lite).
    // Exclude the full runtime here and force javalite everywhere below.
    implementation(libs.mediapipe.genai) {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation(libs.litertlm.android)
    implementation(libs.localagents.fc)

    implementation(libs.mlkit.docscanner)
    implementation(libs.gson)
    implementation(libs.coroutines.android)
}

// Force a single protobuf variant across the entire dependency graph.
configurations.all {
    resolutionStrategy {
        force("com.google.protobuf:protobuf-javalite:3.25.3")
    }
}
