# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Error Prone / Tink Annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Room
-keep class * extends androidx.room.RoomDatabase

# Moshi (Since it's using Moshi, not Gson)
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# Strip Logging (Log.v, Log.d, Log.i, Log.w, Log.e)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
