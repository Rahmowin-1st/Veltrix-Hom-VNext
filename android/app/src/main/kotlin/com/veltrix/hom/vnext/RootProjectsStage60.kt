package com.veltrix.hom.vnext

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Root Reset Stage 60. Projects are operating environments, never folders. */
@Composable
fun RootProjectsWorldStage60(
    projects: List<ProjectCardModel>,
    workspace: RepositoryState<ProjectWorkspaceUiModel>,
    onMenu: () -> Unit,
    onOpenProject: (String) -> Unit,
    onCloseProject: () -> Unit,
) {
    val freshWorkspace = workspace.value?.takeIf { workspace.freshness == DataFreshness.FRESH }
    when {
        freshWorkspace != null -> ProjectWorkspace60(freshWorkspace, onCloseProject)
        workspace.loading -> ProjectWorkspaceLoading60(onCloseProject)
        workspace.value != null && workspace.freshness != DataFreshness.FRESH -> ProjectWorkspaceUnavailable60(onCloseProject)
        else -> ProjectOverview60(projects, onMenu, onOpenProject)
    }
}

@Composable
private fun ProjectOverview60(projects: List<ProjectCardModel>, onMenu: () -> Unit, onOpenProject: (String) -> Unit) {
    val ordered = projects.sortedWith(compareByDescending<ProjectCardModel> { it.priority }.thenByDescending { it.lastActiveAt })
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 108.dp).testTag("projects-stage60"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProjectsHeader60(onMenu)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Operating worlds", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
            Text("Each project is one persistent goal-space with its own memory, sources and learning work.", color = KineticColor.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (ordered.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(34.dp)).drawWithCache {
                    val base = Brush.linearGradient(listOf(Color(0xFFE6F8F1), Color(0xFFF8FBFF), Color(0xFFE9EEFF)), Offset.Zero, Offset(size.width, size.height))
                    onDrawBehind {
                        drawRect(base)
                        drawCircle(KineticColor.Mint.copy(.20f), size.minDimension * .34f, Offset(size.width * .80f, size.height * .24f))
                        val route = Path().apply { moveTo(-size.width * .1f, size.height * .78f); cubicTo(size.width * .26f, size.height * .54f, size.width * .60f, size.height * .82f, size.width * 1.08f, size.height * .38f) }
                        drawPath(route, Color.White.copy(.64f), style = Stroke(12.dp.toPx()))
                    }
                }.testTag("projects-empty"),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(Modifier.fillMaxWidth(.72f).padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No active project world yet", color = KineticColor.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Create one through an existing project flow; Veltrix will keep its context isolated here.", color = KineticColor.Muted)
                }
            }
        } else {
            ordered.forEachIndexed { index, project -> ProjectCard60(project, hero = index == 0, onOpen = { onOpenProject(project.id) }) }
        }
    }
}

@Composable
private fun ProjectsHeader60(onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("projects-header"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(onClick = onMenu, modifier = Modifier.size(48.dp).testTag("projects-menu"), radius = 24.dp, strong = true) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("≡", color = KineticColor.Ink, fontWeight = FontWeight.Black) }
        }
        Column {
            Text("Projects", color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("GOAL-CENTRIC WORLDS", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProjectCard60(project: ProjectCardModel, hero: Boolean, onOpen: () -> Unit) {
    val status = project.status.lowercase().replaceFirstChar { it.uppercase() }
    val accent = if (hero) KineticColor.Mint else KineticColor.Sky
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (hero) 220.dp else 164.dp)
            .clip(RoundedCornerShape(if (hero) 34.dp else 28.dp))
            .drawWithCache {
                val base = Brush.linearGradient(
                    if (hero) listOf(Color(0xFFDDF6EC), Color(0xFFF8FBFF), Color(0xFFE8EDFF), Color(0xFFFFF3E8))
                    else listOf(Color(0xFFF2F6FF), Color(0xFFF9FBFF), Color(0xFFEAF8F3)),
                    Offset.Zero,
                    Offset(size.width, size.height),
                )
                val beacon = Brush.radialGradient(listOf(accent.copy(.30f), Color.Transparent), Offset(size.width * .82f, size.height * .25f), size.minDimension * .58f)
                onDrawBehind {
                    drawRect(base)
                    drawRect(beacon)
                    val route = Path().apply {
                        moveTo(-size.width * .06f, size.height * .76f)
                        cubicTo(size.width * .22f, size.height * .56f, size.width * .48f, size.height * .82f, size.width * .72f, size.height * .60f)
                        cubicTo(size.width * .84f, size.height * .49f, size.width * .94f, size.height * .54f, size.width * 1.06f, size.height * .36f)
                    }
                    drawPath(route, Color.White.copy(.70f), style = Stroke(if (hero) 16.dp.toPx() else 11.dp.toPx()))
                    drawPath(route, accent.copy(.19f), style = Stroke(if (hero) 6.dp.toPx() else 4.dp.toPx()))
                    drawCircle(Color.White.copy(.58f), size.minDimension * .17f, Offset(size.width * .82f, size.height * .25f))
                    drawCircle(accent.copy(.72f), 3.5.dp.toPx(), Offset(size.width * .82f, size.height * .25f))
                }
            }
            .clickable(onClick = onOpen)
            .testTag("project-card-${project.id}")
            .semantics { role = Role.Button; contentDescription = "Open project ${project.title}. Status $status. Priority ${project.priority}." },
    ) {
        Column(Modifier.fillMaxSize().padding(if (hero) 20.dp else 17.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (hero) "ACTIVE WORLD" else "PROJECT WORLD", color = if (hero) Color(0xFF237E68) else Color(0xFF3159B8), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.weight(1f))
                KineticGlass(radius = 16.dp) { Text(status, Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall) }
            }
            Column(Modifier.fillMaxWidth(.82f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(project.title, color = KineticColor.Ink, style = if (hero) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                project.purpose?.takeIf { it.isNotBlank() }?.let { Text(it, color = KineticColor.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                Text("Priority ${project.priority} · Enter world →", color = KineticColor.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProjectWorkspaceLoading60(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().testTag("project-workspace-loading"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator()
            Text("Opening verified project context…", color = KineticColor.Muted)
            PressableGlass(onClick = onBack, radius = 20.dp) { Text("Back to projects", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = KineticColor.Ink) }
        }
    }
}

@Composable
private fun ProjectWorkspaceUnavailable60(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).testTag("project-workspace-unavailable"), contentAlignment = Alignment.Center) {
        KineticGlass(radius = 28.dp, strong = true) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current project context is unavailable", color = KineticColor.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Veltrix will not present stale workspace data as current account truth.", color = KineticColor.Muted)
                PressableGlass(onClick = onBack, radius = 20.dp) { Text("Back to projects", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = KineticColor.Ink) }
            }
        }
    }
}

@Composable
private fun ProjectWorkspace60(workspace: ProjectWorkspaceUiModel, onBack: () -> Unit) {
    val project = workspace.project
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 108.dp).testTag("project-workspace"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            PressableGlass(onClick = onBack, modifier = Modifier.height(44.dp).testTag("project-workspace-back"), radius = 22.dp) {
                Box(Modifier.padding(horizontal = 13.dp).fillMaxSize(), contentAlignment = Alignment.Center) { Text("← Projects", color = KineticColor.Ink, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.weight(1f))
            Text(project.status.lowercase().replaceFirstChar { it.uppercase() }, color = KineticColor.Muted, style = MaterialTheme.typography.labelMedium)
        }

        Box(
            Modifier.fillMaxWidth().height(216.dp).clip(RoundedCornerShape(34.dp)).drawWithCache {
                val base = Brush.linearGradient(listOf(Color(0xFFDDF6EC), Color(0xFFF8FAFF), Color(0xFFE5ECFF)), Offset.Zero, Offset(size.width, size.height))
                onDrawBehind {
                    drawRect(base)
                    drawCircle(KineticColor.Mint.copy(.26f), size.minDimension * .30f, Offset(size.width * .82f, size.height * .22f))
                    val route = Path().apply { moveTo(-size.width * .08f, size.height * .80f); cubicTo(size.width * .22f, size.height * .55f, size.width * .50f, size.height * .84f, size.width * 1.06f, size.height * .38f) }
                    drawPath(route, Color.White.copy(.66f), style = Stroke(15.dp.toPx()))
                    drawPath(route, KineticColor.Mint.copy(.18f), style = Stroke(5.dp.toPx()))
                }
            },
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("PROJECT WORLD", color = Color(0xFF237E68), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Column(Modifier.fillMaxWidth(.80f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(project.title, Modifier.semantics { heading() }.testTag("project-workspace-title"), color = KineticColor.Ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    project.purpose?.takeIf { it.isNotBlank() }?.let { Text(it, color = KineticColor.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        ProjectBrain60(workspace)
        Text("CURRENT GOALS", color = KineticColor.Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (workspace.goals.isEmpty()) Text("No project goal has been recorded yet.", color = KineticColor.Muted)
        else workspace.goals.sortedByDescending { it.priority }.take(5).forEach { goal ->
            KineticGlass(Modifier.fillMaxWidth(), radius = 20.dp) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape), contentAlignment = Alignment.Center) { Canvas(Modifier.fillMaxSize()) { drawCircle(if (goal.status.equals("COMPLETED", true)) KineticColor.Mint else KineticColor.Sky) } }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(goal.title, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(goal.status.lowercase().replaceFirstChar { it.uppercase() }, color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Text("WORKSPACE SIGNALS", color = KineticColor.Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("project-workspace-stats"), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ProjectFact60("Sources", workspace.sourceCount); ProjectFact60("Notes", workspace.noteCount); ProjectFact60("Chats", workspace.recentChats.size); ProjectFact60("Tests", workspace.assessmentCount); ProjectFact60("Cards", workspace.flashcardCount); ProjectFact60("Mistakes", workspace.mistakeCount); ProjectFact60("Practice", workspace.practiceCount); ProjectFact60("Memory", workspace.projectMemorySignals); ProjectFact60("Events", workspace.meaningfulEvents)
        }
        if (workspace.recommendationActions.isNotEmpty()) {
            Text("NEXT ACTIONS", color = KineticColor.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            workspace.recommendationActions.take(4).forEach { action -> KineticGlass(Modifier.fillMaxWidth(), radius = 19.dp) { Text(action, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = KineticColor.Ink, fontWeight = FontWeight.Medium) } }
        }
        if (workspace.recentChats.isNotEmpty()) {
            Text("RECENT PROJECT CHATS", color = KineticColor.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            workspace.recentChats.take(4).forEach { chat -> Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(chat.title, Modifier.weight(1f), color = KineticColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(chat.learningMode.lowercase().replaceFirstChar { it.uppercase() }, color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall) } }
        }
    }
}

@Composable
private fun ProjectBrain60(workspace: ProjectWorkspaceUiModel) {
    KineticGlass(Modifier.fillMaxWidth().testTag("project-brain"), radius = 26.dp, strong = true) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("PROJECT BRAIN", color = KineticColor.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            val topic = workspace.contextTopic?.takeIf { it.isNotBlank() }
            val mode = workspace.contextLearningMode?.takeIf { it.isNotBlank() }
            Text(topic ?: "No active project topic yet", color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            mode?.let { Text("Learning mode · ${it.lowercase().replaceFirstChar { c -> c.uppercase() }}", color = KineticColor.Muted) }
            workspace.instruction?.takeIf { it.isNotBlank() }?.let { Text(it, color = KineticColor.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun ProjectFact60(label: String, value: Number) {
    KineticGlass(Modifier.width(92.dp), radius = 18.dp) { Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { Text(label.uppercase(), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall); Text(value.toString(), color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) } }
}
