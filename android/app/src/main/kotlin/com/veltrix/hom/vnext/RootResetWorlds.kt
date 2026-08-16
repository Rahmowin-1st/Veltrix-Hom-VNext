package com.veltrix.hom.vnext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun RootHomeWorld(
    model: HomeFinalModel?,
    game: GameProfileUiModel?,
    activeProject: ProjectCardModel?,
    onMenu: () -> Unit,
) {
    val level = model?.level ?: game?.level ?: 1
    val coins = model?.coins ?: game?.coinBalance ?: 0L
    val avatar = model?.avatarId ?: game?.avatarId
    val nextXp = model?.nextLevelXp ?: game?.nextLevelXp ?: 0L
    val currentXp = model?.currentLevelXp ?: game?.currentLevelXp ?: 0L
    val progress = if (nextXp <= 0L) 1f else (currentXp.toFloat() / nextXp.toFloat()).coerceIn(0f, 1f)
    val focus = model?.currentFocus?.takeIf { it.isNotBlank() } ?: activeProject?.title
    val brain = rootBrainPulse(model)
    val nextMove = rootNextMove(model, activeProject)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        RootWorldHeader("Home", "NOW", onMenu)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    model?.displayName?.takeIf { it.isNotBlank() } ?: "Your Veltrix world",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = KineticColor.Ink,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Level $level", color = KineticColor.Muted, style = MaterialTheme.typography.labelLarge)
            }
            KineticAvatar(avatar, Modifier.size(92.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Next level", color = KineticColor.Muted, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(if ((model?.remainingXp ?: 0L) > 0) "${model?.remainingXp} XP" else "Level $level", color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = KineticColor.Sky, trackColor = Color.White.copy(alpha = .65f))
            }
            KineticGlass(radius = 22.dp, strong = true) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.End) {
                    Text("COINS", style = MaterialTheme.typography.labelSmall, color = KineticColor.Muted)
                    Text(coins.toString(), style = MaterialTheme.typography.titleMedium, color = KineticColor.Ink, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("WHAT MATTERS NOW", style = MaterialTheme.typography.labelMedium, color = KineticColor.Sky, fontWeight = FontWeight.Bold)
        Text(
            focus ?: "Build your next meaningful focus",
            style = MaterialTheme.typography.displaySmall,
            color = KineticColor.Ink,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        activeProject?.purpose?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = KineticColor.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        KineticGlass(Modifier.fillMaxWidth(), radius = 26.dp) {
            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(KineticColor.Violet.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Text("V", color = KineticColor.Violet, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("VELTRIX BRAIN PULSE", style = MaterialTheme.typography.labelSmall, color = KineticColor.Violet, fontWeight = FontWeight.Bold)
                    Text(brain, color = KineticColor.Ink, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }

        Button(onClick = {}, enabled = nextMove.enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(nextMove.label, fontWeight = FontWeight.SemiBold)
        }
        Text(nextMove.reason, color = KineticColor.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

private data class NextMove(val label: String, val reason: String, val enabled: Boolean)

private fun rootBrainPulse(model: HomeFinalModel?): String {
    if (model == null) return "Veltrix is assembling your current learning context."
    val code = model.insightCodes.firstOrNull()
    return when (code) {
        "XP_REMAINING" -> if (model.remainingXp > 0) "${model.remainingXp} XP remains before your next level." else "Your current level is fully resolved."
        "UNFINISHED_GOALS" -> "Your active goals still contain unfinished work."
        "WEAK_REVIEW" -> "Review is currently a higher-impact move than adding more new material."
        "MAP_STATUS" -> when (model.mapState.uppercase()) {
            "LOCKED" -> "Your learning map is still building enough evidence to open."
            "ACTIVE", "ELIGIBLE" -> "Your personal learning map has a meaningful next path available."
            else -> "Your map state changed with your latest learning evidence."
        }
        else -> when {
            !model.currentFocus.isNullOrBlank() -> "Your current project remains the strongest place to continue."
            model.memoryMaturity.isNotBlank() -> "Your student model is ${model.memoryMaturity.lowercase()} and still adapting from real work."
            else -> "Veltrix is waiting for enough evidence to make a stronger recommendation."
        }
    }
}

private fun rootNextMove(model: HomeFinalModel?, project: ProjectCardModel?): NextMove {
    val key = model?.priorityKeys?.firstOrNull()
    return when (key) {
        "PROJECT_FOCUS" -> NextMove("Continue ${model.currentFocus ?: project?.title ?: "project"}", "Resume the strongest active context.", true)
        "WEAK_REVIEW" -> NextMove("Review unstable concepts", "Based on active mistake evidence.", true)
        "PERSONAL_MAP" -> NextMove("Continue your learning map", "Your current map state is ready for action.", true)
        "RECENT_SOURCE" -> NextMove("Work from recent sources", "Recent source context is available.", true)
        else -> if (project != null) NextMove("Enter ${project.title}", "Continue your most recent active project.", true)
        else NextMove("Start with Veltrix", "Create a meaningful project or ask a learning question.", true)
    }
}

@Composable
fun RootPersonalWorld(model: PersonalFinalModel?, map: PersonalMapUiModel?, game: GameProfileUiModel?, onMenu: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        RootWorldHeader("Personal", "WHO YOU'RE BECOMING", onMenu)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KineticAvatar(model?.avatarId ?: game?.avatarId, Modifier.size(136.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(model?.displayName?.ifBlank { "Veltrix learner" } ?: "Veltrix learner", style = MaterialTheme.typography.headlineSmall, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
                Text("Level ${model?.level ?: game?.level ?: 1}", color = KineticColor.Violet, fontWeight = FontWeight.Bold)
                Text("${model?.coins ?: game?.coinBalance ?: 0} coins", color = KineticColor.Muted)
                Text("Memory ${model?.memoryMaturity?.ifBlank { "building" } ?: "building"}", color = KineticColor.Muted)
            }
        }

        Text("GROWTH SIGNALS", style = MaterialTheme.typography.labelMedium, color = KineticColor.Violet, fontWeight = FontWeight.Bold)
        RootSignalStrip("Strengths", model?.strengths.orEmpty(), KineticColor.Mint)
        RootSignalStrip("Needs review", model?.weaknesses.orEmpty(), Color(0xFFE88973))
        RootSignalStrip("Goals", model?.goals.orEmpty(), KineticColor.Sky)

        Text("PERSONAL LEARNING MAP", style = MaterialTheme.typography.labelMedium, color = KineticColor.Violet, fontWeight = FontWeight.Bold)
        RootMapStage(map)
    }
}

@Composable
private fun RootSignalStrip(label: String, values: List<String>, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = KineticColor.Muted, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (values.isEmpty()) {
                Text("Not enough evidence yet", color = KineticColor.Muted, modifier = Modifier.padding(vertical = 8.dp))
            } else values.take(6).forEach { value ->
                KineticGlass(radius = 18.dp) { Text(value, color = KineticColor.Ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), maxLines = 1) }
            }
        }
    }
}

@Composable
private fun RootMapStage(map: PersonalMapUiModel?) {
    KineticGlass(Modifier.fillMaxWidth().height(250.dp), radius = 30.dp) {
        if (map == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Map state is loading from Veltrix.", color = KineticColor.Muted) }
            return@KineticGlass
        }
        if (!map.eligible || map.state.equals("LOCKED", true)) {
            Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
                Text("Your map is still forming", style = MaterialTheme.typography.headlineSmall, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Level ${map.levelRequirement} and ${map.memoryRequirement.lowercase()} memory readiness are required by your current progression rules.", color = KineticColor.Muted)
            }
            return@KineticGlass
        }
        val units = map.units.sortedBy { it.ordinal }.take(8)
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Personal learning map with ${units.size} visible units" }) {
                if (units.isEmpty()) return@Canvas
                val gap = size.width / max(1, units.size - 1)
                val points = units.mapIndexed { i, unit -> Offset(i * gap, size.height * if (i % 2 == 0) .58f else .34f) }
                val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, KineticColor.Violet.copy(alpha = .34f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                points.forEachIndexed { i, point ->
                    val state = units[i].state.uppercase()
                    val color = when (state) {
                        "COMPLETED" -> KineticColor.Mint
                        "IN_PROGRESS", "ACTIVE" -> KineticColor.Violet
                        "AVAILABLE" -> KineticColor.Sky
                        else -> KineticColor.Muted.copy(alpha = .35f)
                    }
                    drawCircle(Color.White, 18.dp.toPx(), point)
                    drawCircle(color, 12.dp.toPx(), point)
                }
            }
            Column(Modifier.align(Alignment.TopStart)) {
                Text("Current path", color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
                Text(map.state.lowercase().replaceFirstChar { it.uppercase() }, color = KineticColor.Muted)
            }
        }
    }
}

@Composable
fun RootStoreWorld(
    store: StoreCatalogUiModel?,
    inventory: List<InventoryItemUiModel>,
    avatars: List<AvatarCatalogUiModel>,
    game: GameProfileUiModel?,
    onMenu: () -> Unit,
) {
    var selectedAvatarId by remember(avatars) { mutableStateOf(avatars.firstOrNull { it.equipped }?.avatarId ?: avatars.firstOrNull()?.avatarId) }
    val selectedAvatar = avatars.firstOrNull { it.avatarId == selectedAvatarId }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        RootWorldHeader("Store", "COLLECT · PREVIEW · EQUIP", onMenu)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Preview Studio", style = MaterialTheme.typography.displaySmall, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
                Text("See identity in context before any purchase or equip action.", color = KineticColor.Muted)
            }
            KineticGlass(radius = 20.dp, strong = true) { Text("${store?.coinBalance ?: game?.coinBalance ?: 0} coins", modifier = Modifier.padding(13.dp), color = KineticColor.Ink, fontWeight = FontWeight.Bold) }
        }
        KineticGlass(Modifier.fillMaxWidth().height(260.dp), radius = 32.dp) {
            Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                KineticAvatar(selectedAvatar?.avatarId ?: game?.avatarId, Modifier.size(150.dp), contentDescription = selectedAvatar?.name ?: "Equipped Veltrix avatar")
                Spacer(Modifier.height(8.dp))
                Text(selectedAvatar?.name ?: "Your equipped identity", color = KineticColor.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(selectedAvatar?.tier?.lowercase()?.replaceFirstChar { it.uppercase() } ?: game?.avatarTier.orEmpty(), color = KineticColor.Muted)
            }
        }
        Text("AVATAR IDENTITIES", style = MaterialTheme.typography.labelMedium, color = KineticColor.Ember, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            avatars.take(12).forEach { avatar ->
                KineticGlass(
                    Modifier.width(132.dp).clickable { selectedAvatarId = avatar.avatarId },
                    radius = 24.dp,
                    strong = avatar.avatarId == selectedAvatarId,
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        KineticAvatar(avatar.avatarId, Modifier.size(70.dp), avatar.name)
                        Text(avatar.name, color = KineticColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (avatar.equipped) "Equipped" else if (avatar.owned) "Owned" else avatar.storePrice?.let { "$it coins" } ?: "Unavailable", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text("${inventory.size} owned customization items", color = KineticColor.Muted)
        val cosmeticTypes = store?.items.orEmpty().map { friendlyItemType(it.itemType) }.distinct().take(6)
        if (cosmeticTypes.isNotEmpty()) Text(cosmeticTypes.joinToString("  ·  "), color = KineticColor.Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun friendlyItemType(raw: String): String = raw.lowercase().replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

@Composable
fun RootProjectsWorld(projects: List<ProjectCardModel>, onMenu: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        RootWorldHeader("Projects", "GOAL-CENTRIC WORLDS", onMenu)
        Text("Operating worlds", style = MaterialTheme.typography.displaySmall, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
        Text("Each project holds its own goal, memory, sources, chats and learning work.", color = KineticColor.Muted, style = MaterialTheme.typography.bodyLarge)
        if (projects.isEmpty()) {
            KineticGlass(Modifier.fillMaxWidth().height(220.dp), radius = 32.dp) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("No active project world yet", style = MaterialTheme.typography.headlineSmall, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
                    Text("Create one around a meaningful goal — not a folder.", color = KineticColor.Muted)
                }
            }
        } else {
            projects.sortedWith(compareByDescending<ProjectCardModel> { it.priority }.thenByDescending { it.lastActiveAt }).forEachIndexed { index, project ->
                RootProjectIdentity(project, hero = index == 0)
            }
        }
    }
}

@Composable
private fun RootProjectIdentity(project: ProjectCardModel, hero: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(if (hero) 210.dp else 150.dp).clip(RoundedCornerShape(if (hero) 34.dp else 28.dp))
            .background(if (hero) KineticColor.Mint.copy(alpha = .14f) else Color.White.copy(alpha = .58f))
            .semantics { contentDescription = "Project ${project.title}, ${project.status}" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * .55f
            drawCircle(KineticColor.Mint.copy(alpha = if (hero) .16f else .08f), r, Offset(size.width * .88f, size.height * .18f))
            drawCircle(KineticColor.Sky.copy(alpha = .10f), r * .65f, Offset(size.width * .08f, size.height * .9f))
        }
        Column(Modifier.fillMaxSize().padding(if (hero) 24.dp else 20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth()) {
                Text(if (hero) "ACTIVE WORLD" else project.status, color = KineticColor.Mint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("P${project.priority}", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
            }
            Column {
                Text(project.title, style = if (hero) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                project.purpose?.takeIf { it.isNotBlank() }?.let { Text(it, color = KineticColor.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun RootWorldHeader(title: String, context: String, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu, modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = .68f))) {
            Text("≡", color = KineticColor.Ink, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(context, color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
