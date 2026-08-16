package com.veltrix.hom.vnext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class RootHomeRoute { PROJECTS, PERSONAL, MISTAKES, LIBRARY, CHAT }

private data class Stage40NextMove(
    val label: String,
    val reason: String,
    val enabled: Boolean,
    val route: RootHomeRoute?,
)

/**
 * Final Root Reset Stage 40 Home.
 *
 * This surface is intentionally a compact command center rather than a dashboard. Every adaptive
 * claim is derived from fresh backend-owned HomeFinalModel/ProjectCardModel data supplied by the
 * account-first RootResetViewModel. Presentation routing never mutates backend truth.
 */
@Composable
fun RootHomeWorldStage40(
    model: HomeFinalModel?,
    game: GameProfileUiModel?,
    activeProject: ProjectCardModel?,
    onMenu: () -> Unit,
    onNextMove: (RootHomeRoute) -> Unit,
) {
    val level = model?.level ?: game?.level ?: 1
    val coins = model?.coins ?: game?.coinBalance ?: 0L
    val avatar = model?.avatarId ?: game?.avatarId
    val nextXp = model?.nextLevelXp ?: game?.nextLevelXp ?: 0L
    val currentXp = model?.currentLevelXp ?: game?.currentLevelXp ?: 0L
    val progress = if (nextXp <= 0L) 0f else (currentXp.toFloat() / nextXp.toFloat()).coerceIn(0f, 1f)
    val focus = model?.currentFocus?.takeIf { it.isNotBlank() } ?: activeProject?.title
    val brain = stage40BrainPulse(model)
    val nextMove = stage40NextMove(model, activeProject)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 92.dp)
            .testTag("home-stage40"),
    ) {
        // Phone width is the decisive constraint: the command center must fit without scrolling.
        val compact = maxWidth < 600.dp || maxHeight < 900.dp
        val sectionGap = if (compact) 6.dp else 12.dp
        val avatarSize = if (compact) 58.dp else 78.dp

        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(sectionGap),
        ) {
            Row(
                Modifier.fillMaxWidth().height(if (compact) 44.dp else 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onMenu,
                    modifier = Modifier
                        .size(if (compact) 40.dp else 44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .70f))
                        .testTag("home-menu"),
                ) {
                    Text("≡", color = KineticColor.Ink, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Home", color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!compact) {
                        Text("YOUR WORLD · NOW", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        model?.displayName?.takeIf { it.isNotBlank() } ?: "Veltrix learner",
                        modifier = Modifier.semantics { heading() }.testTag("home-identity"),
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        color = KineticColor.Ink,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Level $level", color = KineticColor.Violet, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (!compact && (model?.qualifiedActiveDays ?: 0) > 0) {
                        Text("${model?.qualifiedActiveDays} qualified active days", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                KineticAvatar(avatar, Modifier.size(avatarSize), "Current Veltrix avatar")
            }

            Row(
                Modifier.fillMaxWidth().testTag("home-progression"),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("NEXT LEVEL", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.weight(1f))
                        Text(
                            when {
                                model == null -> "Syncing"
                                model.remainingXp > 0 -> "${model.remainingXp} XP"
                                else -> "Ready"
                            },
                            color = KineticColor.Ink,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = KineticColor.Sky,
                        trackColor = Color.White.copy(alpha = .68f),
                    )
                }
                KineticGlass(radius = if (compact) 17.dp else 20.dp, strong = true) {
                    Column(
                        Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 6.dp else 8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        if (!compact) Text("COINS", style = MaterialTheme.typography.labelSmall, color = KineticColor.Muted)
                        Text(coins.toString(), style = MaterialTheme.typography.titleMedium, color = KineticColor.Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)) {
                Text("WHAT MATTERS NOW", style = MaterialTheme.typography.labelMedium, color = KineticColor.Sky, fontWeight = FontWeight.Bold)
                Text(
                    focus ?: if (model == null) "Loading your verified focus…" else "Choose a meaningful next focus",
                    style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    color = KineticColor.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("home-focus"),
                )
                activeProject?.let { project ->
                    Text(
                        "ACTIVE PROJECT · ${project.title}",
                        color = KineticColor.Mint,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("home-active-project"),
                    )
                    if (!compact) {
                        project.purpose?.takeIf { it.isNotBlank() }?.let { purpose ->
                            Text(
                                purpose,
                                color = KineticColor.Muted,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            KineticGlass(Modifier.fillMaxWidth().testTag("home-brain-pulse"), radius = if (compact) 20.dp else 24.dp) {
                Row(
                    Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 9.dp else 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(if (compact) 32.dp else 38.dp)
                            .clip(CircleShape)
                            .background(KineticColor.Violet.copy(alpha = .14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("V", color = KineticColor.Violet, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("VELTRIX BRAIN PULSE", style = MaterialTheme.typography.labelSmall, color = KineticColor.Violet, fontWeight = FontWeight.Bold)
                        Text(
                            brain,
                            color = KineticColor.Ink,
                            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (compact) 2 else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = { nextMove.route?.let(onNextMove) },
                enabled = nextMove.enabled && nextMove.route != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 48.dp else 54.dp)
                    .testTag("home-next-move")
                    .semantics { contentDescription = "Next move: ${nextMove.label}. ${nextMove.reason}" },
            ) {
                Text(nextMove.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!compact) {
                Text(
                    nextMove.reason,
                    color = KineticColor.Muted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("home-next-move-reason"),
                )
            }
        }
    }
}

private fun stage40BrainPulse(model: HomeFinalModel?): String {
    if (model == null) return "Syncing verified account evidence before making a recommendation."
    return when (model.insightCodes.firstOrNull()) {
        "XP_REMAINING" -> if (model.remainingXp > 0) "${model.remainingXp} XP remains before your next level." else "Your current level progression is resolved."
        "UNFINISHED_GOALS" -> "Your active goals still contain unfinished work."
        "WEAK_REVIEW" -> "Review is currently a higher-impact move than adding new material."
        "MAP_STATUS" -> when (model.mapState.uppercase()) {
            "LOCKED" -> "Your learning map is still building enough evidence to open."
            "ACTIVE", "ELIGIBLE" -> model.currentMapUnit?.takeIf { it.isNotBlank() }?.let { "Your map is ready to continue at $it." }
                ?: "Your personal learning map has a meaningful next path available."
            else -> "Your map changed with your latest learning evidence."
        }
        else -> when {
            !model.currentFocus.isNullOrBlank() -> "Your current focus remains the strongest place to continue."
            model.qualifiedActiveDays > 0 -> "Your account has ${model.qualifiedActiveDays} qualified active days of learning evidence."
            model.memoryMaturity.isNotBlank() -> "Your learning model is ${model.memoryMaturity.lowercase()} and still adapting from real work."
            else -> "Veltrix is waiting for stronger evidence before making a specific recommendation."
        }
    }
}

private fun stage40NextMove(model: HomeFinalModel?, project: ProjectCardModel?): Stage40NextMove {
    if (model == null && project == null) {
        return Stage40NextMove("Preparing your next move", "Waiting for verified account context.", false, null)
    }
    return when (model?.priorityKeys?.firstOrNull()) {
        "PROJECT_FOCUS" -> Stage40NextMove(
            "Continue ${model.currentFocus ?: project?.title ?: "project"}",
            "Resume the strongest active context.",
            true,
            RootHomeRoute.PROJECTS,
        )
        "WEAK_REVIEW" -> Stage40NextMove("Review unstable concepts", "Based on active mistake evidence.", true, RootHomeRoute.MISTAKES)
        "PERSONAL_MAP" -> Stage40NextMove("Continue your learning map", "Your current map state is ready for action.", true, RootHomeRoute.PERSONAL)
        "RECENT_SOURCE" -> Stage40NextMove("Work from recent sources", "Recent source context is available.", true, RootHomeRoute.LIBRARY)
        else -> if (project != null) {
            Stage40NextMove("Enter ${project.title}", "Continue your most recent active project.", true, RootHomeRoute.PROJECTS)
        } else {
            Stage40NextMove("Start with Veltrix", "Ask a learning question and build context from real work.", true, RootHomeRoute.CHAT)
        }
    }
}
