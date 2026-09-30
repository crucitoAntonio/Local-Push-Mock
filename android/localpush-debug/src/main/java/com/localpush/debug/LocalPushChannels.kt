package com.localpush.debug

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Resolves and creates the channel the same way FCM does:
 * payload.channelId → Firebase `default_notification_channel_id` meta-data → FCM fallback channel.
 */
internal object LocalPushChannels {

    const val FCM_DEFAULT_CHANNEL_META = "com.google.firebase.messaging.default_notification_channel_id"
    const val FCM_FALLBACK_CHANNEL_ID = "fcm_fallback_notification_channel"
    private const val FCM_FALLBACK_CHANNEL_NAME = "Misc"

    data class Resolved(val id: String, val warning: String?)

    fun ensure(context: Context, payload: LocalPushPayload, appMeta: Bundle?): Resolved {
        val id = payload.channelId
            ?: appMeta?.getString(FCM_DEFAULT_CHANNEL_META)?.ifBlank { null }
            ?: FCM_FALLBACK_CHANNEL_ID

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return Resolved(id, null)

        val manager = NotificationManagerCompat.from(context)
        val requested = payload.importance ?: LocalPushPayload.Importance.DEFAULT
        val existing = manager.getNotificationChannelCompat(id)

        if (existing == null) {
            val name = payload.channelName
                ?: if (id == FCM_FALLBACK_CHANNEL_ID) FCM_FALLBACK_CHANNEL_NAME else id
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(id, requested.channelImportance)
                    .setName(name)
                    .build()
            )
            return Resolved(id, null)
        }

        // An existing channel's importance is immutable for the app (only the user can change it).
        val warning = when {
            existing.importance == NotificationManagerCompat.IMPORTANCE_NONE ->
                "channel '$id' is disabled in Settings: it will not be shown"
            payload.importance != null && existing.importance != requested.channelImportance ->
                "channel '$id' already exists with importance ${existing.importance}; Android ignores ${requested.name} " +
                    "(use another channelId or clear the app data)"
            else -> null
        }
        return Resolved(id, warning)
    }
}
