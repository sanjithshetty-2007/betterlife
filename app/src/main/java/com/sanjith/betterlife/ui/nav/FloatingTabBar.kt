package com.sanjith.betterlife.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.sanjith.betterlife.core.rememberReducedMotion
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Motion
import com.sanjith.betterlife.ui.theme.Spacing
import kotlin.math.roundToInt

private const val INDICATOR_DAMPING = 0.8f

/** One main screen on the tab bar. */
public data class TabItem(val route: String, @param:DrawableRes val icon: Int, @param:StringRes val label: Int)

/** The room the floating bar takes over the bottom of a main screen, system navigation included. */
@Composable
public fun tabBarClearance(): Dp =
    Dimens.floatingBarHeight + Spacing.sm * 2 + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** The usual screen padding, with the last row able to scroll clear of the floating bar. */
@Composable
public fun tabContentPadding(): PaddingValues =
    PaddingValues(start = Spacing.md, top = Spacing.md, end = Spacing.md, bottom = Spacing.md + tabBarClearance())

/**
 * The main navigation (owner, 2026-11-08): a floating rounded bar that the screen shows through, with
 * no blur. One indicator slides between the tabs; its position is read while placing it, so the slide
 * moves one shape and recomposes nothing.
 */
@Composable
public fun FloatingTabBar(tabs: List<TabItem>, selectedRoute: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.extraLarge
    val reduced = rememberReducedMotion()
    val position = animateFloatAsState(
        targetValue = tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0).toFloat(),
        animationSpec = if (reduced) snap() else spring(dampingRatio = INDICATOR_DAMPING, stiffness = Spring.StiffnessMedium),
        label = "tabIndicator",
    )
    val indicatorColor = MaterialTheme.colorScheme.secondary
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .height(Dimens.floatingBarHeight)
            .clip(shape)
            .background(BetterLifeColors.FloatingBar)
            .border(Dimens.borderWidth, MaterialTheme.colorScheme.outline, shape),
    ) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val cell = constraints.maxWidth / tabs.size.toFloat()
                    val width = Dimens.tabIndicatorWidth.roundToPx().coerceAtMost(cell.toInt())
                    val placeable = measurable.measure(Constraints.fixed(width, Dimens.tabIndicatorHeight.roundToPx()))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeRelative(((position.value + 0.5f) * cell - width / 2f).roundToInt(), Dimens.tabIndicatorTop.roundToPx())
                    }
                }
                .background(indicatorColor, CircleShape),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            tabs.forEach { tab ->
                TabCell(tab, selected = tab.route == selectedRoute, onClick = { onSelect(tab.route) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TabCell(tab: TabItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val iconColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(Motion.TAB_SWITCH_MS),
        label = "tabIcon",
    )
    Column(
        modifier.fillMaxHeight().selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = Dimens.tabIndicatorTop).height(Dimens.tabIndicatorHeight), contentAlignment = Alignment.Center) {
            Icon(painterResource(tab.icon), contentDescription = null, tint = iconColor)
        }
        Text(
            stringResource(tab.label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
