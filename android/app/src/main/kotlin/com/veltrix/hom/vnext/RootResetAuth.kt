package com.veltrix.hom.vnext

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    RootResetWorldScene(destination = "AUTH", interactive = false) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("VELTRIX", color = RootInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(.46f), color = RootSky, trackColor = Color.White.copy(alpha = .32f))
                Text("Restoring your world", style = MaterialTheme.typography.headlineMedium, color = RootInk, fontWeight = FontWeight.SemiBold)
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
    RootResetWorldScene(destination = "CONNECTION", interactive = false) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFED8E72)))
                Text("VELTRIX · CONNECTION", color = RootInk, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                KineticIdentityMark(Modifier.size(116.dp), motion = false)
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
                    else "Veltrix needs a live connection before it can show or change account state.",
                    color = RootMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = 52.dp).testTag("connection-retry")) { Text("Try again") }
            }
            Text("No stale account state is treated as current while disconnected.", color = RootMuted, style = MaterialTheme.typography.labelMedium)
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

    RootResetWorldScene(destination = "AUTH", interactive = true) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("VELTRIX HOM", color = RootInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("ACCOUNT WORLD", color = RootMuted, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                KineticIdentityMark(Modifier.size(178.dp), motion = !rememberVeltrixEffectPolicy().reducedMotion)
            }

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

            RootGlassControl(Modifier.fillMaxWidth()) {
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
                        HorizontalDivider(Modifier.weight(1f), color = Color(0x1F334155))
                        Text("  or  ", color = RootMuted, style = MaterialTheme.typography.labelSmall)
                        HorizontalDivider(Modifier.weight(1f), color = Color(0x1F334155))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuthMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.mode == mode,
                                onClick = { onMode(mode) },
                                label = { Text(if (mode == AuthMode.SIGN_IN) "Sign in" else "Create account") },
                            )
                        }
                    }

                    AnimatedContent(targetState = state.mode, label = "auth-mode") { mode ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (mode == AuthMode.CREATE_ACCOUNT) {
                                OutlinedTextField(
                                    displayName,
                                    { displayName = it },
                                        Modifier.fillMaxWidth(),
                                      label = { Text("Display name") },
                                      singleLine = true,
                                )
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

            Text(
                "Signing in unlocks your personal learning world. Account and progression truth always comes from the Veltrix server.",
                color = RootMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun RootResetWorldScene(destination: String, interactive: Boolean, content: @Composable BoxScope.() -> Unit) {
    val policy = rememberVeltrixEffectPolicy()
    val transition = rememberInfiniteTransition(label = "world-ambient")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000), RepeatMode.Reverse),
        label = "ambient-phase",
    )
    val livePhase = if (policy.reducedMotion || !interactive) .35f else phase
    val accent = when (destination) {
        "PERSONAL" -> RootViolet
        "STORE" -> Color(0xFFF0A15F)
        "PROJECTS" -> RootMint
        "CONNECTION" -> Color(0xFFE88973)
        else -> RootSky
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFFF8FAFF), Color(0xFFEAF1FF), Color(0xFFF2FFF9)),
                start = Offset.Zero,
                end = Offset(1400f, 2200f),
            ),
        ),
    ) {
        Canvas(Modifier.fillMaxSize().alpha(if (policy.highContrast) .34f else .72f)) {
            val center = Offset(size.width * (.62f + livePhase * .04f), size.height * (.28f + livePhase * .03f))
            drawCircle(accent.copy(alpha = .16f), radius = size.minDimension * .44f, center = center)
            drawCircle(RootMint.copy(alpha = .10f), radius = size.minDimension * .34f, center = Offset(size.width * .12f, size.height * .72f))
            drawOval(
                Brush.radialGradient(listOf(Color.White.copy(alpha = .78f), Color.Transparent)),
                topLeft = Offset(size.width * .08f, size.height * .08f),
                size = Size(size.width * .9f, size.height * .38f),
            )
        }
        content()
    }
}

@Composable
fun RootGlassControl(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val policy = rememberVeltrixEffectPolicy()
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(if (policy.highContrast) Color(0xFFF7F9FC) else Color.White.copy(alpha = .58f))
            .semantics { contentDescription = "Veltrix control surface" },
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = .78f), RootSky.copy(alpha = .08f), RootMint.copy(alpha = .08f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = .82f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        content()
    }
}

@Composable
private fun KineticIdentityMark(modifier: Modifier, motion: Boolean) {
    val transition = rememberInfiniteTransition(label = "identity-idle")
    val raw by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3_800), RepeatMode.Reverse), label = "identity-idle-phase")
    val phase by animateFloatAsState(if (motion) raw else .45f, label = "identity-policy")
    Canvas(modifier.semantics { contentDescription = "Veltrix identity" }) {
        val r = size.minDimension * .31f
        val c = center + Offset(0f, (phase - .5f) * 8.dp.toPx())
        drawCircle(Brush.radialGradient(listOf(Color.White, RootSky.copy(alpha = .72f), RootViolet.copy(alpha = .34f)), c, r * 1.55f), r * 1.18f, c)
        val stroke = 5.dp.toPx()
        drawArc(RootInk.copy(alpha = .76f), -28f, 214f, false, topLeft = Offset(c.x-r, c.y-r), size = Size(r*2,r*2), style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawCircle(RootMint.copy(alpha = .9f), radius = 8.dp.toPx(), center = c + Offset(r * .64f, -r * .56f))
        drawCircle(Color.White.copy(alpha = .9f), radius = r * .18f, center = c + Offset(-r * .2f, -r * .25f))
    }
}

suspend fun clearGoogleCredentialState(context: Context) {
    runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
}

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
    val manager = CredentialManager.create(context)
    val nonce = secureNonce()
    try {
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
        val result = manager.getCredential(request = request, context = context)
        val credential = result.credential
        if (credential ! is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
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
