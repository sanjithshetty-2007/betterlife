package com.sanjith.betterlife.feature.today

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.mascot.MascotMood
import com.sanjith.betterlife.domain.mascot.MascotStage
import com.sanjith.betterlife.ui.mascot.Mascot
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * First launch (Prompt 17): asks the owner's name for the Today greeting. Shown while the name is
 * empty; the full onboarding comes in Prompt 27.
 */
@Composable
public fun OnboardingNameScreen(onDone: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    // The name is needed for the greeting: Back leaves the app instead of returning to a Today that asks again.
    val activity = LocalContext.current as? android.app.Activity
    BackHandler { activity?.moveTaskToBack(true) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        ) {
            Mascot(MascotMood.CURIOUS, MascotStage.BABY, mascotDescription(MascotMood.CURIOUS), Modifier.size(Dimens.mascotLarge))
            settings?.let { Text(stringResource(R.string.onboarding_name_bubble, it.mascotName), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                stringResource(R.string.onboarding_name_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace("\n", "") },
                singleLine = true,
                label = { Text(stringResource(R.string.onboarding_name_label)) },
                supportingText = { Text(stringResource(R.string.onboarding_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val chosen = name.trim()
                    scope.launch {
                        container.settingsRepository.update { it.copy(ownerName = chosen) }
                        onDone()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            ) { Text(stringResource(R.string.onboarding_continue)) }
        }
    }
}
