package co.rivium.push.voip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Main entry point for Rivium Push VoIP SDK
 *
 * Usage:
 * ```kotlin
 * // Initialize
 * RiviumPushVoip.initialize(context, VoipConfig(appName = "MyApp"))
 *
 * // Set callback
 * RiviumPushVoip.setCallback(object : VoipCallback {
 *     override fun onCallAccepted(callData: CallData) { }
 *     override fun onCallDeclined(callData: CallData) { }
 *     override fun onCallTimeout(callData: CallData) { }
 * })
 *
 * // Show incoming call
 * RiviumPushVoip.showIncomingCall(callData)
 * ```
 */
object RiviumPushVoip {
    private const val TAG = "RiviumPushVoip"

    // Broadcast actions (kept for backwards compatibility but deprecated)
    @Deprecated("Use NativeCallHandler instead")
    const val ACTION_CALL_ACCEPTED = "co.rivium.push.voip.CALL_ACCEPTED"
    @Deprecated("Use NativeCallHandler instead")
    const val ACTION_CALL_DECLINED = "co.rivium.push.voip.CALL_DECLINED"
    @Deprecated("Use NativeCallHandler instead")
    const val ACTION_CALL_TIMEOUT = "co.rivium.push.voip.CALL_TIMEOUT"
    const val EXTRA_CALL_DATA = "call_data"

    private var context: Context? = null
    private var config: VoipConfig? = null
    private var callback: VoipCallback? = null
    private var nativeCallHandler: NativeCallHandler? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    // Current active call
    var activeCall: CallData? = null
        internal set

    // Initial call (for app cold start)
    private var initialCall: CallData? = null

    /**
     * Initialize the VoIP SDK
     */
    fun initialize(context: Context, config: VoipConfig = VoipConfig()) {
        this.context = context.applicationContext
        this.config = config
        setupBroadcastReceiver()
        Log.d(TAG, "Initialized with config: $config")
    }

    /**
     * Get current configuration
     */
    fun getConfig(): VoipConfig? = config

    /**
     * Update configuration
     */
    fun updateConfig(config: VoipConfig) {
        this.config = config
        Log.d(TAG, "Config updated: $config")
    }

    /**
     * Set callback for VoIP events (Flutter/RN layer)
     */
    fun setCallback(callback: VoipCallback?) {
        this.callback = callback
        Log.d(TAG, "Callback set: ${callback != null}")

        // Deliver pending initial call if any
        initialCall?.let { call ->
            Log.d(TAG, "Delivering pending initial call: ${call.callId}")
            VoipExecutors.executeMainDelayed(300) {
                initialCall?.let {
                    callback?.onCallAccepted(it)
                    initialCall = null
                }
            }
        }
    }

    /**
     * Set native call handler for handling calls BEFORE Flutter is ready.
     *
     * This is the recommended way for apps to handle VoIP calls natively
     * (e.g., launching Jitsi immediately when user accepts a call from
     * the lock screen before Flutter engine is initialized).
     *
     * Call this in your Application.onCreate() to ensure it's available
     * even when the app is cold-started from a VoIP notification.
     *
     * Example:
     * ```kotlin
     * class MyApplication : Application() {
     *     override fun onCreate() {
     *         super.onCreate()
     *         RiviumPushVoip.setNativeCallHandler(object : NativeCallHandler {
     *             override fun onCallAcceptedNative(callData: CallData): Boolean {
     *                 // Launch your video call SDK natively
     *                 JitsiMeet.launch(callData)
     *                 return true // Handled natively
     *             }
     *         })
     *     }
     * }
     * ```
     */
    fun setNativeCallHandler(handler: NativeCallHandler?) {
        this.nativeCallHandler = handler
        Log.d(TAG, "NativeCallHandler set: ${handler != null}")
    }

    /**
     * Get the current native call handler
     */
    fun getNativeCallHandler(): NativeCallHandler? = nativeCallHandler

    /**
     * Check if a native call handler is registered
     */
    fun hasNativeCallHandler(): Boolean = nativeCallHandler != null

    /**
     * Check if a callback (Flutter/RN layer) is registered.
     * This indicates the app is running (not a cold start).
     */
    fun hasCallback(): Boolean = callback != null

    /**
     * Show incoming call notification/UI
     */
    fun showIncomingCall(callData: CallData) {
        val ctx = context ?: run {
            Log.e(TAG, "SDK not initialized")
            return
        }

        Log.d(TAG, "Showing incoming call: ${callData.callId}")
        activeCall = callData

        VoipExecutors.executeMain {
            CallNotificationManager.showIncomingCallNotification(ctx, callData, config)
        }
    }

    /**
     * End/cancel an active call
     */
    fun endCall(callId: String) {
        val ctx = context ?: return

        Log.d(TAG, "Ending call: $callId")
        if (activeCall?.callId == callId) {
            activeCall = null
        }
        CallNotificationManager.cancelNotification(ctx, callId)
    }

    /**
     * Report call as connected (hides notification)
     */
    fun reportCallConnected(callId: String) {
        val ctx = context ?: return

        Log.d(TAG, "Call connected: $callId")
        CallNotificationManager.cancelNotification(ctx, callId)
    }

    /**
     * Get initial call (if app was launched from accepting a call)
     */
    fun getInitialCall(): CallData? {
        val call = initialCall
        initialCall = null
        return call
    }

    /**
     * Set initial call (called internally when call is accepted before app is ready)
     */
    fun setInitialCall(callData: CallData) {
        Log.d(TAG, "Setting initial call: ${callData.callId}")
        initialCall = callData
    }

    /**
     * Handle push message to check if it's a VoIP call
     */
    fun handlePushMessage(data: Map<String, Any?>): Boolean {
        val messageType = data["type"] as? String
        val nestedData = data["data"] as? Map<*, *>

        if (messageType == "voip_call" || nestedData?.get("type") == "voip_call") {
            Log.d(TAG, "VoIP call detected in push message")
            val callData = CallData.fromPushPayload(data, config)
            showIncomingCall(callData)
            return true
        }
        return false
    }

    private fun setupBroadcastReceiver() {
        val ctx = context ?: return

        broadcastReceiver?.let {
            LocalBroadcastManager.getInstance(ctx).unregisterReceiver(it)
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { handleBroadcast(it) }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_CALL_ACCEPTED)
            addAction(ACTION_CALL_DECLINED)
            addAction(ACTION_CALL_TIMEOUT)
        }
        LocalBroadcastManager.getInstance(ctx).registerReceiver(broadcastReceiver!!, filter)
    }

    private fun handleBroadcast(intent: Intent) {
        val callDataJson = intent.getStringExtra(EXTRA_CALL_DATA)
        val callData = callDataJson?.let { CallData.fromJson(it) }

        when (intent.action) {
            ACTION_CALL_ACCEPTED -> {
                Log.d(TAG, "Call accepted broadcast received")
                callData?.let { callback?.onCallAccepted(it) }
            }
            ACTION_CALL_DECLINED -> {
                Log.d(TAG, "Call declined broadcast received")
                callData?.let { callback?.onCallDeclined(it) }
            }
            ACTION_CALL_TIMEOUT -> {
                Log.d(TAG, "Call timeout broadcast received")
                callData?.let { callback?.onCallTimeout(it) }
            }
        }
    }

    /**
     * Internal: Notify callback of call accepted
     *
     * Order of execution:
     * 1. NativeCallHandler (if set) - for immediate native actions like launching Jitsi
     * 2. VoipCallback (Flutter/RN layer) - for cross-platform handling
     * 3. LocalBroadcast (deprecated) - for backwards compatibility
     *
     * @return true if the call was handled natively (e.g., Jitsi was launched),
     *         meaning the caller should NOT launch MainActivity on top
     */
    internal fun notifyCallAccepted(callData: CallData): Boolean {
        val ctx = context

        // 1. Call native handler first (before Flutter is ready)
        var handledNatively = false
        if (ctx != null && nativeCallHandler != null) {
            try {
                handledNatively = nativeCallHandler!!.onCallAcceptedNative(ctx, callData)
                Log.d(TAG, "NativeCallHandler.onCallAcceptedNative returned: $handledNatively")
            } catch (e: Exception) {
                Log.e(TAG, "Error in NativeCallHandler.onCallAcceptedNative: ${e.message}", e)
            }
        }

        // 2. Always notify Flutter/RN callback (even if handled natively)
        // This allows Flutter to track call state
        VoipExecutors.executeMain {
            callback?.onCallAccepted(callData)
        }

        // 3. Send LocalBroadcast for backwards compatibility (deprecated)
        sendLocalBroadcast(ACTION_CALL_ACCEPTED, callData)

        return handledNatively
    }

    /**
     * Internal: Notify callback of call declined
     */
    internal fun notifyCallDeclined(callData: CallData) {
        val ctx = context

        // 1. Call native handler first
        if (ctx != null && nativeCallHandler != null) {
            try {
                nativeCallHandler!!.onCallDeclinedNative(ctx, callData)
                Log.d(TAG, "NativeCallHandler.onCallDeclinedNative called")
            } catch (e: Exception) {
                Log.e(TAG, "Error in NativeCallHandler.onCallDeclinedNative: ${e.message}", e)
            }
        }

        // 2. Notify Flutter/RN callback
        VoipExecutors.executeMain {
            callback?.onCallDeclined(callData)
        }

        // 3. Send LocalBroadcast for backwards compatibility (deprecated)
        sendLocalBroadcast(ACTION_CALL_DECLINED, callData)
    }

    /**
     * Internal: Notify callback of call timeout
     */
    internal fun notifyCallTimeout(callData: CallData) {
        val ctx = context

        // 1. Call native handler first
        if (ctx != null && nativeCallHandler != null) {
            try {
                nativeCallHandler!!.onCallTimeoutNative(ctx, callData)
                Log.d(TAG, "NativeCallHandler.onCallTimeoutNative called")
            } catch (e: Exception) {
                Log.e(TAG, "Error in NativeCallHandler.onCallTimeoutNative: ${e.message}", e)
            }
        }

        // 2. Notify Flutter/RN callback
        VoipExecutors.executeMain {
            callback?.onCallTimeout(callData)
        }

        // 3. Send LocalBroadcast for backwards compatibility (deprecated)
        sendLocalBroadcast(ACTION_CALL_TIMEOUT, callData)
    }

    /**
     * Send LocalBroadcast for backwards compatibility.
     * @deprecated Apps should use NativeCallHandler instead
     */
    @Suppress("DEPRECATION")
    private fun sendLocalBroadcast(action: String, callData: CallData) {
        val ctx = context ?: return
        val intent = Intent(action).apply {
            putExtra(EXTRA_CALL_DATA, callData.toJson())
        }
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent)
        Log.d(TAG, "LocalBroadcast sent: $action")
    }

    /**
     * Internal: Get application context
     */
    internal fun getContext(): Context? = context
}

/**
 * Callback interface for VoIP events
 */
interface VoipCallback {
    /** Called when user accepts an incoming call */
    fun onCallAccepted(callData: CallData)

    /** Called when user declines an incoming call */
    fun onCallDeclined(callData: CallData)

    /** Called when call times out (not answered) */
    fun onCallTimeout(callData: CallData)

    /** Called on error */
    fun onError(error: String) {}
}
