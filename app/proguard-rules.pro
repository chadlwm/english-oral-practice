# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep Room entities
-keep class com.example.englishoralpractice.library.data.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
