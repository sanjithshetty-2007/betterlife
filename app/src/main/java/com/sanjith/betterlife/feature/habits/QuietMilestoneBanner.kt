package com.sanjith.betterlife.feature.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.mascot.MascotMood
import com.sanjith.betterlife.domain.mascot.MascotStage
import com.sanjith.betterlife.ui.mascot.MascotStatic
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.delay

/** How long the quiet banner stays before it goes away by itself. */
private const val QUIET_BANNER_MILLIS = 8_000L

/**
 * The quiet banner (Prompt 16): a milestone reached by back-filling a past day gets this small note
 * instead of the full-screen celebration. It closes by itself or with the close button.
 */
@Composable
internal fun QuietMilestoneBanner() {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val center = container.celebrations
    val event by center.quiet.collectAsStateWithLifecycle()
    val stage by produceState(MascotStage.BABY) { value = container.mascotMood.stage() }
    val shown = event ?: return
    LaunchedEffect(shown) {
        delay(QUIET_BANNER_MILLIS)
        center.quietSeen(shown)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MascotStatic(MascotMood.PROUD, stage, description = null, modifier = Modifier.size(Dimens.habitAvatar))
            Text(
                text = stringResource(R.string.celebration_quiet_banner, shown.milestone, shown.habitName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { center.quietSeen(shown) }) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.celebration_quiet_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
