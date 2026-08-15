package com.veltrix.hom.vnext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Accessibility-first Home arrangement for very large system font scales.
 * It keeps the authoritative identity/focus and primary continuation action in the
 * first viewport, then lets secondary signals scroll below. No values or eligibility
 * are computed here; all displayed state is taken from the accepted repository model.
 */
@Composable
fun AccessibleLargeTextHome(
    state: RepositoryState<HomeFinalModel>,
    sessionResolved: Boolean,
    onRetry: () -> Unit,
    onOpenPersonal: () -> Unit,
    onAskVeltrix: () -> Unit,
    onPractice: () -> Unit,
    onProjects: () -> Unit,
) {
    val model = state.value
    if (!sessionResolved || model == null) {
        HomeScreen(
            state,
            sessionResolved,
            onRetry,
            onOpenPersonal,
            onAskVeltrix,
            onPractice,
            onProjects,
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("home-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            if (state.freshness != DataFreshness.FRESH) {
                GlassSurface(Modifier.fillMaxWidth(), radius = 18.dp, strong = true) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (state.freshness == DataFreshness.OFFLINE) "Offline · showing available saved state"
                            else "Showing saved data · refresh unavailable",
                            color = VeltrixColors.InkMuted,
                        )
                        PressableGlass(onRetry, Modifier.fillMaxWidth().heightIn(min = 48.dp), radius = 16.dp) {
                            Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                                Text("Retry", color = VeltrixColors.SkyDeep, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PressableGlass(
                    onOpenPersonal,
                    Modifier.heightIn(min = 58.dp).weight(0.34f).semantics {
                        contentDescription = "Open Personal profile for ${model.displayName.ifBlank { "this account" }}"
                    },
                    radius = 24.dp,
                ) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            model.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "V",
                            color = VeltrixColors.Ink,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        model.displayName.ifBlank { "Your Veltrix account" },
                        style = MaterialTheme.typography.titleMedium,
                        color = VeltrixColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "Level ${model.level} · ${model.lifetimeXp} XP · ${model.coins} Coins",
                        color = VeltrixColors.InkMuted,
                    )
                }
            }
        }
        item {
            GlassSurface(Modifier.fillMaxWidth(), radius = 26.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NOW", color = VeltrixColors.SkyDeep, fontWeight = FontWeight.Bold)
                    Text(
                        model.currentFocus?.takeIf { it.isNotBlank() } ?: "Build your next learning focus",
                        style = MaterialTheme.typography.titleLarge,
                        color = VeltrixColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PressableGlass(
                        onAskVeltrix,
                        Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("home-primary-action"),
                        radius = 22.dp,
                        strong = true,
                    ) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (model.currentFocus.isNullOrBlank()) "Ask Veltrix" else "Continue with Veltrix",
                                color = VeltrixColors.Ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PressableGlass(onPractice, Modifier.fillMaxWidth().heightIn(min = 54.dp), radius = 20.dp) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Text("Practice", color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                    }
                }
                PressableGlass(onProjects, Modifier.fillMaxWidth().heightIn(min = 54.dp), radius = 20.dp) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Text("Projects", color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            GlassSurface(Modifier.fillMaxWidth(), radius = 24.dp) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Account signals", color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                    Text("${model.remainingXp} XP to next level", color = VeltrixColors.InkMuted)
                    Text("Consistency ${model.consistency} · ${model.qualifiedActiveDays} qualified days", color = VeltrixColors.InkMuted)
                    Text("Memory ${model.memoryMaturity.ifBlank { "Learning" }}", color = VeltrixColors.InkMuted)
                    Text("Personal Map ${model.mapState.ifBlank { "Unknown" }}", color = VeltrixColors.InkMuted)
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}
