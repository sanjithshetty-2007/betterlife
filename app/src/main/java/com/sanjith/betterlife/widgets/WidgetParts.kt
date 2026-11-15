package com.sanjith.betterlife.widgets

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import com.sanjith.betterlife.ui.theme.WidgetType

/** The theme's colours as Glance colour providers, shared by every BetterLife widget (Prompts 19 and 20). */
internal object WidgetColors {
    val surface = ColorProvider(BetterLifeColors.Surface)
    val text = ColorProvider(BetterLifeColors.Text)
    val muted = ColorProvider(BetterLifeColors.TextMuted)
    val openBorder = ColorProvider(BetterLifeColors.BoxOpenBorder)
    val outline = ColorProvider(BetterLifeColors.SoftBorder)
    val accent = ColorProvider(BetterLifeColors.AccentYellow)
    val onAccent = ColorProvider(BetterLifeColors.Background)
    val bad = ColorProvider(BetterLifeColors.StatusBad)
}

/**
 * The card every widget sits on: the Surface colour, the large corner, the app cards' 1 dp soft
 * outline (Glance has no border, so an outer layer one border width wider draws it), and the padding.
 */
@Composable
internal fun WidgetCard(padding: androidx.compose.ui.unit.Dp = Spacing.md, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(Dimens.widgetCorner).background(WidgetColors.outline).padding(Dimens.borderWidth)) {
        Column(modifier = GlanceModifier.fillMaxSize().cornerRadius(Dimens.widgetCorner).background(WidgetColors.surface).padding(padding), content = content)
    }
}

/** A short friendly line for an empty widget; a tap opens the app at [open]. */
@Composable
internal fun WidgetEmpty(text: String, open: Intent) {
    Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(open))) {
        Text(text, style = TextStyle(color = WidgetColors.muted, fontSize = WidgetType.header, textAlign = TextAlign.Center))
    }
}

/** A small text button with a 48 dp tall target. */
@Composable
internal fun WidgetButton(text: String, color: ColorProvider, action: Action) {
    Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.height(Dimens.minTouchTarget).padding(horizontal = Spacing.sm).clickable(action)) {
        Text(text, style = TextStyle(color = color, fontSize = WidgetType.header, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/** A full-width text line in the label size (a header's companion, the done line). */
@Composable
internal fun WidgetLabel(text: String, color: ColorProvider = WidgetColors.muted) {
    Text(text, style = TextStyle(color = color, fontSize = WidgetType.label), maxLines = 1, modifier = GlanceModifier.fillMaxWidth())
}
