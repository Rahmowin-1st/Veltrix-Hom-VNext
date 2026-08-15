package com.veltrix.hom.vnext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veltrix.hom.vnext.core.CapabilityRoute
import com.veltrix.hom.vnext.core.PrimaryDestination
import kotlinx.coroutines.launch

private const val SEARCH_ROUTE = "SEARCH"
private const val HISTORY_ROUTE = "HISTORY"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncScheduler.ensure(applicationContext)
        setContent { VeltrixTheme { VeltrixApp() } }
    }
}

@Composable
private fun VeltrixApp(vm: AppViewModel = viewModel()) {
    var destinationName by rememberSaveable { mutableStateOf(PrimaryDestination.HOME.name) }
    var secondaryName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var conversationId by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = PrimaryDestination.entries.firstOrNull { it.name == destinationName } ?: PrimaryDestination.HOME
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val createdConversation by vm.createdConversationId.collectAsStateWithLifecycle()
    val openedPractice by vm.openedPracticeId.collectAsStateWithLifecycle()

    LaunchedEffect(createdConversation) {
        createdConversation?.let { conversationId = it; secondaryName = CapabilityRoute.CHAT.name; vm.consumeCreatedConversation() }
    }
    LaunchedEffect(openedPractice) {
        openedPractice?.let { secondaryName = CapabilityRoute.PRACTICE.name; vm.consumeOpenedPractice() }
    }

    fun selectDestination(value: PrimaryDestination) {
        destinationName = value.name
        secondaryName = null
        conversationId = null
        if (value != PrimaryDestination.PROJECTS) selectedProjectId = null
    }
    fun selectCapability(value: String) {
        secondaryName = value
        if (value != CapabilityRoute.CHAT.name) conversationId = null
        if (value == CapabilityRoute.FLASHCARDS.name) vm.refreshFlashcards()
        if (value == CapabilityRoute.MISTAKES.name) vm.refreshMistakes()
        if (value == HISTORY_ROUTE) vm.refreshHistory()
        if (value == CapabilityRoute.LIBRARY.name) vm.refreshSources()
    }

    val nested = drawer.currentValue == DrawerValue.Open || conversationId != null || selectedProjectId != null || secondaryName != null
    BackHandler(enabled = nested) {
        when {
            drawer.currentValue == DrawerValue.Open -> scope.launch { drawer.close() }
            conversationId != null -> conversationId = null
            selectedProjectId != null && secondaryName == null && destination == PrimaryDestination.PROJECTS -> { selectedProjectId = null; vm.clearWorkspace() }
            secondaryName != null -> { secondaryName = null; conversationId = null; vm.clearAssessment() }
            else -> Unit
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = true,
        drawerContent = {
            Sidebar(
                onCapability = { selectCapability(it); scope.launch { drawer.close() } },
                onDestination = { selectDestination(it); scope.launch { drawer.close() } },
            )
        },
        scrimColor = VeltrixColors.Scrim,
    ) {
        VeltrixWorldBackground {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 840.dp
                if (expanded) {
                    Row(Modifier.fillMaxSize()) {
                        PrimaryRail(destination, { scope.launch { drawer.open() } }, ::selectDestination)
                        MainWorld(
                            Modifier.weight(1f), vm, destination, secondaryName, selectedProjectId, conversationId,
                            onMenu = { scope.launch { drawer.open() } },
                            onDestination = ::selectDestination,
                            onCapability = ::selectCapability,
                            onProject = { id -> selectedProjectId = id; vm.openProject(id) },
                            onConversation = { id -> conversationId = id; vm.openConversation(id) },
                            showBottomNav = false,
                        )
                    }
                } else {
                    MainWorld(
                        Modifier, vm, destination, secondaryName, selectedProjectId, conversationId,
                        onMenu = { scope.launch { drawer.open() } },
                        onDestination = ::selectDestination,
                        onCapability = ::selectCapability,
                        onProject = { id -> selectedProjectId = id; vm.openProject(id) },
                        onConversation = { id -> conversationId = id; vm.openConversation(id) },
                        showBottomNav = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainWorld(
    modifier: Modifier,
    vm: AppViewModel,
    destination: PrimaryDestination,
    secondaryName: String?,
    selectedProjectId: String?,
    conversationId: String?,
    onMenu: () -> Unit,
    onDestination: (PrimaryDestination) -> Unit,
    onCapability: (String) -> Unit,
    onProject: (String) -> Unit,
    onConversation: (String) -> Unit,
    showBottomNav: Boolean,
) {
    Scaffold(
        modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = { AppHeader(onMenu, secondaryName ?: destination.name, onSearch = { onCapability(SEARCH_ROUTE) }) },
        bottomBar = { if (showBottomNav) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) { PrimaryNavLens(destination, onDestination) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).testTag("world-content")) {
            if (secondaryName == null) DestinationWorld(vm, destination, selectedProjectId, onDestination, onCapability, onProject)
            else SecondaryWorld(vm, secondaryName, selectedProjectId, conversationId, onDestination, onCapability, onProject, onConversation)
        }
    }
}

@Composable
private fun DestinationWorld(
    vm: AppViewModel,
    destination: PrimaryDestination,
    selectedProjectId: String?,
    onDestination: (PrimaryDestination) -> Unit,
    onCapability: (String) -> Unit,
    onProject: (String) -> Unit,
) {
    when (destination) {
        PrimaryDestination.HOME -> {
            val home by vm.home.collectAsStateWithLifecycle(); val resolved by vm.sessionResolved.collectAsStateWithLifecycle(); val extreme = LocalDensity.current.fontScale >= 1.75f
            val personal = { onDestination(PrimaryDestination.PERSONAL) }; val chat = { onCapability(CapabilityRoute.CHAT.name) }; val practice = { onCapability(CapabilityRoute.PRACTICE.name) }; val projects = { onDestination(PrimaryDestination.PROJECTS) }
            if (extreme) AccessibleLargeTextHome(home, resolved, vm::refreshHome, personal, chat, practice, projects) else PremiumHomeScreen(home, resolved, vm::refreshHome, personal, chat, practice, projects)
        }
        PrimaryDestination.PERSONAL -> {
            val personal by vm.personal.collectAsStateWithLifecycle(); val resolved by vm.sessionResolved.collectAsStateWithLifecycle()
            PremiumPersonalScreen(personal, resolved, vm::refreshPersonal)
        }
        PrimaryDestination.STORE -> {
            val store by vm.store.collectAsStateWithLifecycle(); val inventory by vm.inventory.collectAsStateWithLifecycle(); val avatars by vm.avatars.collectAsStateWithLifecycle(); val profile by vm.gameProfile.collectAsStateWithLifecycle(); val feedback by vm.mutationFeedback.collectAsStateWithLifecycle()
            StoreWorldScreen(store, inventory, avatars, profile, feedback, vm::refreshStore, vm::purchase, vm::equipAvatar)
        }
        PrimaryDestination.PROJECTS -> {
            val remote by vm.remoteProjects.collectAsStateWithLifecycle(); val pending by vm.projects.collectAsStateWithLifecycle(); val workspace by vm.workspace.collectAsStateWithLifecycle()
            ProjectsWorldScreen(remote, pending, workspace, selectedProjectId, vm::refreshProjects, vm::createProject, onProject, { vm.clearWorkspace() }, { onCapability(it.name) })
        }
    }
}

@Composable
private fun SecondaryWorld(
    vm: AppViewModel,
    route: String,
    selectedProjectId: String?,
    conversationId: String?,
    onDestination: (PrimaryDestination) -> Unit,
    onCapability: (String) -> Unit,
    onProject: (String) -> Unit,
    onConversation: (String) -> Unit,
) {
    when (route) {
        CapabilityRoute.CHAT.name -> {
            val chats by vm.chats.collectAsStateWithLifecycle(); val messages by vm.messages.collectAsStateWithLifecycle(); val sources by vm.sources.collectAsStateWithLifecycle(); val streaming by vm.streaming.collectAsStateWithLifecycle(); val streamingText by vm.streamingText.collectAsStateWithLifecycle(); val streamError by vm.streamError.collectAsStateWithLifecycle(); val selectedSources by vm.selectedSources.collectAsStateWithLifecycle(); val citations by vm.citations.collectAsStateWithLifecycle()
            ChatWorldScreen(chats, messages, sources, conversationId, selectedProjectId, streaming, streamingText, streamError, selectedSources, citations, { vm.refreshChats(selectedProjectId) }, { vm.createChat(selectedProjectId) }, onConversation, vm::toggleSource, { text -> conversationId?.let { vm.sendChat(it, selectedProjectId, text) } }, { id -> conversationId?.let { vm.retryMessage(it, id) } }, { id -> conversationId?.let { vm.regenerateMessage(it, id) } }, { id -> conversationId?.let { vm.loadCitations(it, id) } })
        }
        CapabilityRoute.LIBRARY.name -> { val state by vm.sources.collectAsStateWithLifecycle(); LibraryWorldScreen(state, vm::refreshSources, vm::createTextSource, vm::retrySource) }
        CapabilityRoute.TESTING.name, CapabilityRoute.QUIZZES.name -> {
            val search by vm.search.collectAsStateWithLifecycle(); val assessment by vm.assessment.collectAsStateWithLifecycle(); val attempt by vm.attempt.collectAsStateWithLifecycle(); val result by vm.assessmentResult.collectAsStateWithLifecycle()
            AssessmentWorldScreen(route == CapabilityRoute.QUIZZES.name, search, assessment, attempt, result, { vm.search(it, selectedProjectId) }, vm::openAssessment, vm::startAssessment, vm::answerAssessment, vm::submitAssessment)
        }
        CapabilityRoute.PRACTICE.name -> { val state by vm.practice.collectAsStateWithLifecycle(); val hint by vm.practiceHint.collectAsStateWithLifecycle(); val check by vm.practiceCheck.collectAsStateWithLifecycle(); val complete by vm.practiceComplete.collectAsStateWithLifecycle(); PracticeWorldScreen(state, hint, check, complete, { vm.createPractice(selectedProjectId, it) }, vm::practiceAttempt, vm::practiceHint, vm::practiceCheck, vm::practiceSkip, vm::completePractice) }
        CapabilityRoute.FLASHCARDS.name -> { val state by vm.flashcards.collectAsStateWithLifecycle(); FlashcardsWorldScreen(state, vm::refreshFlashcards, vm::reviewFlashcard) }
        CapabilityRoute.MISTAKES.name -> { val state by vm.mistakes.collectAsStateWithLifecycle(); MistakesWorldScreen(state, vm::refreshMistakes, vm::resolveMistake, vm::practiceFromMistake, vm::flashcardFromMistake) }
        SEARCH_ROUTE -> {
            val state by vm.search.collectAsStateWithLifecycle()
            SearchWorldScreen(state, { vm.search(it, selectedProjectId) }) { r ->
                when {
                    r.type.contains("PROJECT", true) -> { onDestination(PrimaryDestination.PROJECTS); onProject(r.id) }
                    r.type.contains("CHAT", true) -> { onCapability(CapabilityRoute.CHAT.name); onConversation(r.id) }
                    r.type.contains("SOURCE", true) -> onCapability(CapabilityRoute.LIBRARY.name)
                    r.type.contains("ASSESS", true) -> { onCapability(CapabilityRoute.TESTING.name); vm.openAssessment(r.id) }
                    else -> Unit
                }
            }
        }
        HISTORY_ROUTE -> { val state by vm.history.collectAsStateWithLifecycle(); HistoryWorldScreen(state, vm::refreshHistory) { e -> when { e.projectId != null -> { onDestination(PrimaryDestination.PROJECTS); onProject(e.projectId) }; e.type.contains("CHAT", true) && e.objectId != null -> { onCapability(CapabilityRoute.CHAT.name); onConversation(e.objectId) }; else -> Unit } } }
        CapabilityRoute.CALCULATOR.name, CapabilityRoute.TRANSLATE.name, CapabilityRoute.NOTIFICATIONS.name, CapabilityRoute.SETTINGS.name -> CapabilityBridgeScreen(route)
        else -> CapabilityBridgeScreen(route)
    }
}

@Composable
private fun AppHeader(onMenu: () -> Unit, routeName: String, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(onMenu, Modifier.size(50.dp).testTag("open-capabilities").semantics { contentDescription = "Open Veltrix capabilities" }, 999.dp) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("≡", color = VeltrixColors.Ink, fontWeight = FontWeight.Bold) } }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("Veltrix Hom", color = VeltrixColors.Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium); Text(routeName.lowercase().replace('_',' ').replaceFirstChar { it.uppercaseChar().toString() }, color = VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("active-route")) }
        PressableGlass(onSearch, Modifier.size(50.dp).semantics { contentDescription = "Search Veltrix" }, 999.dp) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("⌕", color = VeltrixColors.Ink, fontWeight = FontWeight.Bold) } }
    }
}

@Composable
private fun PrimaryNavLens(selected: PrimaryDestination, onSelect: (PrimaryDestination) -> Unit) {
    val destinations = listOf(PrimaryDestination.HOME, PrimaryDestination.PERSONAL, PrimaryDestination.STORE, PrimaryDestination.PROJECTS)
    val policy = rememberVeltrixEffectPolicy()
    GlassSurface(Modifier.fillMaxWidth().height(68.dp), 28.dp, true) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
            val itemWidth = maxWidth / destinations.size.toFloat(); val index = destinations.indexOf(selected).coerceAtLeast(0)
            val x by animateDpAsState(itemWidth * index.toFloat(), if (policy.reducedMotion) snap() else spring(dampingRatio = .82f, stiffness = 430f), label = "primary-nav-lens")
            Box(Modifier.offset(x = x).width(itemWidth).fillMaxHeight().clip(RoundedCornerShape(23.dp)).background(Brush.linearGradient(listOf(Color(0xF6FFFFFF), Color(0xCEEAF2FF), Color(0xD5EEFFF9)))).semantics { contentDescription = "Selected destination lens" })
            Row(Modifier.fillMaxSize()) { destinations.forEach { d -> val active = d == selected; Box(Modifier.width(itemWidth).fillMaxHeight().selectable(active, role = Role.Tab, onClick = { onSelect(d) }).semantics { this.selected = active }.testTag("nav-${d.name}"), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(if (active) 7.dp else 5.dp).clip(CircleShape).background(if (active) VeltrixColors.Sky else Color(0xFF9AABC2))); Text(d.labelP2(), color = if (active) VeltrixColors.Ink else VeltrixColors.InkMuted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, style = MaterialTheme.typography.labelMedium) } } } }
        }
    }
}

@Composable
private fun PrimaryRail(selected: PrimaryDestination, onMenu: () -> Unit, onSelect: (PrimaryDestination) -> Unit) {
    Column(Modifier.fillMaxHeight().width(116.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PressableGlass(onMenu, Modifier.size(52.dp), 999.dp) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("≡", fontWeight = FontWeight.Bold, color = VeltrixColors.Ink) } }
        Spacer(Modifier.height(8.dp))
        PrimaryDestination.entries.forEach { d -> val active = selected == d; PressableGlass({ onSelect(d) }, Modifier.fillMaxWidth().heightIn(min = 62.dp).semantics { role = Role.Tab; this.selected = active }, 22.dp, active) { Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Box(Modifier.size(7.dp).clip(CircleShape).background(if (active) VeltrixColors.Sky else Color(0xFF9AABC2))); Text(d.labelP2(), color = if (active) VeltrixColors.Ink else VeltrixColors.InkMuted, style = MaterialTheme.typography.labelSmall) } } }
    }
}

@Composable
private fun Sidebar(onCapability: (String) -> Unit, onDestination: (PrimaryDestination) -> Unit) {
    Box(Modifier.fillMaxHeight().widthIn(max = 340.dp).fillMaxWidth(.86f).padding(10.dp)) {
        GlassSurface(Modifier.fillMaxSize(), 34.dp, true) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Text("Veltrix", style = MaterialTheme.typography.headlineSmall, color = VeltrixColors.Ink, fontWeight = FontWeight.Bold)
                Text("Learning, intelligence and continuity", color = VeltrixColors.InkMuted)
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0x1F4A638A))
                LazyColumn(Modifier.weight(1f).testTag("capability-list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { SidebarRoute(SEARCH_ROUTE, "Search", onCapability) }
                    item { SidebarRoute(HISTORY_ROUTE, "History", onCapability) }
                    items(CapabilityRoute.entries) { route -> SidebarRoute(route.name, route.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercaseChar().toString() }, onCapability, "capability-${route.name}") }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0x1F4A638A))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(PrimaryDestination.HOME, PrimaryDestination.PERSONAL).forEach { d -> PressableGlass({ onDestination(d) }, Modifier.weight(1f).heightIn(min = 48.dp), 18.dp) { Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) { Text(d.labelP2(), color = VeltrixColors.Ink) } } } }
            }
        }
    }
}

@Composable
private fun SidebarRoute(route: String, label: String, onCapability: (String) -> Unit, tag: String? = null) {
    Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(16.dp)).selectable(false, role = Role.Button, onClick = { onCapability(route) }).padding(horizontal = 12.dp).then(if (tag == null) Modifier else Modifier.testTag(tag)), contentAlignment = Alignment.CenterStart) { Text(label, color = VeltrixColors.Ink, fontWeight = FontWeight.Medium) }
}

private fun PrimaryDestination.labelP2() = name.lowercase().replaceFirstChar { it.uppercaseChar().toString() }
