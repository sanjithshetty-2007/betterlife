package com.sanjith.betterlife.reminders.popup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.popup.ProteinPrompt
import com.sanjith.betterlife.domain.protein.ProteinRules
import com.sanjith.betterlife.feature.protein.GramsField
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

/**
 * The daily protein question (owner's feature, 2026-11-21): the day's target, one grams box and Save.
 * No Snooze, Done or dismiss: it closes on Save, when the day is logged elsewhere, or after 2 minutes.
 */
@Composable
internal fun ProteinPopupCard(prompt: ProteinPrompt, title: String, reduceMotion: Boolean, onAction: (CardAction) -> Unit) {
    var text by rememberSaveable(prompt.date.toString()) { mutableStateOf("") }
    val grams = ProteinRules.parseGrams(text)
    val save = { grams?.let { onAction(CardAction.SaveProtein(it)) } }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PopupMascot(reduceMotion = reduceMotion, description = stringResource(R.string.popup_mascot_description))
                Text(
                    text = stringResource(R.string.protein_popup_target, prompt.targetGrams),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.sm).weight(1f),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            GramsField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.protein_amount_hint),
                onDone = { save() },
            )
            Button(
                onClick = { save() },
                enabled = grams != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.protein_save_intake)) }
        }
    }
}
