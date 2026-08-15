package com.veltrix.hom.vnext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val premiumInteger = NumberFormat.getIntegerInstance(Locale.US)
private fun Long.premiumPretty(): String = premiumInteger.format(this)
private fun Int.premiumPretty(): String = premiumInteger.format(this)

@Composable
fun PremiumHomeScreen(
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
        val model = state.value
        if (!sessionResolved || model == null) {
            HomeScreen(state, sessionResolved, onRetry, onOpenPersonal, onAskVeltrix, onPractice, onProjects)
        } else {
            PremiumHomeLoaded(model, state.freshness, compact, onRetry, onOpenPersonal, onAskVeltrix, onPractice, onProjects)
        }
    }
}

@Composable
private fun PremiumHomeLoaded(
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
        Modifier.fillMaxSize().padding(horizontal = if (compact) 15.dp else 19.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        if (freshness != DataFreshness.FRESH) PremiumFreshness(freshness, onRetry)
        HomeIdentity(model, compact, onOpenPersonal)
        PremiumXpProgress(model)
        LearningWorld(Modifier.weight(1f).fillMaxWidth(), model, compact, onAskVeltrix)
        ActionLens(onAskVeltrix, onPractice, onProjects)
        if (!compact) HomeSignalDock(model)
    }
}

@Composable
private fun HomeIdentity(model: HomeFinalModel, compact: Boolean, onOpenPersonal: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PremiumAvatar(model.displayName, model.avatarId, compact, onOpenPersonal)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                model.displayName.ifBlank { "Your Veltrix account" },
                style = MaterialTheme.typography.titleLarge,
                color = VeltrixColors.Ink,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(VeltrixColors.Mint))
                Text(
                    "Level ${model.level} · ${model.lifetimeXp.premiumPretty()} XP",
                    color = VeltrixColors.InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = "Level ${model.level}, ${model.lifetimeXp} lifetime experience points"
                    },
                )
            }
        }
        GlassSurface(radius = 22.dp, strong = true) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalAlignment = Alignment.End) {
                Text(model.coins.premiumPretty(), color = VeltrixColors.Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Coins", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PremiumAvatar(name: String, avatarId: String, compact: Boolean, onClick: () -> Unit) {
    val label = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "V"
    val diameter = if (compact) 60.dp else 72.dp
    PressableGlass(
        onClick = onClick,
        modifier = Modifier.size(diameter).semantics {
            contentDescription = if (avatarId.isBlank()) "Profile avatar fallback for $name" else "Equipped profile avatar for $name"
        },
        radius = 999.dp,
        strong = true,
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD4E3FF), Color(0xFFB4F2E0))),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color.White.copy(alpha = .92f), size.minDimension * .34f, c)
                drawCircle(Color(0x704B7DFF), size.minDimension * .38f, c, style = Stroke(1.2.dp.toPx()))
                drawCircle(Color(0x4056D9B3), size.minDimension * .46f, c, style = Stroke(.8.dp.toPx()))
                drawCircle(VeltrixColors.Sky, 3.dp.toPx(), Offset(size.width * .83f, size.height * .30f))
                drawCircle(VeltrixColors.Mint, 2.6.dp.toPx(), Offset(size.width * .24f, size.height * .82f))
                drawCircle(Color.White, 2.3.dp.toPx(), Offset(size.width * .18f, size.height * .24f))
            }
            Text(label, color = VeltrixColors.Ink, fontWeight = FontWeight.Bold, fontSize = if (compact) 20.sp else 25.sp)
        }
    }
}

@Composable
private fun PremiumXpProgress(model: HomeFinalModel) {
    val denominator = model.nextLevelXp.coerceAtLeast(1L)
    val fraction = (model.currentLevelXp.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${model.level} progress", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelMedium)
            Text("${model.remainingXp.premiumPretty()} XP to next level", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelMedium)
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xBFD5E0F2)).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
        ) {
            Box(
                Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(
                    Brush.horizontalGradient(listOf(VeltrixColors.Sky, Color(0xFF7C65FF), VeltrixColors.Mint)),
                ),
            )
        }
    }
}

@Composable
private fun LearningWorld(
    modifier: Modifier,
    model: HomeFinalModel,
    compact: Boolean,
    onAskVeltrix: () -> Unit,
) {
    val focus = model.currentFocus?.takeIf { it.isNotBlank() }
    Box(
        modifier
            .clip(RoundedCornerShape(if (compact) 30.dp else 40.dp))
            .drawWithCache {
                val world = Brush.linearGradient(
                    listOf(Color(0xFFD5E4FF), Color(0xFFF8FAFF), Color(0xFFDAF8EF)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val halo = Brush.radialGradient(
                    listOf(Color(0x704779FF), Color(0x1F4779FF), Color.Transparent),
                    center = Offset(size.width * .82f, size.height * .22f),
                    radius = size.minDimension * .84f,
                )
                val mint = Brush.radialGradient(
                    listOf(Color(0x4852D9AE), Color.Transparent),
                    center = Offset(size.width * .14f, size.height * .92f),
                    radius = size.minDimension * .66f,
                )
                onDrawBehind {
                    drawRect(world)
                    drawRect(halo)
                    drawRect(mint)
                    val center = Offset(size.width * .83f, size.height * .28f)
                    drawCircle(Color.White.copy(alpha = .42f), size.minDimension * .22f, center, style = Stroke(1.dp.toPx()))
                    drawCircle(Color(0x385D83EA), size.minDimension * .155f, center, style = Stroke(.8.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = .95f), 4.dp.toPx(), Offset(size.width * .94f, size.height * .14f))
                    drawCircle(VeltrixColors.Sky.copy(alpha = .78f), 3.3.dp.toPx(), Offset(size.width * .74f, size.height * .33f))
                    drawCircle(VeltrixColors.Mint.copy(alpha = .82f), 3.6.dp.toPx(), Offset(size.width * .91f, size.height * .43f))
                    drawLine(Color.White.copy(alpha = .55f), Offset(size.width * .70f, size.height * .10f), Offset(size.width * .95f, size.height * .44f), .7.dp.toPx())
                    drawLine(Color.White.copy(alpha = .30f), Offset(0f, size.height * .72f), Offset(size.width, size.height * .72f), 1.dp.toPx())
                }
            }
            .padding(if (compact) 18.dp else 24.dp)
            .semantics { contentDescription = "Current focus world" },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.fillMaxWidth(.84f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(VeltrixColors.Sky))
                    Text("NOW", color = VeltrixColors.SkyDeep, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                }
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("home-primary-action"),
                radius = 27.dp,
                strong = true,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(if (focus == null) "Ask Veltrix" else "Continue with Veltrix", fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink)
                        if (!compact) Text("Open the next learning action", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x204B7DFF)), contentAlignment = Alignment.Center) {
                        Text("→", color = VeltrixColors.SkyDeep, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionLens(onAskVeltrix: () -> Unit, onPractice: () -> Unit, onProjects: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), radius = 25.dp) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PremiumAction("Ask Veltrix", onAskVeltrix, Modifier.weight(1.18f))
            PremiumAction("Practice", onPractice, Modifier.weight(1f))
            PremiumAction("Projects", onProjects, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PremiumAction(label: String, onClick: () -> Unit, modifier: Modifier) {
    PressableGlass(onClick, modifier.heightIn(min = 50.dp), radius = 20.dp) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun HomeSignalDock(model: HomeFinalModel) {
    CoreWorldSurface(Modifier.fillMaxWidth(), 24.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp)) {
            SignalCell("Consistency", model.consistency.toString(), "${model.qualifiedActiveDays} days", VeltrixColors.Sky, Modifier.weight(1f))
            SignalCell("Memory", model.memoryMaturity.ifBlank { "Learning" }.premiumTitle(), "Model", Color(0xFF7866FF), Modifier.weight(1f))
            SignalCell("Personal Map", model.mapState.ifBlank { "Unknown" }.premiumTitle(), model.seasonId?.let { "Season $it" } ?: "Server state", VeltrixColors.Mint, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SignalCell(label: String, value: String, detail: String, accent: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Text(label, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Text(value, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PremiumPersonalScreen(
    state: RepositoryState<PersonalFinalModel>,
    sessionResolved: Boolean,
    onRetry: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("personal-screen")) {
        val expanded = maxWidth >= 720.dp
        val model = state.value
        if (!sessionResolved || model == null) {
            PersonalScreen(state, sessionResolved, onRetry)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = if (expanded) 28.dp else 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.freshness != DataFreshness.FRESH) item { PremiumFreshness(state.freshness, onRetry) }
                item { PersonalWorldHero(model) }
                item {
                    if (expanded) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ProgressionWorld(model, Modifier.weight(1f))
                            IntelligenceWorld(model, Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ProgressionWorld(model)
                            IntelligenceWorld(model)
                        }
                    }
                }
                item { PersonalMapWorld(model) }
                item { GrowthWorld(model) }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun PersonalWorldHero(model: PersonalFinalModel) {
    CoreWorldSurface(Modifier.fillMaxWidth(), 38.dp, emphasis = true) {
        Box(Modifier.fillMaxWidth().padding(20.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width * .88f, size.height * .20f)
                drawCircle(Color.White.copy(alpha = .48f), size.minDimension * .38f, center, style = Stroke(1.dp.toPx()))
                drawCircle(Color(0x3C4B7DFF), size.minDimension * .28f, center, style = Stroke(.8.dp.toPx()))
                drawCircle(VeltrixColors.Mint.copy(alpha = .82f), 3.3.dp.toPx(), Offset(size.width * .81f, size.height * .16f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PremiumAvatar(model.displayName, model.avatarId, false) {}
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                GlassSurface(radius = 22.dp, strong = true) {
                    Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
                        Text("LEVEL", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
                        Text(model.level.toString(), style = MaterialTheme.typography.headlineMedium, color = VeltrixColors.Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressionWorld(model: PersonalFinalModel, modifier: Modifier = Modifier) {
    WorldModule("Progression", modifier) {
        MetricWorldLine("Lifetime XP", model.lifetimeXp.premiumPretty(), "Backend-authoritative")
        MetricWorldLine("Coins", model.coins.premiumPretty(), "Backend-authoritative")
        MetricWorldLine("Consistency", model.currentConsistency.toString(), "Current account signal")
        MetricWorldLine("Inventory", model.inventoryCount.premiumPretty(), "Owned item count")
    }
}

@Composable
private fun IntelligenceWorld(model: PersonalFinalModel, modifier: Modifier = Modifier) {
    WorldModule("Veltrix intelligence", modifier) {
        MetricWorldLine("Memory maturity", model.memoryMaturity.ifBlank { "Unavailable" }.premiumTitle(), "Learning context available")
        TagWorld("Strengths", model.strengths, VeltrixColors.Sky)
        TagWorld("Weaknesses", model.weaknesses, Color(0xFF7866FF))
    }
}

@Composable
private fun PersonalMapWorld(model: PersonalFinalModel) {
    WorldModule("Personal Map", Modifier.fillMaxWidth().testTag("personal-map"), emphasis = true) {
        val locked = model.mapState.contains("LOCK", ignoreCase = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.mapState.ifBlank { "Unknown" }.premiumTitle(), style = MaterialTheme.typography.titleLarge, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                Text(model.seasonId?.let { "Season $it" } ?: "Season information is not exposed in this snapshot", color = VeltrixColors.InkMuted)
            }
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(if (locked) VeltrixColors.InkMuted else VeltrixColors.Mint).semantics {
                    contentDescription = "Map state ${model.mapState.ifBlank { "unknown" }}"
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        JourneyWorld(locked)
        Text(
            if (locked) "Eligibility and unlock decisions stay on the server. Veltrix reveals the journey only when your account becomes eligible."
            else "Map access is active. Unit order and hidden content remain server-authoritative; missing unit details are never invented.",
            color = VeltrixColors.InkMuted,
        )
    }
}

@Composable
private fun JourneyWorld(locked: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(25.dp)).drawWithCache {
            val sky = Brush.verticalGradient(listOf(Color(0xFFDCE8FF), Color(0xFFF8FAFF), Color(0xFFE5FAF4)))
            val glow = Brush.radialGradient(
                listOf(Color(0x526A8BFF), Color.Transparent),
                center = Offset(size.width * .78f, size.height * .12f),
                radius = size.minDimension * .92f,
            )
            onDrawBehind {
                drawRect(sky)
                drawRect(glow)
                val back = Path().apply {
                    moveTo(0f, size.height * .61f)
                    cubicTo(size.width * .18f, size.height * .42f, size.width * .34f, size.height * .70f, size.width * .55f, size.height * .53f)
                    cubicTo(size.width * .75f, size.height * .37f, size.width * .88f, size.height * .62f, size.width, size.height * .43f)
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(back, Color(0x3571D9BE))
                val front = Path().apply {
                    moveTo(0f, size.height * .76f)
                    cubicTo(size.width * .21f, size.height * .57f, size.width * .44f, size.height * .84f, size.width * .69f, size.height * .63f)
                    cubicTo(size.width * .83f, size.height * .52f, size.width * .92f, size.height * .70f, size.width, size.height * .60f)
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(front, Color(0x294B7DFF))
            }
        }.semantics {
            contentDescription = if (locked) "Personal Map is locked" else "Personal Map is unlocked; detailed unit sequence is not present in the current snapshot"
        },
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            val route = Path().apply {
                moveTo(size.width * .05f, size.height * .72f)
                cubicTo(size.width * .27f, size.height * .13f, size.width * .54f, size.height * .87f, size.width * .94f, size.height * .24f)
            }
            drawPath(
                route,
                Brush.linearGradient(listOf(Color(0xFF7396EA), Color(0xFF52D2B1))),
                alpha = if (locked) .30f else .84f,
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
            )
            val nodes = listOf(
                Offset(size.width * .07f, size.height * .68f),
                Offset(size.width * .31f, size.height * .32f),
                Offset(size.width * .56f, size.height * .59f),
                Offset(size.width * .76f, size.height * .45f),
                Offset(size.width * .92f, size.height * .27f),
            )
            nodes.forEachIndexed { index, point ->
                if (!locked) drawCircle(VeltrixColors.Sky.copy(alpha = .12f), if (index == 0) 20.dp.toPx() else 15.dp.toPx(), point)
                drawCircle(Color.White.copy(alpha = .95f), if (index == 0) 12.dp.toPx() else 9.dp.toPx(), point)
                drawCircle(if (locked) Color(0xFF9AA7B8) else if (index == 0) VeltrixColors.Mint else VeltrixColors.Sky, if (index == 0) 7.dp.toPx() else 5.dp.toPx(), point)
            }
        }
    }
}

@Composable
private fun GrowthWorld(model: PersonalFinalModel) {
    WorldModule("Growth & achievements", Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(66.dp).clip(CircleShape).background(
                    Brush.radialGradient(listOf(Color.White, Color(0xFFD7E5FF), Color(0xFFC7F3E7))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(model.achievementCount.premiumPretty(), style = MaterialTheme.typography.titleLarge, color = VeltrixColors.Ink, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("Achievements", color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                Text(
                    if (model.achievementCount == 0) "No confirmed achievements yet." else "Confirmed accomplishments in your account.",
                    color = VeltrixColors.InkMuted,
                )
            }
        }
        TagWorld("Goals", model.goals, VeltrixColors.Mint)
        TagWorld("Interests", model.interests, VeltrixColors.Sky)
        if (model.goals.isEmpty() && model.interests.isEmpty()) {
            Text("Veltrix is still building a trustworthy learning profile from real activity.", color = VeltrixColors.InkMuted)
        }
    }
}

@Composable
private fun WorldModule(
    title: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    content: @Composable () -> Unit,
) {
    CoreWorldSurface(modifier.fillMaxWidth(), 30.dp, emphasis) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (emphasis) VeltrixColors.Mint else VeltrixColors.Sky))
                Text(title, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            }
            content()
        }
    }
}

@Composable
private fun MetricWorldLine(label: String, value: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = VeltrixColors.Ink)
            Text(detail, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text(value, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TagWorld(label: String, values: List<String>, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelMedium)
        if (values.isEmpty()) {
            Text("Not enough confirmed data yet", color = VeltrixColors.InkMuted)
        } else {
            values.take(4).forEach { value ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .42f)).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
                    Text(value, color = VeltrixColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (values.size > 4) Text("+${values.size - 4} more", color = VeltrixColors.InkMuted)
        }
    }
}

@Composable
private fun PremiumFreshness(freshness: DataFreshness, onRetry: () -> Unit) {
    if (freshness == DataFreshness.FRESH) return
    GlassSurface(Modifier.fillMaxWidth(), radius = 20.dp, strong = true) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (freshness == DataFreshness.OFFLINE) "Offline · showing available saved state" else "Showing saved data · refresh unavailable",
                color = VeltrixColors.InkMuted,
                modifier = Modifier.weight(1f),
            )
            PressableGlass(onRetry, Modifier.heightIn(min = 44.dp), radius = 16.dp) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text("Retry", color = VeltrixColors.SkyDeep, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CoreWorldSurface(
    modifier: Modifier = Modifier,
    radius: Dp,
    emphasis: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val policy = rememberVeltrixEffectPolicy()
    Box(
        modifier.clip(RoundedCornerShape(radius)).drawWithCache {
            val corner = CornerRadius(radius.toPx())
            val body = if (policy.highContrast) {
                Brush.linearGradient(listOf(Color.White, Color(0xFFF7F9FD)))
            } else if (emphasis) {
                Brush.linearGradient(
                    listOf(Color(0xFFF0F5FF), Color(0xFFFBFCFF), Color(0xFFEAF9F4)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            } else {
                Brush.linearGradient(
                    listOf(Color(0xEAF8FAFF), Color(0xEAF3FAFC)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            }
            val atmosphere = Brush.radialGradient(
                listOf(if (emphasis) Color(0x385F8DFF) else Color(0x225F8DFF), Color.Transparent),
                center = Offset(size.width * .86f, size.height * .15f),
                radius = size.minDimension * .9f,
            )
            onDrawBehind {
                drawRoundRect(body, cornerRadius = corner)
                if (!policy.highContrast) drawRoundRect(atmosphere, cornerRadius = corner)
                drawRoundRect(Color.White.copy(alpha = if (policy.highContrast) .9f else .56f), cornerRadius = corner, style = Stroke(.8.dp.toPx()))
                drawRoundRect(
                    Color(0x14445E86),
                    topLeft = Offset(0f, size.height - 1.5.dp.toPx()),
                    size = Size(size.width, 1.5.dp.toPx()),
                    cornerRadius = corner,
                )
            }
        },
        content = content,
    )
}

private fun String.premiumTitle(): String = lowercase(Locale.US)
    .replace('_', ' ')
    .split(' ')
    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar().toString() } }
