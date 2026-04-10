plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ezansi.app.core.ai"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:llama"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ONNX Runtime Android for embedding model inference (all-MiniLM-L6-v2).
    // Legacy fallback — kept for rollback if Gemma 4 embedding quality is insufficient.
    // The OnnxEmbeddingModel uses reflection to load ONNX classes so the app
    // compiles and runs with MockEmbeddingModel even if this is commented out.
    implementation(libs.onnxruntime.android)

    // MediaPipe GenAI SDK for Gemma 4 LLM inference + embedding via LiteRT.
    // Replaces llama.cpp (generation) + ONNX (embedding) with a unified model.
    // Apache 2.0 licensed, no GMS dependency, min SDK 24 (our target: 29).
    implementation(libs.mediapipe.tasks.genai)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit5.vintage.engine)
    testImplementation(libs.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
