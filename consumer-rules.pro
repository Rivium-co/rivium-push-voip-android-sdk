# Consumer ProGuard rules for Rivium Push VoIP SDK
# These rules are applied to apps using this library

# Keep VoIP SDK classes
-keep class co.rivium.push.voip.** { *; }

# Coil image loading library
-dontwarn coil.**
-keep class coil.** { *; }
-dontwarn coil.bitmap.**

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
