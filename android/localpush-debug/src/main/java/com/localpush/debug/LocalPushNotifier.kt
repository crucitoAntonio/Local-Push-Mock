package com.localpush.debug

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.random.Random

/**
 * Default builder. Replicates what the FCM SDK does when it displays a `notification` message
 * while the app is in the background: default icon/color/channel from the Firebase meta-data, and a tap
 * that opens the launcher activity (or the deep link) with the `data` payload copied as Intent extras.
 */
internal object LocalPushNotifier {

    private const val FCM_DEFAULT_ICON_META = "com.google.firebase.messaging.default_notification_icon"
    private const val FCM_DEFAULT_COLOR_META = "com.google.firebase.messaging.default_notification_color"

    /** Extra added to the tap Intent so the app can tell a local push apart from a real one. */
    const val EXTRA_FROM_LOCAL_PUSH = "com.localpush.debug.FROM_LOCAL_PUSH"

    data class Posted(val id: Int, val channelId: String, val warnings: List<String>)

    @SuppressLint("MissingPermission") // checked in ensureCanPost()
    fun post(context: Context, payload: LocalPushPayload, appMeta: Bundle?): Posted {
        ensureCanPost(context)

        val warnings = mutableListOf<String>()
        val channel = LocalPushChannels.ensure(context, payload, appMeta)
        channel.warning?.let(warnings::add)

        val id = payload.id ?: Random.nextInt(1, Int.MAX_VALUE)
        val title = payload.title.ifBlank { context.applicationInfo.loadLabel(context.packageManager) }

        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(resolveSmallIcon(context, payload, appMeta, warnings))
            .setContentTitle(title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setPriority((payload.importance ?: LocalPushPayload.Importance.DEFAULT).legacyPriority)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, payload, id, warnings))

        appMeta?.getInt(FCM_DEFAULT_COLOR_META)?.takeIf { it != 0 }?.let { colorRes ->
            builder.setColor(ContextCompat.getColor(context, colorRes))
        }

        NotificationManagerCompat.from(context).notify(id, builder.build())
        return Posted(id, channel.id, warnings)
    }

    private fun ensureCanPost(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw LocalPushException("POST_NOTIFICATIONS not granted (use 'Grant POST_NOTIFICATIONS' in the plugin)")
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            throw LocalPushException("notifications are disabled for the app in Settings")
        }
    }

    private fun resolveSmallIcon(
        context: Context,
        payload: LocalPushPayload,
        appMeta: Bundle?,
        warnings: MutableList<String>,
    ): Int {
        payload.smallIcon?.let { name ->
            @SuppressLint("DiscouragedApi")
            val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (resId != 0) return resId
            warnings += "drawable '$name' not found; using the default icon"
        }
        return appMeta?.getInt(FCM_DEFAULT_ICON_META)?.takeIf { it != 0 }
            ?: context.applicationInfo.icon
    }

    private fun buildContentIntent(
        context: Context,
        payload: LocalPushPayload,
        requestCode: Int,
        warnings: MutableList<String>,
    ): PendingIntent {
        val pm = context.packageManager
        val intent = payload.deepLink?.let { link ->
            Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(context.packageName).also {
                if (it.resolveActivity(pm) == null) warnings += "no Activity in the app handles '$link'"
            }
        } ?: pm.getLaunchIntentForPackage(context.packageName)
        ?: Intent().setPackage(context.packageName)

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        payload.data.forEach { (key, value) -> intent.putExtra(key, value) }
        intent.putExtra(EXTRA_FROM_LOCAL_PUSH, true)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }
}
