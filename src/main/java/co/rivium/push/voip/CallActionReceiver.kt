package co.rivium.push.voip

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Handles accept/decline notification actions (for pre-Android 12)
 */
class CallActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CallActionReceiver"
        const val ACTION_ACCEPT = "co.rivium.push.voip.ACTION_ACCEPT"
        const val ACTION_DECLINE = "co.rivium.push.voip.ACTION_DECLINE"
        const val EXTRA_CALL_DATA = "call_data"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val callDataJson = intent.getStringExtra(EXTRA_CALL_DATA)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val callData = callDataJson?.let { CallData.fromJson(it) }

        Log.d(TAG, "Action received: ${intent.action}")

        if (callData == null) {
            Log.e(TAG, "Call data is null")
            return
        }

        // Cancel the notification
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }

        // Cancel timeout handler
        CallNotificationManager.cancelNotification(context, callData.callId)

        when (intent.action) {
            ACTION_ACCEPT -> handleAccept(context, callData)
            ACTION_DECLINE -> handleDecline(context, callData)
        }
    }

    private fun handleAccept(context: Context, callData: CallData) {
        Log.d(TAG, "Call accepted from notification action: ${callData.callId}")

        // Clear active call
        RiviumPushVoip.activeCall = null

        // Set initial call so app can retrieve it
        RiviumPushVoip.setInitialCall(callData)

        // On Android 12+, we need to use a trampoline activity
        // The trampoline activity will handle calling notifyCallAccepted and deciding
        // whether to launch MainActivity based on native handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.d(TAG, "Android 12+: Using CallTrampolineActivity to launch app")
            val trampolineIntent = Intent(context, CallTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("rivium_push_voip_call_accepted", true)
                putExtra("rivium_push_voip_call_data", callData.toJson())
            }
            context.startActivity(trampolineIntent)
            // Note: Don't call notifyCallAccepted here - trampoline will do it
        } else {
            // Older Android versions: Check for native handler
            Log.d(TAG, "Pre-Android 12: Checking for native handler")

            val hasNativeHandler = RiviumPushVoip.hasNativeCallHandler()
            val appWasRunning = RiviumPushVoip.hasCallback()
            Log.d(TAG, "Has native call handler: $hasNativeHandler, app was running: $appWasRunning")

            if (hasNativeHandler) {
                if (appWasRunning) {
                    // App is already running: Launch MainActivity FIRST, then Jitsi on top
                    Log.d(TAG, "App running + native handler - launching MainActivity first, then Jitsi")

                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        putExtra("rivium_push_voip_call_accepted", true)
                        putExtra("rivium_push_voip_call_data", callData.toJson())
                        putExtra("rivium_push_voip_launching_native", true)
                    }
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                        Log.d(TAG, "MainActivity launched (will be in background)")
                    }

                    // Small delay to ensure MainActivity is in the stack before launching Jitsi
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val handledNatively = RiviumPushVoip.notifyCallAccepted(callData)
                        Log.d(TAG, "Call handled natively: $handledNatively")

                        val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_ACCEPTED).apply {
                            putExtra(RiviumPushVoip.EXTRA_CALL_DATA, callData.toJson())
                        }
                        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
                    }, 100)
                } else {
                    // Cold start: Launch Jitsi directly
                    Log.d(TAG, "Cold start + native handler - launching Jitsi directly (no MainActivity)")

                    val handledNatively = RiviumPushVoip.notifyCallAccepted(callData)
                    Log.d(TAG, "Call handled natively: $handledNatively")

                    val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_ACCEPTED).apply {
                        putExtra(RiviumPushVoip.EXTRA_CALL_DATA, callData.toJson())
                    }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
                }
            } else {
                // No native handler - just launch MainActivity normally
                Log.d(TAG, "No native handler - launching MainActivity only")

                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("rivium_push_voip_call_accepted", true)
                    putExtra("rivium_push_voip_call_data", callData.toJson())
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }

                RiviumPushVoip.notifyCallAccepted(callData)
                val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_ACCEPTED).apply {
                    putExtra(RiviumPushVoip.EXTRA_CALL_DATA, callData.toJson())
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
            }
        }
    }

    private fun handleDecline(context: Context, callData: CallData) {
        Log.d(TAG, "Call declined: ${callData.callId}")

        // Clear active call
        RiviumPushVoip.activeCall = null

        // Notify via callback
        RiviumPushVoip.notifyCallDeclined(callData)

        // Notify via broadcast
        val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_DECLINED).apply {
            putExtra(RiviumPushVoip.EXTRA_CALL_DATA, callData.toJson())
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
    }
}
