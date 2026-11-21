package com.sanjith.betterlife.feature.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.tracker.TrackerKind
import com.sanjith.betterlife.domain.tracker.TrackerRules
import com.sanjith.betterlife.ui.theme.Spacing

private const val GRAMS_DIGITS = 3
private const val TIME_DIGITS = 2

/** The amount typed into the boxes: grams in [first]; or hours in [first] and minutes in [second]. Null if not valid. */
internal fun parseAmount(kind: TrackerKind, first: String, second: String): Int? = when (kind) {
    TrackerKind.PROTEIN -> TrackerRules.parseGrams(first)
    TrackerKind.SLEEP -> TrackerRules.parseDuration(first, second)
}

/** Like [parseAmount], but a target must be above zero. */
internal fun parseTarget(kind: TrackerKind, first: String, second: String): Int? =
    parseAmount(kind, first, second)?.takeIf { TrackerRules.validTarget(kind, it) }

/** An amount split back into the boxes ("" when there is none). */
internal fun splitAmount(kind: TrackerKind, amount: Int?): Pair<String, String> = when {
    amount == null -> "" to ""
    kind == TrackerKind.PROTEIN -> amount.toString() to ""
    else -> (amount / TrackerRules.MINUTES_PER_HOUR).toString() to (amount % TrackerRules.MINUTES_PER_HOUR).toString()
}

/**
 * The amount boxes (pop-up, dialogs, goal): one grams box for protein, hours and minutes boxes for
 * sleep. Numeric keyboard; [onDone] runs on the keyboard's Done key. [label] names the grams box.
 */
@Composable
internal fun AmountFields(
    kind: TrackerKind,
    first: String,
    onFirst: (String) -> Unit,
    second: String,
    onSecond: (String) -> Unit,
    label: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (kind) {
        TrackerKind.PROTEIN -> NumberField(first, onFirst, label, stringResource(R.string.protein_grams_unit), GRAMS_DIGITS, onDone, modifier.fillMaxWidth())
        TrackerKind.SLEEP -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            NumberField(first, onFirst, stringResource(R.string.sleep_hours_hint), stringResource(R.string.sleep_hours_unit), TIME_DIGITS, onDone, Modifier.weight(1f))
            NumberField(second, onSecond, stringResource(R.string.sleep_minutes_hint), stringResource(R.string.sleep_minutes_unit), TIME_DIGITS, onDone, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, unit: String, digits: Int, onDone: () -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(digits)) },
        label = { Text(label) },
        suffix = { Text(unit) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
    )
}
