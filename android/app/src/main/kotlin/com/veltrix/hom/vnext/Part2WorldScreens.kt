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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
    val variant = remember(seed) { (seed and Int.MAX_VALUE) % 5 }
    val energy by animateFloatAsState(
        if (equipped) 1f else .86f,
        if (policy.reducedMotion) snap() else spring(dampingRatio = .74f, stiffness = 240f),
        label = "avatar-energy",
    )
    val base = modifier.semantics {
        contentDescription = "Veltrix ${avatarDisplayNameP2(avatarId)}, ${tier.humanP2()} identity${if (equipped) ", equipped" else ""}"
        if (onClick != null) role = Role.Button
    }
    val interactive = if (onClick == null) base else base.clickable(onClick = onClick)
    Box(
        interactive.drawWithCache {
            val w = size.width
            val h = size.height
            val c = Offset(w / 2f, h / 2f)
            val d = size.minDimension
            val accent = when (variant) {
                0 -> VeltrixColors.Sky
                1 -> VeltrixColors.Mint
                2 -> Color(0xFF7868E9)
                3 -> Color(0xFFE78A58)
                else -> Color(0xFF4D7FD8)
            }
            val secondary = when (variant) {
                0 -> Color(0xFF9ED0FF)
                1 -> Color(0xFF8DE5CC)
                2 -> Color(0xFFC1A9FF)
                3 -> Color(0xFFFFC69A)
                else -> Color(0xFF8CE0F0)
            }
            val body = Brush.linearGradient(listOf(Color.White, secondary.copy(alpha = .72f), Color(0xFFE5ECF8)), Offset(0f, 0f), Offset(w, h))
            val dark = Color(0xFF263A58)
            onDrawBehind {
                if (!policy.highContrast) {
                    drawCircle(accent.copy(alpha = .11f * energy), d * .49f, c)
                    drawCircle(accent.copy(alpha = .18f), d * .43f, c, style = Stroke(1.2.dp.toPx()))
                }
                when (variant) {
                    0 -> { // Prism: faceted learning explorer.
                        val p = Path().apply { moveTo(w*.50f,h*.12f); lineTo(w*.82f,h*.40f); lineTo(w*.68f,h*.82f); lineTo(w*.32f,h*.82f); lineTo(w*.18f,h*.40f); close() }
                        drawPath(p, body); drawPath(p, accent.copy(alpha=.78f), style=Stroke(1.6.dp.toPx()))
                        drawLine(Color.White.copy(alpha=.88f),Offset(w*.29f,h*.34f),Offset(w*.50f,h*.18f),1.5.dp.toPx(),StrokeCap.Round)
                    }
                    1 -> { // Sentinel: compact capsule with side fins.
                        drawRoundRect(body, Offset(w*.23f,h*.20f), androidx.compose.ui.geometry.Size(w*.54f,h*.62f), androidx.compose.ui.geometry.CornerRadius(d*.18f))
                        drawRoundRect(accent.copy(alpha=.65f),Offset(w*.11f,h*.34f),androidx.compose.ui.geometry.Size(w*.16f,h*.25f),androidx.compose.ui.geometry.CornerRadius(d*.07f))
                        drawRoundRect(accent.copy(alpha=.65f),Offset(w*.73f,h*.34f),androidx.compose.ui.geometry.Size(w*.16f,h*.25f),androidx.compose.ui.geometry.CornerRadius(d*.07f))
                    }
                    2 -> { // Bloom: six-node memory constellation.
                        repeat(6) { i ->
                            val a=(i/6f)*6.28318f
                            drawCircle(secondary.copy(alpha=.92f),d*.115f,Offset(c.x+kotlin.math.cos(a)*d*.27f,c.y+kotlin.math.sin(a)*d*.27f))
                        }
                        drawCircle(body,d*.25f,c); drawCircle(accent,d*.25f,c,style=Stroke(1.4.dp.toPx()))
                    }
                    3 -> { // Comet: directional kite with orbit trail.
                        val p=Path().apply{moveTo(w*.52f,h*.14f);lineTo(w*.82f,h*.52f);lineTo(w*.52f,h*.83f);lineTo(w*.20f,h*.60f);lineTo(w*.31f,h*.34f);close()}
                        drawPath(p,body);drawPath(p,accent.copy(alpha=.8f),style=Stroke(1.6.dp.toPx()))
                        drawArc(accent.copy(alpha=.48f),205f,230f,false,Offset(w*.06f,h*.10f),androidx.compose.ui.geometry.Size(w*.88f,h*.82f),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                    }
                    else -> { // Atlas: grounded hex-shell with an upper crown ridge.
                        val p=Path().apply{moveTo(w*.30f,h*.23f);lineTo(w*.43f,h*.12f);lineTo(w*.50f,h*.25f);lineTo(w*.58f,h*.12f);lineTo(w*.72f,h*.23f);lineTo(w*.80f,h*.55f);lineTo(w*.64f,h*.82f);lineTo(w*.36f,h*.82f);lineTo(w*.20f,h*.55f);close()}
                        drawPath(p,body);drawPath(p,accent.copy(alpha=.8f),style=Stroke(1.6.dp.toPx()))
                    }
                }
                // Shared face language keeps identity continuity while silhouettes remain distinct.
                val visorY = if (variant==2) .50f else .47f
                drawRoundRect(dark.copy(alpha=.9f),Offset(w*.32f,h*visorY),androidx.compose.ui.geometry.Size(w*.36f,h*.13f),androidx.compose.ui.geometry.CornerRadius(d*.055f))
                drawCircle(Color(0xFF8CEBD2),d*.025f,Offset(w*.43f,h*(visorY+.065f)))
                drawCircle(Color(0xFF91BEFF),d*.025f,Offset(w*.57f,h*(visorY+.065f)))
                if (equipped) drawCircle(VeltrixColors.Mint,d*.045f,Offset(w*.82f,h*.20f))
            }
        },
    )
}

private fun avatarDisplayNameP2(id:String):String = when {
    id.contains("ultra",true) -> "Atlas"
    id.contains("elite",true) -> "Comet"
    id.contains("pro",true) -> "Prism"
    id.contains("focus",true) -> "Sentinel"
    id.contains("memory",true) -> "Bloom"
    else -> id.humanP2().takeIf { it.isNotBlank() && !it.contains("avatar",true) } ?: "Core"
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
                item { WorldHeading(w.project.title, "Project Space", w.project.purpose ?: "Context-preserving learning workspace") { TextButton(onClick = onCloseWorkspace) { Text("Back to Projects") } } }
                item { ProjectIdentityStageP3(w) }
                item { ProjectModuleDockP3(w, onCapability) }
                if (w.goals.isNotEmpty()) item { WorldPanel(accent = VeltrixColors.Mint) { Text("Goals", fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); w.goals.take(5).forEach { g -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(g.title, Modifier.weight(1f), color = VeltrixColors.Ink); StatusBadge(g.status) } } } }
                if (w.recentChats.isNotEmpty()) item { WorldPanel(accent = Color(0xFF7868E9)) { Text("Recent project conversations", fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); w.recentChats.take(4).forEach { c -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(c.title.ifBlank { "Project conversation" }, color = VeltrixColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(c.learningMode.humanP2(), color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall) }; Text("Project", color = VeltrixColors.SkyDeep, style = MaterialTheme.typography.labelSmall) } }; TextButton(onClick = { onCapability(CapabilityRoute.CHAT) }) { Text("Open project chat") } } }
                if (w.recommendationActions.isNotEmpty()) item { WorldPanel(accent = VeltrixColors.Sky) { Text("Next useful actions", fontWeight = FontWeight.SemiBold, color = VeltrixColors.Ink); w.recommendationActions.take(4).forEach { Text("• ${it.humanP2()}", color = VeltrixColors.InkMuted) } } }
            }
        }
        if (expanded) Row(Modifier.fillMaxSize()) { list(Modifier.weight(.44f).fillMaxHeight()); VerticalDivider(); detail(Modifier.weight(.56f).fillMaxHeight()) }
        else if (selectedProjectId == null) list(Modifier.fillMaxSize()) else detail(Modifier.fillMaxSize())
    }
}

@Composable
private fun ProjectIdentityStageP3(w:ProjectWorkspaceUiModel) {
    val accent=projectAccentP2(w.project.id)
    Box(Modifier.fillMaxWidth().heightIn(min=190.dp).clip(RoundedCornerShape(32.dp)).drawWithCache {
        val bg=Brush.linearGradient(listOf(accent.copy(alpha=.22f),Color(0xFFF8FAFF),VeltrixColors.Mint.copy(alpha=.14f)))
        val halo=Brush.radialGradient(listOf(Color.White.copy(alpha=.72f),Color.Transparent),Offset(size.width*.78f,size.height*.16f),size.minDimension*.72f)
        onDrawBehind{drawRect(bg);drawRect(halo);drawCircle(accent.copy(alpha=.13f),size.minDimension*.28f,Offset(size.width*.86f,size.height*.22f));drawLine(Color.White.copy(alpha=.74f),Offset(size.width*.60f,0f),Offset(size.width*.95f,size.height*.55f),1.dp.toPx())}
    }.padding(18.dp).semantics { contentDescription="Project ${w.project.title} operating space" }) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha=.15f)),contentAlignment=Alignment.Center){Text("◆",color=accent,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(w.contextTopic ?: "Current project focus",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis);Text(w.contextLearningMode?.let{"${it.humanP2()} mode"} ?: "Project context is isolated and persistent",color=VeltrixColors.InkMuted)};StatusBadge(w.project.status)}
            Row(Modifier.fillMaxWidth()){MetricMini("Goals",w.goals.size.toString(),Modifier.weight(1f));MetricMini("Sources",w.sourceCount.toString(),Modifier.weight(1f));MetricMini("Learning",(w.assessmentCount+w.practiceCount+w.flashcardCount).toString(),Modifier.weight(1f));MetricMini("Signals",w.projectMemorySignals.toString(),Modifier.weight(1f))}
            w.instruction?.let{Text("AI instruction · $it",color=VeltrixColors.InkMuted,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}
        }
    }
}

@Composable
private fun ProjectModuleDockP3(w:ProjectWorkspaceUiModel,onCapability:(CapabilityRoute)->Unit) {
    val modules=listOf(
        Triple(CapabilityRoute.CHAT,"Chats",w.recentChats.size),
        Triple(CapabilityRoute.LIBRARY,"Sources",w.sourceCount),
        Triple(CapabilityRoute.PRACTICE,"Practice",w.practiceCount),
        Triple(CapabilityRoute.TESTING,"Tests",w.assessmentCount),
        Triple(CapabilityRoute.FLASHCARDS,"Flashcards",w.flashcardCount),
        Triple(CapabilityRoute.MISTAKES,"Mistakes",w.mistakeCount),
    )
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Project modules",color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)
        modules.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{(route,label,count)->PressableGlass({onCapability(route)},Modifier.weight(1f).heightIn(min=68.dp),20.dp){Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.Center){Text(label,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelMedium);Text(count.toString(),color=VeltrixColors.InkMuted,style=MaterialTheme.typography.labelSmall)}}};repeat(3-row.size){Spacer(Modifier.weight(1f))}} }
        if(w.noteCount>0) Text("${w.noteCount} note${if(w.noteCount==1)"" else "s"} remain part of this Project Space even when no dedicated Notes route is exposed.",color=VeltrixColors.InkMuted,style=MaterialTheme.typography.bodySmall)
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
    val clipboard = LocalClipboardManager.current
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
                                SelectionContainer { ChatMessageContentP3(msg.content) }
                                if (!user) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    TextButton(onClick = { clipboard.setText(AnnotatedString(msg.content)) }) { Text("Copy") }
                                    TextButton(onClick = { onLoadCitations(msg.id) }) { Text("Sources") }
                                    TextButton(onClick = { onRegenerate(msg.id) }) { Text("Regenerate") }
                                    if (msg.state.contains("FAIL", true)) TextButton(onClick = { onRetry(msg.id) }) { Text("Retry") }
                                }
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
private fun ChatMessageContentP3(content:String) {
    val trimmed = content.trim()
    val kind = when {
        trimmed.contains("```") -> "CODE"
        trimmed.startsWith("rule:", true) || trimmed.startsWith("qoida:", true) -> "RULE"
        trimmed.startsWith("example:", true) || trimmed.startsWith("misol:", true) -> "EXAMPLE"
        trimmed.contains("=") && (trimmed.contains("$") || trimmed.contains("\\") || trimmed.length < 180) -> "FORMULA"
        else -> "TEXT"
    }
    if (kind == "TEXT") Text(content, color = VeltrixColors.Ink, style = MaterialTheme.typography.bodyLarge)
    else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha=.48f)).padding(12.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(kind, color=VeltrixColors.SkyDeep, style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold)
        Text(content, color=VeltrixColors.Ink, fontFamily=if(kind=="CODE"||kind=="FORMULA")FontFamily.Monospace else FontFamily.Default, style=MaterialTheme.typography.bodyLarge)
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
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PressableGlass(
                    { reveal = !reveal },
                    Modifier.fillMaxWidth().heightIn(min = 300.dp).testTag("flashcard-stage").semantics { contentDescription = if (reveal) "Flashcard answer revealed" else "Flashcard prompt. Tap to reveal answer" },
                    34.dp,
                    strong = true,
                ) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        if (policy.reducedMotion) FlashcardFace(card, reveal) else AnimatedContent(reveal, label = "flashcard-reveal") { FlashcardFace(card, it) }
                    }
                }
            }
            if (reveal) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("AGAIN", "HARD", "GOOD", "EASY").forEach { rating -> OutlinedButton(onClick = { onRate(card.id, rating); reveal = false; if (index < cards.lastIndex) index++ }, modifier = Modifier.weight(1f)) { Text(rating.humanP2(), maxLines = 1) } } } else Text("Tap the card to reveal the answer", Modifier.align(Alignment.CenterHorizontally), color = VeltrixColors.InkMuted)
        }
    }
}

@Composable
private fun FlashcardFace(card: FlashcardUiModel, back: Boolean) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (back) "ANSWER" else "PROMPT", style = MaterialTheme.typography.labelSmall, color = VeltrixColors.SkyDeep, fontWeight = FontWeight.Bold)
        Text(
            if (back) card.back else card.front,
            style = MaterialTheme.typography.headlineSmall,
            color = VeltrixColors.Ink,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("flashcard-primary-text").semantics { contentDescription = if (back) "Flashcard answer" else "Flashcard prompt" },
        )
        if (back) card.explanation?.let { Text(it, color = VeltrixColors.InkMuted, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
fun StoreWorldScreen(store: RepositoryState<StoreCatalogUiModel>, inventory: RepositoryState<List<InventoryItemUiModel>>, avatars: RepositoryState<List<AvatarCatalogUiModel>>, profile: RepositoryState<GameProfileUiModel>, feedback: MutationFeedback?, onRetry: () -> Unit, onPurchase: (String) -> Unit, onEquip: (String, Long) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf("ALL") }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp).testTag("store-screen"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WorldHeading("Store & identity", "Earned customization", "Preview freely. Ownership, price, availability and equip state remain backend truth.")
        val balance = store.value?.coinBalance ?: profile.value?.coinBalance
        GlassSurface(Modifier.fillMaxWidth(), 24.dp, strong = true) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text("VELTRIX WALLET",color=VeltrixColors.SkyDeep,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Text("Earned coins",color=VeltrixColors.InkMuted)};Text(balance?.prettyP2() ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = VeltrixColors.Ink) } }
        TabRow(tab, containerColor = Color.Transparent) { listOf("Catalog", "Inventory", "Avatar").forEachIndexed { i, label -> Tab(tab == i, onClick = { tab = i }, text = { Text(label) }) } }
        feedback?.takeIf { !it.success }?.let { Text(errorCopyP2(it.code), color = VeltrixColors.Error) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val expanded=maxWidth>=780.dp
            when (tab) {
                0 -> StateFrame(store, "store", onRetry, isEmpty = { it.items.isEmpty() }, emptyText = "No catalog items are available.") { c ->
                    val categories=listOf("ALL")+c.items.map{it.itemType}.distinct()
                    val filtered=c.items.filter{category=="ALL"||it.itemType==category}
                    val selected=c.items.firstOrNull{it.itemId==selectedItemId} ?: filtered.firstOrNull() ?: c.items.first()
                    val catalogPane:@Composable (Modifier)->Unit={m->LazyColumn(m,verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
                        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){items(categories,key={it}){cat->FilterChip(selected=category==cat,onClick={category=cat;selectedItemId=null},label={Text(if(cat=="ALL")"All" else cat.humanP2())})}}}
                        items(filtered,key={it.itemId}){item->PressableGlass({selectedItemId=item.itemId},Modifier.fillMaxWidth().heightIn(min=78.dp),22.dp,strong=selected.itemId==item.itemId){Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){if(item.itemType.contains("AVATAR",true)||item.itemId.contains("AVATAR",true)){LivingVeltrixAvatar(item.itemId,tier="PREVIEW",modifier=Modifier.size(54.dp))}else Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(projectAccentP2(item.itemId).copy(alpha=.14f)),contentAlignment=Alignment.Center){Text("◆",color=projectAccentP2(item.itemId))};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(storeDisplayNameP3(item),color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(item.itemType.humanP2(),color=VeltrixColors.InkMuted,style=MaterialTheme.typography.labelSmall)};StatusBadge(if(item.owned)"OWNED" else if(item.available)"AVAILABLE" else "LOCKED")}}}
                    }}
                    val previewPane:@Composable (Modifier)->Unit={m->Column(m.padding(if(expanded)16.dp else 0.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){StorePreviewP3(selected,c.coinBalance,onPurchase)}}
                    if(expanded)Row(Modifier.fillMaxSize()){catalogPane(Modifier.weight(.50f).fillMaxHeight());VerticalDivider();previewPane(Modifier.weight(.50f).fillMaxHeight())}else LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){item{previewPane(Modifier.fillMaxWidth())};item{Box(Modifier.fillMaxWidth().height(480.dp)){catalogPane(Modifier.fillMaxSize())}}}
                }
                1 -> StateFrame(inventory, "inventory", onRetry, isEmpty = { it.isEmpty() }, emptyText = "Owned items appear here after backend-confirmed acquisition.") { list -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) { item{Text("Owned collection",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)};items(list, key = { it.itemId }) { i -> WorldPanel(accent = VeltrixColors.Mint) { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(inventoryDisplayNameP3(i), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text("${i.type.humanP2()} · ${i.ownershipSource.humanP2()}", color = VeltrixColors.InkMuted) }; StatusBadge("OWNED");Text(" ×${i.quantity}", fontWeight = FontWeight.Bold,color=VeltrixColors.Ink) } } } } }
                else -> StateFrame(avatars, "avatars", onRetry, isEmpty = { it.isEmpty() }, emptyText = "No avatar catalog is available.") { list -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) { item{Text("Identity collection",style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold)};items(list, key = { it.avatarId }) { a -> WorldPanel(accent = if (a.equipped) VeltrixColors.Mint else projectAccentP2(a.avatarId)) { Row(verticalAlignment = Alignment.CenterVertically) { LivingVeltrixAvatar(a.avatarId, a.assetKey, a.tier, Modifier.size(82.dp), a.equipped); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(a.name.takeIf{it.isNotBlank()}?.humanP2() ?: avatarDisplayNameP2(a.avatarId), color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold); Text(a.tier.humanP2(), color = VeltrixColors.InkMuted); StatusBadge(if (a.equipped) "EQUIPPED" else if (a.owned) "OWNED" else "PREVIEW") }; if (a.owned && !a.equipped) Button(enabled = profile.value != null, onClick = { onEquip(a.avatarId, profile.value?.avatarRevision ?: 0) }) { Text("Equip") } } } } } }
            }
        }
    }
}

@Composable
private fun StorePreviewP3(item:StoreItemUiModel,balance:Long,onPurchase:(String)->Unit) {
    val accent=if(item.owned)VeltrixColors.Mint else projectAccentP2(item.itemId)
    WorldPanel(Modifier.fillMaxWidth().testTag("store-preview"),accent=accent) {
        Box(Modifier.fillMaxWidth().heightIn(min=170.dp).clip(RoundedCornerShape(26.dp)).background(Brush.radialGradient(listOf(accent.copy(alpha=.18f),Color(0xFFF8FAFF),Color.White))),contentAlignment=Alignment.Center){if(item.itemType.contains("AVATAR",true)||item.itemId.contains("AVATAR",true))LivingVeltrixAvatar(item.itemId,tier="PREVIEW",modifier=Modifier.size(142.dp),equipped=false)else Text("◆",style=MaterialTheme.typography.displayMedium,color=accent)}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(storeDisplayNameP3(item),style=MaterialTheme.typography.titleLarge,color=VeltrixColors.Ink,fontWeight=FontWeight.SemiBold);Text(item.itemType.humanP2(),color=VeltrixColors.InkMuted)};StatusBadge(if(item.owned)"OWNED" else if(item.available)"AVAILABLE" else "LOCKED")}
        item.requirements.takeIf{it.isNotBlank()&&it!="{}"}?.let{Text("Requirement · ${it.humanP2()}",color=VeltrixColors.InkMuted,style=MaterialTheme.typography.bodySmall)}
        if(item.owned) Text("Owned on this account. Preview does not mutate equipped state.",color=VeltrixColors.InkMuted)
        else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("${item.priceCoins.prettyP2()} coins",color=VeltrixColors.Ink,fontWeight=FontWeight.Bold);if(balance<item.priceCoins)Text("Need ${(item.priceCoins-balance).prettyP2()} more",color=VeltrixColors.InkMuted,style=MaterialTheme.typography.labelSmall)};Button(enabled=item.available&&balance>=item.priceCoins,onClick={onPurchase(item.itemId)}){Text(if(!item.available)"Locked" else if(balance<item.priceCoins)"Insufficient" else "Buy")}}
    }
}

private fun storeDisplayNameP3(item:StoreItemUiModel):String = when {
    item.itemId.contains("HYPER",true) -> "Nova Hyper"
    item.itemId.contains("ULTRA",true) -> "Atlas Ultra"
    item.itemId.contains("ELITE",true) -> "Comet Elite"
    item.itemId.contains("PRO",true) -> "Prism Pro"
    item.itemId.contains("CORE",true) -> "Veltrix Core"
    else -> item.itemId.removePrefix("AVATAR_").removePrefix("ITEM_").humanP2()
}
private fun inventoryDisplayNameP3(item:InventoryItemUiModel):String = when {
    item.itemId.contains("HYPER",true)->"Nova Hyper"
    item.itemId.contains("ULTRA",true)->"Atlas Ultra"
    item.itemId.contains("ELITE",true)->"Comet Elite"
    item.itemId.contains("PRO",true)->"Prism Pro"
    else->item.itemId.removePrefix("AVATAR_").removePrefix("ITEM_").humanP2()
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
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    StateFrame(state, "Personal Map", onRetry) { map ->
        WorldPanel(modifier.fillMaxWidth().testTag("personal-map-live"), accent = VeltrixColors.Mint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Personal Map", style = MaterialTheme.typography.titleLarge, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold)
                    Text(if (map.eligible) "Your progression world is available" else "Progression requirements are still forming", color = VeltrixColors.InkMuted)
                }
                StatusBadge(map.state)
            }
            if (map.state.contains("LOCK", true)) {
                MapLockedStageP3(map)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricMini("Profile level", if (map.levelSatisfied) "Ready" else "Need ${map.levelRequirement}", Modifier.weight(1f))
                    MetricMini("Memory maturity", if (map.memorySatisfied) "Ready" else map.memoryRequirement.humanP2(), Modifier.weight(1f))
                }
                if (map.eligible) Button(onClick = onUnlock, Modifier.fillMaxWidth()) { Text("Enter Personal Map") }
            } else if (map.units.isEmpty()) {
                Text("This authoritative snapshot has no visible units. Veltrix will not invent a route.", color = VeltrixColors.InkMuted)
            } else {
                val sorted = map.units.sortedBy { it.ordinal }
                val selected = sorted.firstOrNull { it.unitId == selectedId } ?: sorted.firstOrNull { it.state == "ACTIVE" } ?: sorted.firstOrNull { it.state == "AVAILABLE" } ?: sorted.first()
                MapWorldGraphicP3(sorted, selected.unitId)
                WorldPanel(accent = when (selected.state) { "COMPLETED" -> VeltrixColors.Mint; "ACTIVE", "AVAILABLE" -> VeltrixColors.Sky; else -> Color(0xFF9BA6B8) }) {
                    Text(mapUnitTitleP3(selected), style = MaterialTheme.typography.titleMedium, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("map-selected-title"))
                    Text(mapUnitStateCopyP3(selected), color = VeltrixColors.InkMuted)
                    if (!selected.state.contains("HIDDEN", true) && !selected.state.contains("UNKNOWN", true)) {
                        val denom = selected.requiredProgress.coerceAtLeast(1)
                        LinearProgressIndicator(progress = { (selected.progress.toFloat()/denom.toFloat()).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth())
                    }
                    if (selected.state == "AVAILABLE") Button(onClick = { onStart(selected.unitId, map.revision) }) { Text("Start this unit") }
                }
                Text("Map route", color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall)
                sorted.forEach { u ->
                    val chosen = u.unitId == selected.unitId
                    PressableGlass(
                        onClick = { selectedId = u.unitId },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).semantics { contentDescription = "${mapUnitTitleP3(u)}, ${mapUnitStateCopyP3(u)}" },
                        radius = 19.dp,
                        strong = chosen,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(mapUnitColorP3(u).copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text((u.ordinal + 1).toString(), color = mapUnitColorP3(u), fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(mapUnitTitleP3(u), color = VeltrixColors.Ink, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium); Text(mapUnitStateCopyP3(u), color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall) }
                            StatusBadge(u.state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapLockedStageP3(map: PersonalMapUiModel) {
    val policy = rememberVeltrixEffectPolicy()
    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFFDDE8FA), Color(0xFFF3F6FA), Color(0xFFE5F3EE)))).semantics { contentDescription = "Personal Map locked. Future route is hidden until backend requirements are satisfied." }) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply { moveTo(size.width*.10f,size.height*.76f); cubicTo(size.width*.29f,size.height*.65f,size.width*.30f,size.height*.28f,size.width*.52f,size.height*.42f); cubicTo(size.width*.70f,size.height*.54f,size.width*.72f,size.height*.23f,size.width*.91f,size.height*.18f) }
            drawPath(path, Color.White.copy(alpha=.9f), style=Stroke(7.dp.toPx(),cap=StrokeCap.Round))
            drawPath(path, VeltrixColors.Sky.copy(alpha=.22f), style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
            listOf(Offset(size.width*.14f,size.height*.72f),Offset(size.width*.50f,size.height*.42f),Offset(size.width*.86f,size.height*.21f)).forEachIndexed{i,p->drawCircle(if(i==0&&map.eligible)VeltrixColors.Mint else Color(0xFF9EA9B9),9.dp.toPx(),p);if(!policy.highContrast)drawCircle(Color.White.copy(alpha=.5f),18.dp.toPx(),p,style=Stroke(1.dp.toPx()))}
            if(!policy.highContrast) drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color(0xBFF4F6FA))),Offset(size.width*.55f,0f),androidx.compose.ui.geometry.Size(size.width*.45f,size.height))
        }
        Text("Future territory stays hidden until it is truly available.", Modifier.align(Alignment.BottomStart).padding(16.dp).fillMaxWidth(.72f), color = VeltrixColors.InkMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MapWorldGraphicP3(units: List<MapUnitUiModel>, selectedId:String) {
    val policy = rememberVeltrixEffectPolicy()
    Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFFD8E6FF), Color(0xFFF7F9FF), Color(0xFFDDF5EA)))).semantics { contentDescription = "Spatial Personal Map. A semantic route list follows below." }) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val sorted=units.sortedBy{it.ordinal}; if(sorted.isEmpty())return@Canvas
            val pts=sorted.mapIndexed{i,_->
                val x=size.width*(.10f + .80f*(i.toFloat()/sorted.lastIndex.coerceAtLeast(1)))
                val y=size.height*when(i%4){0->.72f;1->.43f;2->.61f;else->.28f}
                Offset(x,y)
            }
            val route=Path().apply { moveTo(pts.first().x,pts.first().y); for(i in 1 until pts.size){val a=pts[i-1];val b=pts[i];cubicTo((a.x+b.x)/2,a.y,(a.x+b.x)/2,b.y,b.x,b.y)} }
            drawPath(route,Color.White.copy(alpha=.92f),style=Stroke(10.dp.toPx(),cap=StrokeCap.Round))
            drawPath(route,VeltrixColors.Sky.copy(alpha=.30f),style=Stroke(4.dp.toPx(),cap=StrokeCap.Round))
            pts.forEachIndexed{i,p->
                val u=sorted[i]; val c=mapUnitColorP3(u); val active=u.unitId==selectedId
                if(!policy.highContrast)drawCircle(c.copy(alpha=if(active).24f else .12f),if(active)28.dp.toPx() else 21.dp.toPx(),p)
                drawCircle(Color.White,if(active)16.dp.toPx() else 13.dp.toPx(),p)
                drawCircle(c,if(active)10.dp.toPx() else 8.dp.toPx(),p)
                if(u.state.contains("LOCK",true)||u.state.contains("HIDDEN",true)||u.state.contains("UNKNOWN",true)) drawCircle(Color(0xFFB7C0CD).copy(alpha=.18f),31.dp.toPx(),p)
            }
            if(!policy.highContrast){drawCircle(Color.White.copy(alpha=.55f),size.minDimension*.23f,Offset(size.width*.82f,size.height*.13f),style=Stroke(1.dp.toPx()));drawCircle(VeltrixColors.Mint.copy(alpha=.10f),size.minDimension*.34f,Offset(size.width*.12f,size.height*.92f))}
        }
        Text("CURRENT JOURNEY", Modifier.align(Alignment.TopStart).padding(16.dp), color = VeltrixColors.SkyDeep, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private fun mapUnitTitleP3(u:MapUnitUiModel):String = if(u.state.contains("HIDDEN",true)||u.state.contains("UNKNOWN",true)) "Unknown territory" else u.titleKey.humanP2()
private fun mapUnitStateCopyP3(u:MapUnitUiModel):String = when {
    u.state == "COMPLETED" -> "Completed · ${u.progress}/${u.requiredProgress}"
    u.state == "ACTIVE" -> "Current location · ${u.progress}/${u.requiredProgress} progress"
    u.state == "AVAILABLE" -> "Available next"
    u.state.contains("HIDDEN",true)||u.state.contains("UNKNOWN",true) -> "Details hidden by progression"
    u.state.contains("LOCK",true) -> "Locked by backend progression"
    else -> "${u.state.humanP2()} · ${u.progress}/${u.requiredProgress}"
}
private fun mapUnitColorP3(u:MapUnitUiModel):Color = when {u.state=="COMPLETED"->VeltrixColors.Mint;u.state=="ACTIVE"||u.state=="AVAILABLE"->VeltrixColors.Sky;u.state.contains("HIDDEN",true)||u.state.contains("UNKNOWN",true)->Color(0xFFADB5C2);else->Color(0xFF8E99AA)}

@Composable private fun MetricMini(label: String, value: String, modifier: Modifier) { Column(modifier.padding(5.dp)) { Text(label, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall); Text(value, color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold) } }
private fun projectAccentP2(key: String): Color { val palette = listOf(VeltrixColors.Sky, VeltrixColors.Mint, Color(0xFF7868E9), Color(0xFFDF7C55)); return palette[(key.hashCode() and Int.MAX_VALUE) % palette.size] }
