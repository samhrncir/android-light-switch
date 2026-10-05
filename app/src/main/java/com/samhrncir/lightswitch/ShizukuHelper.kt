package com.samhrncir.lightswitch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Grants WRITE_SECURE_SETTINGS to this app without a computer, through Shizuku
 * (https://shizuku.rikka.app). Shizuku runs a shell-level service that the user
 * starts once from the phone via Wireless debugging; we ask it to run
 * `pm grant <our package> WRITE_SECURE_SETTINGS`. The grant is permanent, so
 * Shizuku is only needed once.
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 0x51

    enum class State { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun state(context: Context): State {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) return if (isInstalled(context)) State.NOT_RUNNING else State.NOT_INSTALLED
        // Pre-v11 Shizuku used a normal runtime permission; far too old to bother with.
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) return State.NOT_RUNNING
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return if (granted) State.READY else State.NEEDS_PERMISSION
    }

    /** Shizuku server version, or null when it is not running. */
    fun version(): Int? = runCatching {
        if (Shizuku.pingBinder()) Shizuku.getVersion() else null
    }.getOrNull()

    /** Opens Shizuku if installed, otherwise its Play Store page. */
    fun openShizuku(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        val intent = launch
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** Asks the user, through Shizuku's own dialog, to let this app use Shizuku. */
    fun requestPermission(onResult: (granted: Boolean) -> Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }.onFailure {
            Shizuku.removeRequestPermissionResultListener(listener)
            Log.w(TAG, "requestPermission failed", it)
            onResult(false)
        }
    }

    /**
     * Runs `pm grant` as the shell user through Shizuku. Blocking: call off the main thread.
     * Returns the error text on failure, null on success.
     */
    fun grantWriteSecureSettings(context: Context): String? {
        val command = arrayOf(
            "pm", "grant", context.packageName, Manifest.permission.WRITE_SECURE_SETTINGS,
        )
        return try {
            // newProcess is deliberately not public in the Shizuku API, but it is the
            // simplest way to run one shell command and is widely relied upon.
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val process = method.invoke(null, command, null, null) as Process
            val exit = process.waitFor()
            val error = process.errorStream.bufferedReader().readText().trim()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (exit == 0 && ThemeController.hasWriteSecureSettings(context)) {
                null
            } else {
                (error.ifEmpty { output }).ifEmpty { "exit code $exit" }
            }
        } catch (e: Exception) {
            Log.w(TAG, "pm grant via Shizuku failed", e)
            e.message ?: e.javaClass.simpleName
        }
    }
}
