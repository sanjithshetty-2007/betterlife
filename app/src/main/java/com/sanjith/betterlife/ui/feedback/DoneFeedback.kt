package com.sanjith.betterlife.ui.feedback

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.core.rememberReducedMotion
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Strong feedback when a task or habit is done (owner, 2026-11-08).

/**
 * A firm double buzz for "done" and a light tick for "undone". The vibrator directly, not the view's
 * haptic feedback: that one is weak and disappears when the phone's touch vibration is off.
 */
public class DoneHaptics(context: Context, private val enabled: () -> Boolean = { true }) {
    private val vibrator = context.getSystemService(Vibrator::class.java)

    public fun done() {
        buzz(VibrationEffect.createWaveform(DONE_TIMINGS, DONE_AMPLITUDES, NO_REPEAT))
    }

    public fun undone() {
        buzz(VibrationEffect.createOneShot(UNDONE_MILLIS, UNDONE_AMPLITUDE))
    }

    private fun buzz(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (enabled() && v.hasVibrator()) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
    }

    private companion object {
        val DONE_TIMINGS = longArrayOf(0, 70, 60, 130)
        val DONE_AMPLITUDES = intArrayOf(0, 255, 0, 255)
        const val NO_REPEAT = -1
        const val UNDONE_MILLIS = 30L
        const val UNDONE_AMPLITUDE = 120
    }
}

/** [DoneHaptics] that follows the owner's "Vibrate on done" setting. */
@Composable
public fun rememberDoneHaptics(): DoneHaptics {
    val context = LocalContext.current.applicationContext
    val settings by (context as BetterLifeApp).container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val on by rememberUpdatedState(settings?.habitTickHaptics != false)
    return remember { DoneHaptics(context) { on } }
}

private const val BURST_PIECES = 10
private const val BURST_START = 0.15f
private const val MARK_MILLIS = 480
private const val FILL_END = 0.45f

/**
 * A round mark: an empty ring, or a filled circle with a check. When [checked] turns true it pops in
 * with a burst of colour flying outwards; with the system animation scale off it just shows filled.
 */
@Composable
public fun DoneMark(checked: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        when {
            !checked -> progress.snapTo(0f)
            reduced -> progress.snapTo(1f)
            progress.value < 1f -> progress.animateTo(1f, tween(MARK_MILLIS, easing = LinearEasing))
        }
    }
    val check = painterResource(R.drawable.ic_check)
    val ring = BetterLifeColors.BoxOpenBorder
    val fill = MaterialTheme.colorScheme.primary
    val tint = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(size)) { drawMark(progress.value, ring, fill, tint, check) }
}

private fun DrawScope.drawMark(p: Float, ring: androidx.compose.ui.graphics.Color, fill: androidx.compose.ui.graphics.Color, tint: androidx.compose.ui.graphics.Color, check: Painter) {
    val r = size.minDimension / 2
    if (p < 1f) drawCircle(ring, r - Dimens.borderWidth.toPx(), style = Stroke(Dimens.borderWidth.toPx() * 2))
    if (p <= 0f) return
    // Grows to a little over full size, then settles.
    val grow = (p / FILL_END).coerceAtMost(1f)
    val settle = ((p - FILL_END) / (1f - FILL_END)).coerceIn(0f, 1f)
    drawCircle(fill, r * (grow * OVERSHOOT - settle * (OVERSHOOT - 1f)))
    val icon = r * CHECK_SCALE
    translate(center.x - icon / 2, center.y - icon / 2) {
        with(check) { draw(androidx.compose.ui.geometry.Size(icon, icon), alpha = grow, colorFilter = ColorFilter.tint(tint)) }
    }
    drawBurst(p, r)
}

/** Dots of the palette flying out from the mark, fading as they go; drawn past the mark's own bounds. */
internal fun DrawScope.drawBurst(p: Float, r: Float, centre: Offset = center) {
    if (p <= BURST_START || p >= 1f) return
    val t = (p - BURST_START) / (1f - BURST_START)
    val palette = BetterLifeColors.CategoryPalette
    repeat(BURST_PIECES) { i ->
        val angle = 2 * PI * i / BURST_PIECES
        val distance = r * (1f + t * BURST_REACH)
        drawCircle(
            palette[i % palette.size].copy(alpha = 1f - t),
            radius = r * BURST_DOT * (1f - t * 0.5f),
            center = Offset(centre.x + distance * cos(angle).toFloat(), centre.y + distance * sin(angle).toFloat()),
        )
    }
}

private const val OVERSHOOT = 1.2f
private const val CHECK_SCALE = 1.2f
private const val BURST_REACH = 1.1f
private const val BURST_DOT = 0.14f

/** How long the mark shows before the item actually leaves the list. */
private const val HOLD_MILLIS = 450L

/**
 * The tick on a task or Today row, a 48 dp target. A tap buzzes, shows the mark's burst, tells the
 * row at once through [onTicked] (so it can strike its title) and then calls [onDone], so the owner
 * sees it land before the row leaves. [onDone] always runs, even if the row is scrolled away first.
 */
@Composable
public fun DoneTick(description: String, onDone: () -> Unit, modifier: Modifier = Modifier, onTicked: () -> Unit = {}) {
    val haptics = rememberDoneHaptics()
    val reduced = rememberReducedMotion()
    var ticked by remember { mutableStateOf(false) }
    val delivered = remember { booleanArrayOf(false) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(ticked) {
        if (!ticked) return@LaunchedEffect
        if (!reduced) delay(HOLD_MILLIS)
        if (!delivered[0]) {
            delivered[0] = true
            done()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (ticked && !delivered[0]) {
                delivered[0] = true
                done()
            }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Dimens.minTouchTarget)
            .semantics { contentDescription = description }
            // A round ripple: the default square one looks like noise behind the round mark.
            .toggleable(
                value = ticked,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = Dimens.minTouchTarget / 2),
                enabled = !ticked,
                role = Role.Checkbox,
            ) {
                ticked = true
                haptics.done()
                onTicked()
            },
    ) { DoneMark(ticked, Dimens.doneMark) }
}
