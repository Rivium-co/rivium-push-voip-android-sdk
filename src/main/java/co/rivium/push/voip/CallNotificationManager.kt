package co.rivium.push.voip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages incoming call notifications with CallStyle support for Android 12+
 */
object CallNotificationManager {
    private const val TAG = "CallNotificationMgr"
    private const val CHANNEL_ID = "rivium_push_voip_incoming_call"
    private const val CHANNEL_NAME = "Incoming Calls"

    private val activeNotifications = mutableMapOf<String, Int>()
    private val timeoutRunnables = mutableMapOf<String, Runnable>()
    private var notificationIdCounter = 10000

    /**
     * Show incoming call notification
     */
    fun showIncomingCallNotification(context: Context, callData: CallData, config: VoipConfig?) {
        Log.d(TAG, "Showing incoming call notification for: ${callData.callId}")

        createNotificationChannel(context, config)

        val notificationId = notificationIdCounter++
        activeNotifications[callData.callId] = notificationId

        // Load avatar image asynchronously, then show notification
        val avatarUrl = callData.callerAvatar
        if (!avatarUrl.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val imageLoader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .allowHardware(false) // Required for getting bitmap
                        .build()

                    val result = imageLoader.execute(request)
                    val bitmap = if (result is SuccessResult) {
                        (result.drawable as? BitmapDrawable)?.bitmap
                    } else null

                    // Show notification on main thread using cached handler
                    VoipExecutors.executeMain {
                        val notification = buildCallNotification(context, callData, config, notificationId, bitmap)
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(notificationId, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load avatar: ${e.message}")
                    // Show notification without avatar
                    VoipExecutors.executeMain {
                        val notification = buildCallNotification(context, callData, config, notificationId, null)
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(notificationId, notification)
                    }
                }
            }
        } else {
            // No avatar URL, show notification immediately
            val notification = buildCallNotification(context, callData, config, notificationId, null)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notification)
        }

        // Set up timeout using cached handler
        val timeoutMs = (config?.timeoutSeconds ?: 30) * 1000L
        val timeoutRunnable = Runnable {
            handleCallTimeout(context, callData, config)
        }
        timeoutRunnables[callData.callId] = timeoutRunnable
        VoipExecutors.executeMainDelayed(timeoutMs, timeoutRunnable)
    }

    private fun createNotificationChannel(context: Context, config: VoipConfig?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) {
                return
            }

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call notifications"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC

                val ringtoneUri = config?.ringtoneUri?.let { Uri.parse(it) }
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                setSound(
                    ringtoneUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildCallNotification(
        context: Context,
        callData: CallData,
        config: VoipConfig?,
        notificationId: Int,
        avatarBitmap: Bitmap? = null
    ): Notification {
        val callDataJson = callData.toJson()

        val acceptPendingIntent: PendingIntent
        val declinePendingIntent: PendingIntent

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Use Activity intents for CallStyle
            Log.d(TAG, "Building CallStyle notification with Activity intents")

            val acceptIntent = Intent(context, CallTrampolineActivity::class.java).apply {
                action = "co.rivium.push.voip.ACTION_ACCEPT_CALL"
                putExtra("rivium_push_voip_call_accepted", true)
                putExtra("rivium_push_voip_call_data", callDataJson)
                putExtra(CallActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            acceptPendingIntent = PendingIntent.getActivity(
                context,
                notificationId * 10 + 1,
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val declineIntent = Intent(context, DeclineCallActivity::class.java).apply {
                action = "co.rivium.push.voip.ACTION_DECLINE_CALL"
                putExtra(CallActionReceiver.EXTRA_CALL_DATA, callDataJson)
                putExtra(CallActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            declinePendingIntent = PendingIntent.getActivity(
                context,
                notificationId * 10 + 2,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            // Pre-Android 12: Use Broadcast intents
            Log.d(TAG, "Building notification with Broadcast intents")

            val acceptIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_ACCEPT
                putExtra(CallActionReceiver.EXTRA_CALL_DATA, callDataJson)
                putExtra(CallActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            acceptPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 1,
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_DECLINE
                putExtra(CallActionReceiver.EXTRA_CALL_DATA, callDataJson)
                putExtra(CallActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            declinePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 2,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Full screen intent
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_CALL_DATA, callDataJson)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId * 10 + 3,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Content intent
        val contentIntent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_CALL_DATA, callDataJson)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId * 10 + 4,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeText = if (callData.isVideoCall) "Video Call" else "Voice Call"

        // Create caller Person for CallStyle notification
        val callerBuilder = Person.Builder()
            .setName(callData.callerName)
            .setImportant(true)

        // Add avatar icon if available
        if (avatarBitmap != null) {
            callerBuilder.setIcon(IconCompat.createWithBitmap(avatarBitmap))
        }

        val caller = callerBuilder.build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(getAppIcon(context))
            .setContentTitle(callData.callerName)
            .setContentText("Incoming $callTypeText")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(contentPendingIntent)

        // Add large icon (avatar) if available
        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap)
        }

        // Use CallStyle for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    acceptPendingIntent
                )
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Decline",
                declinePendingIntent
            )
            builder.addAction(
                android.R.drawable.ic_menu_call,
                "Accept",
                acceptPendingIntent
            )
        }

        return builder.build()
    }

    private fun getAppIcon(context: Context): Int {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            appInfo.icon
        } catch (e: Exception) {
            android.R.drawable.ic_menu_call
        }
    }

    /**
     * Cancel notification for a call
     */
    fun cancelNotification(context: Context, callId: String) {
        Log.d(TAG, "Canceling notification for: $callId")

        // Cancel timeout runnable using cached handler
        timeoutRunnables[callId]?.let { runnable ->
            VoipExecutors.removeCallbacks(runnable)
        }
        timeoutRunnables.remove(callId)

        // Cancel notification
        val notificationId = activeNotifications[callId]
        if (notificationId != null) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            activeNotifications.remove(callId)
        }
    }

    private fun handleCallTimeout(context: Context, callData: CallData, config: VoipConfig?) {
        Log.d(TAG, "Call timeout for: ${callData.callId}")

        cancelNotification(context, callData.callId)
        RiviumPushVoip.activeCall = null

        // Show missed call notification if configured
        if (config?.showMissedCallNotification == true) {
            showMissedCallNotification(context, callData, config)
        }

        // Notify via callback
        RiviumPushVoip.notifyCallTimeout(callData)

        // Also send broadcast for Flutter/RN
        val intent = Intent(RiviumPushVoip.ACTION_CALL_TIMEOUT).apply {
            putExtra(RiviumPushVoip.EXTRA_CALL_DATA, callData.toJson())
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun showMissedCallNotification(context: Context, callData: CallData, config: VoipConfig) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "rivium_push_voip_missed_call",
                "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "rivium_push_voip_missed_call")
            .setSmallIcon(getAppIcon(context))
            .setContentTitle("Missed Call")
            .setContentText(callData.callerName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(callData.callId.hashCode(), notification)
    }
}
