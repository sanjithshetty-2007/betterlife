package com.sanjith.betterlife.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.MainActivity
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.calendar.CalendarRanges
import com.sanjith.betterlife.domain.event.AgendaKind
import com.sanjith.betterlife.domain.widget.CalendarWidgetBuilder
import com.sanjith.betterlife.domain.widget.CalendarWidgetEntry
import com.sanjith.betterlife.domain.widget.CalendarWidgetMode
import com.sanjith.betterlife.domain.widget.CalendarWidgetMonth
import com.sanjith.betterlife.feature.tasks.DueText
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import com.sanjith.betterlife.ui.theme.WidgetType
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** The calendar widget (Prompt 20): this month with dots, or the next events; chosen when it is added. */
public class CalendarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as BetterLifeApp).container
        val source = container.calendarWidgetSource
        val dueText = DueText(context) { container.clock.zone }
        provideContent {
            val input by remember { source.observe() }.collectAsState(initial = null)
            val mode = currentState(MODE)?.let { name -> CalendarWidgetMode.entries.firstOrNull { it.name == name } } ?: CalendarWidgetMode.MONTH
            val open = MainActivity.openRouteIntent(LocalContext.current, MainActivity.ROUTE_CALENDAR)
            WidgetCard {
                // The whole card opens the Calendar tab.
                Column(GlanceModifier.fillMaxSize().clickable(actionStartActivity(open))) {
                    input?.let { data ->
                        when (mode) {
                            CalendarWidgetMode.MONTH -> MonthContent(CalendarWidgetBuilder.month(data.items, data.now.atZone(data.zone).toLocalDate(), data.weekStart, data.zone, data::colorIndex))
                            CalendarWidgetMode.AGENDA -> AgendaContent(CalendarWidgetBuilder.agenda(data.items, data.now, data.zone, data.hideDetails, data::colorIndex), data.now.atZone(data.zone).toLocalDate(), data.zone, dueText, open)
                        }
                    }
                }
            }
        }
    }

    public companion object {
        /** Month or next events, saved per widget by the setup screen. */
        internal val MODE = stringPreferencesKey("calendar_widget_mode")
    }
}

public class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

/** "NOVEMBER", the weekday letters, then the weeks; today in a yellow box, dots and cakes under the days. */
@Composable
private fun MonthContent(month: CalendarWidgetMonth) {
    Text(
        month.today.format(DateTimeFormatter.ofPattern("MMMM")).uppercase(Locale.getDefault()),
        style = TextStyle(color = WidgetColors.muted, fontSize = WidgetType.header, fontWeight = FontWeight.Bold), maxLines = 1,
    )
    Spacer(GlanceModifier.height(Spacing.xs))
    Row(GlanceModifier.fillMaxWidth()) {
        CalendarRanges.weekdays(month.grid.weekStart).forEach { day ->
            Text(
                day.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                style = TextStyle(color = WidgetColors.muted, fontSize = WidgetType.label, textAlign = TextAlign.Center),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
    month.grid.weeks.forEach { week ->
        Row(GlanceModifier.fillMaxWidth()) {
            week.forEach { date ->
                val inMonth = month.grid.inMonth(date)
                val today = date == month.today
                val marks = month.marks[date]
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.defaultWeight()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = GlanceModifier.size(Dimens.widgetDayCell).cornerRadius(Dimens.habitBoxCorner)
                            .background(if (today) WidgetColors.accent else WidgetColors.surface),
                    ) {
                        Text(
                            if (inMonth) date.dayOfMonth.toString() else "",
                            style = TextStyle(color = if (today) WidgetColors.onAccent else WidgetColors.text, fontSize = WidgetType.label, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.height(Dimens.smallIconSize)) {
                        if (inMonth) {
                            marks?.colors?.forEach { index ->
                                Box(GlanceModifier.size(Dimens.calendarDot).cornerRadius(Dimens.calendarDot).background(dotColor(index))) {}
                                Spacer(GlanceModifier.width(Dimens.gridBlockGap))
                            }
                            // A birthday shows its cake, as in the Calendar tab's month.
                            if (marks?.birthday == true) Text("🎂", style = TextStyle(fontSize = WidgetType.label))
                        }
                    }
                }
            }
        }
    }
}

private fun dotColor(index: Int?): ColorProvider =
    index?.let { ColorProvider(BetterLifeColors.CategoryPalette[it % BetterLifeColors.CategoryPalette.size]) } ?: WidgetColors.muted

/** "NEXT UP" and the next events: a colour stripe, when, and the title (or what it is when hidden). */
@Composable
private fun AgendaContent(entries: List<CalendarWidgetEntry>, today: java.time.LocalDate, zone: java.time.ZoneId, dueText: DueText, open: android.content.Intent) {
    val context = LocalContext.current
    Text(context.getString(R.string.widget_calendar_next), style = TextStyle(color = WidgetColors.muted, fontSize = WidgetType.header, fontWeight = FontWeight.Bold), maxLines = 1)
    Spacer(GlanceModifier.height(Spacing.sm))
    if (entries.isEmpty()) {
        WidgetEmpty(context.getString(R.string.widget_calendar_empty), open)
        return
    }
    entries.forEach { entry ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(vertical = Dimens.gridBlockGap)) {
            Box(GlanceModifier.width(Dimens.categoryStripe).height(Dimens.widgetAgendaStripe).background(dotColor(entry.colorIndex))) {}
            Column(GlanceModifier.defaultWeight().padding(start = Spacing.sm)) {
                Text(whenText(entry, today, zone, dueText), style = TextStyle(color = WidgetColors.muted, fontSize = WidgetType.label), maxLines = 1)
                Text(entry.title ?: context.getString(kindLabel(entry.kind)), style = TextStyle(color = WidgetColors.text, fontSize = WidgetType.header), maxLines = 1)
            }
        }
    }
}

@Composable
private fun whenText(entry: CalendarWidgetEntry, today: java.time.LocalDate, zone: java.time.ZoneId, dueText: DueText): String {
    val context = LocalContext.current
    val day = dueText.day(entry.date, today)
    val start = entry.start ?: return context.getString(R.string.widget_calendar_all_day, day)
    return "$day ${dueText.time(start.atZone(zone).toLocalTime())}"
}

private fun kindLabel(kind: AgendaKind): Int = when (kind) {
    AgendaKind.MEETING -> R.string.agenda_kind_meeting
    AgendaKind.BIRTHDAY -> R.string.agenda_kind_birthday
    AgendaKind.TASK -> R.string.agenda_kind_task
    AgendaKind.REMINDER -> R.string.agenda_kind_reminder
    AgendaKind.EVENT -> R.string.agenda_kind_event
}
