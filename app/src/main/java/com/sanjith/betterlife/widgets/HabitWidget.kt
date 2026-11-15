package com.sanjith.betterlife.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.MainActivity
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.habit.Consistency
import com.sanjith.betterlife.domain.widget.HabitWidgetBuilder
import com.sanjith.betterlife.domain.widget.HabitWidgetLayout
import com.sanjith.betterlife.domain.widget.HabitWidgetModel
import com.sanjith.betterlife.domain.widget.WidgetBox
import com.sanjith.betterlife.feature.habits.habitColor
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import com.sanjith.betterlife.ui.theme.WidgetType

/**
 * The habit widget (Prompt 19, context.md 5.8): today's habits as boxes in their colours, the
 * consistency header, "+N more" when they do not fit. A tap ticks or clears today's box.
 */
public class HabitWidget : GlanceAppWidget() {

    // Exact: launchers size a "2x2" differently, so the widget measures its real size (HabitWidgetLayout).
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as BetterLifeApp).container
        // Added before the app was ever opened: the starters appear here too (once per install).
        container.seedStarterHabits()
        val source = container.habitWidgetSource
        provideContent {
            val input by remember { source.observe() }.collectAsState(initial = null)
            val size = LocalSize.current
            val layout = HabitWidgetLayout.fit(
                width = size.width.value, height = size.height.value, cell = Dimens.minTouchTarget.value,
                header = Dimens.widgetHeaderHeight.value, labelAndFooter = Dimens.widgetLabelAndFooter.value,
                padding = Spacing.md.value, smallPadding = Spacing.sm.value, roomyFrom = Dimens.widgetRoomyFrom.value,
            )
            val model = input?.let { HabitWidgetBuilder.build(it.habits, it.now, it.zone, it.rolloverHour, it.hideDetails, layout.capacity) }
            HabitWidgetContent(model, layout)
        }
    }
}

private val surface = ColorProvider(BetterLifeColors.Surface)
private val text = ColorProvider(BetterLifeColors.Text)
private val muted = ColorProvider(BetterLifeColors.TextMuted)
private val openBorder = ColorProvider(BetterLifeColors.BoxOpenBorder)
private val cardOutline = ColorProvider(BetterLifeColors.SoftBorder)

@Composable
private fun HabitWidgetContent(model: HabitWidgetModel?, layout: HabitWidgetLayout) {
    val openApp = actionStartActivity<MainActivity>()
    // The app's cards have a 1 dp soft outline; Glance has no border, so an outer layer one border width wider draws it.
    Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(Dimens.widgetCorner).background(cardOutline).padding(Dimens.borderWidth)) {
        Card(model, layout, openApp)
    }
}

@Composable
private fun Card(model: HabitWidgetModel?, layout: HabitWidgetLayout, openApp: Action) {
    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(Dimens.widgetCorner).background(surface)
            .padding(if (layout.roomy) Spacing.md else Spacing.sm),
    ) {
        Text(headerText(model), style = TextStyle(color = muted, fontSize = WidgetType.header, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.height(Spacing.sm))
        when {
            model == null -> Unit
            model.isEmpty -> Text(
                LocalContext.current.getString(R.string.widget_empty),
                style = TextStyle(color = text, fontSize = WidgetType.label),
                modifier = GlanceModifier.fillMaxWidth().clickable(openApp),
            )
            layout.labels -> {
                Row(modifier = GlanceModifier.fillMaxWidth()) { Cells(model.boxes, model.more, labels = true) }
                Spacer(GlanceModifier.height(Spacing.sm))
                Text(footerText(model), style = TextStyle(color = muted, fontSize = WidgetType.label), maxLines = 1)
            }
            else -> chunk(model, layout.columns).forEach { (boxes, more) ->
                Row(modifier = GlanceModifier.fillMaxWidth()) { Cells(boxes, more, labels = false) }
            }
        }
    }
}

@Composable
private fun RowScope.Cells(boxes: List<WidgetBox>, more: Int, labels: Boolean) {
    // Spread evenly across the card, as in the design.
    boxes.forEach { box -> Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { Cell(box, labels) } }
    if (more > 0) Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { MoreCell(more) }
}

/** The boxes in rows of [columns]; the "+N more" box (if any) takes the last place of the last row. */
private fun chunk(model: HabitWidgetModel, columns: Int): List<Pair<List<WidgetBox>, Int>> {
    val rows = model.boxes.chunked(columns).map { it to 0 }.toMutableList()
    if (model.more == 0) return rows
    val last = rows.lastOrNull()
    return if (last != null && last.first.size < columns) rows.dropLast(1) + (last.first to model.more) else rows + (emptyList<WidgetBox>() to model.more)
}

/** One 48 dp target: a filled box with a tick when done, an outlined one when not, the name or "Habit N" under it. */
@Composable
private fun Cell(box: WidgetBox, labels: Boolean) {
    val context = LocalContext.current
    val label = box.name ?: context.getString(R.string.widget_habit_number, box.number)
    val spoken = context.getString(
        when {
            !box.tappable -> R.string.widget_box_paused
            box.done -> R.string.widget_box_done
            else -> R.string.widget_box_open
        },
        label,
    )
    val tap = if (box.tappable) {
        GlanceModifier.clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(ToggleHabitAction.HABIT_ID to box.habitId)))
    } else {
        GlanceModifier
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = (if (labels) tap.width(Dimens.minTouchTarget) else tap.size(Dimens.minTouchTarget))
            .padding(bottom = Spacing.xs).semantics { contentDescription = spoken },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier.size(Dimens.habitBox).cornerRadius(Dimens.habitBoxCorner).background(if (box.done) ColorProvider(habitColor(box.colorHex)) else openBorder),
        ) {
            if (box.done) {
                Image(ImageProvider(R.drawable.ic_check), contentDescription = null, colorFilter = ColorFilter.tint(ColorProvider(BetterLifeColors.Background)), modifier = GlanceModifier.size(Dimens.habitBoxCheck))
            } else {
                // An outline: the inner box in the card's colour, one border width in.
                Box(GlanceModifier.size(Dimens.habitBox - Dimens.borderWidth * 2).cornerRadius(Dimens.habitBoxCorner).background(surface)) {}
            }
        }
        if (labels) Text(label, style = TextStyle(color = text, fontSize = WidgetType.label), maxLines = 1)
    }
}

@Composable
private fun MoreCell(more: Int) {
    val context = LocalContext.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier.size(Dimens.minTouchTarget).clickable(actionStartActivity<MainActivity>())
            .semantics { contentDescription = context.getString(R.string.widget_more_description, more) },
    ) {
        Text(context.getString(R.string.widget_more, more), style = TextStyle(color = text, fontSize = WidgetType.header, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun headerText(model: HabitWidgetModel?): String {
    val context = LocalContext.current
    val percent = when (val c = model?.consistency) {
        is Consistency.Percent -> context.getString(R.string.widget_percent, c.percent)
        else -> context.getString(R.string.widget_new)
    }
    return context.getString(R.string.widget_header, percent)
}

@Composable
private fun footerText(model: HabitWidgetModel): String {
    val context = LocalContext.current
    val count = context.getString(R.string.widget_done_count, model.settled, model.total)
    return if (model.detailsHidden) context.getString(R.string.widget_details_hidden, count) else count
}
