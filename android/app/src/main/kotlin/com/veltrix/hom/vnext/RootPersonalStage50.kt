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
import androidx.compose.foundation.verticalScroll
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

@Composable
fun RootPersonalWorldStage50(model: PersonalFinalModel?, map: PersonalMapUiModel?, game: GameProfileUiModel?, onMenu: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val identity = model?.avatarId?.takeIf(String::isNotBlank) ?: game?.avatarId?.takeIf(String::isNotBlank)
    val tier = game?.avatarTier?.takeIf(String::isNotBlank)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 106.dp).testTag("personal-stage50"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PersonalHeader50(onMenu)
        KineticGlass(Modifier.fillMaxWidth().testTag("personal-identity"), 30.dp, true) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(132.dp).testTag("personal-character")) {
                    KineticAvatar(identity, Modifier.fillMaxSize(), buildString { append("Equipped Veltrix character"); tier?.let { append(", $it tier") } })
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(model?.displayName?.takeIf(String::isNotBlank) ?: "Veltrix learner", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Level ${model?.level ?: game?.level ?: 1}", color = KineticColor.Violet, fontWeight = FontWeight.Bold)
                    tier?.let { Text("${it.lowercase().replaceFirstChar(Char::uppercase)} character", color = KineticColor.Muted) }
                    Text("${model?.coins ?: game?.coinBalance ?: 0} coins", color = KineticColor.Muted)
                    Text("Memory ${model?.memoryMaturity?.takeIf(String::isNotBlank)?.lowercase() ?: "building"}", color = KineticColor.Muted)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GROWTH NOW", style = MaterialTheme.typography.labelMedium, color = KineticColor.Violet, fontWeight = FontWeight.Bold)
                Text("Real account evidence", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton({ expanded = !expanded }, Modifier.testTag("personal-growth-toggle")) { Text(if (expanded) "Less" else "Details") }
        }
        Signal50("Strength", model?.strengths.orEmpty(), KineticColor.Mint, expanded)
        Signal50("Needs review", model?.weaknesses.orEmpty(), Color(0xFFE88973), expanded)
        Signal50("Goal", model?.goals.orEmpty(), KineticColor.Sky, expanded)
        if (expanded && model != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact50("Consistency", model.currentConsistency.toString(), Modifier.weight(1f))
                Fact50("Achievements", model.achievementCount.toString(), Modifier.weight(1f))
                Fact50("Inventory", model.inventoryCount.toString(), Modifier.weight(1f))
            }
        }

        Text("PERSONAL LEARNING WORLD", style = MaterialTheme.typography.labelMedium, color = KineticColor.Violet, fontWeight = FontWeight.Bold)
        Text("Only backend-qualified regions are revealed.", color = KineticColor.Muted, style = MaterialTheme.typography.bodySmall)
        PersonalMapWorld50(map)
    }
}

@Composable
private fun PersonalHeader50(onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onMenu, modifier = Modifier.heightIn(min = 48.dp).testTag("personal-menu")) {
            Text("≡", color = KineticColor.Ink, fontWeight = FontWeight.Black)
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text("Personal", color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("WHO YOU'RE BECOMING", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun Signal50(label: String, values: List<String>, accent: Color, expanded: Boolean) {
    val visible = if (expanded) values.take(6) else values.take(1)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        if (visible.isEmpty()) Text("Not enough evidence yet", color = KineticColor.Muted)
        else visible.forEach { KineticGlass(radius = 17.dp) { Text(it, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = KineticColor.Ink, maxLines = 1) } }
    }
}

@Composable private fun Fact50(label: String, value: String, modifier: Modifier) {
    KineticGlass(modifier, 18.dp) { Column(Modifier.padding(10.dp)) { Text(label.uppercase(), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall); Text(value, color = KineticColor.Ink, fontWeight = FontWeight.Bold) } }
}

@Composable private fun PersonalMapWorld50(map: PersonalMapUiModel?) {
    KineticGlass(Modifier.fillMaxWidth().testTag("personal-map-world"), 30.dp) {
        when {
            map == null -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Text("Loading verified map state…", color = KineticColor.Muted) }
            !map.eligible || map.state.equals("LOCKED", true) -> Column(
                Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(20.dp).testTag("map-locked-gate").semantics { contentDescription = "Personal map locked. Level ${map.levelRequirement} required. Memory requirement ${map.memoryRequirement}." },
                verticalArrangement = Arrangement.Center,
            ) {
                Text("YOUR WORLD IS STILL FORMING", color = KineticColor.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp)); Text("More of the world appears only when real progression qualifies.", style = MaterialTheme.typography.headlineSmall, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(12.dp))
                Gate50("Level ${map.levelRequirement}", map.levelSatisfied); Gate50("${map.memoryRequirement.lowercase().replaceFirstChar(Char::uppercase)} memory readiness", map.memorySatisfied)
            }
            else -> {
                val units = map.units.sortedBy { it.ordinal }.take(12)
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp).testTag("map-active-world").semantics { contentDescription = "Active personal progression world with ${units.size} visible regions" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp)) { Column(Modifier.weight(1f)) { Text("EXPEDITION ${map.version}", color = KineticColor.Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Text(map.state.lowercase().replaceFirstChar(Char::uppercase), color = KineticColor.Ink, fontWeight = FontWeight.SemiBold) }; Text(map.unlockState.lowercase().replace('_',' ').replaceFirstChar(Char::uppercase), color = KineticColor.Muted) }
                    if (units.isEmpty()) Text("No visible regions are available yet.", color = KineticColor.Muted, modifier = Modifier.padding(18.dp))
                    else Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { units.forEach { Region50(it) } }
                }
            }
        }
    }
}

@Composable private fun Gate50(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) KineticColor.Mint else KineticColor.Muted.copy(.35f))); Text("${if (ok) "Met" else "Required"} · $label", color = if (ok) KineticColor.Ink else KineticColor.Muted) }
}

@Composable private fun Region50(unit: MapUnitUiModel) {
    val state = unit.state.uppercase(); val hidden = state == "HIDDEN"; val title = if (hidden) "Undiscovered region" else unit.titleKey.ifBlank { unit.semanticKey }
    val fraction = if (unit.requiredProgress <= 0) 0f else (unit.progress.toFloat() / unit.requiredProgress).coerceIn(0f,1f)
    val accent = when(state){ "COMPLETED"->KineticColor.Mint; "IN_PROGRESS","ACTIVE"->KineticColor.Violet; "AVAILABLE","ELIGIBLE"->KineticColor.Sky; else->KineticColor.Muted }
    KineticGlass(Modifier.width(184.dp).height(214.dp).testTag("map-region-${unit.unitId}").semantics { contentDescription = if(hidden) "Undiscovered region. Hidden by current progression." else "$title. State ${state.lowercase()}. Progress ${unit.progress} of ${unit.requiredProgress}." }, 26.dp, state=="ACTIVE"||state=="IN_PROGRESS") {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val y=size.height*.47f; drawOval(accent.copy(.09f), Offset(-size.width*.18f,y), Size(size.width*.90f,size.height*.48f)); drawOval(accent.copy(.14f), Offset(size.width*.35f,y-size.height*.08f), Size(size.width*.90f,size.height*.52f)); drawCircle(accent.copy(.18f), size.minDimension*.11f, Offset(size.width*.73f,size.height*.24f))
            }
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column { Text("REGION ${unit.ordinal+1}", color=accent, style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold); Text(title, color=KineticColor.Ink, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.SemiBold, maxLines=2, overflow=TextOverflow.Ellipsis) }
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) { Text(if(hidden) "HIDDEN" else state.replace('_',' '), color=KineticColor.Muted, style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold); if(!hidden && unit.requiredProgress>0){ LinearProgressIndicator({fraction}, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color=accent, trackColor=Color.White.copy(.68f)); Text("${unit.progress} / ${unit.requiredProgress}", color=KineticColor.Muted, style=MaterialTheme.typography.labelSmall) } }
            }
        }
    }
}