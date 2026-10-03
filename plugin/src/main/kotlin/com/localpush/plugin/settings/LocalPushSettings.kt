package com.localpush.plugin.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

/** Last used form, per project and per developer (.idea/workspace.xml, not version-controlled). */
@Service(Service.Level.PROJECT)
@State(name = "LocalPushMock", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class LocalPushSettings : SimplePersistentStateComponent<LocalPushSettings.FormState>(FormState()) {

    class FormState : BaseState() {
        var lastSerial by string()
        var packageName by string()
        var title by string("Your order is on the way")
        var body by string("Order #42 will arrive in 10 minutes.")
        var channelId by string()
        var channelName by string()
        var importance by string("HIGH")
        var deepLink by string()
        var smallIcon by string()
        var notificationId by string()
        var dataText by string("{\n  \"type\": \"order_update\",\n  \"orderId\": \"42\"\n}")
        var adbPath by string()
        var saved by list<SavedPush>()

        /** Call after editing [saved] in place, so the IDE knows the state must be written. */
        fun savedChanged() = incrementModificationCount()
    }

    /** A notification the user chose to keep, newest first in [FormState.saved]. */
    class SavedPush : BaseState() {
        var name by string()
        var title by string()
        var body by string()
        var channelId by string()
        var channelName by string()
        var importance by string()
        var deepLink by string()
        var smallIcon by string()
        var notificationId by string()
        var dataText by string()

        override fun toString() = name.orEmpty()
    }
}
