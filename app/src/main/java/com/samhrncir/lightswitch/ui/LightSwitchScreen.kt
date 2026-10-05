package com.samhrncir.lightswitch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.samhrncir.lightswitch.R
import com.samhrncir.lightswitch.ShizukuHelper
import com.samhrncir.lightswitch.ThemeController
import com.samhrncir.lightswitch.widget.LightSwitchWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

@Composable
fun LightSwitchApp() {
    // Live value: MainActivity declares configChanges="uiMode", and Compose updates
    // LocalConfiguration in place, so this flips without recreating the activity.
    val isDark = isSystemInDarkTheme()
    LightSwitchTheme(dark = isDark) {
        LightSwitchScreen(isDark = isDark)
    }
}

@Composable
fun LightSwitchScreen(isDark: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermission by remember { mutableStateOf(ThemeController.hasWriteSecureSettings(context)) }
    // The permission may be granted from outside the app (ADB); re-check whenever we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = ThemeController.hasWriteSecureSettings(context)
    }
    // Home screen widgets behave differently with and without the permission.
    LaunchedEffect(hasPermission) {
        LightSwitchWidget.updateAll(context)
    }

    val errorNoPermission = stringResource(R.string.error_no_permission)
    val errorFailed = stringResource(R.string.error_failed)

    val background by animateColorAsState(
        targetValue = if (isDark) DarkRoom.background else LightRoom.background,
        animationSpec = tween(durationMillis = 400),
        label = "background",
    )
    val glow by animateColorAsState(
        targetValue = if (isDark) DarkRoom.glow else LightRoom.glow,
        animationSpec = tween(durationMillis = 400),
        label = "glow",
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(background)
                // A soft pool of light from a ceiling lamp above the switch.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow, Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.height * 0.75f,
                    ),
                )
            },
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Leave room for the setup card on short screens.
            val switchHeight = min(330.dp, maxHeight * if (hasPermission) 0.48f else 0.36f)
            val switchWidth = switchHeight * (2f / 3f)

            // Centered when it's just the switch; scrollable once the setup card is shown.
            val columnModifier = if (hasPermission) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            }

            Column(
                modifier = columnModifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (hasPermission) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(28.dp))

                LightSwitch(
                    isOn = !isDark,
                    onToggleRequest = { on ->
                        when (ThemeController.setSystemDark(context, dark = !on)) {
                            ThemeController.Result.APPLIED -> true
                            ThemeController.Result.NO_PERMISSION -> {
                                hasPermission = false
                                scope.launch { snackbarHostState.showSnackbar(errorNoPermission) }
                                false
                            }
                            ThemeController.Result.FAILED -> {
                                scope.launch { snackbarHostState.showSnackbar(errorFailed) }
                                false
                            }
                        }
                    },
                    width = switchWidth,
                    height = switchHeight,
                    contentDescription = stringResource(R.string.a11y_switch),
                    stateOnDescription = stringResource(R.string.a11y_state_on),
                    stateOffDescription = stringResource(R.string.a11y_state_off),
                )

                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(if (isDark) R.string.status_dark else R.string.status_light),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (hasPermission) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Spacer(Modifier.height(28.dp))
                    SetupCard(
                        onGranted = { hasPermission = true },
                        showMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Explains the one-time WRITE_SECURE_SETTINGS grant and offers two ways to do it:
 * on the phone through Shizuku, or from a computer over ADB.
 */
@Composable
private fun SetupCard(
    onGranted: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var shizukuState by remember { mutableStateOf(ShizukuHelper.state(context)) }
    var busy by remember { mutableStateOf(false) }

    // Shizuku's binder may arrive (or die) while we're on screen; refresh on both,
    // and whenever the user comes back from the Shizuku app.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        shizukuState = ShizukuHelper.state(context)
    }
    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener { shizukuState = ShizukuHelper.state(context) }
        val dead = Shizuku.OnBinderDeadListener { shizukuState = ShizukuHelper.state(context) }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
        }
    }

    val command = stringResource(R.string.setup_command)
    val copiedMessage = stringResource(R.string.setup_copied)
    val grantedMessage = stringResource(R.string.setup_granted)
    val notRunningMessage = stringResource(R.string.setup_shizuku_not_running)
    val deniedMessage = stringResource(R.string.setup_shizuku_denied)
    val failedTemplate = stringResource(R.string.setup_shizuku_failed)

    fun grantNow() {
        busy = true
        scope.launch {
            val error = withContext(Dispatchers.IO) { ShizukuHelper.grantWriteSecureSettings(context) }
            busy = false
            if (error == null) {
                showMessage(grantedMessage)
                onGranted()
            } else {
                showMessage(failedTemplate.format(error))
                shizukuState = ShizukuHelper.state(context)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- Option 1: Shizuku, no computer -------------------------------------
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setup_shizuku_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.setup_shizuku_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (shizukuState) {
                    ShizukuHelper.State.NOT_INSTALLED -> stringResource(R.string.setup_status_not_installed)
                    ShizukuHelper.State.NOT_RUNNING -> stringResource(R.string.setup_status_not_running)
                    ShizukuHelper.State.NEEDS_PERMISSION ->
                        stringResource(R.string.setup_status_needs_permission, ShizukuHelper.version() ?: 0)
                    ShizukuHelper.State.READY ->
                        stringResource(R.string.setup_status_ready, ShizukuHelper.version() ?: 0)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = {
                    when (ShizukuHelper.state(context).also { shizukuState = it }) {
                        ShizukuHelper.State.NOT_INSTALLED -> ShizukuHelper.openShizuku(context)
                        ShizukuHelper.State.NOT_RUNNING -> {
                            showMessage(notRunningMessage)
                            ShizukuHelper.openShizuku(context)
                        }
                        ShizukuHelper.State.NEEDS_PERMISSION -> {
                            ShizukuHelper.requestPermission { granted ->
                                shizukuState = ShizukuHelper.state(context)
                                if (granted) grantNow() else showMessage(deniedMessage)
                            }
                        }
                        ShizukuHelper.State.READY -> grantNow()
                    }
                },
            ) {
                Text(
                    text = stringResource(
                        when (shizukuState) {
                            ShizukuHelper.State.NOT_INSTALLED -> R.string.setup_shizuku_install
                            ShizukuHelper.State.NOT_RUNNING -> R.string.setup_shizuku_open
                            ShizukuHelper.State.NEEDS_PERMISSION -> R.string.setup_shizuku_allow
                            ShizukuHelper.State.READY -> R.string.setup_shizuku_grant
                        },
                    ),
                )
            }

            // --- Option 2: ADB from a computer ----------------------------------------
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setup_adb_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.setup_adb_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("adb command", command))
                        // Android 13+ shows its own "Copied" confirmation.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) showMessage(copiedMessage)
                    },
                ) {
                    Text(stringResource(R.string.setup_copy), textAlign = TextAlign.Center)
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_DISPLAY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.setup_open_settings), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.setup_done_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val versionName = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "?"
            }
            Text(
                text = stringResource(R.string.setup_app_version, versionName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }
    }
}
