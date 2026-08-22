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

# Tink / crypto transitive annotations (compile-only; not on device)
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn javax.annotation.concurrent.GuardedBy
