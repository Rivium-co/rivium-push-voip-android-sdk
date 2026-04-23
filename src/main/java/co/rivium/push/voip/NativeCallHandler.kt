package co.rivium.push.voip

/**
 * Interface for handling VoIP call events natively, BEFORE Flutter is ready.
 *
 * Implement this interface in your Application class to handle call events
 * immediately when the user interacts with the call notification, without
 * waiting for Flutter to initialize.
 *
 * This is useful for:
 * - Launching video call SDKs (Jitsi, Twilio, Agora, etc.) immediately
 * - Making API calls before Flutter is ready
 * - Any native action that should happen instantly when user accepts/declines
 *
 * Example usage:
 * ```kotlin
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *
 *         RiviumPushVoip.setNativeCallHandler(object : NativeCallHandler {
 *             override fun onCallAcceptedNative(context: Context, callData: CallData): Boolean {
 *                 // Launch your video call SDK
 *                 MyVideoSDK.launch(context, callData.payload)
 *                 return true // Handled natively
 *             }
 *
 *             override fun onCallDeclinedNative(context: Context, callData: CallData) {
 *                 // Optionally notify your backend
 *                 MyApi.notifyCallDeclined(callData.callId)
 *             }
 *         })
 *     }
 * }
 * ```
 *
 * Note: Flutter callbacks (set via RiviumPushVoIP.init in Dart) will ALSO be called
 * after the native handler, so Flutter can track call state regardless of whether
 * the native handler processed the call.
 */
interface NativeCallHandler {
    /**
     * Called when user accepts an incoming call.
     *
     * This is called BEFORE Flutter receives the event, allowing you to
     * launch video call SDKs or perform other native actions immediately.
     *
     * @param context Application context
     * @param callData The call information including callId, callerName, payload, etc.
     * @return true if you handled the call natively (e.g., launched video SDK),
     *         false to let Flutter handle it entirely
     */
    fun onCallAcceptedNative(context: android.content.Context, callData: CallData): Boolean

    /**
     * Called when user declines an incoming call.
     *
     * @param context Application context
     * @param callData The call information
     */
    fun onCallDeclinedNative(context: android.content.Context, callData: CallData) {}

    /**
     * Called when a call times out (user didn't answer).
     *
     * @param context Application context
     * @param callData The call information
     */
    fun onCallTimeoutNative(context: android.content.Context, callData: CallData) {}
}
