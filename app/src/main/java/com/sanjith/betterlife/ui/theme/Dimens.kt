package com.sanjith.betterlife.ui.theme

import androidx.compose.ui.unit.dp

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

    /** Blip on the pop-up card. */
    public val mascotSmall = 56.dp

    /** Quiet Hours weekday circle (its touch cell is still 48 dp tall). */
    public val dayCircle = 40.dp

    /** Habits (Prompt 15): a grid box inside its 48 dp touch cell, the avatar tile, the editor's big avatar. */
    public val habitBox = 40.dp
    public val habitBoxCheck = 20.dp
    public val habitAvatar = 48.dp
    public val habitAvatarLarge = 88.dp

    /** Yearly heatmap: the gap between day squares (the squares share the card's width). */
    public val heatmapGap = 2.dp
}
