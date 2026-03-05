# Rules applied to apps that consume this library.
# Keep JNI bridge so the native library can call back into Kotlin.
-keep class com.llmengine.LlamaJni { *; }
-keep interface com.llmengine.LlamaJni$TokenCallback { *; }
