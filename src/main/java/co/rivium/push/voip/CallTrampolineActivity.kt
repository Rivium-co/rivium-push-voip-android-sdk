package co.rivium.push.voip

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Trampoline activity for Android 12+ call acceptance.
 * Works around restrictions on starting activities from BroadcastReceivers.
 */
class CallTrampolineActivity : Activity() {
    companion object {
        private const val TAG = "CallTrampolineActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "CallTrampolineActivity created")

        val callAccepted = intent.getBooleanExtra("rivium_push_voip_call_accepted", false)
        val callDataJson = intent.getStringExtra("rivium_push_voip_call_data")
        val notificationId = intent.getIntExtra(CallActionReceiver.EXTRA_NOTIFICATION_ID, -1)

        Log.d(TAG, "Call accepted: $callAccepted, callData: $callDataJson, notificationId: $notificationId")

        // Cancel the notification
        if (notificationId != -1) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            Log.d(TAG, "Notification cancelled: $notificationId")
        }

        // Parse call data and set it as initial call
        val callData = callDataJson?.let { CallData.fromJson(it) }
        if (callData != null) {
            CallNotificationManager.cancelNotification(this, callData.callId)
            RiviumPushVoip.activeCall = null
            RiviumPushVoip.setInitialCall(callData)

            // Check if a NativeCallHandler is registered and would handle this call
            val hasNativeHandler = RiviumPushVoip.hasNativeCallHandler()
            // Check if app was already running (Flutter callback is set when app is running)
            val appWasRunning = RiviumPushVoip.hasCallback()
            Log.d(TAG, "Has native call handler: $hasNativeHandler, app was running: $appWasRunning")

            if (hasNativeHandler) {
                if (appWasRunning) {
                    // App is already running: Launch MainActivity FIRST (in background),
                    // then notify which launches Jitsi on top. This ensures MainActivity is in the
                    // back stack when user minimizes Jitsi.
                    Log.d(TAG, "App running + native handler - launching MainActivity first, then Jitsi")

                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        // Use flags that bring MainActivity to front without animation, keeping it behind
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        putExtra("rivium_push_voip_call_accepted", callAccepted)
                        putExtra("rivium_push_voip_call_data", callDataJson)
                        putExtra("rivium_push_voip_launching_native", true) // Flag to indicate native handler will take over
                    }
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                        Log.d(TAG, "MainActivity launched (will be in background)")
                    }

                    // Small delay to ensure MainActivity is in the stack before launching Jitsi
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // Now notify which will launch Jitsi on top
                        val handledNatively = RiviumPushVoip.notifyCallAccepted(callData)
                        Log.d(TAG, "Call handled natively: $handledNatively")

                        val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_ACCEPTED).apply {
                            putExtra(RiviumPushVoip.EXTRA_CALL_DATA, callDataJson)
                        }
                        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
                        Log.d(TAG, "Broadcast sent for call accepted: ${callData.callId}")
                    }, 100)
                } else {
                    // Cold start (app was killed): Launch Jitsi directly, MainActivity will be
                    // launched after Jitsi ends by the native handler (NativeJitsiMeetPlugin)
                    Log.d(TAG, "Cold start + native handler - launching Jitsi directly (no MainActivity)")

                    val handledNatively = RiviumPushVoip.notifyCallAccepted(callData)
                    Log.d(TAG, "Call handled natively: $handledNatively")

                    val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_ACCEPTED).apply {
                        putExtra(RiviumPushVoip.EXTRA_CALL_DATA, callDataJson)
                    }
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
                    Log.d(TAG, "Broadcast sent for call accepted: ${callData.callId}")
                }
            } else {
                // No native handler - just launch MainActivity normally
                Log.d(TAG, "No native handler - launching MainActivity only")

                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("rivium_push_voip_call_accepted", callAccepted)
                    putExtra("rivium_push_voip_call_data", callDataJson)
                }
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    Log.d(TAG, "MainActivity launched")
                }

                // Notify via callback and broadcast
                RiviumPushVoip.notifyCallAccepted(callData)
                val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_ACCEPTED).apply {
                    putExtra(RiviumPushVoip.EXTRA_CALL_DATA, callDataJson)
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
                Log.d(TAG, "Broadcast sent for call accepted: ${callData.callId}")
            }
        }

        finish()
    }
}
