# eZansiEdgeAI ProGuard/R8 Rules
# ================================

# Keep Application class
-keep class com.ezansi.app.EzansiApplication { *; }

# Keep Activity classes
-keep class * extends android.app.Activity

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin metadata (required for reflection-based DI if used)
-keep class kotlin.Metadata { *; }

# llama.cpp JNI keep rules (core:llama native bridge)
-keep class com.ezansi.app.core.llama.LlamaAndroid { *; }
-keepclasseswithmembernames class * { native <methods>; }

# TODO: Add ONNX Runtime keep rules when embedding model is integrated
# -keep class ai.onnxruntime.** { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
