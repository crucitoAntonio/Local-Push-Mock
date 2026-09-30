package com.localpush.debug

import android.content.Intent
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

/**
 * Message contract sent by the plugin (protocol v1).
 *
 * Transport: extra `payload_b64` = Base64(UTF-8(JSON)). Base64 is used so quotes, spaces,
 * emojis or line breaks survive the device shell (`adb shell am broadcast`).
 *
 * ```json
 * {
 *   "v": 1,
 *   "id": 42,                          // optional: same id = replace the notification
 *   "title": "Order on the way",
 *   "body": "Arriving in 10 min",
 *   "channelId": "orders",             // optional: when missing, FCM behavior is mimicked
 *   "channelName": "Orders",           // optional: only used when the channel is created
 *   "importance": "HIGH",              // MIN | LOW | DEFAULT | HIGH | MAX
 *   "deepLink": "myapp://orders/42",   // optional
 *   "smallIcon": "ic_stat_notification", // optional: drawable name
 *   "data": { "orderId": "42" }        // same as RemoteMessage.data: Map<String, String>
 * }
 * ```
 */
data class LocalPushPayload(
    val id: Int?,
    val title: String,
    val body: String,
    val channelId: String?,
    val channelName: String?,
    val importance: Importance?,
    val deepLink: String?,
    val smallIcon: String?,
    val data: Map<String, String>,
) {

    enum class Importance(val channelImportance: Int, val legacyPriority: Int) {
        MIN(NotificationManagerCompat.IMPORTANCE_MIN, NotificationCompat.PRIORITY_MIN),
        LOW(NotificationManagerCompat.IMPORTANCE_LOW, NotificationCompat.PRIORITY_LOW),
        DEFAULT(NotificationManagerCompat.IMPORTANCE_DEFAULT, NotificationCompat.PRIORITY_DEFAULT),
        HIGH(NotificationManagerCompat.IMPORTANCE_HIGH, NotificationCompat.PRIORITY_HIGH),
        MAX(NotificationManagerCompat.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX);

        companion object {
            fun parse(value: String?): Importance? =
                value?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let { name ->
                    entries.firstOrNull { it.name == name }
                        ?: throw LocalPushException("invalid importance: '$value'")
                }
        }
    }

    companion object {
        const val EXTRA_PAYLOAD_B64 = "payload_b64"
        private const val DATA_EXTRA_PREFIX = "data."

        fun fromIntent(intent: Intent): LocalPushPayload {
            val b64 = intent.getStringExtra(EXTRA_PAYLOAD_B64)
            val payload = if (b64 != null) {
                val json = try {
                    String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                } catch (e: IllegalArgumentException) {
                    throw LocalPushException("payload_b64 is not valid Base64")
                }
                fromJson(json)
            } else {
                fromPlainExtras(intent)
            }
            if (payload.title.isBlank() && payload.body.isBlank()) {
                throw LocalPushException("title and body are both empty")
            }
            return payload
        }

        fun fromJson(json: String): LocalPushPayload {
            val o = try {
                JSONObject(json)
            } catch (e: org.json.JSONException) {
                throw LocalPushException("invalid JSON: ${e.message}")
            }
            val dataObj = o.optJSONObject("data")
            val data = buildMap {
                dataObj?.keys()?.forEach { key ->
                    if (!dataObj.isNull(key)) put(key, dataObj.get(key).toString())
                }
            }
            return LocalPushPayload(
                id = if (o.has("id") && !o.isNull("id")) o.getInt("id") else null,
                title = o.optString("title"),
                body = o.optString("body"),
                channelId = o.stringOrNull("channelId"),
                channelName = o.stringOrNull("channelName"),
                importance = Importance.parse(o.stringOrNull("importance")),
                deepLink = o.stringOrNull("deepLink"),
                smallIcon = o.stringOrNull("smallIcon"),
                data = data,
            )
        }

        /**
         * Manual mode without Base64, handy for quick tests from a terminal:
         * `adb shell am broadcast ... --es title Hello --es body World --es data.orderId 42`
         */
        private fun fromPlainExtras(intent: Intent): LocalPushPayload {
            val data = buildMap {
                intent.extras?.let { extras ->
                    extras.keySet().filter { it.startsWith(DATA_EXTRA_PREFIX) }.forEach { key ->
                        @Suppress("DEPRECATION")
                        extras.get(key)?.let { put(key.removePrefix(DATA_EXTRA_PREFIX), it.toString()) }
                    }
                }
            }
            return LocalPushPayload(
                id = intent.getStringExtra("id")?.toIntOrNull(),
                title = intent.getStringExtra("title").orEmpty(),
                body = intent.getStringExtra("body").orEmpty(),
                channelId = intent.getStringExtra("channelId")?.ifBlank { null },
                channelName = intent.getStringExtra("channelName")?.ifBlank { null },
                importance = Importance.parse(intent.getStringExtra("importance")),
                deepLink = intent.getStringExtra("deepLink")?.ifBlank { null },
                smallIcon = intent.getStringExtra("smallIcon")?.ifBlank { null },
                data = data,
            )
        }

        private fun JSONObject.stringOrNull(key: String): String? =
            if (has(key) && !isNull(key)) getString(key).ifBlank { null } else null
    }
}

class LocalPushException(message: String) : RuntimeException(message)
