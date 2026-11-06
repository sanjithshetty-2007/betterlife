package com.sanjith.betterlife.ui.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.sanjith.betterlife.domain.mascot.MascotMood
import com.sanjith.betterlife.domain.mascot.MascotStage
import com.sanjith.betterlife.ui.theme.BetterLifeColors

// All sizes are fractions of the drawing's shorter side (u), so Blip scales from a widget to a
// full-screen celebration. Blip is drawn centred in a square of side u.

private const val BOUNCE_HEIGHT = 0.04f
private const val ARM_SWING_DEGREES = 25f

/** Draws Blip in [mood] at [stage] with [pose]. */
internal fun DrawScope.drawMascot(mood: MascotMood, stage: MascotStage, pose: MascotPose) {
    val u = size.minDimension
    translate((size.width - u) / 2, (size.height - u) / 2 - pose.bounce * BOUNCE_HEIGHT * u) {
        if (stage == MascotStage.POD) drawPod(u, pose) else drawBlob(u, mood, pose)
    }
}

/** Stage 1: a glowing egg with a warm zigzag and a dot of light. */
private fun DrawScope.drawPod(u: Float, pose: MascotPose) {
    val c = Offset(u * 0.5f, u * 0.56f)
    glow(c, u * 0.42f, pose.glow * 0.5f)
    drawOval(BetterLifeColors.Cherry, topLeft = Offset(c.x - u * 0.24f, c.y - u * 0.32f), size = Size(u * 0.48f, u * 0.62f))
    drawOval(BetterLifeColors.MascotSheen, topLeft = Offset(c.x - u * 0.16f, c.y - u * 0.26f), size = Size(u * 0.14f, u * 0.2f))
    val zig = Path().apply {
        moveTo(c.x - u * 0.12f, c.y - u * 0.02f)
        var x = c.x - u * 0.12f
        var up = true
        repeat(6) {
            x += u * 0.04f
            lineTo(x, if (up) c.y - u * 0.06f else c.y - u * 0.02f)
            up = !up
        }
    }
    drawPath(zig, BetterLifeColors.AccentYellow.copy(alpha = pose.glow), style = Stroke(u * 0.015f, cap = StrokeCap.Round))
    drawCircle(BetterLifeColors.AccentYellow.copy(alpha = pose.glow), u * 0.022f, Offset(c.x, c.y + u * 0.08f))
}

/** Stage 2 and up: the round blob with arms, antenna, eyes, cheeks and mouth. */
private fun DrawScope.drawBlob(u: Float, mood: MascotMood, pose: MascotPose) {
    val body = Offset(u * 0.5f, u * 0.6f)
    val rx = u * 0.34f
    val ry = u * 0.28f
    drawAntenna(u, body, pose)
    drawArms(u, body, rx, pose)
    drawOval(BetterLifeColors.Cherry, topLeft = Offset(body.x - rx, body.y - ry), size = Size(rx * 2, ry * 2))
    // A soft sheen on the top-left, like the design's glossy body.
    drawOval(BetterLifeColors.MascotSheen, topLeft = Offset(body.x - rx * 0.7f, body.y - ry * 0.85f), size = Size(rx * 0.7f, ry * 0.35f))
    val eyeY = body.y - ry * 0.12f
    val left = Offset(body.x - rx * 0.36f, eyeY)
    val right = Offset(body.x + rx * 0.36f, eyeY)
    when (mood) {
        MascotMood.PROUD -> { happyEye(u, left); happyEye(u, right) }
        MascotMood.SLEEPY -> { sleepyEye(u, left); sleepyEye(u, right); drawZ(u, pose.phase) }
        else -> { openEye(u, left, pose.eyeOpen, mood); openEye(u, right, pose.eyeOpen, mood) }
    }
    if (mood == MascotMood.DISAPPOINTED) { sadBrow(u, left, inner = 1f); sadBrow(u, right, inner = -1f) }
    if (mood != MascotMood.DISAPPOINTED && mood != MascotMood.SLEEPY) {
        val cheekY = eyeY + u * 0.1f
        drawOval(BetterLifeColors.MascotBlush, topLeft = Offset(left.x - u * 0.07f, cheekY), size = Size(u * 0.09f, u * 0.045f))
        drawOval(BetterLifeColors.MascotBlush, topLeft = Offset(right.x - u * 0.02f, cheekY), size = Size(u * 0.09f, u * 0.045f))
    }
    drawMouth(u, Offset(body.x, eyeY + u * 0.13f), mood)
}

private fun DrawScope.drawAntenna(u: Float, body: Offset, pose: MascotPose) {
    val base = Offset(body.x, body.y - u * 0.26f)
    val bulb = Offset(body.x + u * 0.07f, body.y - u * 0.47f)
    val stalk = Path().apply {
        moveTo(base.x, base.y)
        // Ends at the bulb's lower edge, so a dimmed (see-through) bulb never shows the stalk inside it.
        quadraticTo(body.x - u * 0.03f, body.y - u * 0.38f, bulb.x - u * 0.015f, bulb.y + u * 0.045f)
    }
    drawPath(stalk, BetterLifeColors.Cherry, style = Stroke(u * 0.03f, cap = StrokeCap.Round))
    glow(bulb, u * 0.14f, pose.glow)
    val bulbColor = if (pose.glow >= 1f - 0.2f) BetterLifeColors.AccentYellow else BetterLifeColors.BulbDim
    drawCircle(bulbColor, u * 0.05f, bulb)
    drawCircle(BetterLifeColors.MascotSheen, u * 0.018f, Offset(bulb.x - u * 0.015f, bulb.y - u * 0.015f))
}

/** Stubby arms on each side; [MascotPose.wave] lifts them (cheering) or lets them hang (down). */
private fun DrawScope.drawArms(u: Float, body: Offset, rx: Float, pose: MascotPose) {
    listOf(-1f, 1f).forEach { side ->
        val pivot = Offset(body.x + side * rx * 0.95f, body.y)
        rotate(degrees = -side * pose.wave * ARM_SWING_DEGREES, pivot = pivot) {
            drawOval(
                BetterLifeColors.Cherry,
                topLeft = Offset(pivot.x - u * 0.05f + side * u * 0.02f, pivot.y - u * 0.09f),
                size = Size(u * 0.1f, u * 0.16f),
            )
            drawOval(
                BetterLifeColors.MascotShade,
                topLeft = Offset(pivot.x - u * 0.05f + side * u * 0.02f, pivot.y - u * 0.09f),
                size = Size(u * 0.1f, u * 0.16f),
                style = Stroke(u * 0.006f),
            )
        }
    }
}

private fun DrawScope.openEye(u: Float, at: Offset, open: Float, mood: MascotMood) {
    val w = u * 0.15f
    val h = u * 0.17f * open.coerceIn(0.08f, 1f)
    drawOval(BetterLifeColors.Text, topLeft = Offset(at.x - w / 2, at.y - h / 2), size = Size(w, h))
    if (open < 0.5f) return
    // Curious looks up and aside; down looks at the floor; cheering looks straight at you.
    val look = when (mood) {
        MascotMood.CURIOUS -> Offset(w * 0.12f, -h * 0.12f)
        MascotMood.DISAPPOINTED -> Offset(0f, h * 0.16f)
        else -> Offset(w * 0.06f, 0f)
    }
    val pupil = Offset(at.x + look.x, at.y + look.y)
    drawCircle(BetterLifeColors.Background, w * 0.3f, pupil)
    drawCircle(BetterLifeColors.Text, w * 0.11f, Offset(pupil.x - w * 0.1f, pupil.y - w * 0.1f))
}

/** Proud: closed, smiling eyes (^ ^). */
private fun DrawScope.happyEye(u: Float, at: Offset) {
    val r = u * 0.06f
    drawArc(BetterLifeColors.Background, 200f, 140f, false, Offset(at.x - r, at.y - r * 0.4f), Size(r * 2, r * 2), style = Stroke(u * 0.022f, cap = StrokeCap.Round))
}

/** Sleepy: closed, drooping eyes. */
private fun DrawScope.sleepyEye(u: Float, at: Offset) {
    val r = u * 0.06f
    drawArc(BetterLifeColors.Background, 20f, 140f, false, Offset(at.x - r, at.y - r * 1.4f), Size(r * 2, r * 2), style = Stroke(u * 0.022f, cap = StrokeCap.Round))
}

/** Down: brows tilted up in the middle. Sad, never angry. */
private fun DrawScope.sadBrow(u: Float, at: Offset, inner: Float) {
    val y = at.y - u * 0.11f
    drawLine(
        BetterLifeColors.Background,
        Offset(at.x - inner * u * 0.065f, y + u * 0.015f),
        Offset(at.x + inner * u * 0.03f, y - u * 0.015f),
        strokeWidth = u * 0.02f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawMouth(u: Float, at: Offset, mood: MascotMood) {
    val stroke = Stroke(u * 0.02f, cap = StrokeCap.Round)
    when (mood) {
        MascotMood.CHEERING -> {
            val w = u * 0.14f
            val path = Path().apply {
                moveTo(at.x - w / 2, at.y - u * 0.02f)
                quadraticTo(at.x, at.y + u * 0.09f, at.x + w / 2, at.y - u * 0.02f)
                close()
            }
            drawPath(path, BetterLifeColors.Background)
            drawOval(BetterLifeColors.MascotBlush, topLeft = Offset(at.x - u * 0.035f, at.y + u * 0.012f), size = Size(u * 0.07f, u * 0.03f))
        }
        MascotMood.PROUD -> drawArc(BetterLifeColors.Background, 20f, 140f, false, Offset(at.x - u * 0.05f, at.y - u * 0.06f), Size(u * 0.1f, u * 0.08f), style = stroke)
        MascotMood.DISAPPOINTED -> drawArc(BetterLifeColors.Background, 200f, 140f, false, Offset(at.x - u * 0.045f, at.y), Size(u * 0.09f, u * 0.07f), style = stroke)
        MascotMood.CURIOUS, MascotMood.SLEEPY -> drawCircle(BetterLifeColors.Background, u * 0.018f, at)
    }
}

/** A small "z" that drifts up and fades as [phase] goes from 0 to 1. */
private fun DrawScope.drawZ(u: Float, phase: Float) {
    val s = u * 0.06f
    val origin = Offset(u * 0.74f, u * 0.3f - phase * u * 0.12f)
    val z = Path().apply {
        moveTo(origin.x, origin.y)
        lineTo(origin.x + s, origin.y)
        lineTo(origin.x, origin.y + s)
        lineTo(origin.x + s, origin.y + s)
    }
    drawPath(z, BetterLifeColors.TextMuted.copy(alpha = 1f - phase * 0.8f), style = Stroke(u * 0.012f, cap = StrokeCap.Round))
}

/** The soft yellow halo of the mood light. */
private fun DrawScope.glow(center: Offset, radius: Float, strength: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(BetterLifeColors.AccentYellow.copy(alpha = 0.55f * strength.coerceIn(0f, 1f)), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
