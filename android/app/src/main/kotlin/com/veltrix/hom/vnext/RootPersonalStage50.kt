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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Root Reset Stage 50: identity first, growth second, personal progression world third. */
@Composable
fun RootPersonalWorldStage50(
    model: PersonalFinalModel?,
    map: PersonalMapUiModel?,
    game: GameProfileUiModel?,
    onMenu: () -> Unit,
) {
    var growthExpanded by rememberSaveable { mutableStateOf(false) }
    val identity = model?.avatarId?.takeIf { it.isNotBlank() } ?: game?.avatarId?.takeIf { it.isNotBlank() }
    val displayName = model?.displayName?.takeIf { it.isNotBlank() } ?: "Veltrix learner"
    val level = model?.level ?: game?.level ?: 1
    val coins = model?.coins ?: game?.coinBalance ?: 0L
    val memory = model?.memoryMaturity?.takeIf { it.isNotBlank() } ?: "building"
    val tier = game?.avatarTier?.takeIf { it.isNotBlank() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 106.dp)
            .testTag("personal-stage50"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RootWorldHeader("Personal", "WHO YOU'RE BECOMING", onMenu)

        KineticGlass(Modifier.fillMaxWidth().testTag("personal-identity"), radius = 30.dp, strong = true) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(Modifier.size(132.dp).testTag("personal-character"), contentAlignment = Alignment.Center) {
                    KineticAvatar(
                        identity = identity,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = buildString {
                            append("Equipped Veltrix character")
                            tier?.let { append(", $it tier") }
                        },
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        displayName,
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        color = KineticColor.Ink,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Level $level", color = KineticColor.Violet, fontWeight = FontWeight.Bold)
                    tier?.let { Text("${it.lowercase().replaceFirstChar { c -> c.uppercase() }} character", color = KineticColor.Muted) }
                    Text("$coins coins", color = KineticColor.Muted)
                    Text("Memory ${memory.lowercase()}", color = KineticColor.Muted)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GROWTH NOW", style = MaterialTheme.typography.labelMedium, color = KineticColor.Violet, fontWeight = FontWeight.Bold)
                    Text("Real signals from your current account evidence", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { growthExpanded = !growthExpanded }, modifier = Modifier.testTag("personal-growth-toggle")) {
                    Text(if (growthExpanded) "Less" else "Details")
                }
            }
            RootStage50Signal("Strength", model?.strengths.orEmpty(), KineticColor.Mint, growthExpanded)
            RootStage50Signal("Needs review", model?.weaknesses.orEmpty(), Color(0xFFE88973), growthExpanded)
            RootStage50Signal("Goal", model?.goals.orEmpty(), KineticColor.Sky, growthExpanded)
            if (growthExpanded && model != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stage50Fact("Consistency", model.currentConsistency.toString(), Modifier.weight(1f))
                    Stage50Fact("Achievements", model.achievementCount.toString(), Modifier.weight(1f))
                    Stage50Fact("Inventory", model.inventoryCount.toString(), Modifier.weight(1f))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PERSONAL LEARNING WORLD", style = MaterialTheme.typography.labelMedium, color = KineticColor.Violet, fontWeight = FontWeight.Bold)
            Text("Your path reveals only what current backend progression allows.", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall)
            PersonalMapWorldStage50(map)
        }
    }
}

@Composable
private fun RootStage50Signal(label: String, values: List<String>, accent: Color, expanded: Boolean) {
    val visible = if (expanded) values.take(6) else values.take(1)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        if (visible.isEmpty()) {
            Text("Not enough evidence yet", color = KineticColor.Muted, style = MaterialTheme.typography.bodyMedium)
        } else {
            visible.forEach { value ->
                KineticGlass(radius = 17.dp) {
                    Text(
                        value,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = KineticColor.Ink,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stage50Fact(label: String, value: String, modifier: Modifier = Modifier) {
    KineticGlass(modifier, radius = 18.dp) {
        Column(Modifier.padding(10.dp)) {
            Text(label.uppercase(), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
            Text(value, color = KineticColor.Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PersonalMapWorldStage50(map: PersonalMapUiModel?) {
    KineticGlass(Modifier.fillMaxWidth().testTag("personal-map-world"), radius = 30.dp) {
        when {
            map == null -> {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    Text("Loading your verified map state…", color = KineticColor.Muted)
                }
            }
            !map.eligible || map.state.equals("LOCKED", true) -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .padding(20.dp)
                        .testTag("map-locked-gate")
                        .semantics {
                            contentDescription = "Personal map locked. Level ${map.levelRequirement} required. Memory requirement ${map.memoryRequirement}."
                        },
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("YOUR WORLD IS STILL FORMING", color = KineticColor.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("More of the world will appear when your real progression qualifies.", style = MaterialTheme.typography.headlineSmall, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Stage50GateLine("Level ${map.levelRequirement}", map.levelSatisfied)
                    Stage50GateLine("${map.memoryRequirement.lowercase().replaceFirstChar { it.uppercase() }} memory readiness", map.memorySatisfied)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("EXPEDITION ${map.version}", color = KineticColor.Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(map.state.lowercase().replaceFirstChar { it.uppercase() }, color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(map.unlockState.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, color = KineticColor.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                    if (units.isEmpty()) {
                        Text("No visible regions are available yet.", color = KineticColor.Muted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 28.dp))
                    } else {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            units.forEach { unit -> Stage50MapRegion(unit) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stage50GateLine(label: String, satisfied: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(if (satisfied) KineticColor.Mint else KineticColor.Muted.copy(alpha = .35f)),
        )
        Text("${if (satisfied) "Met" else "Required"} · $label", color = if (satisfied) KineticColor.Ink else KineticColor.Muted)
    }
}

@Composable
private fun Stage50MapRegion(unit: MapUnitUiModel) {
    val state = unit.state.uppercase()
    val hidden = state == "HIDDEN"
    val title = if (hidden) "Undiscovered region" else unit.titleKey.ifBlank { unit.semanticKey }
    val fraction = if (unit.requiredProgress <= 0L) 0f else (unit.progress.toFloat() / unit.requiredProgress.toFloat()).coerceIn(0f, 1f)
    val accent = when (state) {
        "COMPLETED" -> KineticColor.Mint
        "IN_PROGRESS", "ACTIVE" -> KineticColor.Violet
        "AVAILABLE", "ELIGIBLE" -> KineticColor.Sky
        else -> KineticColor.Muted
    }
    KineticGlass(
        Modifier
            .width(184.dp)
            .height(214.dp)
            .testTag("map-region-${unit.unitId}")
            .semantics {
                contentDescription = if (hidden) {
                    "Undiscovered map region. Hidden by current progression."
                } else {
                    "$title. State ${state.lowercase()}. Progress ${unit.progress} of ${unit.requiredProgress}."
                }
            },
        radius = 26.dp,
        strong = state == "ACTIVE" || state == "IN_PROGRESS",
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val horizon = size.height * .47f
                drawOval(
                    color = accent.copy(alpha = .09f),
                    topLeft = Offset(-size.width * .18f, horizon),
                    size = Size(size.width * .90f, size.height * .48f),
                )
                drawOval(
                    color = accent.copy(alpha = .14f),
                    topLeft = Offset(size.width * .35f, horizon - size.height * .08f),
                    size = Size(size.width * .90f, size.height * .52f),
                )
                drawCircle(accent.copy(alpha = .18f), radius = size.minDimension * .11f, center = Offset(size.width * .73f, size.height * .24f))
                repeat(3) { i ->
                    drawOval(
                        color = Color.White.copy(alpha = .32f - i * .07f),
                        topLeft = Offset(size.width * (.08f + i * .19f), size.height * (.18f + i * .07f)),
                        size = Size(size.width * .34f, size.height * .10f),
                    )
                }
            }
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("REGION ${unit.ordinal + 1}", color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(title, color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (hidden) "HIDDEN" else state.replace('_', ' '), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    if (!hidden && unit.requiredProgress > 0L) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = accent,
                            trackColor = Color.White.copy(alpha = .68f),
                        )
                        Text("${unit.progress} / ${unit.requiredProgress}", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
