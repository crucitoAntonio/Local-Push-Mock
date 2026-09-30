# Local Push Mock

Trigger local notifications on an Android device or emulator straight from Android Studio. No backend, no web console and no FCM credentials required.

```
local-push-mock/
├── plugin/                      IntelliJ / Android Studio plugin ("Local Push" tool window)
├── android/localpush-debug/     Android module included in debug builds only (debugImplementation)
├── scripts/localpush.sh         Same protocol from a terminal / CI
├── scripts/install-plugin.*     Plugin installer (sh / ps1)
├── install-plugin.command       Double-click installer (macOS)
└── install-plugin.cmd           Double-click installer (Windows)
```

**[User Guide](docs/USER_GUIDE.md)**: step-by-step setup and usage for any project.

---

## 1. Design choice: explicit ADB broadcast

| Criterion | A · `adb shell am broadcast` (chosen) | B · HTTP/socket server inside the app |
|---|---|---|
| App closed or force-stopped | Works: Android starts the process to deliver the broadcast (`--include-stopped-packages`) | Fails: no live process, no server |
| Dependencies in the app | None (only `androidx.core`) | Ktor/NanoHTTPD + `INTERNET` permission + a live Service |
| Background restrictions (8+, 12+, 14+) | Don't apply: explicit broadcasts are exempt | Requires a foreground service to keep the socket open |
| Multiple devices at once | `adb -s <serial>` | `adb forward` and one port per device |
| Attack surface | Receiver protected by `android.permission.DUMP` (only the shell/adb user holds it) | Open port: any local app can connect |
| Feedback to the IDE | `Broadcast completed: result=…, data="…"` | HTTP response |
| Code to maintain | ~250 lines | Considerably more |

B would only pay off for a continuous bidirectional channel (event streaming). To "fire a notification", A is lighter, more robust and easier to share with a team.

## 2. Architecture and data flow

```
┌──────────────── Android Studio ────────────────┐
│ "Local Push" tool window (Swing, Kotlin UI DSL) │
│  1. Form → PushRequest                           │
│  2. JSON → Base64 (no shell quoting issues)      │
│  3. AdbClient (adb process on a background thread)│
└───────────────┬─────────────────────────────────┘
                │ adb -s <serial> shell am broadcast
                │   -a com.localpush.debug.RECEIVE_LOCAL_PUSH
                │   -n <appId>/com.localpush.debug.LocalPushReceiver
                │   --include-stopped-packages --es payload_b64 <b64>
                ▼
┌───────────── Device / Emulator ─────────────────┐
│ system_server: checks the sender (shell) holds   │
│ DUMP → delivers the Intent (starts the app if    │
│ it was not running)                              │
│                                                  │
│ LocalPushReceiver (exists in debug only)         │
│  4. Debuggable app? otherwise reject             │
│  5. Decode → LocalPushPayload                    │
│  6. HANDLER meta-data? → your production code    │
│     otherwise → LocalPushNotifier:               │
│       channel (NotificationChannelCompat)        │
│       NotificationCompat.Builder                 │
│       PendingIntent(launcher | deep link)        │
│         + data as extras (same as FCM)           │
│       NotificationManagerCompat.notify()         │
│  7. setResult(code, "id=… channel=…")            │
└───────────────┬─────────────────────────────────┘
                │ stdout: Broadcast completed: result=1, data="id=… channel=orders"
                ▼
        Tool window log (color-coded: success / warning / error)
```

Result codes: `0` nobody received the broadcast (wrong app or variant), `1` posted, `2` handled by the app's handler, `3` error (the message says which).

### Fidelity with a real FCM push

The default builder behaves like the FCM SDK when it displays a `notification` message while the app is in the background:
- **Default icon, color and channel:** read from the same meta-data FCM uses (`com.google.firebase.messaging.default_notification_icon`, `default_notification_color` and `default_notification_channel_id`). With no channel configured it uses `fcm_fallback_notification_channel` ("Misc").
- **Tapping the notification:** opens the launcher Activity with `data` copied as Intent extras, which is what your app reads in `onCreate`/`onNewIntent`.
- **Local push marker:** the extra `com.localpush.debug.FROM_LOCAL_PUSH=true` is added so you can tell it apart from a real one.

To test **exactly** your production code (e.g. your `onMessageReceived` logic for *data* messages), register a `LocalPushHandler` (see 4.3).

## 3. Security: nothing reaches release

1. **`debugImplementation`:** the module (code + `<receiver>` + `POST_NOTIFICATIONS`) is only merged into the debug manifest.
2. **`android:permission="android.permission.DUMP"`:** even in debug, only adb (the `shell` user) can send the broadcast; no installed app can. A custom `signature` permission is not used because adb is not signed with your key and the broadcast would be rejected.
3. **Runtime check:** the receiver refuses to act if the app is not `debuggable`.
4. **applicationId validation** in the plugin and the script: nothing injectable reaches the device shell, and the payload travels as Base64.

CI check:
```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep localpush-debug && exit 1 || echo "OK: release is clean"
```

## 4. Team integration guide

### 4.1 Install the plugin in Android Studio

**Double-click `install-plugin.command` (macOS) or `install-plugin.cmd` (Windows).** On Linux: `bash scripts/install-plugin.sh`.

The installer detects Android Studio, builds with its bundled Java 21 (JBR) and the included Gradle Wrapper (no JDK or Gradle install needed) and copies the plugin into the plugins folder of every Android Studio it finds. Then restart the IDE: the **Local Push** tool window appears.

Options: `--zip <file>` (install a prebuilt .zip), `--uninstall`, `--dest <folder>`. Details in the [User Guide](docs/USER_GUIDE.md#3-part-a--install-the-plugin-in-android-studio-once-per-computer).

To develop the plugin: `cd plugin && ./gradlew runIde` opens a sandbox IDE with the plugin loaded.

### 4.2 Add the debug module to the app

1. Copy `android/localpush-debug/` to the root of the Android project.
2. In `settings.gradle.kts`:
   ```kotlin
   include(":localpush-debug")
   ```
3. In `app/build.gradle.kts`:
   ```kotlin
   dependencies {
       debugImplementation(project(":localpush-debug"))
   }
   ```
   If you use flavors or a debuggable `staging` build type, also add `stagingImplementation(...)`.
4. Align `compileSdk`, `minSdk` and the `core-ktx` version in `localpush-debug/build.gradle.kts` with your project (or use your version catalog). With AGP 9+ remove the `org.jetbrains.kotlin.android` plugin.
5. Install the debug variant (`./gradlew :app:installDebug`).

### 4.3 (Optional) Reuse your production notification logic

`app/src/debug/java/com/myapp/debug/AppLocalPushHandler.kt`:
```kotlin
object AppLocalPushHandler : LocalPushHandler {
    override fun handle(context: Context, payload: LocalPushPayload): Boolean {
        // Same entry point as your FirebaseMessagingService.onMessageReceived
        AppNotificationFactory.show(context, payload.title, payload.body, payload.data)
        return true // false = use the localpush-debug default builder
    }
}
```
`app/src/debug/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <meta-data android:name="com.localpush.debug.HANDLER"
                   android:value="com.myapp.debug.AppLocalPushHandler" />
    </application>
</manifest>
```

### 4.4 Day-to-day use

1. Open **Local Push**, click **Refresh** and pick the device.
2. Click **Detect**: it lists the installed apps that include the receiver, with their real applicationId (including the `.debug` suffix).
3. On Android 13+, click **Advanced › Grant POST_NOTIFICATIONS** once.
4. Fill in the form and click **Send notification**. The result shows up in the log.

From a terminal or CI:
```bash
scripts/localpush.sh -p com.myapp.debug -s emulator-5554 scripts/sample-payload.json
# Quick mode without JSON:
adb shell am broadcast -a com.localpush.debug.RECEIVE_LOCAL_PUSH \
  -n com.myapp.debug/com.localpush.debug.LocalPushReceiver \
  --es title Hello --es body World --es data.orderId 42
```

## 5. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `result=0` | The app has no receiver: release variant installed, applicationId without the `.debug` suffix, or module not added. Use **Detect**. |
| `POST_NOTIFICATIONS not granted` | Android 13+: *Grant POST_NOTIFICATIONS* button, or `adb shell pm grant <pkg> android.permission.POST_NOTIFICATIONS`. |
| Warning "channel already exists with importance…" | Android does not let the app change an existing channel's importance. Use another `channelId` or clear the app data. |
| No heads-up | The channel importance must be `HIGH`/`MAX` when it is **created**; on the emulator, check Do Not Disturb is off. |
| "adb not found" | Set the SDK path in *Advanced*. On macOS an IDE launched from the Dock does not inherit the shell `PATH`. |
| Device `unauthorized` | Accept the RSA fingerprint prompt on the device. |
| Tapping does not open the deep link | The log warns if no Activity in the app handles the URI: check the `<intent-filter>` with `VIEW` + `BROWSABLE`. |

## 6. Keeping the protocol in sync

The constants (action, receiver class, `payload_b64` extra and result codes) are duplicated in:
- `plugin/src/main/kotlin/com/localpush/plugin/model/PushRequest.kt` → `LocalPushProtocol`
- `android/localpush-debug/.../LocalPushReceiver.kt` and `LocalPushPayload.kt`
- `scripts/localpush.sh`

If you change the JSON format, bump `v`.
