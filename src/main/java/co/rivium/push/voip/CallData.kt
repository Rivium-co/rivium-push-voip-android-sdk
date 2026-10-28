package co.rivium.push.voip

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Data model for VoIP call information
 */
data class CallData(
    @SerializedName("callId")
    val callId: String,

    @SerializedName("callerName")
    val callerName: String,

    @SerializedName("callerId")
    val callerId: String? = null,

    @SerializedName("callerAvatar")
    val callerAvatar: String? = null,

    @SerializedName("callType")
    val callType: String = "audio",

    @SerializedName("payload")
    val payload: Map<String, Any?>? = null,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private val gson = Gson()

        /**
         * Parse CallData from JSON string
         */
        fun fromJson(json: String): CallData? {
            return try {
                gson.fromJson(json, CallData::class.java)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Create CallData from a map
         */
        fun fromMap(map: Map<*, *>): CallData {
            return CallData(
                callId = map["callId"] as? String ?: java.util.UUID.randomUUID().toString(),
                callerName = map["callerName"] as? String ?: "Unknown",
                callerId = map["callerId"] as? String,
                callerAvatar = map["callerAvatar"] as? String,
                callType = map["callType"] as? String ?: "audio",
                payload = (map["payload"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value },
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }

        /**
         * Create CallData from push notification payload
         * Handles both flat payloads and nested "data" structure
         */
        fun fromPushPayload(payload: Map<String, Any?>, config: VoipConfig?): CallData {
            val safeConfig = config ?: VoipConfig()

            // Extract data from nested "data" object if present, otherwise use top-level
            // Handle various Map types (LinkedTreeMap from Gson, HashMap, etc.)
            val nested = payload["data"]
            val dataMap: Map<String, Any?> = when (nested) {
                is Map<*, *> -> nested.mapKeys { it.key?.toString() ?: "" }
                    .mapValues { it.value }
                    .filterKeys { it.isNotEmpty() }
                else -> emptyMap()
            }

            // Use nested data if available, fallback to top-level payload
            val effectiveDataMap = dataMap.ifEmpty { payload }

            // Also check top-level for backwards compatibility
            fun getValue(key: String): Any? = effectiveDataMap[key] ?: payload[key]

            // Call ID: the configured key, then "callId", then "uuid"
            val resolvedCallId = getValue(safeConfig.callIdKey) as? String
                ?: getValue("callId") as? String
                ?: getValue("uuid") as? String
                ?: java.util.UUID.randomUUID().toString()

            return CallData(
                callId = resolvedCallId,
                callerName = getValue(safeConfig.callerNameKey) as? String ?: "Unknown Caller",
                callerId = getValue(safeConfig.callerIdKey) as? String,
                callerAvatar = getValue(safeConfig.callerAvatarKey) as? String,
                callType = getValue(safeConfig.callTypeKey) as? String ?: "audio",
                payload = payload,  // Keep full payload for Flutter
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Convert to JSON string
     */
    fun toJson(): String = gson.toJson(this)

    /**
     * Convert to map (for Flutter/RN bridge)
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "callId" to callId,
        "callerName" to callerName,
        "callerId" to callerId,
        "callerAvatar" to callerAvatar,
        "mediaType" to callType.uppercase(),
        "extra" to payload,
        "receivedAt" to timestamp
    )

    /**
     * Check if this is a video call
     * Supports multiple formats: "video", "VIDEO", "Video", "1", "true"
     */
    val isVideoCall: Boolean
        get() = callType.equals("video", ignoreCase = true) ||
                callType == "1" ||
                callType.equals("true", ignoreCase = true)
}
