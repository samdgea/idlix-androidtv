# Keep Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer();
}
-keepclassmembers class **$companion {
    kotlinx.serialization.KSerializer serializer();
}

# Preserve Data Models for JSON serialization
-keep class com.idlix.data.model.** { *; }
-keep class com.idlix.data.local.StoredCookie { *; }
-keep class com.idlix.data.local.StoredCookie$** { *; }
-keep class com.idlix.tv.data.model.** { *; }

# Debug symbols & stack trace line numbers deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit & OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase

# WebView JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
