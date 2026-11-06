package com.sanjith.betterlife.feature.celebration

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.core.isReducedMotion
import com.sanjith.betterlife.data.habit.CelebrationEvent
import com.sanjith.betterlife.domain.mascot.CelebrationMessages
import com.sanjith.betterlife.domain.mascot.MascotMood
import com.sanjith.betterlife.domain.mascot.MascotStage
import com.sanjith.betterlife.ui.mascot.Mascot
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlin.random.Random

private const val CONFETTI_PIECES = 60
private const val CONFETTI_FALL_MS = 4_000

/**
 * The full-screen celebration (Prompt 16): confetti, Blip cheering, the milestone, the habit and one
 * of the owner's messages. Shown once per milestone per streak (the ledger decides that).
 */
@Composable
public fun CelebrationScreen(event: CelebrationEvent, onClose: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val defaults = stringArrayResource(R.array.celebration_default_messages).toList()
    val message by produceState("", event) { value = CelebrationMessages.pick(container.celebrationMessages.lines(), event.milestone, defaults) }
    val stage by produceState(MascotStage.BABY, event) { value = container.mascotMood.stage() }
    val name by produceState("", event) { value = container.settingsRepository.current().mascotName }
    BackHandler(onBack = onClose)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Confetti(Modifier.fillMaxSize())
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
            ) {
                Mascot(MascotMood.CHEERING, stage, stringResource(R.string.mascot_cheering_description, name), Modifier.size(Dimens.mascotLarge))
                Text(
                    text = event.milestone.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.celebration_days_of, event.habitName),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                if (message.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.celebration_quote, message),
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
                    Text(stringResource(R.string.celebration_keep_going))
                }
            }
        }
    }
}

/** One piece of confetti: where it starts, how fast it falls and spins, its colour and shape. */
private class Piece(val x: Float, val start: Float, val speed: Float, val spin: Float, val colorIndex: Int, val round: Boolean)

/**
 * Falling confetti on ONE Canvas. With the system animation scale off the pieces stay still where
 * they are (a sprinkle, no motion).
 */
@Composable
private fun Confetti(modifier: Modifier) {
    val pieces = remember {
        val random = Random(CONFETTI_PIECES)
        List(CONFETTI_PIECES) {
            Piece(random.nextFloat(), random.nextFloat(), 0.6f + random.nextFloat() * 0.8f, random.nextFloat() * 360f, random.nextInt(8), random.nextBoolean())
        }
    }
    val reduced = LocalContext.current.isReducedMotion()
    val t = if (reduced) {
        0f
    } else {
        val fall by rememberInfiniteTransition(label = "confetti").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(CONFETTI_FALL_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "fall",
        )
        fall
    }
    val palette = BetterLifeColors.CategoryPalette
    Canvas(modifier) {
        val piece = Dimens.confettiPiece.toPx()
        pieces.forEach { p ->
            val y = ((p.start + t * p.speed) % 1f) * size.height
            val at = Offset(p.x * size.width, y)
            val color = palette[p.colorIndex % palette.size]
            if (p.round) {
                drawCircle(color, piece / 2, at)
            } else {
                rotate(p.spin + t * 360f * p.speed, pivot = at) {
                    drawRect(color, topLeft = Offset(at.x - piece / 2, at.y - piece / 4), size = Size(piece, piece / 2))
                }
            }
        }
    }
}
