package co.rivium.push.voip

/**
 * Configuration for VoIP call handling
 */
data class VoipConfig(
    /** App name shown in notifications */
    val appName: String = "App",

    /** Custom ringtone URI (null for default) */
    val ringtoneUri: String? = null,

    /** Call timeout in seconds */
    val timeoutSeconds: Int = 30,

    /** Whether to show missed call notification on timeout */
    val showMissedCallNotification: Boolean = true,

    // Keys for extracting call data from push payload
    /** Key for caller name in push payload */
    val callerNameKey: String = "callerName",

    /** Key for caller ID in push payload */
    val callerIdKey: String = "callerId",

    /** Key for caller avatar URL in push payload */
    val callerAvatarKey: String = "callerAvatar",

    /** Key for call type (audio/video) in push payload */
    val callTypeKey: String = "callType",

    /** Key for the call ID in push payload ("callId" and "uuid" are tried after it) */
    val callIdKey: String = "callId"
) {
    companion object {
        /**
         * Create config from a map (for Flutter/RN bridge)
         */
        fun fromMap(map: Map<*, *>): VoipConfig {
            return VoipConfig(
                appName = map["appName"] as? String ?: "App",
                ringtoneUri = map["ringtoneUri"] as? String,
                timeoutSeconds = (map["timeoutSeconds"] as? Number)?.toInt() ?: 30,
                showMissedCallNotification = map["showMissedCallNotification"] as? Boolean ?: true,
                callerNameKey = map["callerNameKey"] as? String ?: "callerName",
                callerIdKey = map["callerIdKey"] as? String ?: "callerId",
                callerAvatarKey = map["callerAvatarKey"] as? String ?: "callerAvatar",
                callTypeKey = map["callTypeKey"] as? String ?: "callType",
                callIdKey = map["callIdKey"] as? String ?: "callId"
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "appName" to appName,
        "ringtoneUri" to ringtoneUri,
        "timeoutSeconds" to timeoutSeconds,
        "showMissedCallNotification" to showMissedCallNotification,
        "callerNameKey" to callerNameKey,
        "callerIdKey" to callerIdKey,
        "callerAvatarKey" to callerAvatarKey,
        "callTypeKey" to callTypeKey,
        "callIdKey" to callIdKey
    )
}
