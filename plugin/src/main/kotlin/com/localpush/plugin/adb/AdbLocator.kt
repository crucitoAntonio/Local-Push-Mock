package com.localpush.plugin.adb

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.util.Properties

/**
 * Locates the `adb` binary without depending on the Android plugin (works the same in Android Studio and IntelliJ).
 *
 * Order: manual path → `sdk.dir` from local.properties → ANDROID_HOME / ANDROID_SDK_ROOT →
 * default SDK locations → PATH.
 * Note: on macOS an IDE launched from the Dock does not inherit your shell's PATH, which is why
 * local.properties and the default locations come before PATH.
 */
object AdbLocator {

    private val adbName = if (SystemInfo.isWindows) "adb.exe" else "adb"

    fun locate(project: Project, manualPath: String?): File? {
        manualPath?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            // Accepts the path to the binary, to the SDK or to platform-tools.
            val file = File(path)
            val adb = if (file.isDirectory) adbInSdk(file).takeIf { it.exists() } ?: File(file, adbName) else file
            return adb.takeIf { it.canExecute() }
        }

        val sdkCandidates = sequenceOf(
            sdkDirFromLocalProperties(project),
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            "${System.getProperty("user.home")}/Library/Android/sdk", // macOS
            "${System.getProperty("user.home")}/Android/Sdk",         // Linux
            System.getenv("LOCALAPPDATA")?.let { "$it\\Android\\Sdk" }, // Windows
        )
        return sdkCandidates
            .filterNotNull()
            .map { adbInSdk(File(it)) }
            .firstOrNull { it.canExecute() }
            ?: findInPath()
    }

    private fun findInPath(): File? =
        System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { File(it, adbName) }
            .firstOrNull { it.isFile && it.canExecute() }

    private fun adbInSdk(sdkDir: File) = File(File(sdkDir, "platform-tools"), adbName)

    private fun sdkDirFromLocalProperties(project: Project): String? {
        val file = project.basePath?.let { File(it, "local.properties") }?.takeIf { it.isFile } ?: return null
        return Properties().apply { file.reader().use(::load) }.getProperty("sdk.dir")
    }
}
