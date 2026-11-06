package com.sanjith.betterlife.feature.habits

import android.content.res.Resources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.core.isReducedMotion
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import kotlin.math.PI
import kotlin.math.sin

/** Test tags: every box carries [BOX_TAG]; a greyed one [GREYED_TAG] instead. */
internal const val BOX_TAG = "habit-box"
internal const val GREYED_TAG = "habit-box-greyed"
private const val FILL_MILLIS = 250
private const val POP = 0.15f

/** The habit's stored #RRGGBB colour, or the first palette colour if the text is not a colour. */
internal fun habitColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { BetterLifeColors.CategoryPalette.first() }

/** Everything a box draws with, read once per grid instead of once per box. */
@Immutable
private class BoxLook(
    val habit: Color,
    val open: Color,
    val future: Color,
    val today: Color,
    val checkTint: Color,
    val muted: Color,
    val shape: Shape,
    val check: Painter,
    val pause: Painter,
    val iconSize: Dp,
    val thin: Dp,
)

/**
 * The 31 boxes in rows of [HabitGridModel.COLUMNS] (Prompt 15). Each box sits in a cell at least
 * 48 dp tall and a seventh of the width wide, which is the whole touch target. A box is one cell
 * drawn in a single pass (no child composables), so 12 cards of 31 boxes scroll smoothly.
 */
@Composable
internal fun HabitMonthGrid(boxes: List<GridBox>, color: Color, onTap: (GridBox) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reducedMotion = context.isReducedMotion()
    val look = BoxLook(
        habit = color,
        open = BetterLifeColors.BoxOpenBorder,
        future = MaterialTheme.colorScheme.outline,
        today = MaterialTheme.colorScheme.primary,
        checkTint = MaterialTheme.colorScheme.background,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
        check = painterResource(R.drawable.ic_check),
        pause = painterResource(R.drawable.ic_pause_circle),
        iconSize = Dimens.habitBoxCheck,
        thin = Dimens.borderWidth,
    )
    Column(modifier) {
        boxes.chunked(HabitGridModel.COLUMNS).forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { box ->
                    HabitBox(box, look, reducedMotion, context.resources, onTap = { onTap(box) }, modifier = Modifier.weight(1f))
                }
                repeat(HabitGridModel.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HabitBox(box: GridBox, look: BoxLook, reducedMotion: Boolean, resources: Resources, onTap: () -> Unit, modifier: Modifier) {
    val filled = box.state.filled
    // Fill sweep from the left edge; the pop rides on the same animation (biggest halfway through).
    val fill by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else FILL_MILLIS),
        label = "fill",
    )
    val greyed = box.state == BoxState.NO_SUCH_DAY || box.state == BoxState.BEFORE_START
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = Dimens.minTouchTarget)
            .testTag(if (greyed) GREYED_TAG else BOX_TAG)
            // Worked out only when something reads the semantics (TalkBack, tests).
            .semantics {
                contentDescription = describe(resources, box)
                if (box.state.tappable) {
                    stateDescription = resources.getString(if (filled) R.string.habit_box_state_done else R.string.habit_box_state_not_done)
                }
            }
            .clickable(enabled = box.state.tappable, role = Role.Checkbox, onClick = onTap),
    ) {
        Spacer(Modifier.size(Dimens.habitBox).drawBehind { drawBox(box, look, fill, popping = filled, greyed = greyed) })
    }
}

private fun DrawScope.drawBox(box: GridBox, look: BoxLook, fill: Float, popping: Boolean, greyed: Boolean) {
    // A tick pops (biggest halfway through the sweep); an untick just drains.
    val pop = if (popping && fill > 0f && fill < 1f) 1f + POP * sin(PI * fill).toFloat() else 1f
    scale(pop) {
        val outline = look.shape.createOutline(size, layoutDirection, this)
        if (greyed || box.state == BoxState.PAUSED) drawOutline(outline, BetterLifeColors.Greyed)
        if (fill > 0f) clipRect(right = size.width * fill) { drawOutline(outline, look.habit) }
        val thin = look.thin.toPx()
        when {
            box.isToday -> drawOutline(outline, look.today, style = Stroke(thin * 2))
            box.state == BoxState.OPEN -> drawOutline(outline, look.open, style = Stroke(thin))
            box.state == BoxState.FUTURE -> drawOutline(outline, look.future, style = Stroke(thin))
        }
        val icon = look.iconSize.toPx()
        val at = Offset((size.width - icon) / 2, (size.height - icon) / 2)
        when {
            fill > 0f -> translate(at.x, at.y) {
                with(look.check) { draw(Size(icon, icon), alpha = fill, colorFilter = ColorFilter.tint(look.checkTint)) }
            }
            box.state == BoxState.PAUSED -> translate(at.x, at.y) {
                with(look.pause) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(look.muted)) }
            }
        }
    }
}

private fun describe(resources: Resources, box: GridBox): String = when (box.state) {
    BoxState.DONE -> resources.getString(R.string.habit_box_done, box.day)
    BoxState.KEPT -> resources.getString(R.string.habit_box_kept, box.day)
    BoxState.PAUSED -> resources.getString(R.string.habit_box_paused, box.day)
    BoxState.OPEN -> resources.getString(if (box.isToday) R.string.habit_box_today else R.string.habit_box_open, box.day)
    BoxState.FUTURE -> resources.getString(R.string.habit_box_future, box.day)
    BoxState.NO_SUCH_DAY -> resources.getString(R.string.habit_box_no_such_day, box.day)
    BoxState.BEFORE_START -> resources.getString(R.string.habit_box_before_start, box.day)
}
