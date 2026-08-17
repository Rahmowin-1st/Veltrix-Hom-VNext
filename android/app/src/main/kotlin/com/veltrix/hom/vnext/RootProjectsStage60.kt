package com.veltrix.hom.vnext

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Root Reset Stage 60. Projects are operating environments, never folders.
 * Backend workspace snapshots remain authoritative; this layer only composes their current state.
 */
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
private fun ProjectOverview60(
    projects: List<ProjectCardModel>,
    onMenu: () -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val ordered = projects.sortedWith(compareByDescending<ProjectCardModel> { it.priority }.thenByDescending { it.lastActiveAt })
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 108.dp)
            .testTag("projects-stage60"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProjectsHeader60(onMenu)
        Text(
            "Operating worlds",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = KineticColor.Ink,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "A project keeps one goal context together: memory, sources, chats and learning work.",
            color = KineticColor.Muted,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (ordered.isEmpty()) {
            KineticGlass(Modifier.fillMaxWidth().testTag("projects-empty"), radius = 28.dp) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("No active project world yet", color = KineticColor.Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Create one through an existing project flow; Veltrix will keep its context isolated here.", color = KineticColor.Muted)
                }
            }
        } else {
            ordered.forEachIndexed { index, project ->
                ProjectCard60(project, hero = index == 0, onOpen = { onOpenProject(project.id) })
            }
        }
    }
}

@Composable
private fun ProjectsHeader60(onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onMenu, modifier = Modifier.size(48.dp).testTag("projects-menu")) {
            Text("≡", color = KineticColor.Ink, fontWeight = FontWeight.Black)
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text("Projects", color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("GOAL-CENTRIC WORLDS", color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProjectCard60(project: ProjectCardModel, hero: Boolean, onOpen: () -> Unit) {
    val status = project.status.lowercase().replaceFirstChar { it.uppercase() }
    KineticGlass(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (hero) 158.dp else 126.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(if (hero) 30.dp else 25.dp))
            .clickable(onClick = onOpen)
            .testTag("project-card-${project.id}")
            .semantics {
                role = Role.Button
                contentDescription = "Open project ${project.title}. Status $status. Priority ${project.priority}."
            },
        radius = if (hero) 30.dp else 25.dp,
        strong = hero,
    ) {
        Column(Modifier.fillMaxWidth().padding(if (hero) 20.dp else 17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hero) "ACTIVE CONTEXT" else "PROJECT WORLD",
                    color = if (hero) KineticColor.Mint else KineticColor.Muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(status, color = KineticColor.Muted, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                project.title,
                color = KineticColor.Ink,
                style = if (hero) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            project.purpose?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = KineticColor.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("Priority ${project.priority}  ·  Open world →", color = KineticColor.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProjectWorkspaceLoading60(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().testTag("project-workspace-loading"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator()
            Text("Opening verified project context…", color = KineticColor.Muted)
            TextButton(onClick = onBack) { Text("Back to projects") }
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
                TextButton(onClick = onBack) { Text("Back to projects") }
            }
        }
    }
}

@Composable
private fun ProjectWorkspace60(workspace: ProjectWorkspaceUiModel, onBack: () -> Unit) {
    val project = workspace.project
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 108.dp)
            .testTag("project-workspace"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("project-workspace-back")) { Text("← Projects") }
            Spacer(Modifier.weight(1f))
            Text(project.status.lowercase().replaceFirstChar { it.uppercase() }, color = KineticColor.Muted, style = MaterialTheme.typography.labelMedium)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("PROJECT WORLD", color = KineticColor.Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                project.title,
                modifier = Modifier.semantics { heading() }.testTag("project-workspace-title"),
                color = KineticColor.Ink,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            project.purpose?.takeIf { it.isNotBlank() }?.let { Text(it, color = KineticColor.Muted, style = MaterialTheme.typography.bodyLarge) }
        }

        ProjectBrain60(workspace)

        Text("CURRENT GOALS", color = KineticColor.Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (workspace.goals.isEmpty()) {
            Text("No project goal has been recorded yet.", color = KineticColor.Muted)
        } else {
            workspace.goals.sortedByDescending { it.priority }.take(5).forEach { goal ->
                KineticGlass(Modifier.fillMaxWidth(), radius = 20.dp) {
                    Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawCircle(if (goal.status.equals("COMPLETED", true)) KineticColor.Mint else KineticColor.Sky) }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(goal.title, color = KineticColor.Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(goal.status.lowercase().replaceFirstChar { it.uppercase() }, color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Text("WORKSPACE SIGNALS", color = KineticColor.Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("project-workspace-stats"), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ProjectFact60("Sources", workspace.sourceCount)
            ProjectFact60("Notes", workspace.noteCount)
            ProjectFact60("Chats", workspace.recentChats.size)
            ProjectFact60("Tests", workspace.assessmentCount)
            ProjectFact60("Cards", workspace.flashcardCount)
            ProjectFact60("Mistakes", workspace.mistakeCount)
            ProjectFact60("Practice", workspace.practiceCount)
            ProjectFact60("Memory", workspace.projectMemorySignals)
            ProjectFact60("Events", workspace.meaningfulEvents)
        }

        if (workspace.recommendationActions.isNotEmpty()) {
            Text("NEXT ACTIONS", color = KineticColor.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            workspace.recommendationActions.take(4).forEach { action ->
                KineticGlass(Modifier.fillMaxWidth(), radius = 19.dp) {
                    Text(action, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = KineticColor.Ink, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (workspace.recentChats.isNotEmpty()) {
            Text("RECENT PROJECT CHATS", color = KineticColor.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            workspace.recentChats.take(4).forEach { chat ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(chat.title, Modifier.weight(1f), color = KineticColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chat.learningMode.lowercase().replaceFirstChar { it.uppercase() }, color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
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
            Text(
                topic ?: "No active project topic yet",
                color = KineticColor.Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            mode?.let { Text("Learning mode · ${it.lowercase().replaceFirstChar { c -> c.uppercase() }}", color = KineticColor.Muted) }
            workspace.instruction?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = KineticColor.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProjectFact60(label: String, value: Number) {
    KineticGlass(Modifier.width(92.dp), radius = 18.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label.uppercase(), color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
            Text(value.toString(), color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
