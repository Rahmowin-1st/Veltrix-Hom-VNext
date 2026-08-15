package com.veltrix.hom.vnext

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

private val p2Number = NumberFormat.getIntegerInstance(Locale.US)
private fun Long.prettyP2(): String = p2Number.format(this)
private fun String.humanP2(): String = lowercase(Locale.US).replace('_',' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar().toString() } }

@Composable
fun LivingVeltrixAvatar(
    avatarId: String,
    assetKey: String = "",
    tier: String = "CORE",
    modifier: Modifier = Modifier,
    equipped: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val policy = rememberVeltrixEffectPolicy()
    val seed = remember(avatarId, assetKey) { (avatarId + assetKey).fold(19) { a, c -> a * 31 + c.code } }
    val orbit = (seed and 0x7f) / 127f * 6.28318f
    val energy by animateFloatAsState(
        if (equipped) 1f else .82f,
        if (policy.reducedMotion) snap() else spring(dampingRatio = .78f, stiffness = 260f),
        label = "avatar-energy",
    )
    val base = modifier.semantics {
        contentDescription = "Veltrix avatar ${avatarId.ifBlank { "fallback" }}, ${tier.humanP2()}${if (equipped) ", equipped" else ""}"
        if (onClick != null) role = Role.Button
    }
    val interactive = if (onClick == null) base else base.clickable(onClick = onClick)
    Box(
        interactive.clip(CircleShape).drawWithCache {
            val center = Offset(size.width / 2f, size.height / 2f)
            val ring = Brush.sweepGradient(
                listOf(VeltrixColors.Sky, Color(0xFF7868E9), VeltrixColors.Mint, VeltrixColors.Sky),
                center,
            )
            val core = Brush.radialGradient(
                listOf(Color.White, Color(0xFFE1EAFF), Color(0xFFD9F7EF)),
                center,
                size.minDimension * .62f,
            )
            onDrawBehind {
                drawCircle(ring, size.minDimension * .49f, center, alpha = if (policy.highContrast) .6f else .82f, style = Stroke(2.dp.toPx()))
                drawCircle(core, size.minDimension * .43f, center)
                val orb = Offset(
                    center.x + kotlin.math.cos(orbit) * size.minDimension * .39f,
                    center.y + kotlin.math.sin(orbit) * size.minDimension * .39f,
                )
                drawCircle(if (equipped) VeltrixColors.Mint else VeltrixColors.Sky, 3.dp.toPx() * energy, orb)
                val visor = Path().apply {
                    moveTo(size.width * .27f, size.height * .45f)
                    quadraticBezierTo(size.width * .5f, size.height * .29f, size.width * .73f, size.height * .45f)
                    quadraticBezierTo(size.width * .5f, size.height * .60f, size.width * .27f, size.height * .45f)
                }
                drawPath(visor, Color(0xFF233955).copy(alpha = .88f))
                drawCircle(Color(0xFF82E7CB), 2.2.dp.toPx(), Offset(size.width * .43f, size.height * .44f))
                drawCircle(Color(0xFF8BB8FF), 2.2.dp.toPx(), Offset(size.width * .57f, size.height * .44f))
                drawLine(Color.White.copy(alpha = .76f), Offset(size.width * .24f, size.height * .25f), Offset(size.width * .58f, size.height * .13f), 1.5.dp.toPx(), StrokeCap.Round)
            }
        },
    )
}

@Composable
private fun WorldHeading(title: String, eyebrow: String, detail: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(eyebrow.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = VeltrixColors.SkyDeep, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.headlineSmall, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            detail?.let { Text(it, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.bodyMedium) }
        }
        action?.invoke()
    }
}

@Composable
private fun WorldPanel(modifier: Modifier = Modifier, accent: Color = VeltrixColors.Sky, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(28.dp)).drawWithCache {
            val body = Brush.linearGradient(listOf(Color(0xF2FAFCFF), Color(0xE8F1F7FF), Color(0xECF2FBF8)))
            val glow = Brush.radialGradient(listOf(accent.copy(alpha = .14f), Color.Transparent), center = Offset(size.width * .88f, 0f), radius = size.minDimension)
            onDrawBehind {
                val corner = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx())
                drawRoundRect(body, cornerRadius = corner)
                drawRoundRect(glow, cornerRadius = corner)
                drawRoundRect(Color.White.copy(alpha = .66f), cornerRadius = corner, style = Stroke(.8.dp.toPx()))
            }
        },
    ) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content) }
}

@Composable
private fun StatusBadge(state: String) {
    val s = state.uppercase(Locale.US)
    val accent = when {
        s.contains("FAIL") || s.contains("ERROR") -> VeltrixColors.Error
        s.contains("LOCK") || s.contains("OFFLINE") -> VeltrixColors.Amber
        s.contains("READY") || s.contains("ACTIVE") || s.contains("OWNED") || s.contains("COMPLETE") || s.contains("EQUIPPED") -> VeltrixColors.Mint
        else -> VeltrixColors.Sky
    }
    Row(Modifier.clip(CircleShape).background(accent.copy(alpha = .12f)).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Text(state.humanP2(), color = VeltrixColors.Ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FreshnessLine(freshness: DataFreshness, onRetry: () -> Unit) {
    if (freshness == DataFreshness.FRESH) return
    GlassSurface(Modifier.fillMaxWidth(), radius = 18.dp, strong = true) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (freshness == DataFreshness.OFFLINE) "Offline · showing saved state" else "Saved state · refresh unavailable", Modifier.weight(1f), color = VeltrixColors.InkMuted)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun <T> StateFrame(state: RepositoryState<T>, label: String, onRetry: () -> Unit, isEmpty: (T) -> Boolean = { false }, emptyText: String = "Nothing here yet.", content: @Composable (T) -> Unit) {
    val value = state.value
    when {
        value == null && state.loading -> CenterState("Loading $label…", true)
        value == null -> CenterState(errorCopyP2(state.errorCode), false, onRetry)
        isEmpty(value) -> Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { FreshnessLine(state.freshness, onRetry); CenterState(emptyText, false) }
        else -> content(value)
    }
}

@Composable
private fun CenterState(text: String, loading: Boolean, retry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
            Text(text, color = VeltrixColors.InkMuted)
            retry?.let { PressableGlass(it, Modifier.heightIn(min = 48.dp), radius = 18.dp) { Text("Retry", Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold) } }
        }
    }
}

private fun errorCopyP2(code: String?): String = when (code) {
    "AUTH_EXPIRED", "HTTP_401" -> "Your session expired. Sign in again to continue."
    "FORBIDDEN", "HTTP_403" -> "This account cannot access that content."
    "REVISION_CONFLICT", "CONFLICT", "HTTP_409" -> "This changed elsewhere. Refresh before trying again."
    "RATE_LIMITED", "HTTP_429" -> "Veltrix is receiving too many requests. Retry shortly."
    "SERVICE_UNAVAILABLE", "HTTP_503" -> "This service is temporarily unavailable."
    "OFFLINE", "NO_SESSION" -> "No live connection is available. Saved data stays visible where possible."
    null -> "This content is unavailable right now."
    else -> "Veltrix could not load this state ($code)."
}

@Composable
fun ProjectsWorldScreen(
    remote: RepositoryState<List<ProjectCardModel>>,
    pending: List<LocalProjectEntity>,
    workspace: RepositoryState<ProjectWorkspaceUiModel>,
    selectedProjectId: String?,
    onRetry: () -> Unit,
    onCreate: (String, String?) -> Unit,
    onOpen: (String) -> Unit,
    onCloseWorkspace: () -> Unit,
    onCapability: (CapabilityRoute) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("projects-screen")) {
        val expanded = maxWidth >= 800.dp
        val list: @Composable (Modifier) -> Unit = { m ->
            Column(m.padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WorldHeading("Projects", "Active learning spaces", "Keep context, sources, chat and practice connected.") {
                    PressableGlass({ adding = !adding }, Modifier.size(48.dp), 18.dp) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (adding) "×" else "+", color = VeltrixColors.Ink, fontWeight = FontWeight.Bold) } }
                }
                if (adding) WorldPanel(accent = VeltrixColors.Mint) {
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Project name") }, singleLine = true)
                    OutlinedTextField(purpose, { purpose = it }, Modifier.fillMaxWidth(), label = { Text("Purpose (optional)") })
                    Button(enabled = title.isNotBlank(), onClick = { onCreate(title, purpose.takeIf { it.isNotBlank() }); title = ""; purpose = ""; adding = false }) { Text("Create project") }
                }
                FreshnessLine(remote.freshness, onRetry)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    val confirmed = remote.value.orEmpty()
                    items(confirmed, key = { it.id }) { p ->
                        PressableGlass({ onOpen(p.id) }, Modifier.fillMaxWidth(), 24.dp, strong = selectedProjectId == p.id) {
                            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(projectAccentP2(p.id).copy(alpha = .13f)), contentAlignment = Alignment.Center) { Text("◆", color = projectAccentP2(p.id)) }
                                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(p.title, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(p.purpose ?: "Learning workspace", color = VeltrixColors.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }; StatusBadge(p.status)
                            }
                        }
                    }
                    items(pending.filter { it.syncState != "SYNCED" }, key = { "pending-${it.id}" }) { p ->
                        WorldPanel(accent = VeltrixColors.Amber) { Text(p.title, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text("Saved locally · waiting to sync", color = VeltrixColors.InkMuted) }
                    }
                    if (confirmed.isEmpty() && pending.isEmpty() && !remote.loading) item { CenterState("Create a project when you want a persistent learning workspace.", false) }
                }
            }
        }
        val detail: @Composable (Modifier) -> Unit = { m ->
            val w = workspace.value
            if (selectedProjectId == null) Box(m, contentAlignment = Alignment.Center) { Text("Select a project to open its workspace.", color = VeltrixColors.InkMuted) }
            else if (w == null) CenterState(if (workspace.loading) "Opening Project Space…" else errorCopyP2(workspace.errorCode), workspace.loading, onRetry)
            else LazyColumn(m.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                item { WorldHeading(w.project.title, "Project Space", w.project.purpose ?: "Context-preserving learning workspace") { TextButton(onClick = onCloseWorkspace) { Text("Close") } } }
                item { WorldPanel(accent = projectAccentP2(w.project.id)) { Row(Modifier.fillMaxWidth()) { MetricMini("Sources", w.sourceCount.toString(), Modifier.weight(1f)); MetricMini("Chats", w.recentChats.size.toString(), Modifier.weight(1f)); MetricMini("Practice", w.practiceCount.toString(), Modifier.weight(1f)) }; w.contextTopic?.let { Text("Current topic · $it", color = VeltrixColors.Ink) }; w.contextLearningMode?.let { Text("Learning mode · ${it.humanP2()}", color = VeltrixColors.InkMuted) }; w.instruction?.let { Text("Project instruction", fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); Text(it, color = VeltrixColors.InkMuted) } } }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(CapabilityRoute.CHAT, CapabilityRoute.LIBRARY, CapabilityRoute.PRACTICE).forEach { r -> PressableGlass({ onCapability(r) }, Modifier.weight(1f).heightIn(min = 54.dp), 18.dp) { Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) { Text(r.name.humanP2(), color = VeltrixColors.Ink, style = MaterialTheme.typography.labelMedium) } } } } }
                if (w.goals.isNotEmpty()) item { WorldPanel(accent = VeltrixColors.Mint) { Text("Goals", fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); w.goals.take(5).forEach { g -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(g.title, Modifier.weight(1f), color = VeltrixColors.Ink); StatusBadge(g.status) } } } }
                if (w.recommendationActions.isNotEmpty()) item { WorldPanel(accent = VeltrixColors.Sky) { Text("Next useful actions", fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); w.recommendationActions.take(4).forEach { Text("• ${it.humanP2()}", color = VeltrixColors.InkMuted) } } }
            }
        }
        if (expanded) Row(Modifier.fillMaxSize()) { list(Modifier.weight(.44f).fillMaxHeight()); VerticalDivider(); detail(Modifier.weight(.56f).fillMaxHeight()) }
        else if (selectedProjectId == null) list(Modifier.fillMaxSize()) else detail(Modifier.fillMaxSize())
    }
}

@Composable
fun ChatWorldScreen(
    chats: RepositoryState<List<ConversationUiModel>>,
    messages: RepositoryState<List<ChatMessageUiModel>>,
    sources: RepositoryState<List<SourceUiModel>>,
    conversationId: String?,
    projectId: String?,
    streaming: Boolean,
    streamingText: String,
    streamError: String?,
    selectedSources: Set<String>,
    citations: Map<String, List<CitationUiModel>>,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onToggleSource: (String) -> Unit,
    onSend: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onLoadCitations: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("chat-screen")) {
        val expanded = maxWidth >= 820.dp
        val history: @Composable (Modifier) -> Unit = { m ->
            Column(m.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Chats", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); TextButton(onClick = onNew) { Text("New") } }
                FreshnessLine(chats.freshness, onRefresh)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(chats.value.orEmpty(), key = { it.id }) { c -> PressableGlass({ onOpen(c.id) }, Modifier.fillMaxWidth(), 18.dp, strong = c.id == conversationId) { Column(Modifier.padding(12.dp)) { Text(c.title.ifBlank { "Conversation" }, color = VeltrixColors.Ink, fontWeight = FontWeight.Medium, maxLines = 1); Text(if (c.projectId == null) "General · ${c.learningMode.humanP2()}" else "Project · ${c.learningMode.humanP2()}", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall) } } } }
            }
        }
        val thread: @Composable (Modifier) -> Unit = { m ->
            Column(m.imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorldHeading(if (projectId == null) "Ask Veltrix" else "Project chat", "Contextual intelligence", if (projectId == null) "Sources and memory stay explicit." else "This conversation remains attached to its Project Space.")
                if (conversationId == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CenterState("Start a conversation. Veltrix will keep the composer stable and context explicit.", false) }
                else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("message-list"), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(messages.value.orEmpty(), key = { it.id }) { msg ->
                        val user = msg.role.equals("USER", true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                            WorldPanel(Modifier.fillMaxWidth(if (user) .86f else .94f), accent = if (user) VeltrixColors.Sky else VeltrixColors.Mint) {
                                Text(msg.content, color = VeltrixColors.Ink, fontFamily = if (msg.content.contains("```")) FontFamily.Monospace else FontFamily.Default)
                                if (!user) Row { TextButton(onClick = { onLoadCitations(msg.id) }) { Text("Sources") }; TextButton(onClick = { onRegenerate(msg.id) }) { Text("Regenerate") }; if (msg.state.contains("FAIL", true)) TextButton(onClick = { onRetry(msg.id) }) { Text("Retry") } }
                                citations[msg.id]?.takeIf { it.isNotEmpty() }?.let { list -> Column { Text("Citations", fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); list.take(4).forEach { c -> Text("${c.index}. ${c.excerpt}", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.bodySmall) } } }
                            }
                        }
                    }
                    if (streaming) item { WorldPanel(accent = VeltrixColors.Mint) { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(if (streamingText.isBlank()) "Veltrix is responding…" else streamingText, color = VeltrixColors.Ink) } } }
                    streamError?.let { code -> item { WorldPanel(accent = VeltrixColors.Error) { Text(errorCopyP2(code), color = VeltrixColors.Error) } } }
                }
                if (conversationId != null) {
                    val readySources = sources.value.orEmpty().filter { it.state == "READY" }
                    if (readySources.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(readySources, key = { it.id }) { s -> FilterChip(s.id in selectedSources, onClick = { onToggleSource(s.id) }, label = { Text(s.title, maxLines = 1) }) } }
                    GlassSurface(Modifier.fillMaxWidth(), 24.dp, strong = true) { Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) { OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Ask, explain, solve, learn…") }, maxLines = 6, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank() && !streaming) { onSend(text); text = "" } })); Spacer(Modifier.width(7.dp)); Button(enabled = text.isNotBlank() && !streaming, onClick = { onSend(text); text = "" }) { Text("Send") } } }
                }
            }
        }
        if (expanded) Row(Modifier.fillMaxSize().padding(horizontal = 12.dp)) { history(Modifier.width(290.dp).fillMaxHeight()); VerticalDivider(); thread(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 14.dp)) }
        else if (conversationId == null) history(Modifier.fillMaxSize()) else thread(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
fun LibraryWorldScreen(state: RepositoryState<List<SourceUiModel>>, onRetry: () -> Unit, onCreateText: (String, String) -> Unit, onRetrySource: (String) -> Unit) {
    var add by rememberSaveable { mutableStateOf(false) }; var title by rememberSaveable { mutableStateOf("") }; var body by rememberSaveable { mutableStateOf("") }
    StateFrame(state, "sources", onRetry, isEmpty = { it.isEmpty() && !add }, emptyText = "Add a trusted source when you want Veltrix to ground project or chat work.") { sources ->
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("library-screen"), contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            item { WorldHeading("Library", "Trusted knowledge", "Processing state and provenance stay visible.") { TextButton(onClick = { add = !add }) { Text(if (add) "Cancel" else "Add text") } }; FreshnessLine(state.freshness, onRetry) }
            if (add) item { WorldPanel(accent = VeltrixColors.Mint) { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Source title") }); OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text("Source text") }, minLines = 5); Button(enabled = title.isNotBlank() && body.isNotBlank(), onClick = { onCreateText(title, body); title = ""; body = ""; add = false }) { Text("Add source") } } }
            items(sources, key = { it.id }) { s -> WorldPanel(accent = when (s.state) { "READY" -> VeltrixColors.Mint; "FAILED" -> VeltrixColors.Error; else -> VeltrixColors.Sky }) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.title, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text("${s.type.humanP2()} · ${s.mimeType}", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall) }; StatusBadge(s.state) }; if (s.state == "PROCESSING") LinearProgressIndicator(progress = { (s.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()); if (s.state == "FAILED") TextButton(onClick = { onRetrySource(s.id) }) { Text("Retry processing") } } }
        }
    }
}

@Composable
fun AssessmentWorldScreen(
    quizMode: Boolean,
    searchState: RepositoryState<List<SearchUiModel>>,
    detail: RepositoryState<AssessmentDetailUiModel>,
    attempt: AttemptUiModel?,
    result: AssessmentResultUiModel?,
    onSearch: (String) -> Unit,
    onOpen: (String) -> Unit,
    onStart: () -> Unit,
    onAnswer: (String, List<String>) -> Unit,
    onSubmit: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }; var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag(if (quizMode) "quiz-screen" else "testing-screen"), contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { WorldHeading(if (quizMode) "Quizzes" else "Testing", if (quizMode) "Focused momentum" else "Assessment", if (quizMode) "Energetic, accessible practice without gambling mechanics." else "Scoring remains deterministic backend truth.") }
        if (detail.value == null) {
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Find an assessment") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch(query) })); if (query.isNotBlank()) TextButton(onClick = { onSearch(query) }) { Text("Search") } }
            val candidates = searchState.value.orEmpty().filter { it.type.contains("ASSESS", true) || it.deepLink.contains("assessment", true) }
            items(candidates, key = { it.id }) { r -> PressableGlass({ onOpen(r.id) }, Modifier.fillMaxWidth(), 22.dp) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(r.title, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); Text(r.snippet, color = VeltrixColors.InkMuted, maxLines = 2) }; Text("→", color = VeltrixColors.SkyDeep) } } }
            if (candidates.isEmpty() && query.isNotBlank() && !searchState.loading) item { CenterState("No assessment matches that search.", false) }
        } else {
            val a = detail.value!!
            item { WorldPanel(accent = if (quizMode) Color(0xFF7868E9) else VeltrixColors.Sky) { Row { Column(Modifier.weight(1f)) { Text(a.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); Text("${a.questionCount} questions · ${a.kind.humanP2()}", color = VeltrixColors.InkMuted) }; StatusBadge(a.state) }; if (attempt == null && result == null) Button(onClick = onStart) { Text(if (quizMode) "Start quiz" else "Start test") } } }
            if (attempt != null && result == null) itemsIndexed(a.questions, key = { _, q -> q.id }) { index, q -> WorldPanel(accent = if (quizMode) Color(0xFF7868E9) else VeltrixColors.Sky) { Text("${index + 1} / ${a.questions.size}", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall); Text(q.prompt, color = VeltrixColors.Ink, fontWeight = FontWeight.Medium); if (q.options.isNotEmpty()) q.options.forEach { option -> Row(Modifier.fillMaxWidth().selectable(answers[q.id] == option, role = Role.RadioButton, onClick = { answers = answers + (q.id to option); onAnswer(q.id, listOf(option)) }).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(answers[q.id] == option, onClick = null); Spacer(Modifier.width(8.dp)); Text(option, color = VeltrixColors.Ink) } } else OutlinedTextField(answers[q.id].orEmpty(), { v -> answers = answers + (q.id to v) }, Modifier.fillMaxWidth(), label = { Text("Answer") }, keyboardActions = KeyboardActions(onDone = { answers[q.id]?.takeIf { it.isNotBlank() }?.let { onAnswer(q.id, listOf(it)) } })) } }
            if (attempt != null && result == null) item { Button(onClick = onSubmit, Modifier.fillMaxWidth()) { Text("Submit for deterministic scoring") } }
            result?.let { r -> item { WorldPanel(accent = VeltrixColors.Mint) { Text(if (quizMode) "Quiz complete" else "Assessment complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); Text("${(r.accuracy * 100).toInt()}% accuracy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = VeltrixColors.Ink); Text("${r.incorrectQuestionIds.size} item(s) need review. Mistake tracking is backend-authoritative.", color = VeltrixColors.InkMuted) } } }
        }
    }
}

@Composable
fun PracticeWorldScreen(state: RepositoryState<PracticeSessionUiModel>, hint: String?, check: PracticeCheckUiModel?, complete: PracticeCompleteUiModel?, onCreate: (String) -> Unit, onAttempt: (String, String) -> Unit, onHint: (String) -> Unit, onCheck: (String) -> Unit, onSkip: (String) -> Unit, onComplete: () -> Unit) {
    var topic by rememberSaveable { mutableStateOf("") }; var answer by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("practice-screen"), contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { WorldHeading("Practice", "Low pressure", "Iterate, receive useful feedback, continue.") }
        val p = state.value
        if (p == null) item { WorldPanel(accent = VeltrixColors.Mint) { OutlinedTextField(topic, { topic = it }, Modifier.fillMaxWidth(), label = { Text("Focus topic") }); Button(onClick = { onCreate(topic) }, enabled = topic.isNotBlank()) { Text("Start practice") }; state.errorCode?.let { Text(errorCopyP2(it), color = VeltrixColors.Error) } } }
        else if (complete != null) item { WorldPanel(accent = VeltrixColors.Mint) { Text("Session complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text("${(complete.accuracy * 100).toInt()}% accuracy · ${complete.correct}/${complete.answered} correct", color = VeltrixColors.Ink); Text("Result from backend practice engine.", color = VeltrixColors.InkMuted) } }
        else {
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { StatusBadge(p.state); Spacer(Modifier.weight(1f)); Text("${p.currentPosition}/${p.targetItemCount}", color = VeltrixColors.InkMuted) } }
            val current = p.items.firstOrNull { !it.state.contains("COMPLETE", true) && !it.state.contains("SKIP", true) } ?: p.items.lastOrNull()
            if (current == null) item { CenterState("The backend has not prepared an item for this session. Veltrix will not invent one locally.", false) }
            else item { WorldPanel(accent = VeltrixColors.Mint) { Text(current.topic ?: p.focusTopic ?: "Practice", color = VeltrixColors.SkyDeep, style = MaterialTheme.typography.labelMedium); Text(current.prompt, style = MaterialTheme.typography.titleLarge, color = VeltrixColors.Ink, fontWeight = FontWeight.Medium); OutlinedTextField(answer, { answer = it }, Modifier.fillMaxWidth(), label = { Text("Your answer") }); Row { TextButton(onClick = { onHint(current.id) }) { Text("Hint") }; TextButton(onClick = { onSkip(current.id) }) { Text("Skip") }; Spacer(Modifier.weight(1f)); Button(enabled = answer.isNotBlank(), onClick = { onAttempt(current.id, answer); answer = "" }) { Text("Answer") } }; hint?.let { Text("Hint · $it", color = VeltrixColors.InkMuted) }; check?.takeIf { it.item.id == current.id }?.let { Text(if (it.correct) "Correct · ${it.explanation}" else "Review · ${it.explanation}", color = if (it.correct) VeltrixColors.Mint else VeltrixColors.Error) }; if (current.userAnswer != null) TextButton(onClick = { onCheck(current.id) }) { Text("Check") } }
            }
            item { Button(onClick = onComplete, Modifier.fillMaxWidth()) { Text("Finish session") } }
        }
    }
}

@Composable
fun FlashcardsWorldScreen(state: RepositoryState<List<FlashcardUiModel>>, onRetry: () -> Unit, onRate: (String, String) -> Unit) {
    val policy = rememberVeltrixEffectPolicy(); var reveal by rememberSaveable { mutableStateOf(false) }; var index by rememberSaveable { mutableIntStateOf(0) }
    StateFrame(state, "due flashcards", onRetry, isEmpty = { it.isEmpty() }, emptyText = "Nothing is due right now.") { cards ->
        val card = cards[index.coerceIn(0, cards.lastIndex)]
        Column(Modifier.fillMaxSize().padding(18.dp).testTag("flashcards-screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WorldHeading("Flashcards", "Spaced repetition", "Scheduling remains backend-authoritative."); Text("${index + 1} of ${cards.size} due", color = VeltrixColors.InkMuted)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { PressableGlass({ reveal = !reveal }, Modifier.fillMaxWidth().heightIn(min = 260.dp), 34.dp, strong = true) { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { if (policy.reducedMotion) FlashcardFace(card, reveal) else AnimatedContent(reveal, label = "flashcard-reveal") { FlashcardFace(card, it) } } } }
            if (reveal) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("AGAIN", "HARD", "GOOD", "EASY").forEach { rating -> OutlinedButton(onClick = { onRate(card.id, rating); reveal = false; if (index < cards.lastIndex) index++ }, modifier = Modifier.weight(1f)) { Text(rating.humanP2(), maxLines = 1) } } } else Text("Tap the card to reveal the answer", Modifier.align(Alignment.CenterHorizontally), color = VeltrixColors.InkMuted)
        }
    }
}

@Composable private fun FlashcardFace(card: FlashcardUiModel, back: Boolean) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(if (back) "ANSWER" else "PROMPT", style = MaterialTheme.typography.labelSmall, color = VeltrixColors.SkyDeep, fontWeight = FontWeight.Bold); Text(if (back) card.back else card.front, style = MaterialTheme.typography.headlineSmall, color = VeltrixColors.Ink, fontWeight = FontWeight.Medium); if (back) card.explanation?.let { Text(it, color = VeltrixColors.InkMuted) } } }

@Composable
fun MistakesWorldScreen(state: RepositoryState<List<MistakeUiModel>>, onRetry: () -> Unit, onResolve: (MistakeUiModel) -> Unit, onPractice: (String) -> Unit, onFlashcard: (String) -> Unit) {
    StateFrame(state, "mistakes", onRetry, isEmpty = { it.isEmpty() }, emptyText = "No active mistake patterns. Keep learning; recurrence will be tracked constructively.") { mistakes ->
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("mistakes-screen"), contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            item { WorldHeading("Mistakes", "Pattern review", "Use mistakes as signals, not punishment."); FreshnessLine(state.freshness, onRetry) }
            items(mistakes, key = { it.id }) { m -> WorldPanel(accent = if (m.status == "RESOLVED") VeltrixColors.Mint else VeltrixColors.Amber) { Row { Column(Modifier.weight(1f)) { Text(m.topic.ifBlank { "Review" }, color = VeltrixColors.SkyDeep, style = MaterialTheme.typography.labelMedium); Text(m.prompt, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text("Seen ${m.occurrenceCount} time${if (m.occurrenceCount == 1) "" else "s"}", color = VeltrixColors.InkMuted) }; StatusBadge(m.status) }; m.userAnswer?.let { Text("Your answer · $it", color = VeltrixColors.InkMuted) }; Row { TextButton(onClick = { onPractice(m.id) }) { Text("Practice") }; TextButton(onClick = { onFlashcard(m.id) }) { Text("Make card") }; Spacer(Modifier.weight(1f)); if (m.status != "RESOLVED") TextButton(onClick = { onResolve(m) }) { Text("Resolved") } } } }
        }
    }
}

@Composable
fun StoreWorldScreen(store: RepositoryState<StoreCatalogUiModel>, inventory: RepositoryState<List<InventoryItemUiModel>>, avatars: RepositoryState<List<AvatarCatalogUiModel>>, profile: RepositoryState<GameProfileUiModel>, feedback: MutationFeedback?, onRetry: () -> Unit, onPurchase: (String) -> Unit, onEquip: (String, Long) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp).testTag("store-screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WorldHeading("Store & identity", "Owned progression", "No scarcity tricks. Prices, balance and ownership come from backend truth.")
        val balance = store.value?.coinBalance ?: profile.value?.coinBalance
        GlassSurface(Modifier.fillMaxWidth(), 24.dp, strong = true) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Text("Coin balance", Modifier.weight(1f), color = VeltrixColors.InkMuted); Text(balance?.prettyP2() ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = VeltrixColors.Ink) } }
        TabRow(tab, containerColor = Color.Transparent) { listOf("Catalog", "Inventory", "Avatar").forEachIndexed { i, label -> Tab(tab == i, onClick = { tab = i }, text = { Text(label) }) } }
        feedback?.takeIf { !it.success }?.let { Text(errorCopyP2(it.code), color = VeltrixColors.Error) }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> StateFrame(store, "store", onRetry, isEmpty = { it.items.isEmpty() }, emptyText = "No catalog items are available.") { c -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) { items(c.items, key = { it.itemId }) { item -> WorldPanel(accent = if (item.owned) VeltrixColors.Mint else projectAccentP2(item.itemId)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.itemId.humanP2(), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text(item.itemType.humanP2(), color = VeltrixColors.InkMuted) }; StatusBadge(if (item.owned) "OWNED" else if (item.available) "AVAILABLE" else "UNAVAILABLE") }; if (!item.owned) Row(verticalAlignment = Alignment.CenterVertically) { Text("${item.priceCoins.prettyP2()} coins", Modifier.weight(1f), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Button(enabled = item.available && c.coinBalance >= item.priceCoins, onClick = { onPurchase(item.itemId) }) { Text(if (c.coinBalance < item.priceCoins) "Insufficient" else "Buy") } } } } } }
                1 -> StateFrame(inventory, "inventory", onRetry, isEmpty = { it.isEmpty() }, emptyText = "Owned items appear here after backend-confirmed acquisition.") { list -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(list, key = { it.itemId }) { i -> WorldPanel(accent = VeltrixColors.Mint) { Row { Column(Modifier.weight(1f)) { Text(i.itemId.humanP2(), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text("${i.type.humanP2()} · ${i.ownershipSource.humanP2()}", color = VeltrixColors.InkMuted) }; Text("×${i.quantity}", fontWeight = FontWeight.Bold) } } } } }
                else -> StateFrame(avatars, "avatars", onRetry, isEmpty = { it.isEmpty() }, emptyText = "No avatar catalog is available.") { list -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(list, key = { it.avatarId }) { a -> WorldPanel(accent = if (a.equipped) VeltrixColors.Mint else projectAccentP2(a.avatarId)) { Row(verticalAlignment = Alignment.CenterVertically) { LivingVeltrixAvatar(a.avatarId, a.assetKey, a.tier, Modifier.size(68.dp), a.equipped); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(a.name.humanP2(), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text(a.tier.humanP2(), color = VeltrixColors.InkMuted); StatusBadge(if (a.equipped) "EQUIPPED" else if (a.owned) "OWNED" else "NOT OWNED") }; if (a.owned && !a.equipped) Button(enabled = profile.value != null, onClick = { onEquip(a.avatarId, profile.value?.avatarRevision ?: 0) }) { Text("Equip") } } } } } }
            }
        }
    }
}

@Composable
fun SearchWorldScreen(state: RepositoryState<List<SearchUiModel>>, onSearch: (String) -> Unit, onOpen: (SearchUiModel) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("search-screen"), contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { WorldHeading("Search", "Across Veltrix", "Projects, chats, sources and learning items remain type-distinct."); OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search Veltrix") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch(query) })) }
        val results = state.value.orEmpty(); if (results.isEmpty() && query.isNotBlank() && !state.loading) item { CenterState("No matching results.", false) } else items(results, key = { it.type + it.id }) { r -> PressableGlass({ onOpen(r) }, Modifier.fillMaxWidth(), 22.dp) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) { StatusBadge(r.type); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(r.title, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text(r.snippet, color = VeltrixColors.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) } } } }
        state.errorCode?.let { item { Text(errorCopyP2(it), color = VeltrixColors.Error) } }
    }
}

@Composable
fun HistoryWorldScreen(state: RepositoryState<List<ActivityUiModel>>, onRetry: () -> Unit, onOpen: (ActivityUiModel) -> Unit) {
    StateFrame(state, "history", onRetry, isEmpty = { it.isEmpty() }, emptyText = "Meaningful activity will appear here as you use Veltrix.") { events ->
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp).testTag("history-screen"), contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { WorldHeading("History", "Continue meaningfully", "A return path, not a raw event log."); FreshnessLine(state.freshness, onRetry) }
            items(events.filter { it.meaningful }.ifEmpty { events }, key = { it.eventId }) { e -> PressableGlass({ onOpen(e) }, Modifier.fillMaxWidth(), 20.dp) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(34.dp).clip(CircleShape).background(projectAccentP2(e.type).copy(alpha = .13f)), contentAlignment = Alignment.Center) { Text("•", color = projectAccentP2(e.type)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(e.type.humanP2(), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text(e.occurredAt, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall) }; Text("→", color = VeltrixColors.SkyDeep) } } }
        }
    }
}

@Composable
fun PersonalMapExplorer(state: RepositoryState<PersonalMapUiModel>, onRetry: () -> Unit, onUnlock: () -> Unit, onStart: (String, Long) -> Unit, modifier: Modifier = Modifier) {
    StateFrame(state, "Personal Map", onRetry) { map ->
        WorldPanel(modifier.fillMaxWidth().testTag("personal-map-live"), accent = VeltrixColors.Mint) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Personal Map", style = MaterialTheme.typography.titleLarge, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text(if (map.eligible) "Backend eligibility satisfied" else "Requirements are not yet satisfied", color = VeltrixColors.InkMuted) }; StatusBadge(map.state) }
            if (map.state.contains("LOCK", true)) { MetricMini("Level", if (map.levelSatisfied) "Satisfied" else "Need ${map.levelRequirement}", Modifier.fillMaxWidth()); MetricMini("Memory", if (map.memorySatisfied) "Satisfied" else map.memoryRequirement.humanP2(), Modifier.fillMaxWidth()); if (map.eligible) Button(onClick = onUnlock, Modifier.fillMaxWidth()) { Text("Unlock map") } }
            else if (map.units.isEmpty()) Text("This authoritative snapshot has no units. Veltrix will not invent a route.", color = VeltrixColors.InkMuted)
            else { MapRouteGraphicP2(map.units); map.units.sortedBy { it.ordinal }.forEach { u -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(u.titleKey.humanP2(), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text("${u.progress}/${u.requiredProgress} progress", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall) }; StatusBadge(u.state); if (u.state == "AVAILABLE") TextButton(onClick = { onStart(u.unitId, map.revision) }) { Text("Start") } } } }
        }
    }
}

@Composable
private fun MapRouteGraphicP2(units: List<MapUnitUiModel>) {
    val policy = rememberVeltrixEffectPolicy()
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFFE6EEFF), Color(0xFFE9FAF4))))) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val sorted = units.sortedBy { it.ordinal }; if (sorted.isEmpty()) return@Canvas
            val points = sorted.mapIndexed { i, _ -> Offset(size.width * (i + 1) / (sorted.size + 1f), size.height * if (i % 2 == 0) .68f else .33f) }
            for (i in 0 until points.lastIndex) drawLine(if (sorted[i].state == "COMPLETED") VeltrixColors.Mint else VeltrixColors.Sky.copy(alpha = .38f), points[i], points[i + 1], 4.dp.toPx(), StrokeCap.Round)
            points.forEachIndexed { i, p -> val u = sorted[i]; val c = when (u.state) { "COMPLETED" -> VeltrixColors.Mint; "ACTIVE", "AVAILABLE" -> VeltrixColors.Sky; else -> Color(0xFF95A1B5) }; if (!policy.highContrast) drawCircle(c.copy(alpha = .15f), 18.dp.toPx(), p); drawCircle(Color.White, 12.dp.toPx(), p); drawCircle(c, 7.dp.toPx(), p) }
        }
    }
}

@Composable private fun MetricMini(label: String, value: String, modifier: Modifier) { Column(modifier.padding(5.dp)) { Text(label, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall); Text(value, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold) } }
private fun projectAccentP2(key: String): Color { val palette = listOf(VeltrixColors.Sky, VeltrixColors.Mint, Color(0xFF7868E9), Color(0xFFDF7C55)); return palette[(key.hashCode() and Int.MAX_VALUE) % palette.size] }
