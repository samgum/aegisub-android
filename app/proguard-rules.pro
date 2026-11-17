# Preserve source file names and line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable

# Keep Kotlin metadata & coroutines
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# Keep Room database entities and DAOs
-keep class io.github.samgum.aegisub.data.**Entity { *; }
