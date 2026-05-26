# Mapsforge
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**

# GPX Parser
-keep class io.ticofab.androidgpxparser.** { *; }
-dontwarn io.ticofab.androidgpxparser.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# AndroidSVG
-keep class com.caverock.androidsvg.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep data classes
-keepclassmembers class com.osm.wear.domain.model.** { *; }
