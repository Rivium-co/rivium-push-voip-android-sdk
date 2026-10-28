package co.rivium.push.voip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manifest-registered BroadcastReceiver that intercepts Rivium Push messages.
 * This receiver is declared in AndroidManifest.xml so it works even when
 * the app is in background (Flutter engine not running).
 *
 * When a VoIP call message is detected, it initializes the VoIP SDK and
 * shows the incoming call UI.
 */
class RiviumPushMessageReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RiviumPushMessageReceiver"
        private const val RIVIUM_PUSH_ACTION_MESSAGE = "co.rivium.push.MESSAGE"
        private const val RIVIUM_PUSH_EXTRA_DATA = "data"
        private val gson = Gson()
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != RIVIUM_PUSH_ACTION_MESSAGE) return

        Log.d(TAG, "Received Rivium Push message broadcast")

        val payload = intent.getStringExtra(RIVIUM_PUSH_EXTRA_DATA)
        if (payload.isNullOrEmpty()) {
            Log.d(TAG, "No payload in broadcast")
            return
        }


        try {
            // Parse JSON payload
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val data: Map<String, Any?> = gson.fromJson(payload, type)

            // Check if this is a VoIP call
            val messageType = data["type"] as? String
            val nestedData = data["data"] as? Map<*, *>
            val nestedType = nestedData?.get("type") as? String

            if (messageType == "voip_call" || nestedType == "voip_call") {
                Log.d(TAG, "VoIP call detected - initializing SDK and showing call UI")
                handleVoipCall(context, data)
            } else {
                Log.d(TAG, "Not a VoIP call message (type: $messageType)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun handleVoipCall(context: Context, data: Map<String, Any?>) {
        try {
            // Initialize VoIP SDK if not already initialized
            // This ensures SDK works even when app was killed
            if (RiviumPushVoip.getConfig() == null) {
                Log.d(TAG, "VoIP SDK not initialized - initializing with defaults")
                RiviumPushVoip.initialize(context.applicationContext, VoipConfig())
            }

            // Handle the push message
            val handled = RiviumPushVoip.handlePushMessage(data)
            Log.d(TAG, "VoIP handlePushMessage returned: $handled")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling VoIP call: ${e.message}")
            e.printStackTrace()
        }
    }
}
