package com.localpush.plugin.ui

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.io.HttpRequests
import com.intellij.util.ui.JBUI
import com.localpush.plugin.adb.AdbClient
import com.localpush.plugin.adb.AdbDevice
import com.localpush.plugin.adb.AdbLocator
import com.localpush.plugin.model.DataPayloadParser
import com.localpush.plugin.model.LogLevel
import com.localpush.plugin.model.LogMessage
import com.localpush.plugin.model.PushImportance
import com.localpush.plugin.model.PushRequest
import com.localpush.plugin.settings.LocalPushSettings
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.DefaultComboBoxModel

class LocalPushPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val state = project.service<LocalPushSettings>().state

    private val deviceCombo = ComboBox<AdbDevice>()
    private val packageCombo = ComboBox<String>().apply { isEditable = true }
    private val titleField = JBTextField()
    private val bodyArea = textArea(rows = 3)
    private val channelIdField = JBTextField()
    private val channelNameField = JBTextField()
    private val importanceCombo = ComboBox(PushImportance.entries.toTypedArray())
    private val deepLinkField = JBTextField()
    private val dataArea = textArea(rows = 6)
    private val notificationIdField = JBTextField(10)
    private val smallIconField = JBTextField(20)
    private val adbPathField = JBTextField()
    // Same console component as Run/Logcat: theme-aware colors, search, copy, scroll-to-end.
    private val console: ConsoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        .apply { setViewer(true) }
        .console
    private lateinit var kofiRow: Row

    init {
        Disposer.register(this, console)
        loadState()
        setContent(JBScrollPane(buildForm()))
        refreshDevices()
        showKofiIfReachable()
    }

    private fun buildForm() = panel {
        group("Target") {
            row("Device:") {
                cell(deviceCombo).align(AlignX.FILL).resizableColumn()
                button("Refresh") { refreshDevices() }
            }
            row("Application ID:") {
                cell(packageCombo).align(AlignX.FILL).resizableColumn()
                button("Detect") { detectPackages() }
            }.rowComment("Detect = apps on the device that include localpush-debug")
        }
        group("Notification") {
            row("Title:") { cell(titleField).align(AlignX.FILL) }
            row("Message:") { cell(JBScrollPane(bodyArea)).align(AlignX.FILL) }
            row("Channel ID:") {
                cell(channelIdField).align(AlignX.FILL)
                    .comment("Empty = Firebase default_notification_channel_id or the FCM fallback channel")
            }
            row("Channel name:") { cell(channelNameField).align(AlignX.FILL) }
            row("Importance:") { cell(importanceCombo) }
            row("Deep link:") { cell(deepLinkField).align(AlignX.FILL).comment("E.g. myapp://orders/42. Empty = opens the launcher Activity") }
        }
        group("Data payload") {
            row { cell(JBScrollPane(dataArea)).align(Align.FILL) }.resizableRow()
            row { comment("JSON object {\"key\": \"value\"} or key=value lines. Delivered as Intent extras on tap.") }
        }
        collapsibleGroup("Advanced") {
            row("Notification ID:") {
                cell(notificationIdField).comment("Empty = random. Reuse an ID to replace the notification")
            }
            row("Small icon:") { cell(smallIconField).comment("Drawable name, e.g. ic_stat_notification") }
            row("adb / SDK path:") {
                cell(adbPathField).align(AlignX.FILL).comment("Empty = auto-detect (local.properties, ANDROID_HOME…)")
            }
            row { button("Grant POST_NOTIFICATIONS") { grantPermission() } }
        }
        row {
            button("Send notification") { send() }
            button("Clear log") { console.clear() }
        }
        group("Log") {
            row {
                cell(console.component).align(Align.FILL)
                    .applyToComponent { preferredSize = JBUI.size(400, 220) }
            }.resizableRow()
        }
        kofiRow = row { browserLink("☕ Support this plugin on Ko-fi", KOFI_URL) }.visible(false)
    }

    // ---------------------------------------------------------------- actions

    private fun refreshDevices() {
        val adb = adbOrLog() ?: return
        background("Looking for ADB devices", { adb.devices() }) { devices ->
            val previous = (deviceCombo.selectedItem as? AdbDevice)?.serial ?: state.lastSerial
            deviceCombo.model = DefaultComboBoxModel(devices.toTypedArray())
            devices.firstOrNull { it.serial == previous }?.let { deviceCombo.selectedItem = it }
            if (devices.isEmpty()) warn("No devices. Connect one or start an emulator, then click Refresh.")
            else info("${devices.size} device(s): ${devices.joinToString { it.serial + "/" + it.state }}")
        }
    }

    private fun detectPackages() {
        val device = onlineDeviceOrLog() ?: return
        val adb = adbOrLog() ?: return
        background("Looking for apps with localpush-debug", { adb.findReceiverPackages(device.serial) }) { packages ->
            if (packages.isEmpty()) {
                warn("No app installed on ${device.serial} exposes the receiver. Install the debug variant.")
                return@background
            }
            val current = packageCombo.editor.item?.toString()
            packageCombo.model = DefaultComboBoxModel(packages.toTypedArray())
            packageCombo.selectedItem = packages.firstOrNull { it == current } ?: packages.first()
            info("Compatible apps: ${packages.joinToString()}")
        }
    }

    private fun grantPermission() {
        val device = onlineDeviceOrLog() ?: return
        val pkg = packageOrLog() ?: return
        val adb = adbOrLog() ?: return
        background("Granting POST_NOTIFICATIONS", { adb.grantNotificationPermission(device.serial, pkg) }, ::log)
    }

    private fun send() {
        val device = onlineDeviceOrLog() ?: return
        val pkg = packageOrLog() ?: return
        val request = try {
            buildRequest()
        } catch (e: IllegalArgumentException) {
            error(e.message.orEmpty())
            return
        }
        val adb = adbOrLog() ?: return
        saveState()

        info("Sending to ${device.serial} · $pkg · \"${request.title}\"")
        background("Sending local notification", { adb.sendPush(device.serial, pkg, request) }) { result ->
            log(result.describe())
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun buildRequest(): PushRequest {
        val title = titleField.text.trim()
        val body = bodyArea.text.trim()
        require(title.isNotEmpty() || body.isNotEmpty()) { "Enter at least a title or a message" }
        val idText = notificationIdField.text.trim()
        return PushRequest(
            title = title,
            body = body,
            channelId = channelIdField.text.trim().ifEmpty { null },
            channelName = channelNameField.text.trim().ifEmpty { null },
            importance = importanceCombo.item ?: PushImportance.DEFAULT,
            deepLink = deepLinkField.text.trim().ifEmpty { null },
            smallIcon = smallIconField.text.trim().ifEmpty { null },
            notificationId = if (idText.isEmpty()) null
            else requireNotNull(idText.toIntOrNull()) { "Notification ID must be an integer" },
            data = DataPayloadParser.parse(dataArea.text),
        )
    }

    private fun adbOrLog(): AdbClient? {
        val adb = AdbLocator.locate(project, adbPathField.text)
        if (adb == null) error("adb not found. Set the SDK path in Advanced › adb / SDK path.")
        return adb?.let(::AdbClient)
    }

    private fun onlineDeviceOrLog(): AdbDevice? {
        val device = deviceCombo.item
        when {
            device == null -> error("Select a device (click Refresh)")
            !device.isOnline -> error("${device.serial} is '${device.state}'. If it is 'unauthorized', accept the RSA prompt on the device.")
            else -> return device
        }
        return null
    }

    private fun packageOrLog(): String? =
        packageCombo.editor.item?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: run { error("Enter the applicationId (e.g. com.myapp.debug) or click Detect"); null }

    /** Runs [work] off the EDT with progress in the status bar; [onDone] runs back on the EDT. */
    private fun <T> background(title: String, work: () -> T, onDone: (T) -> Unit) {
        object : Task.Backgroundable(project, title, false) {
            private var result: Result<T>? = null

            override fun run(indicator: ProgressIndicator) {
                result = runCatching(work)
            }

            override fun onFinished() {
                result?.fold(onDone) { error(it.message ?: it.javaClass.simpleName) }
            }
        }.queue()
    }

    private fun log(message: LogMessage) {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        console.print("$time  ", ConsoleViewContentType.SYSTEM_OUTPUT)
        console.print(message.text + "\n", message.level.contentType)
    }

    private fun info(text: String) = log(LogMessage(LogLevel.INFO, text))
    private fun warn(text: String) = log(LogMessage(LogLevel.WARNING, text))
    private fun error(text: String) = log(LogMessage(LogLevel.ERROR, text))

    /** Shows the Ko-fi link only if ko-fi.com loads (through the IDE proxy), so a firewall never leaves a dead link. */
    private fun showKofiIfReachable() {
        kofiReachable?.let { kofiRow.visible(it); return }
        ApplicationManager.getApplication().executeOnPooledThread {
            val ok = try {
                HttpRequests.request(KOFI_PROBE_URL)
                    .connectTimeout(PROBE_TIMEOUT_MS)
                    .readTimeout(PROBE_TIMEOUT_MS)
                    .tryConnect() in 200..399
            } catch (e: IOException) {
                false
            }
            kofiReachable = ok
            ApplicationManager.getApplication().invokeLater({
                if (!Disposer.isDisposed(this)) kofiRow.visible(ok)
            }, ModalityState.any())
        }
    }

    override fun dispose() = Unit // the console is disposed as a child (Disposer.register in init)

    private fun loadState() {
        state.packageName?.let { packageCombo.editor.item = it; packageCombo.addItem(it) }
        titleField.text = state.title.orEmpty()
        bodyArea.text = state.body.orEmpty()
        channelIdField.text = state.channelId.orEmpty()
        channelNameField.text = state.channelName.orEmpty()
        importanceCombo.selectedItem = PushImportance.entries.firstOrNull { it.name == state.importance } ?: PushImportance.HIGH
        deepLinkField.text = state.deepLink.orEmpty()
        smallIconField.text = state.smallIcon.orEmpty()
        notificationIdField.text = state.notificationId.orEmpty()
        dataArea.text = state.dataText.orEmpty()
        adbPathField.text = state.adbPath.orEmpty()
    }

    private fun saveState() {
        state.lastSerial = deviceCombo.item?.serial
        state.packageName = packageCombo.editor.item?.toString()?.trim()
        state.title = titleField.text
        state.body = bodyArea.text
        state.channelId = channelIdField.text
        state.channelName = channelNameField.text
        state.importance = importanceCombo.item?.name
        state.deepLink = deepLinkField.text
        state.smallIcon = smallIconField.text
        state.notificationId = notificationIdField.text
        state.dataText = dataArea.text
        state.adbPath = adbPathField.text
    }

    private fun textArea(rows: Int) = JBTextArea(rows, 30).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    private companion object {
        const val KOFI_URL = "https://ko-fi.com/N8W327KI3Y"
        const val KOFI_PROBE_URL = "https://ko-fi.com/favicon.png"
        const val PROBE_TIMEOUT_MS = 8_000

        /** Probed once per IDE session, not every time a project opens the tool window. */
        @Volatile
        var kofiReachable: Boolean? = null

        /** Green from the IDE's ANSI console palette, so it adapts to light and dark themes. */
        val SUCCESS_OUTPUT = ConsoleViewContentType(
            "LOCALPUSH_SUCCESS",
            TextAttributesKey.createTextAttributesKey("LOCALPUSH_SUCCESS", ConsoleHighlighter.GREEN),
        )

        val LogLevel.contentType: ConsoleViewContentType
            get() = when (this) {
                LogLevel.INFO -> ConsoleViewContentType.NORMAL_OUTPUT
                LogLevel.SUCCESS -> SUCCESS_OUTPUT
                LogLevel.WARNING -> ConsoleViewContentType.LOG_WARNING_OUTPUT
                LogLevel.ERROR -> ConsoleViewContentType.ERROR_OUTPUT
            }
    }
}
