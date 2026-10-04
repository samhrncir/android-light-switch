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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.samhrncir.lightswitch.ThemeController
import kotlinx.coroutines.launch

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
    // The permission is granted from a computer over ADB; re-check whenever we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = ThemeController.hasWriteSecureSettings(context)
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))

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

                Spacer(Modifier.weight(1f))

                if (!hasPermission) {
                    SetupCard(
                        onCopied = { message ->
                            if (message != null) scope.launch { snackbarHostState.showSnackbar(message) }
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** Explains the one-time ADB grant and offers a fallback to the system settings. */
@Composable
private fun SetupCard(onCopied: (String?) -> Unit) {
    val context = LocalContext.current
    val command = stringResource(R.string.setup_command)
    val copiedMessage = stringResource(R.string.setup_copied)

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
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("adb command", command))
                        // Android 13+ shows its own "Copied" confirmation.
                        onCopied(if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) copiedMessage else null)
                    },
                ) {
                    Text(stringResource(R.string.setup_copy), textAlign = TextAlign.Center)
                }
                OutlinedButton(
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
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.setup_done_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
