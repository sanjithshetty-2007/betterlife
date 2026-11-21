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
import com.sanjith.betterlife.domain.popup.DailyAsk
import com.sanjith.betterlife.domain.tracker.TrackerKind
import com.sanjith.betterlife.feature.tracker.AmountFields
import com.sanjith.betterlife.feature.tracker.amountText
import com.sanjith.betterlife.feature.tracker.parseAmount
import com.sanjith.betterlife.feature.tracker.textsFor
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing

/**
 * A tracker's daily question (owner's features, 2026-11-21): the target, the amount boxes (grams, or
 * hours and minutes of sleep) and Save. No Snooze, Done or dismiss: it closes on Save, when the day is
 * logged elsewhere, or after 2 minutes.
 */
@Composable
internal fun TrackerPopupCard(ask: DailyAsk, title: String, reduceMotion: Boolean, onAction: (CardAction) -> Unit) {
    val key = ask.kind.name + ask.date
    var first by rememberSaveable(key) { mutableStateOf("") }
    var second by rememberSaveable(key) { mutableStateOf("") }
    val amount = parseAmount(ask.kind, first, second)
    val save = { amount?.let { onAction(CardAction.SaveAmount(it)) } }
    val target = when (ask.kind) {
        // The owner's own wording for protein: "Daily target: 120g".
        TrackerKind.PROTEIN -> stringResource(R.string.protein_popup_target, ask.target)
        TrackerKind.SLEEP -> stringResource(R.string.sleep_popup_target, amountText(ask.kind, ask.target))
    }
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
                    text = target,
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
            AmountFields(
                kind = ask.kind,
                first = first,
                onFirst = { first = it },
                second = second,
                onSecond = { second = it },
                label = stringResource(R.string.protein_amount_hint),
                onDone = { save() },
            )
            Button(
                onClick = { save() },
                enabled = amount != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(textsFor(ask.kind).save)) }
        }
    }
}
