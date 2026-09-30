# User Guide · Local Push Mock

A step-by-step guide so **anyone on the team** can send test notifications to an Android device or emulator from Android Studio. It works with **any Android project**, with no backend and no Firebase credentials.

> **What you get:** fill in a form in Android Studio, click *Send notification*, and a real notification appears on the phone or emulator, just as if a production push had arrived.

---

## Contents

1. [How it works (in 30 seconds)](#1-how-it-works-in-30-seconds)
2. [Requirements](#2-requirements)
3. [Part A · Install the plugin in Android Studio (once per computer)](#3-part-a--install-the-plugin-in-android-studio-once-per-computer) · with an automatic installer
4. [Part B · Add the module to an app (once per project)](#4-part-b--add-the-module-to-an-app-once-per-project)
5. [Part C · Send your first notification](#5-part-c--send-your-first-notification)
6. [Form field reference](#6-form-field-reference)
7. [Recipes for common cases](#7-recipes-for-common-cases)
8. [Use your own notification code (optional)](#8-use-your-own-notification-code-optional)
9. [Use it from a terminal or CI](#9-use-it-from-a-terminal-or-ci)
10. [Working with multiple projects and devices](#10-working-with-multiple-projects-and-devices)
11. [Update or uninstall](#11-update-or-uninstall)
12. [Troubleshooting](#12-troubleshooting)
13. [FAQ](#13-faq)
14. [Quick checklist](#14-quick-checklist)

---

## 1. How it works (in 30 seconds)

The system has two pieces:

| Piece | Where it lives | What it does |
|---|---|---|
| **"Local Push" plugin** | In your Android Studio | Shows the form and sends the command to the device using `adb` (the same tool Android Studio uses to install apps). |
| **`localpush-debug` module** | Inside your app, **debug only** | Receives the command and shows the notification with native Android APIs. |

```
Android Studio (form) ──adb──▶ Phone/Emulator ──▶ Debug app ──▶ Notification
```

- **No internet** or account needed.
- **Never reaches production**: the module is only included in *debug* builds.
- Works even if the app is closed: Android starts it to deliver the notification.

---

## 2. Requirements

| You need | Version | How to check |
|---|---|---|
| Android Studio | Ladybug (2024.2) or later | *Android Studio › About Android Studio* |
| Android SDK with *Platform-Tools* (adb) | Any recent version | In Android Studio: *Settings › Languages & Frameworks › Android SDK › SDK Tools*, check **Android SDK Platform-Tools** |
| Device or emulator | Android 5.0 (API 21) or later | It must show up in Android Studio's device selector |
| Internet connection | **First** install only | Downloads Gradle and the libraries needed to build the plugin |

> [!NOTE]
> **You don't need to install Java or Gradle.** The installer uses the Java 21 bundled with Android Studio and the Gradle Wrapper included in the project.

---

## 3. Part A · Install the plugin in Android Studio (once per computer)

### 3.1 Get the project

Clone or copy the `local-push-mock` folder to your computer:

```
local-push-mock/
├── install-plugin.command    ← macOS installer (double-click)
├── install-plugin.cmd        ← Windows installer (double-click)
├── plugin/                   ← plugin source code
├── android/localpush-debug/  ← module for your apps
├── scripts/                  ← scripts (Linux installer, terminal sending)
└── docs/                     ← this guide
```

### 3.2 Run the installer

| OS | What to do |
|---|---|
| **macOS** | Double-click **`install-plugin.command`**. If macOS says it can't be opened because it is from an unidentified developer: right-click › **Open** › **Open**. |
| **Windows** | Double-click **`install-plugin.cmd`**. If SmartScreen warns you: **More info › Run anyway**. |
| **Linux** | In a terminal: `bash scripts/install-plugin.sh` |

The installer does **all the work** for you:

1. **Finds Android Studio** in the usual locations (`/Applications`, `Program Files`, `/opt`, JetBrains Toolbox…).
2. **Uses Android Studio's Java 21** to build.
3. **Builds the plugin** with the Gradle Wrapper. The first run downloads dependencies and takes a few minutes; later runs take seconds.
4. **Installs it** into the plugins folder of **every** Android Studio it finds (stable, Preview…), replacing previous versions.

Expected output:

```
==> Looking for Android Studio
  [ok]    AndroidStudio2025.2.1  (/Applications/Android Studio.app)

==> Looking for Java 21+
  [ok]    Java 21: /Applications/Android Studio.app/Contents/jbr/Contents/Home

==> Building the plugin (the first run downloads dependencies and may take a few minutes)
BUILD SUCCESSFUL in 2m 48s
  [ok]    Built: …/plugin/build/distributions/local-push-mock-1.0.0.zip

==> Installing
  [ok]    Installed in ~/Library/Application Support/Google/AndroidStudio2025.2.1/plugins

==> Plugin installed.
  [warn]  Android Studio is running: quit and reopen it to load the plugin.
```

`[ok]` lines are green, `[warn]` yellow and `[error]` red (the installer stops at the first error).

### 3.3 Restart Android Studio

If Android Studio was open, **quit it completely** (macOS: `Cmd+Q`) and open it again.

### 3.4 Check that it is installed

A tab with a bell named **Local Push** appears on the **right** side. If you don't see it: *View › Tool Windows › Local Push*. It is also listed under *Settings › Plugins › Installed* as **Local Push Mock**.

**Part A is done.** You won't need to repeat it when you switch projects.

### 3.5 Installer options

These options are passed from a terminal. On Windows, use PowerShell: `.\scripts\install-plugin.ps1 -Uninstall`.

| macOS / Linux | Windows | Purpose |
|---|---|---|
| `--zip <file.zip>` | `-Zip <file.zip>` | Install a prebuilt `.zip` **without building** (faster, no internet). |
| `--uninstall` | `-Uninstall` | Uninstall the plugin from every detected Android Studio. |
| `--dest <folder>` | `-Dest <folder>` | Install into a specific plugins folder (if your Android Studio is in a non-standard location). |
| `--help` | — | Show help. |

> [!TIP]
> **Build once, install everywhere (larger teams).**
>
> **One person** runs the installer once and shares (Slack, Drive, email…) these **two files**:
> - `plugin/build/distributions/local-push-mock-1.0.0.zip` (on macOS you can reveal it with `open -R plugin/build/distributions/local-push-mock-1.0.0.zip`)
> - `scripts/install-plugin.sh`
>
> **Everyone else** doesn't need the project, Java or Gradle. With both files in the same folder (e.g. `Downloads`), in Terminal:
> ```bash
> cd ~/Downloads
> bash install-plugin.sh --zip local-push-mock-1.0.0.zip
> ```
> Then restart Android Studio. On Windows: `powershell -ExecutionPolicy Bypass -File install-plugin.ps1 -Zip local-push-mock-1.0.0.zip`.
>
> No terminal works too: *Settings › Plugins ›* gear icon *› Install Plugin from Disk…* and pick the `.zip`.

### 3.6 Manual alternative (no installer)

If you prefer not to use the script:

1. Build: `cd plugin && ./gradlew buildPlugin` (Windows: `gradlew.bat buildPlugin`). If your `JAVA_HOME` is not Java 21, point it to Android Studio's JBR:
   - macOS: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
   - Windows: `set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`
2. In Android Studio: **Settings › Plugins ›** gear icon **› Install Plugin from Disk…** → pick `plugin/build/distributions/local-push-mock-1.0.0.zip` → **Restart IDE**.

---

## 4. Part B · Add the module to an app (once per project)

Do these steps in **every Android project** where you want to use the tool.

### 4.1 Copy the module

Copy the `android/localpush-debug` folder to the **root** of your Android project, next to `app/`:

```
MyProject/
├── app/
├── localpush-debug/     ← here
├── settings.gradle.kts
└── build.gradle.kts
```

### 4.2 Register it in `settings.gradle.kts`

Add this line, usually below `include(":app")`:

```kotlin
include(":localpush-debug")
```

<details>
<summary>Does your project use Groovy (<code>settings.gradle</code> without <code>.kts</code>)?</summary>

```groovy
include ':localpush-debug'
```
</details>

### 4.3 Add the dependency **for debug only**

In `app/build.gradle.kts`, inside the `dependencies { … }` block:

```kotlin
dependencies {
    // ... your dependencies
    debugImplementation(project(":localpush-debug"))
}
```

<details>
<summary>Groovy (<code>app/build.gradle</code>)</summary>

```groovy
dependencies {
    debugImplementation project(':localpush-debug')
}
```
</details>

> [!WARNING]
> Use **`debugImplementation`**, never `implementation`. This guarantees the module **never** ends up in the build you publish to Google Play.

**Do you have flavors or extra build types?** The configuration is named `<variant>Implementation`:

| Your case | What to add |
|---|---|
| Debuggable `staging` build type | `stagingImplementation(project(":localpush-debug"))` |
| `dev` / `prod` flavors and you only want it in `devDebug` | `"devDebugImplementation"(project(":localpush-debug"))` |
| Regular `debug` build type | `debugImplementation` is enough (covers every flavor in debug) |

### 4.4 Align versions with your project

Open `localpush-debug/build.gradle.kts` and check three things:

1. **`compileSdk` and `minSdk`**: use the same values as `app/build.gradle.kts` (the module's `minSdk` cannot be higher than the app's).
2. **`androidx.core:core-ktx`**: if you use a *version catalog*, replace it with `implementation(libs.androidx.core.ktx)`.
3. **Kotlin plugin**:
   - If your project uses **AGP 9 or later**, delete the `id("org.jetbrains.kotlin.android")` line.
   - If the project declares plugins with catalog aliases, you can use `alias(libs.plugins.android.library)` and `alias(libs.plugins.kotlin.android)`.

> If Gradle complains it can't find `com.android.library`, add `id("com.android.library") version "<your AGP version>" apply false` to the root `build.gradle.kts`, next to the application plugin.

### 4.5 Sync and install

1. Click **Sync Now** (or *File › Sync Project with Gradle Files*).
2. Select the **debug** variant in *Build Variants*.
3. Run the app (**Run** button) or run `./gradlew :app:installDebug`.

### 4.6 (Recommended) Check that release is clean

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep localpush-debug
```

If it **prints nothing**, the module is not in release.

**Part B is done for this project.**

---

## 5. Part C · Send your first notification

1. **Connect the device** over USB with *USB debugging* enabled, or start an emulator.
2. Open the **Local Push** panel (right side).
3. In **Target › Device**, click **Refresh** and pick your device from the list.
   - The log will show something like `1 device(s): emulator-5554/device`.
4. In **Application ID**, click **Detect**.
   - The plugin asks the device which apps have the module installed and fills in the field, e.g. `com.mycompany.myapp.debug`.
5. **Android 13 or later only (the first time):** expand **Advanced** and click **Grant POST_NOTIFICATIONS**.
   - Expected log (in green): `POST_NOTIFICATIONS granted to com.mycompany.myapp.debug`.
6. Keep the sample values in the form and click **Send notification**.
7. Check the log:

   ```
   10:32:05  Sending to emulator-5554 · com.mycompany.myapp.debug · "Your order is on the way"
   10:32:06  Notification posted: id=18273645 channel=fcm_fallback_notification_channel
   ```

8. Pull down the device's notification shade: there it is.
9. Tap it: your app opens and receives `data` as Intent extras.

---

## 6. Form field reference

### Target

| Field | What it's for |
|---|---|
| **Device** | Phone or emulator to send to. **Refresh** re-reads the `adb` device list. If it shows `[unauthorized]`, accept the prompt on the phone. |
| **Application ID** | Identifier of the installed app. Note: if your debug build uses `applicationIdSuffix ".debug"`, the ID ends in `.debug`. **Detect** figures it out for you. You can also type it. |

### Notification

| Field | Required | Description | Example |
|---|---|---|---|
| **Title** | Title or message | Bold text of the notification. If empty, the app name is used. | `Your order is on the way` |
| **Message** | Title or message | Body. Long texts are shown in full when the notification is expanded. | `Order #42 will arrive in 10 min` |
| **Channel ID** | No | Android 8+ notification channel. If empty, the Firebase default channel configured in your app is used or, if there is none, `fcm_fallback_notification_channel` (same as FCM). | `orders` |
| **Channel name** | No | Channel name shown in *Settings › Notifications*. **Only applied the first time** the channel is created. | `Orders` |
| **Importance** | Yes | `MIN`, `LOW`, `DEFAULT`, `HIGH` or `MAX`. With `HIGH` or `MAX` it shows as a *heads-up* (floating) notification. **Only applied when the channel is created** (see [FAQ](#13-faq)). | `HIGH` |
| **Deep link** | No | URI opened on tap. Your app needs an `<intent-filter>` that accepts it. Empty = opens the main screen. | `myapp://orders/42` |

### Data payload

Key-value pairs your app receives as Intent *extras* when the notification is tapped (same as FCM `data`). Two formats are supported:

**JSON:**
```json
{
  "type": "order_update",
  "orderId": "42",
  "extra": { "nested": true }
}
```

**key=value (one per line; `#` for comments):**
```
# order notification
type=order_update
orderId=42
```

> All values arrive as **strings** (as in FCM). Numbers are converted to strings, and nested objects or arrays are sent as JSON text.

Read them in your Activity like this:

```kotlin
val orderId = intent.getStringExtra("orderId")
val isLocalPush = intent.getBooleanExtra("com.localpush.debug.FROM_LOCAL_PUSH", false)
```

### Advanced

| Field | Description |
|---|---|
| **Notification ID** | Integer. Empty = random (each send creates a new notification). Reusing the same ID **replaces** the previous notification; useful for testing status updates. |
| **Small icon** | Name of a drawable in your app (without `R.drawable.`), e.g. `ic_stat_notification`. Empty = the Firebase default icon or the app icon. |
| **adb / SDK path** | Usually empty. If the plugin can't find `adb`, enter the SDK folder (`/Users/you/Library/Android/sdk`) or the full path to the `adb` executable. |
| **Grant POST_NOTIFICATIONS** | Grants the notification permission (Android 13+) without opening the app and accepting the dialog. |

### Log

The same console used by Run and Logcat: each line has a timestamp and a color that adapts to your IDE theme. You can search it (`Cmd/Ctrl+F`) and copy from it.

| Color | Meaning |
|---|---|
| Default text | Information (devices found, notification being sent) |
| Green | Success |
| Yellow | Worked with warnings, or didn't reach the app (read the message) |
| Red | Error: the message says what's missing |

The form is **saved automatically** when you send, per project, and restored when you reopen Android Studio.

---

## 7. Recipes for common cases

**Test a notification that opens a specific screen**
- Deep link: `myapp://orders/42`
- Make sure your `AndroidManifest.xml` declares the Activity with:
  ```xml
  <intent-filter>
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data android:scheme="myapp" android:host="orders" />
  </intent-filter>
  ```

**Test that a notification updates (e.g. order status)**
1. Notification ID: `100`. Title: `Order ready`. Send.
2. Same ID `100`. Title: `Order on the way`. Send. The notification changes instead of duplicating.

**Test a new channel with heads-up**
- Channel: `promos_v2` (an ID that doesn't exist yet). Channel name: `Promotions`. Importance: `HIGH`.

**Test behavior with the app closed**
- Close the app from *Recents*, or force-stop it with `adb shell am force-stop <applicationId>`, and send. It should still arrive.

**Test what happens when the user denies notifications**
- `adb shell pm revoke <applicationId> android.permission.POST_NOTIFICATIONS` and send. You'll see a red `… POST_NOTIFICATIONS not granted`.

---

## 8. Use your own notification code (optional)

By default the module builds the notification mimicking FCM. If your app has **its own logic** (e.g. a `NotificationFactory` called from `FirebaseMessagingService.onMessageReceived`) and you want to test **that** code, plug in a *handler*.

**Step 1.** Create `app/src/debug/java/<your/package>/debug/AppLocalPushHandler.kt`. The `src/debug` folder keeps it out of release too.

```kotlin
package com.mycompany.myapp.debug

import android.content.Context
import com.localpush.debug.LocalPushHandler
import com.localpush.debug.LocalPushPayload

object AppLocalPushHandler : LocalPushHandler {
    override fun handle(context: Context, payload: LocalPushPayload): Boolean {
        // Call the SAME code your FCM service uses:
        AppNotificationFactory.show(
            context = context,
            title = payload.title,
            body = payload.body,
            data = payload.data,
        )
        return true   // true = I showed it · false = let the module show it
    }
}
```

**Step 2.** Create or edit `app/src/debug/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <meta-data
            android:name="com.localpush.debug.HANDLER"
            android:value="com.mycompany.myapp.debug.AppLocalPushHandler" />
    </application>
</manifest>
```

**Step 3.** Reinstall the debug app and send. The log will show:
`Handled by the app's LocalPushHandler: handled by com.mycompany.myapp.debug.AppLocalPushHandler` (in green)

> [!IMPORTANT]
> The handler runs on the main thread and has about 10 seconds. Don't make network calls inside it.

---

## 9. Use it from a terminal or CI

Without opening Android Studio, using the included script:

```bash
# With the sample JSON
scripts/localpush.sh -p com.mycompany.myapp.debug scripts/sample-payload.json

# Choosing a device when several are connected
scripts/localpush.sh -p com.mycompany.myapp.debug -s emulator-5554 my-payload.json

# If adb is not on your PATH
ADB=~/Library/Android/sdk/platform-tools/adb scripts/localpush.sh -p … payload.json
```

JSON format (every field except title or body is optional):

```json
{
  "v": 1,
  "id": 100,
  "title": "Title",
  "body": "Message",
  "channelId": "orders",
  "channelName": "Orders",
  "importance": "HIGH",
  "deepLink": "myapp://orders/42",
  "smallIcon": "ic_stat_notification",
  "data": { "orderId": "42" }
}
```

Quick mode without a JSON file (only for text without spaces or special characters):

```bash
adb shell am broadcast -a com.localpush.debug.RECEIVE_LOCAL_PUSH \
  -n com.mycompany.myapp.debug/com.localpush.debug.LocalPushReceiver \
  --es title Hello --es body World --es importance HIGH --es data.orderId 42
```

Expected response:

```
Broadcast completed: result=1, data="id=… channel=…"
```

**In a UI test or CI:** run the script before an Espresso or UI Automator test to verify the app reacts to tapping the notification.

---

## 10. Working with multiple projects and devices

- **Multiple projects:** the plugin is installed once. Each project remembers its own form (applicationId, texts, etc.), stored in `.idea/workspace.xml`, which is not committed.
- **Multiple apps in the same project:** type or **Detect** the applicationId you want for each send; the dropdown keeps the detected ones.
- **Multiple devices at once:** pick each one in the dropdown. The plugin always uses `adb -s <serial>`, so it never mixes up devices.
- **Non-Android projects (e.g. opening just the plugin in IntelliJ):** the panel works the same; it only needs to find `adb`.

---

## 11. Update or uninstall

**Update the plugin:** pull the repository changes (`git pull`) and run the installer again (`install-plugin.command` / `install-plugin.cmd` / `scripts/install-plugin.sh`). It replaces the previous version. Then restart Android Studio.

**Uninstall the plugin:** `bash scripts/install-plugin.sh --uninstall` (Windows: `.\scripts\install-plugin.ps1 -Uninstall`), or from *Settings › Plugins › Installed › Local Push Mock › Uninstall*.

**Remove the module from an app:**
1. Delete the `debugImplementation(project(":localpush-debug"))` line.
2. Delete `include(":localpush-debug")` from `settings.gradle.kts`.
3. Delete the `localpush-debug/` folder and, if you created them, the handler and the `<meta-data>` in `src/debug`.

---

## 12. Troubleshooting

| What you see | What it means | How to fix it |
|---|---|---|
| Installer: `Android Studio not found` | It's in a non-standard location | Run the installer with `--dest "<plugins folder>"` (see [3.5](#35-installer-options)). |
| Installer: `No Java 21+ found` | Android Studio older than Ladybug | Update Android Studio, or set `JAVA_HOME` to a JDK 21. |
| Installer: build fails | No internet on the first run, or a corporate proxy | Connect and retry, or install a prebuilt `.zip` with `--zip`. |
| `adb not found` | The plugin can't locate the SDK | *Advanced › adb / SDK path* → enter the SDK folder. You can find it in *Settings › Android SDK › Android SDK Location*. |
| `No devices` | adb sees none | Start the emulator or connect the phone with USB debugging and click *Refresh*. |
| `… is 'unauthorized'` | The phone doesn't trust your computer | Unlock it and accept *Allow USB debugging?*. |
| `No app installed … exposes the receiver` | The installed app doesn't have the module | Did you install the **debug** variant after adding the module? Did you use `debugImplementation`? |
| `No receiver processed the broadcast` (result=0) | The applicationId doesn't match or the app lacks the module | Click **Detect**. Check the `.debug` suffix. |
| `… POST_NOTIFICATIONS not granted` | Android 13+ without permission | *Advanced › Grant POST_NOTIFICATIONS*. |
| `… notifications are disabled for the app in Settings` | The user turned them off | *Settings › Apps › your app › Notifications* → enable. |
| `channel 'X' already exists with importance …` | Android doesn't allow changing an existing channel's importance | Use another channel ID, or clear the app data (`adb shell pm clear <applicationId>`). |
| `channel 'X' is disabled in Settings` | The channel is turned off | Enable it in the app's notification settings. |
| `warnings: drawable 'X' not found` | The icon name doesn't exist | Check the name in `res/drawable`, without extension. |
| `warnings: no Activity in the app handles 'myapp://…'` | The deep link intent-filter is missing | See the [deep link recipe](#7-recipes-for-common-cases). |
| `Data payload: invalid JSON` | Syntax error in the Data field | Check quotes and commas, or use the `key=value` format. |
| `The app rejected the push: handler 'X' not found` | The `<meta-data>` points to a class that doesn't exist | Check the fully qualified class name and that it's in `src/debug`. |
| Green "Notification posted" but nothing shows up | *Do Not Disturb* mode or low importance | Turn off *Do Not Disturb*; use `HIGH` on a new channel. |

**See the logs on the phone side:**

```bash
adb logcat -s LocalPush
```

---

## 13. FAQ

**Does this send a real Firebase push?**
No. It creates a **local** notification with the same APIs Android uses to display a push. It's meant for testing the look, channels, tap behavior and deep links. To test your `onMessageReceived` code, use the [handler](#8-use-your-own-notification-code-optional).

**Can it reach production by mistake?**
No. The module is added with `debugImplementation`, so it doesn't exist in release. On top of that, it only accepts commands from `adb` (`DUMP` permission) and disables itself if the app isn't debuggable.

**Could another app on the phone send me fake notifications through this?**
No. Sending the broadcast requires the `android.permission.DUMP` permission, which only `adb` holds.

**Why doesn't the importance change when I change it in the form?**
It's an Android 8+ rule: once a channel is created, only the user can change its importance. Use a new channel ID.

**Do I need Firebase in my app?**
No. If you have it, the module reuses your FCM default icon, color and channel. If not, it uses the app icon.

**Does it work with emulators and physical devices?**
Yes, with anything that shows up in `adb devices`, including adb over Wi-Fi.

**Does it work in IntelliJ IDEA as well as Android Studio?**
Yes.

---

## 14. Quick checklist

**Once per computer**
- [ ] Double-click `install-plugin.command` (macOS) / `install-plugin.cmd` (Windows), or `bash scripts/install-plugin.sh` (Linux)
- [ ] Android Studio restarted
- [ ] **Local Push** tab visible

**Once per project**
- [ ] `localpush-debug/` folder copied to the root
- [ ] `include(":localpush-debug")` in `settings.gradle.kts`
- [ ] `debugImplementation(project(":localpush-debug"))` in `app/build.gradle.kts`
- [ ] `compileSdk`/`minSdk` aligned
- [ ] **Debug** app installed

**Every time you test**
- [ ] Refresh → pick a device
- [ ] Detect → applicationId
- [ ] (Android 13+, first time) Grant POST_NOTIFICATIONS
- [ ] Send notification → green line in the log
