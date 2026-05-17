import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.objectbox)
}

// Read local.properties so secrets (HF token etc.) never need to be in source control.
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use(props::load)
}

android {
    namespace = "com.clairedoc.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clairedoc.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Inject the HuggingFace token from local.properties (or CI env var) as a
        // BuildConfig constant. Empty string if not set — app falls back to UI entry.
        val hfToken = localProps["HUGGINGFACE_TOKEN"]?.toString()?.trim()
            ?: System.getenv("HUGGINGFACE_TOKEN").orEmpty()
        buildConfigField("String", "HF_TOKEN", "\"$hfToken\"")

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
        buildConfig = true   // required to generate BuildConfig.HF_TOKEN
        // composeOptions { kotlinCompilerExtensionVersion } is NOT needed.
        // org.jetbrains.kotlin.plugin.compose (added above) handles compiler
        // selection automatically for Kotlin 2.0+.
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
            // LiteRT 2.1.0 and litertlm-android may both bundle the TFLite runtime.
            pickFirsts += "**/libtensorflowlite_jni.so"
            pickFirsts += "**/libtensorflowlite_gpu_jni.so"
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
    implementation(libs.lifecycle.process)
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

    // RAG: TFLite runtime for EmbeddingGemma-300M inference
    implementation(libs.litert)

    // RAG: ObjectBox vector store (HNSW vector index for chunk retrieval)
    implementation(libs.objectbox.android)
    implementation(libs.objectbox.kotlin)
    // Note: no ksp()/kapt() needed — the io.objectbox plugin registers its KAPT processor
    // automatically via kaptDebugKotlin, which generates MyObjectBox + DocumentChunk_.

    // WorkManager — background RAG indexing (CoroutineWorker + unique-work enqueue)
    implementation(libs.work.runtime.ktx)

    // Coil — async image loading for document preview sheet
    implementation(libs.coil.compose)

    // JVM unit tests
    testImplementation(libs.junit)
}

// Force a single protobuf variant across the entire dependency graph.
configurations.all {
    resolutionStrategy {
        force("com.google.protobuf:protobuf-javalite:3.25.3")
    }
}
