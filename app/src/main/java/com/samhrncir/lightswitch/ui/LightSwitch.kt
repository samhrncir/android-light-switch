package com.samhrncir.lightswitch.ui

import android.view.SoundEffectConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How far a finger has to travel for the lever to go from one end to the other. */
private val DragTravel: Dp = 140.dp

/** Vertical fling speed (px/s) that flips the switch regardless of where the lever is. */
private const val FlingThresholdPxPerSecond = 500f

/** Time to wait for the system to confirm a theme change before the lever springs back. */
private const val ConfirmationTimeoutMillis = 2_000L

/**
 * A wall light switch. Lever up = ON (light theme), lever down = OFF (dark theme).
 *
 * The lever can be tapped or dragged/flicked up and down. It animates immediately to
 * give instant feedback, then [onToggleRequest] is asked to perform the change. The
 * caller reports back through [isOn]; if the system never confirms the new state the
 * lever springs back on its own.
 *
 * @param isOn current state as reported by the system (true = light theme).
 * @param onToggleRequest called with the requested state; return true if the request was
 *   accepted, false if it was refused (the lever then springs back right away).
 */
@Composable
fun LightSwitch(
    isOn: Boolean,
    onToggleRequest: (Boolean) -> Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 220.dp,
    height: Dp = 330.dp,
    contentDescription: String = "Light switch",
    stateOnDescription: String = "On",
    stateOffDescription: String = "Off",
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val textMeasurer = rememberTextMeasurer()
    val travelPx = with(LocalDensity.current) { DragTravel.toPx() }

    val latestIsOn by rememberUpdatedState(isOn)
    val latestOnToggle by rememberUpdatedState(onToggleRequest)

    // 1f = lever up (ON), 0f = lever down (OFF).
    val progress = remember { Animatable(if (isOn) 1f else 0f) }
    val settleSpec = remember { spring<Float>(dampingRatio = 0.55f, stiffness = 900f) }

    var dragging by remember { mutableStateOf(false) }
    var dragStartProgress by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }

    // The state we asked the system for and are still waiting on, if any.
    var pendingTarget by remember { mutableStateOf<Boolean?>(null) }

    // Follow the system: both for confirmations of our own request and for changes
    // made elsewhere (e.g. the user flips dark theme in Settings while we're open).
    LaunchedEffect(isOn) {
        if (pendingTarget == isOn) pendingTarget = null
        if (!dragging) progress.animateTo(if (isOn) 1f else 0f, settleSpec)
    }

    // If a request was accepted but the system never changed, spring back.
    LaunchedEffect(pendingTarget) {
        val target = pendingTarget ?: return@LaunchedEffect
        delay(ConfirmationTimeoutMillis)
        if (pendingTarget == target && latestIsOn != target) {
            pendingTarget = null
            progress.animateTo(if (latestIsOn) 1f else 0f, settleSpec)
        }
    }

    fun settle(target: Boolean) {
        scope.launch { progress.animateTo(if (target) 1f else 0f, settleSpec) }
        if (target == latestIsOn) return

        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        view.playSoundEffect(SoundEffectConstants.CLICK)

        val accepted = latestOnToggle(target)
        if (accepted) {
            pendingTarget = target
        } else {
            scope.launch {
                delay(350)
                progress.animateTo(if (latestIsOn) 1f else 0f, settleSpec)
            }
        }
    }

    val dragState = rememberDraggableState { deltaY ->
        dragDistance += deltaY
        // Dragging up (negative y) moves the lever up.
        val p = (dragStartProgress - dragDistance / travelPx).coerceIn(0f, 1f)
        scope.launch { progress.snapTo(p) }
    }

    val colors = rememberSwitchColors(isOn)

    Canvas(
        modifier = modifier
            .size(width, height)
            .semantics {
                role = Role.Switch
                this.contentDescription = contentDescription
                stateDescription = if (isOn) stateOnDescription else stateOffDescription
                onClick {
                    settle(!latestIsOn)
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { settle(!latestIsOn) })
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    dragging = true
                    dragStartProgress = progress.value
                    dragDistance = 0f
                },
                onDragStopped = { velocity ->
                    dragging = false
                    val target = when {
                        velocity < -FlingThresholdPxPerSecond -> true
                        velocity > FlingThresholdPxPerSecond -> false
                        else -> progress.value >= 0.5f
                    }
                    settle(target)
                },
            ),
    ) {
        drawLightSwitch(progress.value, colors, textMeasurer)
    }
}

/** Colors of the physical switch; they follow the room (system theme), not the lever. */
class SwitchColors(
    val plateTop: Color,
    val plateBottom: Color,
    val plateEdge: Color,
    val plateShadow: Color,
    val recess: Color,
    val leverLight: Color,
    val leverShade: Color,
    val leverSide: Color,
    val screw: Color,
    val screwDetail: Color,
    val label: Color,
    val lampOn: Color,
    val lampOff: Color,
)

@Composable
private fun rememberSwitchColors(lightRoom: Boolean): SwitchColors {
    val spec = tween<Color>(durationMillis = 350)

    fun pick(light: Long, dark: Long) = Color(if (lightRoom) light else dark)

    val plateTop by animateColorAsState(pick(0xFFF8F3E8, 0xFF363A43), spec, label = "plateTop")
    val plateBottom by animateColorAsState(pick(0xFFE3DAC8, 0xFF22252C), spec, label = "plateBottom")
    val plateEdge by animateColorAsState(pick(0xFFFFFFFF, 0xFF4B505B), spec, label = "plateEdge")
    val plateShadow by animateColorAsState(pick(0x40000000, 0x80000000), spec, label = "plateShadow")
    val recess by animateColorAsState(pick(0xFF2E2A26, 0xFF0C0D10), spec, label = "recess")
    val leverLight by animateColorAsState(pick(0xFFFFFFFF, 0xFF585D67), spec, label = "leverLight")
    val leverShade by animateColorAsState(pick(0xFFD6CFC2, 0xFF30343B), spec, label = "leverShade")
    val leverSide by animateColorAsState(pick(0xFF9A9286, 0xFF1A1C21), spec, label = "leverSide")
    val screw by animateColorAsState(pick(0xFFC9C1B2, 0xFF5A5F69), spec, label = "screw")
    val screwDetail by animateColorAsState(pick(0xFF8E8678, 0xFF2B2E35), spec, label = "screwDetail")
    val label by animateColorAsState(pick(0xFFA89E8C, 0xFF6B7180), spec, label = "label")
    val lampOn by animateColorAsState(pick(0xFFFFB13D, 0xFFFFB13D), spec, label = "lampOn")
    val lampOff by animateColorAsState(pick(0xFF8C6A3A, 0xFF3A3024), spec, label = "lampOff")

    return SwitchColors(
        plateTop = plateTop,
        plateBottom = plateBottom,
        plateEdge = plateEdge,
        plateShadow = plateShadow,
        recess = recess,
        leverLight = leverLight,
        leverShade = leverShade,
        leverSide = leverSide,
        screw = screw,
        screwDetail = screwDetail,
        label = label,
        lampOn = lampOn,
        lampOff = lampOff,
    )
}

/**
 * Draws the switch. [progress] is 0f (lever down / OFF) to 1f (lever up / ON).
 */
private fun DrawScope.drawLightSwitch(progress: Float, c: SwitchColors, textMeasurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val p = progress.coerceIn(0f, 1f)
    val tilt = p * 2f - 1f // -1 = fully down, +1 = fully up

    // --- Wall plate -------------------------------------------------------------
    val plateCorner = CornerRadius(w * 0.07f)
    drawRoundRect(
        color = c.plateShadow,
        topLeft = Offset(0f, h * 0.02f),
        size = Size(w, h),
        cornerRadius = plateCorner,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(c.plateTop, c.plateBottom)),
        size = Size(w, h),
        cornerRadius = plateCorner,
    )
    drawRoundRect(
        color = c.plateEdge,
        topLeft = Offset(w * 0.006f, h * 0.004f),
        size = Size(w * 0.988f, h * 0.992f),
        cornerRadius = plateCorner,
        style = Stroke(width = w * 0.012f),
    )

    // --- Screws -------------------------------------------------------------------
    val screwRadius = w * 0.032f
    for (cy in listOf(h * 0.085f, h * 0.915f)) {
        val center = Offset(w / 2f, cy)
        drawCircle(color = c.screw, radius = screwRadius, center = center)
        drawCircle(
            color = c.screwDetail,
            radius = screwRadius,
            center = center,
            style = Stroke(width = screwRadius * 0.28f),
        )
        drawLine(
            color = c.screwDetail,
            start = Offset(center.x - screwRadius * 0.62f, cy),
            end = Offset(center.x + screwRadius * 0.62f, cy),
            strokeWidth = screwRadius * 0.34f,
            cap = StrokeCap.Round,
        )
    }

    // --- Labels -------------------------------------------------------------------
    val labelStyle = TextStyle(
        color = c.label,
        fontSize = (w * 0.075f).toSp(),
        fontWeight = FontWeight.Bold,
        letterSpacing = (w * 0.012f).toSp(),
    )
    val onLayout = textMeasurer.measure(AnnotatedString("ON"), labelStyle)
    val offLayout = textMeasurer.measure(AnnotatedString("OFF"), labelStyle)
    drawText(
        textLayoutResult = onLayout,
        topLeft = Offset((w - onLayout.size.width) / 2f, h * 0.175f),
    )
    drawText(
        textLayoutResult = offLayout,
        topLeft = Offset((w - offLayout.size.width) / 2f, h * 0.825f - offLayout.size.height),
    )

    // --- Recess the lever sits in ----------------------------------------------------
    val recessW = w * 0.42f
    val recessH = h * 0.50f
    val recessTopLeft = Offset((w - recessW) / 2f, (h - recessH) / 2f)
    val recessCorner = CornerRadius(recessW * 0.09f)
    drawRoundRect(
        color = c.recess,
        topLeft = recessTopLeft,
        size = Size(recessW, recessH),
        cornerRadius = recessCorner,
    )
    // Inner shadow along the top lip of the recess.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
            startY = recessTopLeft.y,
            endY = recessTopLeft.y + recessH * 0.22f,
        ),
        topLeft = recessTopLeft,
        size = Size(recessW, recessH),
        cornerRadius = recessCorner,
    )

    // --- Lever ----------------------------------------------------------------------
    val leverW = recessW * 0.80f
    val leverH = recessH * 0.56f
    val travel = recessH * 0.20f
    val leverCenterY = h / 2f - tilt * travel
    val leverTopLeft = Offset((w - leverW) / 2f, leverCenterY - leverH / 2f)
    val leverCorner = CornerRadius(leverW * 0.14f)
    val leverSize = Size(leverW, leverH)

    // Shadow cast into the recess. Grows a little as the lever stands further out.
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.38f),
        topLeft = leverTopLeft + Offset(leverW * 0.05f, leverH * 0.07f + abs(tilt) * leverH * 0.05f),
        size = leverSize,
        cornerRadius = leverCorner,
    )

    // Side of the lever: visible under the face when the lever is up, above it when down.
    val sideDepth = leverH * 0.16f * abs(tilt)
    if (sideDepth > 0.5f) {
        val sideOffset = if (tilt > 0f) sideDepth else -sideDepth
        drawRoundRect(
            color = c.leverSide,
            topLeft = leverTopLeft + Offset(0f, sideOffset),
            size = leverSize,
            cornerRadius = leverCorner,
        )
    }

    // Face: the protruding end catches the light.
    val faceTop = lerp(c.leverShade, c.leverLight, p)
    val faceBottom = lerp(c.leverLight, c.leverShade, p)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(faceTop, faceBottom),
            startY = leverTopLeft.y,
            endY = leverTopLeft.y + leverH,
        ),
        topLeft = leverTopLeft,
        size = leverSize,
        cornerRadius = leverCorner,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.18f),
        topLeft = leverTopLeft,
        size = leverSize,
        cornerRadius = leverCorner,
        style = Stroke(width = leverW * 0.02f),
    )

    // Indicator lamp on the lever face, near the protruding end.
    val lampRadius = leverW * 0.075f
    val lampCenter = Offset(w / 2f, leverCenterY - tilt * leverH * 0.30f)
    val lampColor = lerp(c.lampOff, c.lampOn, p)
    if (p > 0.05f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c.lampOn.copy(alpha = 0.55f * p), Color.Transparent),
                center = lampCenter,
                radius = lampRadius * 4f,
            ),
            radius = lampRadius * 4f,
            center = lampCenter,
        )
    }
    drawCircle(color = lampColor, radius = lampRadius, center = lampCenter)
    drawCircle(
        color = Color.Black.copy(alpha = 0.25f),
        radius = lampRadius,
        center = lampCenter,
        style = Stroke(width = lampRadius * 0.25f),
    )
}
