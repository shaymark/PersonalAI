import com.android.build.gradle.internal.cxx.configure.CmakeProperty

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.llmengine"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 28   // Vulkan 1.1 (required by llama.cpp) is only in libvulkan.so from API 28+
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            // Only build for 64-bit ARM — all modern Android phones.
            // Skipping x86_64 cuts Vulkan shader compilation time roughly in half.
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {

                cppFlags("-std=c++17")

                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DGGML_VULKAN=ON",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        jniLibs {
            // Keep only the shared libs we actually need
            pickFirsts += setOf("**/libc++_shared.so", "**/libllama-engine.so")
        }
    }
}

dependencies {
    // Model download
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines for Flow / suspend support
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    implementation("androidx.core:core-ktx:1.13.1")
}
