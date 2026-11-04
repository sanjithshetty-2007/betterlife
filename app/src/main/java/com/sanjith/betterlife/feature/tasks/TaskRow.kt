package com.sanjith.betterlife.feature.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.model.Priority
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

/**
 * A task in a list: tick to complete (the row strikes through, then the list drops it), tap to edit,
 * bin to delete. Priority shows as a flag icon AND a word, never color alone.
 */
@Composable
public fun TaskRow(
    row: TaskRowUi,
    dueText: String?,
    onComplete: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ticked by remember(row.id) { mutableStateOf(false) }
    val titleColor by animateColorAsState(
        if (ticked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        label = "title",
    )
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, if (row.isTop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ticked,
                onCheckedChange = { checked ->
                    if (checked && !ticked) {
                        ticked = true
                        onComplete()
                    }
                },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    textDecoration = if (ticked) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                RowDetails(row, dueText)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.task_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RowDetails(row: TaskRowUi, dueText: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        if (row.isTop) DetailText(stringResource(R.string.task_top), MaterialTheme.colorScheme.primary)
        if (row.priority != Priority.NONE) {
            val color = priorityColor(row.priority)
            Icon(painterResource(R.drawable.ic_flag), contentDescription = null, tint = color, modifier = Modifier.size(Dimens.smallIconSize))
            DetailText(stringResource(priorityLabel(row.priority)), color)
        }
        dueText?.let { DetailText(it, MaterialTheme.colorScheme.onSurfaceVariant) }
        if (row.repeats) {
            Icon(
                painterResource(R.drawable.ic_repeat),
                contentDescription = stringResource(R.string.task_repeats),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.smallIconSize),
            )
        }
        row.projectName?.let { DetailText("@$it", MaterialTheme.colorScheme.onSurfaceVariant) }
        if (row.subtasksTotal > 0) {
            DetailText(stringResource(R.string.task_subtasks, row.subtasksDone, row.subtasksTotal), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailText(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
}

@Composable
internal fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.HIGH -> BetterLifeColors.StatusBad
    Priority.MEDIUM -> BetterLifeColors.StatusWarn
    Priority.LOW -> BetterLifeColors.StatusGood
    Priority.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
}
