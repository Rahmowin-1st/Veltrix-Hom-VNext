package com.veltrix.hom.vnext

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.security.SecureRandom

private val RootInk = Color(0xFF111827)
private val RootMuted = Color(0xFF64748B)
private val RootSky = Color(0xFF4D8CFF)
private val RootMint = Color(0xFF5CCEB2)
private val RootViolet = Color(0xFF7C6AF2)

@Composable
fun RootResetBootstrapGate() {
    RootResetWorldScene(destination = "AUTH") {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("VELTRIX", color = RootInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.fillMaxWidth(.42f), color = RootSky.copy(alpha = .4f))
                Text("Restoring your world", color = RootInk, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("Checking your secure session before personal data is shown.", color = RootMuted)
            }
        }
    }
}

@Composable
fun RootResetConnectionGate(
    issue: ConnectionIssue?,
    message: String?,
    onRetry: () -> Unit,
) {
    RootResetWorldScene(destination = "CONNECTION") {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFE88973)))
                Text("VELTRIX · CONNECTION", color = RootInk, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                IdentityMark(Modifier.size(112.dp))
                Text(
                    if (issue == ConnectionIssue.SERVER_UNAVAILABLE) "Veltrix is temporarily unavailable" else "Connect to continue",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.displaySmall,
                    color = RootInk,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    message ?: if (issue == ConnectionIssue.SERVER_UNAVAILABLE)
                        "Your account stays protected. Try again when the service responds."
                    else "A live connection is required before account state can be shown or changed.",
                    color = RootMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = 52.dp).testTag("connection-retry")) { Text("Try again") }
            }
            Text("Stale account state is never presented as current while disconnected.", color = RootMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun RootResetAuthGateway(
    state: AuthUiState,
    sessionExpired: Boolean,
    onMode: (AuthMode) -> Unit,
    onEmailSignIn: (String, String) -> Unit,
    onCreateAccount: (String, String, String) -> Unit,
    onGoogleToken: (String, String) -> Unit,
    onGoogleFailure: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }

    RootResetWorldScene(destination = "AUTH") {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("VELTRIX HOM", color = RootInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("ACCOUNT WORLD", color = RootMuted, style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) { IdentityMark(Modifier.size(154.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (sessionExpired) "Welcome back" else "Your knowledge, in motion.",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.displaySmall,
                    color = RootInk,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (sessionExpired) "Your session ended. Sign in again to re-enter your Veltrix world."
                    else "One persistent learning account for projects, memory, progression and the work that matters now.",
                    color = RootMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            AuthSurface {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = !state.processing,
                        onClick = {
                            if (activity == null) onGoogleFailure("Google sign-in could not open on this screen.")
                            else scope.launch {
                                runGoogleCredentialFlow(
                                    context = activity,
                                    authorizedOnly = false,
                                    onCredential = onGoogleToken,
                                    onFailure = onGoogleFailure,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("continue-google"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = RootInk),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("G", fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(10.dp))
                        Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = RootMuted.copy(alpha = .18f))
                        Text("  or  ", color = RootMuted, style = MaterialTheme.typography.labelSmall)
                        HorizontalDivider(Modifier.weight(1f), color = RootMuted.copy(alpha = .18f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.mode == AuthMode.SIGN_IN, onClick = { onMode(AuthMode.SIGN_IN) }, label = { Text("Sign in") })
                        FilterChip(selected = state.mode == AuthMode.CREATE_ACCOUNT, onClick = { onMode(AuthMode.CREATE_ACCOUNT) }, label = { Text("Create account") })
                    }
                    AnimatedContent(targetState = state.mode, label = "auth-mode") { mode ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (mode == AuthMode.CREATE_ACCOUNT) {
                                OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true)
                            }
                            OutlinedTextField(login, { login = it }, Modifier.fillMaxWidth(), label = { Text("Email or username") }, singleLine = true)
                            OutlinedTextField(
                                password,
                                { password = it },
                                Modifier.fillMaxWidth(),
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            Button(
                                enabled = !state.processing && login.isNotBlank() && password.length >= 8 && (mode == AuthMode.SIGN_IN || displayName.isNotBlank()),
                                onClick = {
                                    if (mode == AuthMode.SIGN_IN) onEmailSignIn(login, password)
                                    else onCreateAccount(login, password, displayName)
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) {
                                if (state.processing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text(if (mode == AuthMode.SIGN_IN) "Sign in" else "Create account")
                            }
                        }
                    }
                    AnimatedVisibility(state.error != null) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text("Account and progression truth always comes from the Veltrix server.", color = RootMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun RootResetWorldScene(destination: String, content: @Composable BoxScope.() -> Unit) {
    val accent = when (destination) {
        "PERSONAL" -> RootViolet
        "STORE" -> Color(0xFFF0A15F)
        "PROJECTS" -> RootMint
        "CONNECTION" -> Color(0xFFE88973)
        else -> RootSky
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFFF8FAFF), accent.copy(alpha = .11f), Color(0xFFF2FFF9))),
        ),
        content = content,
    )
}

@Composable
private fun AuthSurface(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = .78f))
            .semantics { contentDescription = "Veltrix sign-in controls" },
        content = content,
    )
}

@Composable
private fun IdentityMark(modifier: Modifier) {
    Box(
        modifier.clip(CircleShape).background(
            Brush.radialGradient(listOf(Color.White, RootSky.copy(alpha = .65f), RootViolet.copy(alpha = .3f))),
        ).semantics { contentDescription = "Veltrix identity" },
    )
}

suspend fun clearGoogleCredentialState(context: Context) {
    runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
}

suspend fun runAuthorizedGoogleCredentialFlow(
    context: Context,
    onCredential: (String, String) -> Unit,
    onFailure: (String) -> Unit,
) = runGoogleCredentialFlow(context, true, onCredential, onFailure)

private suspend fun runGoogleCredentialFlow(
    context: Context,
    authorizedOnly: Boolean,
    onCredential: (String, String) -> Unit,
    onFailure: (String) -> Unit,
) {
    val clientId = BuildConfig.VELTRIX_GOOGLE_SERVER_CLIENT_ID.trim()
    if (clientId.isBlank()) {
        onFailure("Google sign-in is not configured for this build yet.")
        return
    }
    val nonce = secureNonce()
    val request = if (authorizedOnly) {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .setNonce(nonce)
            .build()
        GetCredentialRequest.Builder().addCredentialOption(option).build()
    } else {
        val option = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
            .setNonce(nonce)
            .build()
        GetCredentialRequest.Builder().addCredentialOption(option).build()
    }
    try {
        val credential = CredentialManager.create(context).getCredential(context = context, request = request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            onFailure("Google returned an unsupported credential.")
            return
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        onCredential(google.idToken, nonce)
    } catch (_: NoCredentialException) {
        if (authorizedOnly) runGoogleCredentialFlow(context, false, onCredential, onFailure)
        else onFailure("No Google account is currently available for sign-in.")
    } catch (_: GetCredentialException) {
        onFailure("Google sign-in was cancelled or could not complete.")
    } catch (_: Throwable) {
        onFailure("Google sign-in could not complete.")
    }
}

private fun secureNonce(byteLength: Int = 32): String {
    val bytes = ByteArray(byteLength)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
