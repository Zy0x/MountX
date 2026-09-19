# Add project specific ProGuard rules here.

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclasseswithmembers class * { @dagger.hilt.* <methods>; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# Retrofit & OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
-keepattributes EnclosingMethod

# libsu
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# Data classes / models (preserve for serialization)
-keep class app.mountx.data.model.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Coil
-keep class coil.** { *; }
-dontwarn coil.**
