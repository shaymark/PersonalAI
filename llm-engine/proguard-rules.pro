# Keep JNI bridge class and all its members
-keep class com.llmengine.LlamaJni { *; }
-keep interface com.llmengine.LlamaJni$TokenCallback { *; }
-keepclassmembers class com.llmengine.LlamaJni {
    native <methods>;
}

# Keep public API classes used by consumer apps
-keep class com.llmengine.LlmEngine { *; }
-keep interface com.llmengine.LlmSession { *; }
-keep class com.llmengine.EngineParams { *; }
-keep class com.llmengine.GenerationParams { *; }
-keep class com.llmengine.ModelDescriptor { *; }
-keep class com.llmengine.ModelManager { *; }
-keep class com.llmengine.DownloadState { *; }
-keep class com.llmengine.Models { *; }
