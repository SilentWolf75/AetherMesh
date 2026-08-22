# AetherMesh — keep rules for optional minify / Play release builds.
# Debug builds do not enable minify.

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

-keep class com.silentwolf75.aethermesh.proto.** { *; }

-keep class com.silentwolf75.aethermesh.BuildConfig { *; }

# Compose / ViewModels
-keep class com.silentwolf75.aethermesh.ui.** { *; }
-keep class com.silentwolf75.aethermesh.data.** { *; }
-keep class com.silentwolf75.aethermesh.ble.** { *; }

# Nordic DFU / BLE
-keep class no.nordicsemi.android.** { *; }
-dontwarn no.nordicsemi.android.**

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**


# Tink (via androidx.security:security-crypto) references compile-only
# annotations that are absent at runtime. R8 fails the build on these unless
# they are explicitly ignored; none of them affect behaviour.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.j2objc.annotations.**
