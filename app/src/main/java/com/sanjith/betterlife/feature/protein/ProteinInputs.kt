package com.sanjith.betterlife.feature.protein

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
import com.sanjith.betterlife.domain.protein.ProteinRules

/** Digits only, at most 3 (0 to 999 g). */
internal fun gramsInput(text: String): String = text.filter(Char::isDigit).take(ProteinRules.MAX_GRAMS.toString().length)

/**
 * The one grams box (pop-up, log dialog, goal): numeric keyboard, "g" after the number. [onDone] runs
 * on the keyboard's Done key.
 */
@Composable
internal fun GramsField(value: String, onValueChange: (String) -> Unit, label: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(gramsInput(it)) },
        label = { Text(label) },
        suffix = { Text(stringResource(R.string.protein_grams_unit)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}
