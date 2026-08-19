package com.veltrix.hom.vnext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RootPersonalWorldStage50(
    model: PersonalFinalModel?,
    map: PersonalMapUiModel?,
    game: GameProfileUiModel?,
    onMenu: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val identity = model?.avatarId?.takeIf(String::isNotBlank) ?: game?.avatarId?.takeIf(String::isNotBlank)
    val tier = game?.avatarTier?.takeIf(String::isNotBlank)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 108.dp)
            .testTag("personal-stage50"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PersonalHeader50(onMenu)
        PersonalIdentityWorld50(model, game, identity, tier)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GROWTH SIGNALS", style = MaterialTheme.typography.labelMedium, color = KineticColor.Violet, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("What your real learning evidence is becoming", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall)
            }
            PressableGlass(
                onClick = { expanded = !expanded },
                modifier = Modifier.height(44.dp).width(88.dp).testTag("personal-growth-toggle"),
                radius = 22.dp,
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text(if (expanded) "Less" else "Details", color = KineticColor.Ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Signal50("Strength", model?.strengths.orEmpty(), KineticColor.Mint, expanded)
        Signal50("Needs review", model?.weaknesses.orEmpty(), Color(0xFFE06F78), expanded)
        Signal50("Goal", model?.goals.orEmpty(), KineticColor.Sky, expanded)
        if (expanded && model != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact50("Consistency", model.currentConsistency.toString(), Modifier.weight(1f))
                Fact50("Achievements", model.achievementCount.toString(), Modifier.weight(1f))
                Fact50("Inventory", model.inventoryCount.toString(), Modifier.weight(1f))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("YOUR LEARNING WORLD", style = MaterialTheme.typography.labelMedium, color = KineticColor.Violet, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Text("The map reveals only regions your account has genuinely qualified for.", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall)
        }
        PersonalMapWorld50(map)
    }
}

@Composable
private fun PersonalHeader50(onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("personal-header"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(onClick = onMenu, modifier = Modifier.size(48.dp).testTag("personal-menu"), radius = 24.dp, strong = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("≡", color = KineticColor.Ink, fontWeight = FontWeight.Black) }
        }
        Column {
            Text("Personal", color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("WHO YOU'RE BECOMING", modifier = Modifier.testTag("personal-header-subtitle"), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PersonalIdentityWorld50(
    model: PersonalFinalModel?,
    game: GameProfileUiModel?,
    identity: String?,
    tier: String?,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (largeText) 330.dp else 242.dp)
            .clip(RoundedCornerShape(36.dp))
            .drawWithCache {
                val base = Brush.linearGradient(
                    listOf(Color(0xFFF0ECFF), Color(0xFFF9FAFF), Color(0xFFFFEDF4), Color(0xFFEAF8F4)),
                    Offset.Zero,
                    Offset(size.width, size.height),
                )
                val halo = Brush.radialGradient(
                    listOf(KineticColor.Violet.copy(.34f), KineticColor.Rose.copy(.10f), Color.Transparent),
                    center = Offset(size.width * .25f, size.height * .48f),
                    radius = size.minDimension * .74f,
                )
                onDrawBehind {
                    drawRect(base)
                    drawRect(halo)
                    drawCircle(Color.White.copy(.42f), size.minDimension * .25f, Offset(size.width * .25f, size.height * .48f), style = Stroke(1.dp.toPx()))
                    drawCircle(KineticColor.Violet.copy(.16f), size.minDimension * .18f, Offset(size.width * .25f, size.height * .48f), style = Stroke(1.2.dp.toPx()))
                    val arc = Path().apply {
                        moveTo(size.width * .52f, size.height * .20f)
                        cubicTo(size.width * .70f, size.height * .04f, size.width * .86f, size.height * .18f, size.width * 1.05f, size.height * .06f)
                    }
                    drawPath(arc, Color.White.copy(.62f), style = Stroke(1.1.dp.toPx()))
                    drawCircle(KineticColor.Rose.copy(.82f), 3.5.dp.toPx(), Offset(size.width * .78f, size.height * .19f))
                    drawCircle(KineticColor.Mint.copy(.82f), 3.2.dp.toPx(), Offset(size.width * .92f, size.height * .35f))
                }
            }
            .testTag("personal-identity"),
    ) {
        if (largeText) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(104.dp).testTag("personal-character")) {
                    KineticAvatar(identity, Modifier.fillMaxSize(), buildString { append("Equipped Veltrix character"); tier?.let { append(", $it tier") } })
                }
                Text("IDENTITY IN MOTION", color = KineticColor.Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(
                    model?.displayName?.takeIf(String::isNotBlank) ?: "Veltrix learner",
                    Modifier.fillMaxWidth().semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    color = KineticColor.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Level ${model?.level ?: game?.level ?: 1}", color = KineticColor.Violet, fontWeight = FontWeight.Bold)
                    tier?.let { Text("${it.lowercase().replaceFirstChar(Char::uppercase)} character", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
                Text(
                    "${model?.coins ?: game?.coinBalance ?: 0} ◈ · Memory ${model?.memoryMaturity?.takeIf(String::isNotBlank)?.lowercase() ?: "building"}",
                    modifier = Modifier.fillMaxWidth(),
                    color = KineticColor.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(150.dp).testTag("personal-character")) {
                    KineticAvatar(
                        identity,
                        Modifier.fillMaxSize(),
                        buildString { append("Equipped Veltrix character"); tier?.let { append(", $it tier") } },
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("IDENTITY IN MOTION", color = KineticColor.Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(
                        model?.displayName?.takeIf(String::isNotBlank) ?: "Veltrix learner",
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        color = KineticColor.Ink,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Level ${model?.level ?: game?.level ?: 1}", color = KineticColor.Violet, fontWeight = FontWeight.Bold)
                    tier?.let { Text("${it.lowercase().replaceFirstChar(Char::uppercase)} character", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall) }
                    KineticGlass(radius = 18.dp, strong = true) {
                        Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                            Text("${model?.coins ?: game?.coinBalance ?: 0} ◈", color = KineticColor.Ink, fontWeight = FontWeight.Bold)
                            Text("Memory ${model?.memoryMaturity?.takeIf(String::isNotBlank)?.lowercase() ?: "building"}", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Signal50(label: String, values: List<String>, accent: Color, expanded: Boolean) {
    val visible = if (expanded) values.take(6) else values.take(1)
    val tag = label.lowercase().replace(" ", "-")
    KineticGlass(Modifier.fillMaxWidth().testTag("personal-signal-$tag"), radius = 20.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label.uppercase(), color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            if (visible.isEmpty()) {
                Text("Not enough evidence yet", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                visible.forEachIndexed { index, value ->
                    Text(
                        value,
                        modifier = Modifier.fillMaxWidth().testTag("personal-signal-$tag-value-$index"),
                        color = KineticColor.Ink,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Fact50(label: String, value: String, modifier: Modifier) {
    KineticGlass(modifier, 18.dp) {
        Column(Modifier.padding(10.dp)) {
            Text(label.uppercase(), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
            Text(value, color = KineticColor.Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PersonalMapWorld50(map: PersonalMapUiModel?) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 292.dp)
            .clip(RoundedCornerShape(36.dp))
            .drawWithCache {
                val terrain = Brush.linearGradient(
                    listOf(Color(0xFFE8E8FF), Color(0xFFF8F9FF), Color(0xFFE8F7F1), Color(0xFFFFF2E8)),
                    Offset.Zero,
                    Offset(size.width, size.height),
                )
                val distant = Brush.radialGradient(
                    listOf(KineticColor.Violet.copy(.25f), Color.Transparent),
                    center = Offset(size.width * .77f, size.height * .18f),
                    radius = size.minDimension * .72f,
                )
                onDrawBehind {
                    drawRect(terrain)
                    drawRect(distant)
                    val route = Path().apply {
                        moveTo(-size.width * .05f, size.height * .78f)
                        cubicTo(size.width * .20f, size.height * .62f, size.width * .30f, size.height * .82f, size.width * .52f, size.height * .58f)
                        cubicTo(size.width * .70f, size.height * .38f, size.width * .84f, size.height * .55f, size.width * 1.05f, size.height * .30f)
                    }
                    drawPath(route, Color.White.copy(.58f), style = Stroke(14.dp.toPx()))
                    drawPath(route, KineticColor.Violet.copy(.17f), style = Stroke(5.dp.toPx()))
                    drawCircle(Color.White.copy(.52f), size.minDimension * .17f, Offset(size.width * .83f, size.height * .19f))
                    drawCircle(KineticColor.Mint.copy(.20f), size.minDimension * .12f, Offset(size.width * .16f, size.height * .82f))
                }
            }
            .testTag("personal-map-world"),
    ) {
        when {
            map == null -> Box(Modifier.fillMaxWidth().height(292.dp), contentAlignment = Alignment.Center) {
                KineticGlass(radius = 22.dp) { Text("Loading verified map state…", Modifier.padding(horizontal = 16.dp, vertical = 11.dp), color = KineticColor.Muted) }
            }
            !map.eligible || map.state.equals("LOCKED", true) -> Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 292.dp)
                    .padding(22.dp)
                    .testTag("map-locked-gate")
                    .semantics { contentDescription = "Personal map locked. Level ${map.levelRequirement} required. Memory requirement ${map.memoryRequirement}." },
                verticalArrangement = Arrangement.Center,
            ) {
                Text("WORLD FORMING", color = KineticColor.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "More of your world appears only when real progression qualifies.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = KineticColor.Ink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(.78f),
                )
                Spacer(Modifier.height(16.dp))
                KineticGlass(radius = 22.dp) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Gate50("Level ${map.levelRequirement}", map.levelSatisfied)
                        Gate50("${map.memoryRequirement.lowercase().replaceFirstChar(Char::uppercase)} memory readiness", map.memorySatisfied)
                    }
                }
            }
            else -> {
                val units = map.units.sortedBy { it.ordinal }.take(12)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp)
                        .testTag("map-active-world")
                        .semantics { contentDescription = "Active personal progression world with ${units.size} visible regions" },
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("EXPEDITION ${map.version}", color = KineticColor.Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                            Text(map.state.lowercase().replaceFirstChar(Char::uppercase), color = KineticColor.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                        KineticGlass(radius = 17.dp) {
                            Text(map.unlockState.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (units.isEmpty()) Text("No visible regions are available yet.", color = KineticColor.Muted, modifier = Modifier.padding(18.dp))
                    else Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) { units.forEach { Region50(it) } }
                }
            }
        }
    }
}

@Composable
private fun Gate50(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) KineticColor.Mint else KineticColor.Muted.copy(.35f)))
        Text("${if (ok) "Met" else "Required"} · $label", color = if (ok) KineticColor.Ink else KineticColor.Muted)
    }
}

@Composable
private fun Region50(unit: MapUnitUiModel) {
    val state = unit.state.uppercase()
    val hidden = state == "HIDDEN"
    val title = if (hidden) "Undiscovered region" else unit.titleKey.ifBlank { unit.semanticKey }
    val fraction = if (unit.requiredProgress <= 0) 0f else (unit.progress.toFloat() / unit.requiredProgress).coerceIn(0f, 1f)
    val accent = when (state) {
        "COMPLETED" -> KineticColor.Mint
        "IN_PROGRESS", "ACTIVE" -> KineticColor.Violet
        "AVAILABLE", "ELIGIBLE" -> KineticColor.Sky
        else -> KineticColor.Muted
    }
    Box(
        Modifier
            .width(188.dp)
            .height(224.dp)
            .clip(RoundedCornerShape(28.dp))
            .drawWithCache {
                val sky = Brush.linearGradient(listOf(Color.White.copy(.78f), accent.copy(.16f), Color.White.copy(.48f)), Offset.Zero, Offset(size.width, size.height))
                onDrawBehind {
                    drawRect(sky)
                    val y = size.height * .50f
                    drawOval(accent.copy(.15f), Offset(-size.width * .20f, y), Size(size.width * .92f, size.height * .49f))
                    drawOval(accent.copy(.23f), Offset(size.width * .32f, y - size.height * .08f), Size(size.width * .95f, size.height * .54f))
                    drawCircle(Color.White.copy(.58f), size.minDimension * .14f, Offset(size.width * .74f, size.height * .23f))
                    drawCircle(accent.copy(.70f), 3.2.dp.toPx(), Offset(size.width * .74f, size.height * .23f))
                }
            }
            .testTag("map-region-${unit.unitId}")
            .semantics {
                contentDescription = if (hidden) "Undiscovered region. Hidden by current progression."
                else "$title. State ${state.lowercase()}. Progress ${unit.progress} of ${unit.requiredProgress}."
            },
    ) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("REGION ${unit.ordinal + 1}", color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(title, color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (hidden) "HIDDEN" else state.replace('_', ' '), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (!hidden && unit.requiredProgress > 0) {
                    LinearProgressIndicator({ fraction }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = accent, trackColor = Color.White.copy(.68f))
                    Text("${unit.progress} / ${unit.requiredProgress}", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
