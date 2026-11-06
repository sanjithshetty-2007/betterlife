package com.sanjith.betterlife.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjith.betterlife.BetterLifeApp
import com.sanjith.betterlife.R
import com.sanjith.betterlife.domain.sync.AuthError
import com.sanjith.betterlife.domain.sync.AuthRules
import com.sanjith.betterlife.domain.sync.AuthState
import com.sanjith.betterlife.ui.theme.BetterLifeColors
import com.sanjith.betterlife.ui.theme.Dimens
import com.sanjith.betterlife.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Sign up, sign in and sign out with email and password (Prompt S1). Says plainly what goes to the
 * cloud and what never does. The app works fully signed out. Moves into Settings in Prompt 27.
 */
@Composable
public fun AccountScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BetterLifeApp).container
    val account = container.accountRepository
    val state by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.setup_back), tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            WhatSyncsCard()
            when (val s = state) {
                AuthState.NotConfigured -> Body(stringResource(R.string.account_not_configured))
                AuthState.Checking -> Body(stringResource(R.string.account_checking))
                AuthState.Working -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Body(stringResource(R.string.account_working))
                }
                is AuthState.SignedIn -> SignedIn(s.email) { scope.launch { account.signOut() } }
                is AuthState.ConfirmEmail -> Message(stringResource(R.string.account_confirm_email, s.email), BetterLifeColors.StatusWarn) { account.dismissMessage() }
                is AuthState.Failed -> Message(stringResource(errorText(s.error)), BetterLifeColors.StatusBad) { account.dismissMessage() }
                AuthState.SignedOut -> SignInForm(
                    onSignIn = { email, password -> scope.launch { account.signIn(email, password) } },
                    onSignUp = { email, password -> scope.launch { account.signUp(email, password) } },
                )
            }
        }
    }
}

@Composable
private fun WhatSyncsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Dimens.borderWidth, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.account_what_syncs_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.account_what_syncs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.account_optional), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SignInForm(onSignIn: (String, String) -> Unit, onSignUp: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    var checked by rememberSaveable { mutableStateOf(false) }
    val emailOk = AuthRules.looksLikeEmail(email)
    val passwordOk = password.length >= AuthRules.MIN_PASSWORD_LENGTH
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text(stringResource(R.string.account_email)) },
        singleLine = true,
        isError = checked && !emailOk,
        supportingText = if (checked && !emailOk) ({ Text(stringResource(R.string.account_bad_email)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.account_password)) },
        singleLine = true,
        isError = checked && !passwordOk,
        supportingText = if (checked && !passwordOk) ({ Text(stringResource(R.string.account_short_password, AuthRules.MIN_PASSWORD_LENGTH)) }) else null,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { show = !show }) {
                Text(stringResource(if (show) R.string.account_hide_password else R.string.account_show_password))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    val submit: ((String, String) -> Unit) -> Unit = { action ->
        checked = true
        if (emailOk && passwordOk) action(email, password)
    }
    Button(onClick = { submit(onSignIn) }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
        Text(stringResource(R.string.account_sign_in))
    }
    OutlinedButton(onClick = { submit(onSignUp) }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
        Text(stringResource(R.string.account_create))
    }
}

@Composable
private fun SignedIn(email: String, onSignOut: () -> Unit) {
    Text(stringResource(R.string.account_signed_in, email), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    Body(stringResource(R.string.account_sync_later))
    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)) {
        Text(stringResource(R.string.account_sign_out))
    }
    Body(stringResource(R.string.account_sign_out_note))
}

@Composable
private fun Message(text: String, color: androidx.compose.ui.graphics.Color, onOk: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Dimens.borderWidth, color),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onOk, modifier = Modifier.heightIn(min = Dimens.minTouchTarget)) { Text(stringResource(R.string.account_ok)) }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun errorText(error: AuthError): Int = when (error) {
    AuthError.WRONG_PASSWORD -> R.string.auth_error_wrong_password
    AuthError.EMAIL_NOT_CONFIRMED -> R.string.auth_error_email_not_confirmed
    AuthError.ACCOUNT_EXISTS -> R.string.auth_error_account_exists
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.INVALID_EMAIL -> R.string.auth_error_invalid_email
    AuthError.TOO_MANY_TRIES -> R.string.auth_error_too_many_tries
    AuthError.NO_INTERNET -> R.string.auth_error_no_internet
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}
