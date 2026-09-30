package com.localpush.plugin.model

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.util.Base64

/**
 * Constants shared with the Android module `localpush-debug`. If you change anything here,
 * change it in `LocalPushReceiver` / `LocalPushPayload` too.
 */
object LocalPushProtocol {
    const val ACTION = "com.localpush.debug.RECEIVE_LOCAL_PUSH"
    const val RECEIVER_CLASS = "com.localpush.debug.LocalPushReceiver"
    const val EXTRA_PAYLOAD_B64 = "payload_b64"
    const val VERSION = 1

    const val RESULT_NOT_DELIVERED = 0
    const val RESULT_POSTED = 1
    const val RESULT_HANDLED_BY_APP = 2
    const val RESULT_ERROR = 3
}

enum class PushImportance { MIN, LOW, DEFAULT, HIGH, MAX }

data class PushRequest(
    val title: String,
    val body: String,
    val channelId: String?,
    val channelName: String?,
    val importance: PushImportance,
    val deepLink: String?,
    val smallIcon: String?,
    val notificationId: Int?,
    val data: Map<String, String>,
) {
    fun toJson(): String = JsonObject().apply {
        addProperty("v", LocalPushProtocol.VERSION)
        notificationId?.let { addProperty("id", it) }
        addProperty("title", title)
        addProperty("body", body)
        channelId?.let { addProperty("channelId", it) }
        channelName?.let { addProperty("channelName", it) }
        addProperty("importance", importance.name)
        deepLink?.let { addProperty("deepLink", it) }
        smallIcon?.let { addProperty("smallIcon", it) }
        add("data", JsonObject().apply { data.forEach { (k, v) -> addProperty(k, v) } })
    }.toString()

    /** Standard Base64: only [A-Za-z0-9+/=], safe for the remote `adb shell`. */
    fun toBase64(): String = Base64.getEncoder().encodeToString(toJson().toByteArray(Charsets.UTF_8))
}

/**
 * Turns the "Data payload" field text into a `Map<String, String>` (same as `RemoteMessage.data`).
 * Accepts a JSON object or `key=value` lines (blank lines and `#comments` are ignored).
 */
object DataPayloadParser {

    fun parse(text: String): Map<String, String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyMap()
        return if (trimmed.startsWith("{")) parseJson(trimmed) else parseKeyValues(trimmed)
    }

    private fun parseJson(text: String): Map<String, String> {
        val element = try {
            JsonParser.parseString(text)
        } catch (e: JsonParseException) {
            throw IllegalArgumentException("Data payload: invalid JSON (${e.cause?.message ?: e.message})")
        }
        require(element.isJsonObject) { "Data payload: must be a JSON object {…}" }
        return element.asJsonObject.entrySet()
            .filterNot { it.value.isJsonNull }
            // As in FCM, values are strings; nested objects/arrays are sent serialized.
            .associate { (k, v) -> k to if (v.isJsonPrimitive) v.asString else v.toString() }
    }

    private fun parseKeyValues(text: String): Map<String, String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val idx = line.indexOf('=')
                require(idx > 0) { "Data payload: line is not 'key=value': $line" }
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
}
