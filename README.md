# Light Switch

An Android app that flips your phone between the system **light** and **dark**
theme with a wall light switch. Lever up = lights on (light theme), lever down =
lights off (dark theme). Tap it, drag it, or flick it.

Built with Kotlin and Jetpack Compose. Requires Android 10 (API 29) or newer,
which is when Android gained a system-wide dark theme.

## One-time setup

Android does not let ordinary apps change the system theme. The app needs the
`WRITE_SECURE_SETTINGS` permission, which only a shell (ADB) can grant. You do
this once; it survives reboots and app updates (but not an uninstall).

### Option A: from the phone, no computer (Android 11+)

1. Install [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
   from Google Play and open it.
2. Tap **Start via Wireless debugging** and follow Shizuku's on-screen steps
   (turn on Developer options, enable Wireless debugging, pair, start).
3. Open Light Switch and tap **Grant permission** on the setup card. Approve the
   Shizuku prompt. Done; the switch works and Shizuku is no longer needed.

### Option B: from a computer

1. Enable **Developer options** and **USB debugging** on the phone.
2. Connect the phone and run in a terminal:

   ```sh
   adb shell pm grant com.samhrncir.lightswitch android.permission.WRITE_SECURE_SETTINGS
   ```

3. Reopen the app. The setup card disappears and the switch works.

The app shows both options, with a **Copy** button for the command, until the
permission is granted.

## Installing on your phone

Every push builds the app on GitHub Actions and publishes the APK under
**Releases → Latest build** (also as a workflow artifact).

1. On the phone, open the repository's Releases page and download `LightSwitch.apk`.
2. Open the downloaded file. Allow installs from your browser if asked.
3. Complete the ADB step above once.

Builds are signed with the debug key in `app/debug.keystore`, so newer APKs
install over older ones without uninstalling.

## Building yourself

Open the project in Android Studio (Ladybug or newer) and run it, or from the
command line with the Android SDK installed:

```sh
./gradlew :app:installDebug
```

## How it works

- `ThemeController` writes the secure setting `ui_night_mode` (1 = light,
  2 = dark). Android's UI mode service only re-reads that setting when leaving
  car mode, so the app enters and immediately exits car mode to apply the change
  at once. This is the same technique automation apps use. You may notice the
  car-mode notification flash for a split second.
- On builds that do not lock day/night mode (for example Android Automotive),
  `UiModeManager.setNightMode` works directly and is tried first.
- `LightSwitch` is a Canvas-drawn composable. It animates the lever as soon as
  you interact, asks the controller to change the theme, and springs back if the
  system never confirms the change.
- `ShizukuHelper` asks Shizuku, when the user has started it, to run
  `pm grant` for this app so the permission can be granted from the phone itself.
- `MainActivity` handles `uiMode` configuration changes itself so the switch
  animation is not interrupted when the theme flips.
