package co.rivium.push.voip

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Activity for declining calls from notification action buttons on Android 12+.
 * Declines the call, cancels the notification, and finishes immediately.
 */
class DeclineCallActivity : Activity() {
    companion object {
        private const val TAG = "DeclineCallActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "DeclineCallActivity created")

        val callDataJson = intent.getStringExtra(CallActionReceiver.EXTRA_CALL_DATA)
        val notificationId = intent.getIntExtra(CallActionReceiver.EXTRA_NOTIFICATION_ID, -1)

        Log.d(TAG, "Declining call, notificationId: $notificationId")

        // Cancel the notification
        if (notificationId != -1) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            Log.d(TAG, "Notification cancelled: $notificationId")
        }

        // Parse call data
        val callData = callDataJson?.let { CallData.fromJson(it) }
        if (callData != null) {
            CallNotificationManager.cancelNotification(this, callData.callId)
            RiviumPushVoip.activeCall = null

            // Notify
            RiviumPushVoip.notifyCallDeclined(callData)

            val broadcastIntent = Intent(RiviumPushVoip.ACTION_CALL_DECLINED).apply {
                putExtra(RiviumPushVoip.EXTRA_CALL_DATA, callDataJson)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
            Log.d(TAG, "Broadcast sent for call declined: ${callData.callId}")
        }

        finish()
    }
}
