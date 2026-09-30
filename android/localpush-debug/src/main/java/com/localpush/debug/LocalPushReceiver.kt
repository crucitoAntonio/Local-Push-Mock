package com.localpush.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log

/**
 * Entry point. The plugin runs:
 * ```
 * adb -s <serial> shell am broadcast \
 *   -a com.localpush.debug.RECEIVE_LOCAL_PUSH \
 *   -n <applicationId>/com.localpush.debug.LocalPushReceiver \
 *   --include-stopped-packages \
 *   --es payload_b64 <base64>
 * ```
 * `am broadcast` sends the Intent as an ordered broadcast and waits for the result, so the
 * code/data set with [setResult] go back to the plugin as
 * `Broadcast completed: result=<code>, data="<message>"`.
 */
class LocalPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        // Belt and braces: even though the module is debugImplementation, never act in a non-debuggable build.
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            reply(RESULT_ERROR, "app is not debuggable: LocalPush disabled")
            return
        }

        try {
            val payload = LocalPushPayload.fromIntent(intent)
            val appMeta = loadAppMetaData(context)

            val handler = resolveHandler(appMeta)
            if (handler != null && handler.handle(context, payload)) {
                reply(RESULT_HANDLED_BY_APP, "handled by ${handler.javaClass.name}")
                return
            }

            val posted = LocalPushNotifier.post(context, payload, appMeta)
            val warnings = posted.warnings.joinToString(prefix = " | warnings: ", separator = "; ")
                .takeIf { posted.warnings.isNotEmpty() }.orEmpty()
            reply(RESULT_POSTED, "id=${posted.id} channel=${posted.channelId}$warnings")
        } catch (e: LocalPushException) {
            reply(RESULT_ERROR, e.message.orEmpty())
        } catch (e: Exception) {
            Log.e(TAG, "Error processing local push", e)
            reply(RESULT_ERROR, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun reply(code: Int, message: String) {
        val singleLine = message.replace('\n', ' ').replace("\"", "'")
        if (code == RESULT_ERROR) Log.w(TAG, singleLine) else Log.i(TAG, singleLine)
        if (isOrderedBroadcast) setResult(code, singleLine, null)
    }

    @Suppress("DEPRECATION")
    private fun loadAppMetaData(context: Context): Bundle? =
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData

    private fun resolveHandler(appMeta: Bundle?): LocalPushHandler? {
        val className = appMeta?.getString(META_HANDLER)?.ifBlank { null } ?: return null
        val clazz = try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            throw LocalPushException("handler '$className' not found (is it in src/debug?)")
        }
        val instance = runCatching { clazz.getField("INSTANCE").get(null) }.getOrNull() // Kotlin object
            ?: clazz.getDeclaredConstructor().newInstance()
        return instance as? LocalPushHandler
            ?: throw LocalPushException("'$className' does not implement LocalPushHandler")
    }

    companion object {
        private const val TAG = "LocalPush"

        const val ACTION = "com.localpush.debug.RECEIVE_LOCAL_PUSH"
        const val META_HANDLER = "com.localpush.debug.HANDLER"

        // 0 = initial value of `am broadcast` → means "no receiver processed the Intent".
        const val RESULT_POSTED = 1
        const val RESULT_HANDLED_BY_APP = 2
        const val RESULT_ERROR = 3
    }
}
