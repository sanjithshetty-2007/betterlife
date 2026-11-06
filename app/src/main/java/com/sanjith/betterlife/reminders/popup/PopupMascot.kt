package com.sanjith.betterlife.reminders.popup

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens

private const val BLINK_CYCLE_MS = 4_000
private const val BLINK_START_MS = 3_700
private const val BLINK_CLOSED_MS = 3_820
private const val EYE_OPEN = 1f
private const val EYE_CLOSED = 0.12f

/**
 * Placeholder mascot "Blip" in its curious mood: a round maroon blob with big glossy eyes looking up
 * and an antenna with a glowing yellow bulb. Prompt 16 replaces it with the full mascot. Blinks every
 * few seconds unless animations are off.
 */
@Composable
public fun PopupMascot(reduceMotion: Boolean, description: String, mascotSize: Dp = Dimens.mascotSmall) {
    val eyeOpen = if (reduceMotion) {
        EYE_OPEN
    } else {
        val blink by rememberInfiniteTransition(label = "blink").animateFloat(
            initialValue = EYE_OPEN,
            targetValue = EYE_OPEN,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = BLINK_CYCLE_MS
                    EYE_OPEN at BLINK_START_MS
                    EYE_CLOSED at BLINK_CLOSED_MS
                },
                RepeatMode.Restart,
            ),
            label = "eye",
        )
        blink
    }
    Canvas(
        modifier = Modifier
            .size(mascotSize)
            .semantics { contentDescription = description },
    ) {
        drawAntenna()
        drawBody()
        drawEye(Offset(size.width * 0.36f, size.height * 0.58f), eyeOpen)
        drawEye(Offset(size.width * 0.64f, size.height * 0.58f), eyeOpen)
    }
}

private fun DrawScope.drawAntenna() {
    val top = Offset(center.x + size.width * 0.12f, size.height * 0.1f)
    drawLine(BetterLifeColors.Cherry, Offset(center.x, size.height * 0.3f), top, strokeWidth = size.width * 0.05f)
    // The mood light: a soft glow around a bright bulb.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(BetterLifeColors.AccentYellow.copy(alpha = GLOW_ALPHA), BetterLifeColors.AccentYellow.copy(alpha = 0f)),
            center = top,
            radius = size.width * 0.16f,
        ),
        radius = size.width * 0.16f,
        center = top,
    )
    drawCircle(BetterLifeColors.AccentYellow, radius = size.width * 0.07f, center = top)
}

private fun DrawScope.drawBody() {
    val radius = size.width * 0.38f
    val bodyCenter = Offset(center.x, size.height * 0.62f)
    drawCircle(BetterLifeColors.Cherry, radius = radius, center = bodyCenter)
    drawCircle(BetterLifeColors.SoftBorder, radius = radius, center = bodyCenter, style = Stroke(width = size.width * 0.02f))
}

private fun DrawScope.drawEye(at: Offset, open: Float) {
    val w = size.width * 0.17f
    val h = w * 1.15f * open
    drawOval(BetterLifeColors.Text, topLeft = Offset(at.x - w / 2, at.y - h / 2), size = Size(w, h))
    if (open < 0.5f) return
    // Curious: pupils look up and to the side, with a glossy highlight.
    val pupil = Offset(at.x + w * 0.12f, at.y - h * 0.12f)
    drawCircle(BetterLifeColors.Background, radius = w * 0.3f, center = pupil)
    drawCircle(BetterLifeColors.Text, radius = w * 0.1f, center = Offset(pupil.x - w * 0.1f, pupil.y - w * 0.1f))
}

/** How bright the antenna glow starts before fading out. */
private const val GLOW_ALPHA = 0.6f
