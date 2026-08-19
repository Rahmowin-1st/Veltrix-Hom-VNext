package com.veltrix.hom.vnext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class RootHomeRoute { PROJECTS, PERSONAL, MISTAKES, LIBRARY, CHAT }

private data class Stage40NextMove(
    val label: String,
    val reason: String,
    val enabled: Boolean,
    val route: RootHomeRoute?,
)

/**
 * Final Root Reset Home: one fixed living command world, never a dashboard. Backend truth stays in
 * HomeFinalModel/ProjectCardModel; the scene only turns that truth into spatial hierarchy.
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
            .padding(horizontal = 18.dp)
            .padding(bottom = 92.dp)
            .testTag("home-stage40"),
    ) {
        val compact = maxWidth < 600.dp || maxHeight < 860.dp
        val heroRadius = if (compact) 32.dp else 40.dp

        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            HomeWorldHeader40(
                name = model?.displayName?.takeIf { it.isNotBlank() } ?: "Veltrix learner",
                level = level,
                coins = coins,
                onMenu = onMenu,
                compact = compact,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(heroRadius))
                    .drawWithCache {
                        val world = Brush.linearGradient(
                            listOf(Color(0xFFE7EEFF), Color(0xFFF8FAFF), Color(0xFFE4F8F1), Color(0xFFFFF4E9)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        )
                        val sky = Brush.radialGradient(
                            listOf(KineticColor.Sky.copy(.42f), KineticColor.Violet.copy(.12f), Color.Transparent),
                            center = Offset(size.width * .78f, size.height * .20f),
                            radius = size.minDimension * .78f,
                        )
                        val ground = Brush.radialGradient(
                            listOf(KineticColor.Mint.copy(.28f), Color.Transparent),
                            center = Offset(size.width * .18f, size.height * .92f),
                            radius = size.minDimension * .72f,
                        )
                        onDrawBehind {
                            drawRect(world)
                            drawRect(sky)
                            drawRect(ground)

                            // A spatial learning path rather than a dashboard grid.
                            val path = Path().apply {
                                moveTo(-size.width * .08f, size.height * .77f)
                                cubicTo(
                                    size.width * .17f, size.height * .60f,
                                    size.width * .46f, size.height * .86f,
                                    size.width * .73f, size.height * .62f,
                                )
                                cubicTo(
                                    size.width * .87f, size.height * .50f,
                                    size.width * .98f, size.height * .57f,
                                    size.width * 1.10f, size.height * .45f,
                                )
                            }
                            drawPath(path, Color.White.copy(.58f), style = Stroke(18.dp.toPx()))
                            drawPath(path, KineticColor.Sky.copy(.16f), style = Stroke(7.dp.toPx()))

                            val beacon = Offset(size.width * .82f, size.height * .23f)
                            drawCircle(Color.White.copy(.42f), size.minDimension * .20f, beacon)
                            drawCircle(KineticColor.Sky.copy(.15f), size.minDimension * .145f, beacon, style = Stroke(1.4.dp.toPx()))
                            drawCircle(Color.White.copy(.90f), 5.dp.toPx(), Offset(size.width * .90f, size.height * .12f))
                            drawCircle(KineticColor.Mint.copy(.82f), 4.dp.toPx(), Offset(size.width * .68f, size.height * .34f))
                        }
                    }
                    .testTag("home-command-world"),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(if (compact) 16.dp else 22.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(
                            Modifier.weight(1f).padding(end = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape)) {
                                    Canvas(Modifier.fillMaxSize()) { drawCircle(KineticColor.Sky) }
                                }
                                Text(
                                    "WHAT MATTERS NOW",
                                    color = Color(0xFF3159B8),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.3.sp,
                                )
                            }
                            Text(
                                focus ?: if (model == null) "Finding your verified focus…" else "Build a meaningful next focus",
                                modifier = Modifier.testTag("home-focus").semantics { heading() },
                                color = KineticColor.Ink,
                                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            activeProject?.let { project ->
                                Text(
                                    "ACTIVE WORLD · ${project.title}",
                                    modifier = Modifier.testTag("home-active-project"),
                                    color = Color(0xFF237E68),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            model?.let { verified ->
                                val rhythm = buildList {
                                    if (verified.qualifiedActiveDays > 0) add("${verified.qualifiedActiveDays} active days")
                                    verified.memoryMaturity.takeIf { it.isNotBlank() }?.let { add("${it.lowercase()} memory") }
                                }.joinToString(" · ")
                                if (rhythm.isNotBlank()) Text(
                                    rhythm,
                                    modifier = Modifier.testTag("home-recent-state"),
                                    color = KineticColor.Muted,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Box(
                            Modifier.size(if (compact) 150.dp else 184.dp).testTag("home-avatar-anchor"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                val stroke = if (compact) 7.dp.toPx() else 8.dp.toPx()
                                drawCircle(Color.White.copy(.46f), radius = size.minDimension * .44f, style = Stroke(stroke))
                                drawArc(
                                    color = KineticColor.Sky.copy(.88f),
                                    startAngle = -90f,
                                    sweepAngle = 360f * progress,
                                    useCenter = false,
                                    style = Stroke(stroke, cap = StrokeCap.Round),
                                )
                                drawCircle(KineticColor.Violet.copy(.18f), radius = size.minDimension * .34f, style = Stroke(1.2.dp.toPx()))
                            }
                            KineticAvatar(
                                avatar,
                                Modifier.size(if (compact) 118.dp else 146.dp),
                                "Current Veltrix avatar, level $level",
                            )
                            KineticGlass(
                                Modifier.align(Alignment.BottomEnd).testTag("home-level-orbit"),
                                radius = 15.dp,
                                strong = true,
                            ) {
                                Text(
                                    if (model == null) "L$level" else if (model.remainingXp > 0) "L$level · ${model.remainingXp} XP" else "L$level · ready",
                                    Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    color = KineticColor.Ink,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                        KineticGlass(
                            Modifier.fillMaxWidth().testTag("home-brain-pulse"),
                            radius = if (compact) 22.dp else 26.dp,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.size(34.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        drawCircle(KineticColor.Violet.copy(.19f))
                                        drawCircle(KineticColor.Violet.copy(.72f), radius = size.minDimension * .16f)
                                    }
                                    Text("V", color = KineticColor.Violet, fontWeight = FontWeight.Black)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("VELTRIX BRAIN", color = KineticColor.Violet, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        brain,
                                        color = KineticColor.Ink,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = if (compact) 2 else 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        PressableGlass(
                            onClick = { nextMove.route?.let(onNextMove) },
                            enabled = nextMove.enabled && nextMove.route != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (compact) 54.dp else 60.dp)
                                .testTag("home-next-move")
                                .semantics { contentDescription = "Next move: ${nextMove.label}. ${nextMove.reason}" },
                            radius = if (compact) 27.dp else 30.dp,
                            strong = true,
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 17.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        nextMove.label,
                                        color = KineticColor.Ink,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!compact) {
                                        Text(
                                            nextMove.reason,
                                            modifier = Modifier.testTag("home-next-move-reason"),
                                            color = KineticColor.Muted,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Text("→", color = KineticColor.Sky, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWorldHeader40(name: String, level: Int, coins: Long, onMenu: () -> Unit, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PressableGlass(
            onClick = onMenu,
            modifier = Modifier.size(48.dp).testTag("home-menu"),
            radius = 24.dp,
            strong = true,
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("≡", color = KineticColor.Ink, fontWeight = FontWeight.Black) } }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                name,
                modifier = Modifier.testTag("home-identity"),
                color = KineticColor.Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) Text("YOUR WORLD · NOW", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
        }
        KineticGlass(radius = 18.dp, strong = true) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("L$level", color = KineticColor.Violet, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Box(Modifier.width(1.dp).height(14.dp)) { Canvas(Modifier.fillMaxSize()) { drawRect(KineticColor.Line) } }
                Text("$coins ◈", color = KineticColor.Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HomeProgress40(progress: Float, model: HomeFinalModel?, compact: Boolean) {
    Column(Modifier.fillMaxWidth().testTag("home-progression"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("NEXT LEVEL", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
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
            modifier = Modifier.fillMaxWidth().height(if (compact) 7.dp else 8.dp).clip(CircleShape),
            color = KineticColor.Sky,
            trackColor = Color.White.copy(alpha = .55f),
        )
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
