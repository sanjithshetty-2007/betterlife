package com.sanjith.betterlife.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.data.db.IntegrityIssue
import com.sanjith.betterlife.data.db.IntegrityReport
import com.sanjith.betterlife.reminders.engine.RescheduleTrigger
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Debug database integrity check (Prompt 13): find broken references and repair them. */
@Composable
internal fun IntegritySection() {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<IntegrityReport?>(null) }
    var fixed by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.integrity_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = { scope.launch { fixed = false; report = container.integrityChecker.check() } },
                modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.integrity_check)) }
            if (report?.clean == false) {
                Button(
                    onClick = {
                        scope.launch {
                            container.integrityChecker.fix()
                            // Soft-deleted reminders lose their alarms at the next reschedule; do it now.
                            container.reminderEngine.rescheduleAll(RescheduleTrigger.MANUAL)
                            report = container.integrityChecker.check()
                            fixed = true
                        }
                    },
                    modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                ) { Text(stringResource(R.string.integrity_fix)) }
            }
        }
        report?.let { r ->
            val lines = buildList {
                if (fixed) add(stringResource(R.string.integrity_fixed))
                if (r.clean) add(stringResource(R.string.integrity_clean))
                r.issues.forEach { (issue, ids) -> if (ids.isNotEmpty()) add(stringResource(issueText(issue), ids.size)) }
                if (r.logRowsWithoutReminder > 0) add(stringResource(R.string.integrity_log_rows, r.logRowsWithoutReminder))
            }
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground) }
        }
    }
}

private fun issueText(issue: IntegrityIssue): Int = when (issue) {
    IntegrityIssue.ORPHAN_REMINDER -> R.string.integrity_orphan_reminders
    IntegrityIssue.REMINDER_OF_DELETED_ITEM -> R.string.integrity_deleted_items
    IntegrityIssue.MISSING_PROJECT -> R.string.integrity_missing_project
    IntegrityIssue.MISSING_PARENT_TASK -> R.string.integrity_missing_parent
}
