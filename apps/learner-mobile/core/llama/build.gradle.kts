// DEPRECATED: Legacy fallback module — remove after real-device Gemma 4 validation
// This module provides llama.cpp JNI bindings for Qwen2.5-1.5B text generation.
// Superseded by MediaPipe GenAI SDK (Gemma 4) in :core:ai.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ezansi.app.core.llama"
    compileSdk = 35
    ndkVersion = "27.1.12297006"

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // x86_64 for emulator, arm64-v8a for real devices
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DLLAMA_BUILD_COMMON=ON",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DBUILD_SHARED_LIBS=ON",
                    // Force Release-level optimisation even for debug APKs.
                    // Without this, assembleDebug uses -O0 which makes
                    // llama.cpp prompt eval ~10x slower.
                    "-DCMAKE_C_FLAGS_DEBUG=-O3 -DNDEBUG",
                    "-DCMAKE_CXX_FLAGS_DEBUG=-O3 -DNDEBUG",
                )
                cppFlags += listOf("-std=c++17", "-O3")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
