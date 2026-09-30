package com.localpush.debug

import android.content.Context

/**
 * Optional hook so the notification is built by **your production code**
 * (e.g. the `NotificationFactory` used by your `FirebaseMessagingService`) instead of the default builder.
 *
 * Implement this interface in `app/src/debug/...` and register it in `app/src/debug/AndroidManifest.xml`:
 * ```xml
 * <application>
 *     <meta-data
 *         android:name="com.localpush.debug.HANDLER"
 *         android:value="com.myapp.debug.AppLocalPushHandler" />
 * </application>
 * ```
 * Accepts a class with an empty constructor or a Kotlin `object`.
 *
 * Runs on the main thread inside `BroadcastReceiver.onReceive` (~10 s limit):
 * do not perform heavy I/O here.
 */
fun interface LocalPushHandler {
    /**
     * @return `true` if the app showed/handled the notification; `false` to fall back to the default builder.
     */
    fun handle(context: Context, payload: LocalPushPayload): Boolean
}
