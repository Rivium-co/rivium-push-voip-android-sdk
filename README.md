# RiviumPush VoIP SDK for Android

Native incoming call UI for Android with full-screen call notifications. Add real-time calling to your app with Rivium Push.

## Features

- Full-screen incoming call activity with animations
- CallStyle notification (Android 12+) with fallback for older versions
- Accept/Decline actions from notification and lock screen
- Caller name, avatar, and call type (audio/video) display
- Call timeout with missed call notification
- Works when app is killed (via push broadcast receiver)
- Customizable payload key mapping
- Native call handler for cold-start scenarios

## Requirements

- Android 5.0+ (API 21)
- Kotlin
- [Rivium Push Android SDK](https://github.com/Rivium-co/rivium-push-android-sdk) for push delivery

## Installation

### Maven Central

```kotlin
dependencies {
    implementation("co.rivium:rivium-push-android:0.1.1")  // Push SDK
    implementation("co.rivium:rivium-push-voip:0.1.0")     // VoIP SDK
}
```

## Quick Start

### 1. Initialize

```kotlin
import co.rivium.push.voip.RiviumPushVoip
import co.rivium.push.voip.VoipConfig

// In Application.onCreate() or Activity
val config = VoipConfig(
    appName = "MyApp",
    timeoutSeconds = 30
)
RiviumPushVoip.initialize(this, config)
```

### 2. Set Callback

```kotlin
import co.rivium.push.voip.VoipCallback
import co.rivium.push.voip.CallData

RiviumPushVoip.setCallback(object : VoipCallback {
    override fun onCallAccepted(callData: CallData) {
        // User accepted — connect to your calling service (Jitsi, WebRTC, etc.)
        Log.d("VoIP", "Accepted: ${callData.callerName}")
    }

    override fun onCallDeclined(callData: CallData) {
        // User declined
        Log.d("VoIP", "Declined: ${callData.callerName}")
    }

    override fun onCallTimeout(callData: CallData) {
        // No answer
        Log.d("VoIP", "Timeout: ${callData.callerName}")
    }
})
```

### 3. Trigger VoIP Call

VoIP calls are triggered automatically when a push message with `"type": "voip_call"` is received:

```json
{
    "title": "Incoming Call",
    "body": "John Doe is calling",
    "data": {
        "type": "voip_call",
        "callerName": "John Doe",
        "callerId": "user_456",
        "callerAvatar": "https://example.com/avatar.jpg",
        "callType": "video"
    }
}
```

The VoIP SDK includes a `RiviumPushMessageReceiver` that automatically intercepts push messages with `type: "voip_call"` and shows the incoming call screen.

### 4. Manual Call (Optional)

```kotlin
val callData = CallData(
    callId = "call_123",
    callerName = "John Doe",
    callerId = "user_456",
    callerAvatar = "https://example.com/avatar.jpg",
    callType = "video"
)
RiviumPushVoip.showIncomingCall(callData)
```

### 5. Manage Calls

```kotlin
// End active call
RiviumPushVoip.endCall("call_123")

// Get active call
val activeCall = RiviumPushVoip.activeCall

// Get initial call (cold start)
val initialCall = RiviumPushVoip.getInitialCall()
```

## Configuration

```kotlin
val config = VoipConfig(
    appName = "MyApp",              // App name in notifications
    timeoutSeconds = 30,            // Call ring timeout (default: 30)
    showMissedCallNotification = true, // Show missed call (default: true)
    ringtoneUri = null,             // Custom ringtone URI (null = default)
    callerNameKey = "callerName",   // Push payload key for caller name
    callerIdKey = "callerId",       // Push payload key for caller ID
    callerAvatarKey = "callerAvatar", // Push payload key for avatar URL
    callTypeKey = "callType"        // Push payload key for call type
)
```

## Native Call Handler (Cold Start)

For handling calls before the app is fully loaded (e.g., launching Jitsi from lock screen):

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RiviumPushVoip.setNativeCallHandler(object : NativeCallHandler {
            override fun onCallAcceptedNative(callData: CallData): Boolean {
                // Launch your video call SDK natively
                JitsiMeet.launch(callData)
                return true // Handled natively
            }
        })
    }
}
```

## Permissions

The SDK declares these permissions in its manifest (merged automatically):

```xml
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

For full-screen incoming call UI on Android 14+, add to your app's manifest:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

## Used With

This SDK works alongside [Rivium Push Android SDK](https://github.com/Rivium-co/rivium-push-android-sdk) for push delivery. Rivium Push handles message delivery, this SDK handles the incoming call UI.

The Push SDK works independently without VoIP. VoIP is only needed for apps with real calling features.

## Links

- [Rivium Push](https://rivium.co/cloud/rivium-push) - Learn more about Rivium Push
- [Documentation](https://rivium.co/cloud/rivium-push/docs/voip) - VoIP documentation
- [Rivium Console](https://console.rivium.co) - Manage your push notifications

## License

MIT License - see [LICENSE](LICENSE) for details.
