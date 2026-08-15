package com.veltrix.hom.vnext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressBarRangeInfo
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val integerFormat = NumberFormat.getIntegerInstance(Locale.US)
private fun Long.pretty(): String = integerFormat.format(this)
private fun Int.pretty(): String = integerFormat.format(this)

@Composable
fun HomeScreen(
    state: RepositoryState<HomeFinalModel>,
    sessionResolved: Boolean,
    onRetry: () -> Unit,
    onOpenPersonal: () -> Unit,
    onAskVeltrix: () -> Unit,
    onPractice: () -> Unit,
    onProjects: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("home-screen")) {
        val compact = maxHeight < 690.dp || maxWidth < 350.dp
        when {
            !sessionResolved -> LoadingWorld("Preparing your world")
            state.value == null && state.errorCode == "NO_SESSION" -> NoSessionWorld()
            state.value == null && state.loading -> LoadingWorld("Loading your world")
            state.value == null -> FullErrorWorld(state.errorCode, state.retryable, onRetry)
            else -> HomeContent(
                model = state.value,
                freshness = state.freshness,
                compact = compact,
                onRetry = onRetry,
                onOpenPersonal = onOpenPersonal,
                onAskVeltrix = onAskVeltrix,
                onPractice = onPractice,
                onProjects = onProjects,
            )
        }
    }
}

@Composable
private fun HomeContent(
    model: HomeFinalModel,
    freshness: DataFreshness,
    compact: Boolean,
    onRetry: () -> Unit,
    onOpenPersonal: () -> Unit,
    onAskVeltrix: () -> Unit,
    onPractice: () -> Unit,
    onProjects: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = if (compact) 16.dp else 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        if (freshness != DataFreshness.FRESH) FreshnessBanner(freshness, onRetry)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AvatarOrb(model.displayName, model.avatarId, compact, onOpenPersonal)
            Column(Modifier.weight(1f)) {
                Text(
                    model.displayName.ifBlank { "Your Veltrix account" },
                    style = MaterialTheme.typography.titleLarge,
                    color = VeltrixColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Level ${model.level}  ·  ${model.lifetimeXp.pretty()} XP",
                    color = VeltrixColors.InkMuted,
                    modifier = Modifier.semantics {
                        contentDescription = "Level ${model.level}, ${model.lifetimeXp} lifetime experience points"
                    },
                )
            }
            MetricBubble("Coins", model.coins.pretty())
        }
        XpProgress(model)
        FocusWorld(Modifier.weight(1f).fillMaxWidth(), model, compact, onAskVeltrix)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Ask Veltrix", onAskVeltrix, Modifier.weight(1.25f))
            QuickAction("Practice", onPractice, Modifier.weight(1f))
            QuickAction("Projects", onProjects, Modifier.weight(1f))
        }
        if (!compact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalSurface("Consistency", model.consistency.toString(), "${model.qualifiedActiveDays} qualified days", Modifier.weight(1f))
                SignalSurface("Memory", model.memoryMaturity.ifBlank { "Learning" }.titleCase(), "Student model maturity", Modifier.weight(1f))
                SignalSurface("Personal Map", model.mapState.ifBlank { "Unknown" }.titleCase(), model.seasonId?.let { "Season $it" } ?: "Server-authoritative state", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FocusWorld(
    modifier: Modifier,
    model: HomeFinalModel,
    compact: Boolean,
    onAskVeltrix: () -> Unit,
) {
    val focus = model.currentFocus?.takeIf { it.isNotBlank() }
    Box(
        modifier
            .clip(RoundedCornerShape(if (compact) 28.dp else 36.dp))
            .drawWithCache {
                val world = Brush.linearGradient(
                    listOf(Color(0xFFDDE9FF), Color(0xFFF7FAFF), Color(0xFFDDF9F1)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val glow = Brush.radialGradient(
                    listOf(Color(0x554B7DFF), Color.Transparent),
                    center = Offset(size.width * .72f, size.height * .35f),
                    radius = size.minDimension * .75f,
                )
                onDrawBehind { drawRect(world); drawRect(glow) }
            }
            .padding(if (compact) 18.dp else 24.dp)
            .semantics { contentDescription = "Current focus world" },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("NOW", color = VeltrixColors.SkyDeep, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text(
                    focus ?: "Build your next learning focus",
                    style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    color = VeltrixColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (focus == null) "Veltrix will surface the next meaningful signal when your account has enough context."
                    else "Your current focus comes from your account context, not a local recommendation.",
                    color = VeltrixColors.InkMuted,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PressableGlass(
                onClick = onAskVeltrix,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("home-primary-action"),
                radius = 24.dp,
                strong = true,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(if (focus == null) "Ask Veltrix" else "Continue with Veltrix", fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink)
                    Text("→", color = VeltrixColors.SkyDeep, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AvatarOrb(name: String, avatarId: String, compact: Boolean, onClick: () -> Unit) {
    val fallback = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "V"
    PressableGlass(
        onClick = onClick,
        modifier = Modifier
            .size(if (compact) 58.dp else 68.dp)
            .semantics {
                contentDescription = if (avatarId.isBlank()) "Profile avatar fallback for $name" else "Equipped profile avatar for $name"
            },
        radius = 999.dp,
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0xFFEEF4FF), Color(0xFFC8D9FF), Color(0xFFB8F0E1))),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(fallback, color = VeltrixColors.Ink, fontWeight = FontWeight.Bold, fontSize = if (compact) 20.sp else 24.sp)
        }
    }
}

@Composable
private fun MetricBubble(label: String, value: String) {
    GlassSurface(radius = 20.dp, strong = true) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalAlignment = Alignment.End) {
            Text(value, color = VeltrixColors.Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun XpProgress(model: HomeFinalModel) {
    val denominator = model.nextLevelXp.coerceAtLeast(1L)
    val fraction = (model.currentLevelXp.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${model.level} progress", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelMedium)
            Text("${model.remainingXp.pretty()} XP to next level", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelMedium)
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFFD7E2F5)).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
        ) {
            Box(
                Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(
                    Brush.horizontalGradient(listOf(VeltrixColors.Sky, Color(0xFF7B62FF), VeltrixColors.Mint)),
                ),
            )
        }
    }
}

@Composable
private fun QuickAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressableGlass(onClick = onClick, modifier = modifier.heightIn(min = 50.dp), radius = 20.dp) {
        Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink, maxLines = 1)
        }
    }
}

@Composable
private fun SignalSurface(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = 78.dp), color = Color.White.copy(alpha = .58f), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelMedium)
            Text(value, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(detail, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PersonalScreen(
    state: RepositoryState<PersonalFinalModel>,
    sessionResolved: Boolean,
    onRetry: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("personal-screen")) {
        val expanded = maxWidth >= 720.dp
        when {
            !sessionResolved -> LoadingWorld("Preparing your identity")
            state.value == null && state.errorCode == "NO_SESSION" -> NoSessionWorld()
            state.value == null && state.loading -> LoadingWorld("Loading Personal")
            state.value == null -> FullErrorWorld(state.errorCode, state.retryable, onRetry)
            else -> PersonalContent(state.value, state.freshness, expanded, onRetry)
        }
    }
}

@Composable
private fun PersonalContent(
    model: PersonalFinalModel,
    freshness: DataFreshness,
    expanded: Boolean,
    onRetry: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = if (expanded) 28.dp else 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (freshness != DataFreshness.FRESH) item { FreshnessBanner(freshness, onRetry) }
        item { PersonalHero(model) }
        item {
            if (expanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IdentityProgress(model, Modifier.weight(1f))
                    Intelligence(model, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    IdentityProgress(model)
                    Intelligence(model)
                }
            }
        }
        item { PersonalMap(model) }
        item {
            if (expanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Achievements(model, Modifier.weight(1f))
                    Growth(model, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Achievements(model)
                    Growth(model)
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun PersonalHero(model: PersonalFinalModel) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(
            Brush.linearGradient(listOf(Color(0xFFE0E9FF), Color(0xFFF8FAFF), Color(0xFFDCF8EF))),
        ).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AvatarOrb(model.displayName, model.avatarId, false) {}
            Column(Modifier.weight(1f)) {
                Text("PERSONAL", color = VeltrixColors.SkyDeep, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Text(
                    model.displayName.ifBlank { "Your learning identity" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = VeltrixColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text("Who you are becoming over time", color = VeltrixColors.InkMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("LEVEL", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
                Text(model.level.toString(), style = MaterialTheme.typography.headlineMedium, color = VeltrixColors.Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun IdentityProgress(model: PersonalFinalModel, modifier: Modifier = Modifier) {
    SectionPanel("Progression", modifier) {
        MetricLine("Lifetime XP", model.lifetimeXp.pretty(), "Backend-authoritative")
        MetricLine("Coins", model.coins.pretty(), "Backend-authoritative")
        MetricLine("Consistency", model.currentConsistency.toString(), "Current account signal")
        MetricLine("Inventory", model.inventoryCount.pretty(), "Owned item count")
    }
}

@Composable
private fun Intelligence(model: PersonalFinalModel, modifier: Modifier = Modifier) {
    SectionPanel("Veltrix intelligence", modifier) {
        MetricLine("Memory maturity", model.memoryMaturity.ifBlank { "Unavailable" }.titleCase(), "How much learning context is available")
        Tags("Strengths", model.strengths)
        Tags("Weaknesses", model.weaknesses)
    }
}

@Composable
private fun PersonalMap(model: PersonalFinalModel) {
    SectionPanel("Personal Map", Modifier.fillMaxWidth().testTag("personal-map")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.mapState.ifBlank { "Unknown" }.titleCase(), style = MaterialTheme.typography.titleLarge, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                Text(model.seasonId?.let { "Season $it" } ?: "Season information is not exposed in this snapshot", color = VeltrixColors.InkMuted)
            }
            StateDot(model.mapState)
        }
        Spacer(Modifier.height(12.dp))
        MapJourney(model.mapState.contains("LOCK", ignoreCase = true))
        Spacer(Modifier.height(10.dp))
        Text(
            if (model.mapState.contains("LOCK", ignoreCase = true))
                "Eligibility and unlock decisions stay on the server. Veltrix will reveal the journey when your account becomes eligible."
            else
                "Map access is active. Unit order and hidden content remain server-authoritative; this snapshot never invents missing unit details.",
            color = VeltrixColors.InkMuted,
        )
    }
}

@Composable
private fun MapJourney(locked: Boolean) {
    Canvas(
        Modifier.fillMaxWidth().height(150.dp).semantics {
            contentDescription = if (locked) "Personal Map is locked" else "Personal Map is unlocked; detailed unit sequence is not present in the current snapshot"
        },
    ) {
        val path = Path().apply {
            moveTo(size.width * .06f, size.height * .76f)
            cubicTo(size.width * .27f, size.height * .10f, size.width * .54f, size.height * .92f, size.width * .94f, size.height * .26f)
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(listOf(Color(0xFF8DA8E9), Color(0xFF6FD7BC))),
            alpha = if (locked) .34f else .72f,
            style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
        )
        val nodes = listOf(
            Offset(size.width * .08f, size.height * .70f),
            Offset(size.width * .32f, size.height * .35f),
            Offset(size.width * .56f, size.height * .62f),
            Offset(size.width * .76f, size.height * .48f),
            Offset(size.width * .92f, size.height * .28f),
        )
        nodes.forEachIndexed { index, point ->
            drawCircle(Color.White.copy(alpha = .88f), if (index == 0) 13.dp.toPx() else 10.dp.toPx(), point)
            drawCircle(if (locked) Color(0xFF9AA7B8) else VeltrixColors.Sky, if (index == 0) 7.dp.toPx() else 5.dp.toPx(), point)
        }
    }
}

@Composable
private fun Achievements(model: PersonalFinalModel, modifier: Modifier = Modifier) {
    SectionPanel("Achievements", modifier) {
        Text(model.achievementCount.pretty(), style = MaterialTheme.typography.displaySmall, color = VeltrixColors.Ink, fontWeight = FontWeight.Bold)
        Text(if (model.achievementCount == 0) "No achievements are exposed for this account yet." else "Confirmed accomplishments in your account.", color = VeltrixColors.InkMuted)
    }
}

@Composable
private fun Growth(model: PersonalFinalModel, modifier: Modifier = Modifier) {
    SectionPanel("Growth signals", modifier) {
        Tags("Goals", model.goals)
        Tags("Interests", model.interests)
        if (model.goals.isEmpty() && model.interests.isEmpty()) {
            Text("Veltrix is still building a trustworthy learning profile from real activity.", color = VeltrixColors.InkMuted)
        }
    }
}

@Composable
private fun SectionPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxWidth(), color = Color.White.copy(alpha = .72f), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = VeltrixColors.Ink)
            Text(detail, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text(value, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Tags(label: String, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelMedium)
        if (values.isEmpty()) {
            Text("Not enough confirmed data yet", color = VeltrixColors.InkMuted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                values.take(4).forEach { value ->
                    Surface(color = Color(0xFFF0F5FF), shape = RoundedCornerShape(14.dp)) {
                        Text(value, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), color = VeltrixColors.Ink)
                    }
                }
                if (values.size > 4) Text("+${values.size - 4} more", color = VeltrixColors.InkMuted)
            }
        }
    }
}

@Composable
private fun StateDot(state: String) {
    val color = when {
        state.contains("LOCK", ignoreCase = true) -> VeltrixColors.InkMuted
        state.contains("ACTIVE", ignoreCase = true) || state.contains("UNLOCK", ignoreCase = true) -> VeltrixColors.Mint
        else -> VeltrixColors.Amber
    }
    Box(
        Modifier.size(16.dp).clip(CircleShape).background(color).semantics {
            contentDescription = "Map state ${state.ifBlank { "unknown" }}"
        },
    )
}

@Composable
fun TransitionalProjectsScreen(
    projects: List<LocalProjectEntity>,
    create: (String, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().testTag("projects-screen"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Projects", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink, modifier = Modifier.semantics { heading() })
            Text("Functional transitional surface. Final Projects world belongs to Frontend Part 2.", color = VeltrixColors.InkMuted)
        }
        item {
            Surface(color = Color.White.copy(alpha = .72f), shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(purpose, { purpose = it }, label = { Text("Purpose") }, modifier = Modifier.fillMaxWidth())
                    PressableGlass(
                        onClick = { create(title, purpose); title = ""; purpose = "" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        strong = true,
                        enabled = title.isNotBlank(),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                            Text("Create project", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        items(projects, key = { it.id }) { project ->
            Surface(color = Color.White.copy(alpha = .66f), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text(project.title, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink)
                    Text(project.purpose ?: "No purpose", color = VeltrixColors.InkMuted)
                    Text("${project.status} · sync=${project.syncState}", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun TransitionalStoreScreen() {
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color.White.copy(alpha = .72f), shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Store", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink)
                Text("Reachable now. Final Store and Inventory experience is reserved for Frontend Part 2.", color = VeltrixColors.InkMuted)
                Text("No local prices, balances, ownership, or unlock logic is created here.", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun CapabilityBridgeScreen(name: String) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color.White.copy(alpha = .74f), shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name.titleCase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink)
                Text("Capability route retained in the global shell.", color = VeltrixColors.InkMuted)
                Text("Its final product surface is owned by Frontend Part 2 or Part 3.", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FreshnessBanner(freshness: DataFreshness, onRetry: () -> Unit) {
    val text = when (freshness) {
        DataFreshness.FRESH -> return
        DataFreshness.STALE -> "Showing saved data · refresh unavailable"
        DataFreshness.OFFLINE -> "Offline · showing available saved state"
    }
    GlassSurface(Modifier.fillMaxWidth(), radius = 18.dp, strong = true) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text, color = VeltrixColors.InkMuted)
            PressableGlass(onRetry, Modifier.heightIn(min = 44.dp), radius = 16.dp) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text("Retry", color = VeltrixColors.SkyDeep, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LoadingWorld(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = VeltrixColors.Sky)
            Spacer(Modifier.height(12.dp))
            Text(label, color = VeltrixColors.InkMuted)
        }
    }
}

@Composable
private fun NoSessionWorld() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color.White.copy(alpha = .76f), shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your account is not connected", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink)
                Text("Profile, XP, Coins, Map, and learning signals appear only from an authoritative session or saved snapshot.", color = VeltrixColors.InkMuted)
            }
        }
    }
}

@Composable
private fun FullErrorWorld(code: String?, retryable: Boolean, onRetry: () -> Unit) {
    val title = when (code) {
        "HTTP_401" -> "Session expired"
        "HTTP_403" -> "Access is not available"
        "HTTP_409" -> "This view changed elsewhere"
        "HTTP_429" -> "Veltrix is receiving too many requests"
        "HTTP_503" -> "Veltrix is temporarily unavailable"
        else -> "We couldn't load this world"
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color.White.copy(alpha = .82f), shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink)
                Text("No cached content is available. Account state is never replaced with invented local values.", color = VeltrixColors.InkMuted)
                if (retryable) {
                    Spacer(Modifier.height(10.dp))
                    PressableGlass(onRetry, Modifier.fillMaxWidth().heightIn(min = 50.dp), strong = true) {
                        Box(Modifier.fillMaxWidth().padding(13.dp), contentAlignment = Alignment.Center) {
                            Text("Try again", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun String.titleCase(): String = lowercase(Locale.US)
    .replace('_', ' ')
    .split(' ')
    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar().toString() } }
