package com.sanjith.betterlife.feature.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Projects with their progress ("3 of 5 done") and a field to create one. */
@Composable
public fun ProjectsScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val projects by remember { container.projectRepository.observeWithProgress() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(stringResource(R.string.projects_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.editor_new_project)) })
                    Button(
                        onClick = {
                            val newName = name
                            name = ""
                            scope.launch { container.projectRepository.create(newName) }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                    ) { Text(stringResource(R.string.editor_add)) }
                }
            }
            items(projects, key = { it.project.id }) { p ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(p.project.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        LinearProgressIndicator(
                            progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.project_progress, p.done, p.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
