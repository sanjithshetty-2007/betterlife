package com.sanjith.betterlife.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Spacing scale used for padding and gaps. */
public object Spacing {
    public val xs = 4.dp
    public val sm = 8.dp
    public val md = 16.dp
    public val lg = 24.dp
    public val xl = 32.dp
}

/** Border and size tokens. */
public object Dimens {
    public val borderWidth = 1.dp
    public val minTouchTarget = 48.dp
    public val iconSize = 24.dp
    public val popupMaxWidth = 480.dp
    public val smallIconSize = 16.dp
    public val numberFieldWidth = 96.dp

    /** The colour dot of an event category, and the stripe on an agenda row. */
    public val categoryDot = 12.dp
    public val categoryStripe = 4.dp

    /** Reminder Health: the score ring and the tinted tile behind each row icon (Prompt 11). */
    public val healthRing = 72.dp
    public val healthRingStroke = 8.dp
    public val iconTile = 40.dp

    /** Reminder Center: the time column of each row. */
    public val centerTimeColumn = 72.dp

    /** Blip on the pop-up card, on the debug sheet, and on the celebration screen (Prompt 16). */
    public val mascotSmall = 56.dp
    public val mascotMedium = 96.dp
    public val mascotLarge = 200.dp

    /** One piece of celebration confetti. */
    public val confettiPiece = 10.dp

    /** Quiet Hours weekday circle (its touch cell is still 48 dp tall). */
    public val dayCircle = 40.dp

    /** The round done mark on task and Today rows, inside its 48 dp target. */
    public val doneMark = 30.dp

    /** Habits (Prompt 15): a grid box inside its 48 dp touch cell, the avatar tile, the editor's big avatar. */
    public val habitBox = 40.dp
    public val habitBoxCheck = 20.dp
    public val habitAvatar = 48.dp
    public val habitAvatarLarge = 88.dp

    /** The floating tab bar and the pill that slides behind the selected icon. */
    public val floatingBarHeight = 64.dp
    public val tabIndicatorWidth = 56.dp
    public val tabIndicatorHeight = 32.dp
    public val tabIndicatorTop = 6.dp

    /** Yearly heatmap: the gap between day squares (the squares share the card's width). */
    public val heatmapGap = 2.dp

    /** Protein and sleep dashboards: the height of a day's bar in the week overview. */
    public val trackerBarHeight = 72.dp

    /** Calendar (Prompt 18): an hour of the week/day grid, its time labels, a month cell and its dots, the now line. */
    public val hourHeight = 60.dp
    public val timeLabelWidth = 48.dp
    public val monthCellHeight = 56.dp
    public val calendarDot = 6.dp
    public val nowLineWidth = 2.dp
    public val nowDot = 8.dp

    /** The hairline gap between neighbouring blocks and chips in the week/day grid (like heatmapGap). */
    public val gridBlockGap = 2.dp

    /** The habit widget's corner (the cards' large radius, as a size for Glance). */
    public val widgetCorner = 24.dp

    /** A habit box's corner, as the Habits grid draws it (shapes.extraSmall), as a size for Glance. */
    public val habitBoxCorner = 12.dp

    /** Habit widget sizes: the smallest (2x2) and the width from which one row of six boxes fits (4x2). */
    public val widgetMinSize = 110.dp

    /** Habit widget layout: the header line with its gap, a name under a box, the done line, and the height from which the normal padding fits. */
    public val widgetHeaderHeight = 28.dp
    public val widgetLabelHeight = 16.dp
    public val widgetFooterHeight = 20.dp

    /** The calendar widget's day number box (seven to a row in a 4-cell-wide widget). */
    public val widgetDayCell = 24.dp

    /** The category stripe beside an entry in the calendar widget's next-events list (two lines of text). */
    public val widgetAgendaStripe = 32.dp
    public val widgetRoomyFrom = 150.dp
}

/**
 * Text sizes for home screen widgets (Prompt 19). Glance cannot use the app's MaterialTheme
 * typography, so the two roles it needs are named here, matching labelLarge and labelMedium.
 */
public object WidgetType {
    public val header = 14.sp
    public val label = 12.sp
}
