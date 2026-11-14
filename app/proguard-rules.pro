# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entities
-keep class com.ggmausepro.mapper.data.entity.** { *; }

# Keep Gson serialized classes
-keep class com.ggmausepro.mapper.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep accessibility service
-keep class com.ggmausepro.mapper.service.InputMapperService { *; }
-keep class com.ggmausepro.mapper.service.OverlayService { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
