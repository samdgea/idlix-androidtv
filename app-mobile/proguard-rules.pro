# Proguard rules for Idlix Mobile

-keep class com.idlix.data.model.** { *; }
-keep class com.idlix.mobile.download.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# Coil
-keep class coil.** { *; }
