# AetherMesh — keep rules for optional minify / Play release builds.
# Debug builds do not enable minify.

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

-keep class com.example.aethermesh.proto.** { *; }

# Nordic DFU / BLE
-keep class no.nordicsemi.android.** { *; }
-dontwarn no.nordicsemi.android.**

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
