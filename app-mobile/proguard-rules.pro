# Proguard rules for Idlix Mobile

-keep class com.idlix.data.model.** { *; }
-keep class com.idlix.data.local.StoredCookie { *; }
-keep class com.idlix.data.local.StoredCookie$** { *; }
-keep class com.idlix.mobile.download.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer();
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer();
}

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# Coil
-keep class coil.** { *; }

# WebView JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
