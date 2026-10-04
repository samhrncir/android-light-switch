package com.samhrncir.lightswitch

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log

/**
 * Reads and changes the system-wide dark theme.
 *
 * Android has no public API that lets a regular app flip the system dark theme:
 * [UiModeManager.setNightMode] is locked to privileged apps on phones. The
 * well-known workaround, used by automation apps, is to write the secure setting
 * `ui_night_mode` after the user grants `WRITE_SECURE_SETTINGS` over ADB. The
 * UI mode service only re-reads that setting when leaving car mode, so we enter
 * and immediately leave car mode to make the change take effect right away.
 */
object ThemeController {

    private const val TAG = "ThemeController"

    /** Hidden constant `Settings.Secure.UI_NIGHT_MODE`. */
    private const val KEY_UI_NIGHT_MODE = "ui_night_mode"

    /** Result of a theme change request. */
    enum class Result { APPLIED, NO_PERMISSION, FAILED }

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isSystemDark(context: Context): Boolean {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMask == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Requests the system theme to become dark or light.
     *
     * The actual configuration change arrives asynchronously; observe
     * `Configuration.uiMode` (or `isSystemInDarkTheme()` in Compose) for the result.
     */
    fun setSystemDark(context: Context, dark: Boolean): Result {
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
            ?: return Result.FAILED
        val mode = if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO

        // Cheapest path first: some builds (e.g. Automotive, or OEMs that don't lock
        // day/night mode) allow any app to set the night mode directly.
        runCatching { uiModeManager.setNightMode(mode) }
        if (uiModeManager.nightMode == mode) {
            return Result.APPLIED
        }

        if (!hasWriteSecureSettings(context)) {
            return Result.NO_PERMISSION
        }

        return try {
            Settings.Secure.putInt(context.contentResolver, KEY_UI_NIGHT_MODE, mode)
            // UiModeManagerService reloads ui_night_mode from settings when car mode
            // is turned off, which also pushes the new configuration to every app.
            uiModeManager.enableCarMode(0)
            uiModeManager.disableCarMode(0)
            Result.APPLIED
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing WRITE_SECURE_SETTINGS", e)
            Result.NO_PERMISSION
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to change night mode", e)
            Result.FAILED
        }
    }
}
