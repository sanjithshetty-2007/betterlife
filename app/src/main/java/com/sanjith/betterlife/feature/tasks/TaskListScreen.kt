package com.sanjith.betterlife.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.time.today
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Undo stays offered for about 6 seconds (Prompt 9). */
private const val UNDO_WINDOW_MILLIS = 6_000L

/** Today / Upcoming / Overdue / Inbox with quick-add on top (context.md 5.3). */
@Composable
public fun TaskListScreen(
    onBack: () -> Unit,
    onOpenTask: (String?) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenCheckIns: () -> Unit,
    viewModel: TaskListViewModel = viewModel(factory = TaskListViewModel.Factory),
) {
    val context = LocalContext.current
    val container = (context.applicationContext as BetterLifeApp).container
    val dueText = remember { DueText(context) { container.clock.zone } }
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val text by viewModel.text.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    UndoSnackbar(undo, snackbar, viewModel)

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Header(onBack, onOpenTask, onOpenProjects, onOpenCheckIns)
            QuickAddBar(
                text = text,
                preview = preview,
                formatDate = { dueText.day(it, container.clock.today()) },
                formatTime = dueText::time,
                onText = viewModel::onQuickAddText,
                onAmPm = viewModel::answerAmPm,
                onSubmit = viewModel::submitQuickAdd,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            // Scrollable: four labels with counts do not fit across a phone screen.
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background, edgePadding = Spacing.md) {
                TaskTab.entries.forEach { t ->
                    Tab(selected = t == tab, onClick = { viewModel.selectedTab.value = t }, text = {
                        Text(stringResource(R.string.task_tab, stringResource(tabLabel(t)), tabs.rows(t).size), maxLines = 1)
                    })
                }
            }
            val rows = tabs.rows(tab)
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(emptyText(tab)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(rows, key = { it.id }) { row ->
                        TaskRow(
                            row = row,
                            dueText = dueText.format(row.due, row.displayDate, container.clock.today()),
                            onComplete = { viewModel.complete(row) },
                            onOpen = { onOpenTask(row.id) },
                            onDelete = { viewModel.delete(row) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UndoSnackbar(undo: UndoOffer?, snackbar: SnackbarHostState, viewModel: TaskListViewModel) {
    val message = undo?.let {
        stringResource(if (it is UndoOffer.Completed) R.string.task_completed_snack else R.string.task_deleted_snack, it.title)
    }
    val action = stringResource(R.string.undo)
    LaunchedEffect(undo) {
        val offer = undo ?: return@LaunchedEffect
        val expiry = launch {
            delay(UNDO_WINDOW_MILLIS)
            snackbar.currentSnackbarData?.dismiss()
        }
        val result = snackbar.showSnackbar(message.orEmpty(), action, withDismissAction = false, duration = SnackbarDuration.Indefinite)
        expiry.cancel()
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(offer) else viewModel.undoExpired(offer)
    }
}

@Composable
private fun Header(onBack: () -> Unit, onOpenTask: (String?) -> Unit, onOpenProjects: () -> Unit, onOpenCheckIns: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = stringResource(R.string.tasks_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenProjects) { Text(stringResource(R.string.projects_title)) }
        TextButton(onClick = onOpenCheckIns) { Text(stringResource(R.string.check_ins_short)) }
        IconButton(onClick = { onOpenTask(null) }) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.task_new), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun tabLabel(tab: TaskTab): Int = when (tab) {
    TaskTab.TODAY -> R.string.tab_today
    TaskTab.UPCOMING -> R.string.tab_upcoming
    TaskTab.OVERDUE -> R.string.tab_overdue
    TaskTab.INBOX -> R.string.tab_inbox
}

private fun emptyText(tab: TaskTab): Int = when (tab) {
    TaskTab.TODAY -> R.string.empty_today
    TaskTab.UPCOMING -> R.string.empty_upcoming
    TaskTab.OVERDUE -> R.string.empty_overdue
    TaskTab.INBOX -> R.string.empty_inbox
}
