# Rivium Push VoIP SDK ProGuard Rules

# Keep all public API
-keep public class co.rivium.push.voip.** { public *; }

# Keep CallData for serialization
-keep class co.rivium.push.voip.CallData { *; }
-keep class co.rivium.push.voip.VoipConfig { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
