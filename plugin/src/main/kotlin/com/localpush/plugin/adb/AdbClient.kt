package com.localpush.plugin.adb

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.localpush.plugin.model.LocalPushProtocol
import com.localpush.plugin.model.LogLevel
import com.localpush.plugin.model.LogMessage
import com.localpush.plugin.model.PushRequest
import java.io.File

data class AdbDevice(val serial: String, val state: String, val model: String?) {
    val isOnline get() = state == "device"
    override fun toString() = buildString {
        append(model?.replace('_', ' ') ?: serial)
        if (model != null) append("  ($serial)")
        if (!isOnline) append("  [$state]")
    }
}

class AdbException(message: String) : Exception(message)

/** Result of `am broadcast`: `Broadcast completed: result=<code>, data="<msg>"`. */
data class BroadcastResult(val code: Int?, val data: String?, val raw: String) {

    fun describe(): LogMessage = when (code) {
        LocalPushProtocol.RESULT_POSTED -> LogMessage(LogLevel.SUCCESS, "Notification posted: $data")
        LocalPushProtocol.RESULT_HANDLED_BY_APP -> LogMessage(LogLevel.SUCCESS, "Handled by the app's LocalPushHandler: $data")
        LocalPushProtocol.RESULT_ERROR -> LogMessage(LogLevel.ERROR, "The app rejected the push: $data")
        LocalPushProtocol.RESULT_NOT_DELIVERED -> LogMessage(
            LogLevel.WARNING,
            "No receiver processed the broadcast. Is the debug variant with localpush-debug installed " +
                "and is the applicationId correct (including the .debug suffix)?",
        )
        else -> LogMessage(LogLevel.WARNING, "Unexpected adb response:\n$raw")
    }

    companion object {
        private val COMPLETED = Regex("""Broadcast completed: result=(-?\d+)(?:, data="(.*)")?""")

        fun parse(output: String): BroadcastResult {
            val match = COMPLETED.find(output)
            return BroadcastResult(
                code = match?.groupValues?.get(1)?.toIntOrNull(),
                data = match?.groupValues?.get(2)?.ifEmpty { null },
                raw = output.trim(),
            )
        }
    }
}

/** Synchronous wrapper around the adb binary. Always call it from a background thread. */
class AdbClient(private val adb: File) {

    fun devices(): List<AdbDevice> {
        val out = run("devices", "-l")
        return out.stdout.lineSequence()
            .drop(1) // "List of devices attached"
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") } // "* daemon started successfully"
            .map { line ->
                val parts = line.split(Regex("\\s+"))
                val model = parts.drop(2).firstOrNull { it.startsWith("model:") }?.removePrefix("model:")
                AdbDevice(serial = parts[0], state = parts.getOrElse(1) { "unknown" }, model = model)
            }
            .toList()
    }

    /** applicationIds installed on the device that expose the localpush-debug receiver. */
    fun findReceiverPackages(serial: String): List<String> {
        val out = run("-s", serial, "shell", "cmd", "package", "query-receivers", "--brief", "-a", LocalPushProtocol.ACTION)
        return Regex("""([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+""")
            .findAll(out.stdout)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    fun sendPush(serial: String, packageName: String, request: PushRequest): BroadcastResult {
        requireSafePackage(packageName)
        val out = run(
            "-s", serial, "shell", "am", "broadcast",
            "-a", LocalPushProtocol.ACTION,
            "-n", "$packageName/${LocalPushProtocol.RECEIVER_CLASS}",
            "--include-stopped-packages", // also works if the app was never opened or was force-stopped
            "--es", LocalPushProtocol.EXTRA_PAYLOAD_B64, request.toBase64(),
        )
        return BroadcastResult.parse(out.stdout + "\n" + out.stderr)
    }

    fun grantNotificationPermission(serial: String, packageName: String): LogMessage {
        requireSafePackage(packageName)
        val out = run("-s", serial, "shell", "pm", "grant", packageName, "android.permission.POST_NOTIFICATIONS")
        val text = (out.stdout + out.stderr).trim()
        return if (out.exitCode == 0 && text.isEmpty()) LogMessage(LogLevel.SUCCESS, "POST_NOTIFICATIONS granted to $packageName")
        else LogMessage(LogLevel.WARNING, "pm grant: ${text.ifEmpty { "exit ${out.exitCode}" }} (not needed below Android 13)")
    }

    private fun run(vararg args: String, timeoutMs: Int = 15_000): ProcessOutput {
        val commandLine = GeneralCommandLine(listOf(adb.absolutePath) + args).withCharset(Charsets.UTF_8)
        val output = CapturingProcessHandler(commandLine).runProcess(timeoutMs)
        if (output.isTimeout) throw AdbException("Timed out running: adb ${args.take(4).joinToString(" ")}…")
        return output
    }

    /** `adb shell` arguments are re-parsed by the device shell: never accept anything injectable. */
    private fun requireSafePackage(packageName: String) {
        if (!packageName.matches(Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+"""))) {
            throw AdbException("invalid applicationId: '$packageName'")
        }
    }
}
