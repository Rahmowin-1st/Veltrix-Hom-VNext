package com.veltrix.hom.vnext

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            Text("VELTRIX", color = RootInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.6.sp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IdentityMark(Modifier.size(86.dp))
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
                IdentityMark(Modifier.size(118.dp))
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
                PressableGlass(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("connection-retry"),
                    radius = 27.dp,
                    strong = true,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Try again", color = RootInk, fontWeight = FontWeight.SemiBold) }
                }
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
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("VELTRIX HOM", color = RootInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp)
                Spacer(Modifier.weight(1f))
                KineticGlass(radius = 16.dp) {
                    Text("ACCOUNT WORLD", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = RootMuted, style = MaterialTheme.typography.labelSmall)
                }
            }

            Box(Modifier.fillMaxWidth().height(178.dp), contentAlignment = Alignment.Center) {
                IdentityMark(Modifier.size(158.dp))
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

            AuthSurface {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PressableGlass(
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
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("continue-google"),
                        radius = 28.dp,
                        strong = true,
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawCircle(Color.White.copy(.92f))
                                    drawCircle(RootSky.copy(.16f), radius = size.minDimension * .44f, style = Stroke(1.dp.toPx()))
                                }
                                Text("G", color = RootInk, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Continue with Google", color = RootInk, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("→", color = RootSky, fontWeight = FontWeight.Bold)
                        }
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
                                OutlinedTextField(
                                    displayName,
                                    { displayName = it },
                                    Modifier.fillMaxWidth().testTag("auth-display-name"),
                                    label = { Text("Display name") },
                                    singleLine = true,
                                )
                            }
                            OutlinedTextField(
                                login,
                                { login = it },
                                Modifier.fillMaxWidth().testTag("auth-login"),
                                label = { Text("Email or username") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                password,
                                { password = it },
                                Modifier.fillMaxWidth().testTag("auth-password"),
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
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("auth-submit"),
                                shape = RoundedCornerShape(20.dp),
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

/**
 * Entry/gate world. This is deliberately a spatial vector environment instead of a decorative
 * gradient: distant planes, route, beacons and an optical portal tell the same world-language as
 * the signed-in product before any personal data is visible.
 */
@Composable
fun RootResetWorldScene(destination: String, content: @Composable BoxScope.() -> Unit) {
    val accent = when (destination) {
        "PERSONAL" -> RootViolet
        "STORE" -> Color(0xFFF0A15F)
        "PROJECTS" -> RootMint
        "CONNECTION" -> Color(0xFFE88973)
        else -> RootSky
    }
    val companion = when (destination) {
        "CONNECTION" -> Color(0xFFF0A15F)
        "AUTH" -> RootViolet
        else -> RootMint
    }
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val d = size.minDimension
            val base = Brush.linearGradient(
                listOf(Color(0xFFF8FAFF), Color(0xFFF1F5FF), Color(0xFFF3FFF9), Color(0xFFFFFAF5)),
                Offset.Zero,
                Offset(size.width, size.height),
            )
            val upper = Brush.radialGradient(
                listOf(accent.copy(.31f), accent.copy(.08f), Color.Transparent),
                center = Offset(size.width * .77f, size.height * .13f),
                radius = d * .82f,
            )
            val lower = Brush.radialGradient(
                listOf(companion.copy(.19f), Color.Transparent),
                center = Offset(size.width * .12f, size.height * .84f),
                radius = d * .70f,
            )
            onDrawBehind {
                drawRect(base)
                drawRect(upper)
                drawRect(lower)

                val horizon = Path().apply {
                    moveTo(-size.width * .10f, size.height * .68f)
                    cubicTo(size.width * .17f, size.height * .58f, size.width * .42f, size.height * .77f, size.width * .70f, size.height * .63f)
                    cubicTo(size.width * .88f, size.height * .54f, size.width * 1.02f, size.height * .61f, size.width * 1.10f, size.height * .53f)
                    lineTo(size.width * 1.10f, size.height * 1.10f)
                    lineTo(-size.width * .10f, size.height * 1.10f)
                    close()
                }
                drawPath(horizon, Brush.verticalGradient(listOf(Color.White.copy(.22f), accent.copy(.055f))))

                val route = Path().apply {
                    moveTo(-size.width * .06f, size.height * .78f)
                    cubicTo(size.width * .18f, size.height * .61f, size.width * .45f, size.height * .82f, size.width * .72f, size.height * .58f)
                    cubicTo(size.width * .86f, size.height * .46f, size.width * .95f, size.height * .51f, size.width * 1.07f, size.height * .34f)
                }
                drawPath(route, Color.White.copy(.58f), style = Stroke(14.dp.toPx()))
                drawPath(route, accent.copy(.14f), style = Stroke(5.dp.toPx()))

                val portal = Offset(size.width * .80f, size.height * .16f)
                drawCircle(Color.White.copy(.32f), d * .19f, portal, style = Stroke(1.dp.toPx()))
                drawCircle(accent.copy(.18f), d * .135f, portal, style = Stroke(1.4.dp.toPx()))
                drawCircle(Color.White.copy(.88f), 4.4.dp.toPx(), Offset(size.width * .89f, size.height * .09f))
                drawCircle(companion.copy(.78f), 3.4.dp.toPx(), Offset(size.width * .68f, size.height * .28f))
            }
        },
        content = content,
    )
}

@Composable
private fun AuthSurface(content: @Composable BoxScope.() -> Unit) {
    KineticGlass(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Veltrix sign-in controls" },
        radius = 30.dp,
        strong = true,
        content = content,
    )
}

/** A compact optical portal mark shared by bootstrap/auth/connection states. */
@Composable
private fun IdentityMark(modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Veltrix identity" }) {
        val d = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, RootSky.copy(.44f), RootViolet.copy(.18f), Color.Transparent),
                center,
                d * .52f,
            ),
            radius = d * .49f,
            center = center,
        )
        drawCircle(Color.White.copy(.88f), d * .42f, center, style = Stroke(1.2.dp.toPx()))
        drawCircle(RootSky.copy(.31f), d * .34f, center, style = Stroke(2.dp.toPx()))
        drawArc(
            RootViolet.copy(.72f),
            208f,
            112f,
            false,
            Offset(center.x - d * .37f, center.y - d * .37f),
            Size(d * .74f, d * .74f),
            style = Stroke(3.dp.toPx()),
        )
        drawArc(
            RootMint.copy(.78f),
            28f,
            76f,
            false,
            Offset(center.x - d * .29f, center.y - d * .29f),
            Size(d * .58f, d * .58f),
            style = Stroke(2.4.dp.toPx()),
        )
        val v = Path().apply {
            moveTo(center.x - d * .18f, center.y - d * .12f)
            lineTo(center.x, center.y + d * .18f)
            lineTo(center.x + d * .20f, center.y - d * .15f)
        }
        drawPath(v, RootInk.copy(.88f), style = Stroke(d * .045f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(Color.White.copy(.96f), d * .045f, Offset(center.x + d * .24f, center.y - d * .20f))
        drawCircle(RootSky, d * .020f, Offset(center.x + d * .24f, center.y - d * .20f))
    }
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
