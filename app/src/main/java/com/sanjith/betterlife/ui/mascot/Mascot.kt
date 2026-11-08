package com.sanjith.betterlife.ui.mascot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sanjith.betterlife.core.rememberReducedMotion
import com.sanjith.betterlife.domain.mascot.MascotMood
import com.sanjith.betterlife.domain.mascot.MascotStage
import kotlin.math.PI
import kotlin.math.sin

/**
 * How Blip stands at one moment: [eyeOpen] 0 (closed) to 1, [bounce] -1 to 1 (up and down),
 * [wave] -1 to 1 (arms), [glow] 0 to 1 (the bulb), [phase] 0 to 1 (the floating "z" of sleepy).
 */
public data class MascotPose(
    val eyeOpen: Float = 1f,
    val bounce: Float = 0f,
    val wave: Float = 0f,
    val glow: Float = 1f,
    val phase: Float = 0f,
)

private const val CYCLE_MS = 2_400
private const val BLINK_CYCLE_MS = 4_000
private const val BLINK_START_MS = 3_700
private const val BLINK_CLOSED_MS = 3_820
private const val EYE_CLOSED = 0.1f
private const val GLOW_LOW = 0.55f
private const val TWO_PI = 2 * PI

/**
 * Blip (Prompt 16), drawn on one Canvas: a round maroon blob with big glossy eyes, stubby arms and
 * an antenna with a glowing yellow bulb (the mood light). Each mood has its own gentle loop; with
 * the system animation scale off it stands still in that mood's pose. [stage] decides the shape
 * (stages 3 to 5 come in Prompt 27 and are drawn as BABY until then).
 */
@Composable
public fun Mascot(mood: MascotMood, stage: MascotStage, description: String?, modifier: Modifier = Modifier) {
    if (rememberReducedMotion()) {
        MascotStatic(mood, stage, description, modifier)
    } else {
        val pose = animatedPose(mood)
        // Read while drawing: the loop repeats the drawing and recomposes nothing.
        Canvas(modifier.described(description)) { drawMascot(mood, stage, pose()) }
    }
}

/** Blip without any animation, for places that cannot animate (home-screen widgets later). [description] null = decorative. */
@Composable
public fun MascotStatic(mood: MascotMood, stage: MascotStage, description: String?, modifier: Modifier = Modifier) {
    Canvas(modifier.described(description)) { drawMascot(mood, stage, restingPose(mood)) }
}

/** A decorative Blip (null) is left out of TalkBack entirely. */
private fun Modifier.described(description: String?): Modifier =
    if (description == null) this else semantics { contentDescription = description }

/** The still pose of each mood (also what reduced motion shows). */
public fun restingPose(mood: MascotMood): MascotPose = when (mood) {
    MascotMood.CHEERING -> MascotPose(wave = 1f)
    MascotMood.PROUD -> MascotPose(eyeOpen = 0f)
    MascotMood.CURIOUS -> MascotPose()
    MascotMood.SLEEPY -> MascotPose(eyeOpen = 0f, glow = GLOW_LOW, phase = 0.5f)
    MascotMood.DISAPPOINTED -> MascotPose(glow = GLOW_LOW, wave = -1f)
}

@Composable
private fun animatedPose(mood: MascotMood): () -> MascotPose {
    val transition = rememberInfiniteTransition(label = "mascot")
    val cycle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "cycle",
    )
    val blinkState = transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = BLINK_CYCLE_MS
                1f at BLINK_START_MS
                EYE_CLOSED at BLINK_CLOSED_MS
            },
            RepeatMode.Restart,
        ),
        label = "blink",
    )
    return remember(mood) { { poseAt(mood, cycle.value, blinkState.value) } }
}

private fun poseAt(mood: MascotMood, t: Float, blink: Float): MascotPose {
    val wave = sin(TWO_PI * t).toFloat()
    return when (mood) {
        // Bounces twice per loop with arms waving and the bulb pulsing.
        MascotMood.CHEERING -> MascotPose(eyeOpen = blink, bounce = sin(2 * TWO_PI * t).toFloat(), wave = wave, glow = 0.8f + 0.2f * wave)
        MascotMood.PROUD -> MascotPose(eyeOpen = 0f, bounce = wave * 0.3f, wave = 0.3f * wave)
        MascotMood.CURIOUS -> MascotPose(eyeOpen = blink, bounce = wave * 0.15f)
        // Slow breathing, a dim bulb and a "z" drifting up.
        MascotMood.SLEEPY -> MascotPose(eyeOpen = 0f, bounce = wave * 0.2f, glow = GLOW_LOW + 0.1f * wave, phase = t)
        MascotMood.DISAPPOINTED -> MascotPose(eyeOpen = blink, bounce = -0.2f + 0.1f * wave, wave = -1f, glow = GLOW_LOW)
    }
}
