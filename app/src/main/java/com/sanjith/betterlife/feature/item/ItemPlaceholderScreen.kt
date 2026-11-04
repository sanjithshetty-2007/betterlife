package com.sanjith.betterlife.feature.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.ReminderItemType
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Where a pop-up's Reschedule (or "Edit") button lands. A placeholder until the task editor (Prompt 9)
 * and event editor (Prompt 10) exist: it shows which item would be edited.
 */
@Composable
public fun ItemPlaceholderScreen(type: ReminderItemType, id: String, onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val titleFlow = remember(type, id) {
        when (type) {
            ReminderItemType.TASK -> container.taskRepository.observeById(id).map { it?.title }
            ReminderItemType.EVENT -> container.eventRepository.observeById(id).map { it?.title }
            ReminderItemType.STANDALONE -> flowOf(null)
        }
    }
    val title by titleFlow.collectAsStateWithLifecycle(initialValue = null)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.setup_back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.item_placeholder_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = title ?: stringResource(R.string.item_placeholder_missing),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.item_placeholder_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
