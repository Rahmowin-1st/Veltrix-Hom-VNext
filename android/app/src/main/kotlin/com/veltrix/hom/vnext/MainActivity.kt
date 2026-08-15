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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veltrix.hom.vnext.core.CapabilityRoute
import com.veltrix.hom.vnext.core.PrimaryDestination
import kotlinx.coroutines.launch

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
    var capabilityName by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = PrimaryDestination.entries.firstOrNull { it.name == destinationName } ?: PrimaryDestination.HOME
    val capability = capabilityName?.let { value -> CapabilityRoute.entries.firstOrNull { it.name == value } }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val home by vm.home.collectAsStateWithLifecycle()
    val personal by vm.personal.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val sessionResolved by vm.sessionResolved.collectAsStateWithLifecycle()

    BackHandler(enabled = capability != null) { capabilityName = null }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = true,
        drawerContent = {
            Sidebar(
                onCapability = {
                    capabilityName = it.name
                    scope.launch { drawer.close() }
                },
                onDestination = {
                    destinationName = it.name
                    capabilityName = null
                    scope.launch { drawer.close() }
                },
            )
        },
        scrimColor = VeltrixColors.Scrim,
    ) {
        VeltrixWorldBackground {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 840.dp
                if (expanded) {
                    Row(Modifier.fillMaxSize()) {
                        PrimaryRail(
                            selected = destination,
                            onMenu = { scope.launch { drawer.open() } },
                            onSelect = {
                                destinationName = it.name
                                capabilityName = null
                            },
                        )
                        MainWorld(
                            modifier = Modifier.weight(1f),
                            destination = destination,
                            capability = capability,
                            home = home,
                            personal = personal,
                            sessionResolved = sessionResolved,
                            projects = projects,
                            onMenu = { scope.launch { drawer.open() } },
                            onSelectDestination = {
                                destinationName = it.name
                                capabilityName = null
                            },
                            onSelectCapability = { capabilityName = it.name },
                            onRetryHome = vm::refreshHome,
                            onRetryPersonal = vm::refreshPersonal,
                            onCreateProject = vm::createProject,
                            showBottomNav = false,
                        )
                    }
                } else {
                    MainWorld(
                        modifier = Modifier,
                        destination = destination,
                        capability = capability,
                        home = home,
                        personal = personal,
                        sessionResolved = sessionResolved,
                        projects = projects,
                        onMenu = { scope.launch { drawer.open() } },
                        onSelectDestination = {
                            destinationName = it.name
                            capabilityName = null
                        },
                        onSelectCapability = { capabilityName = it.name },
                        onRetryHome = vm::refreshHome,
                        onRetryPersonal = vm::refreshPersonal,
                        onCreateProject = vm::createProject,
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
    destination: PrimaryDestination,
    capability: CapabilityRoute?,
    home: RepositoryState<HomeFinalModel>,
    personal: RepositoryState<PersonalFinalModel>,
    sessionResolved: Boolean,
    projects: List<LocalProjectEntity>,
    onMenu: () -> Unit,
    onSelectDestination: (PrimaryDestination) -> Unit,
    onSelectCapability: (CapabilityRoute) -> Unit,
    onRetryHome: () -> Unit,
    onRetryPersonal: () -> Unit,
    onCreateProject: (String, String?) -> Unit,
    showBottomNav: Boolean,
) {
    Scaffold(
        modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = { AppHeader(onMenu, capability?.name ?: destination.name) },
        bottomBar = {
            if (showBottomNav) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    PrimaryNavLens(destination, onSelectDestination)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).testTag("world-content")) {
            if (capability != null) {
                CapabilityBridgeScreen(capability.name)
            } else {
                when (destination) {
                    PrimaryDestination.HOME -> {
                        if (showBottomNav) {
                            BoxWithConstraints(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Phone Home is intentionally a bounded one-screen stage. This keeps
                                // the primary action visible instead of letting non-critical signals
                                // consume the action area on tall-density phone viewports.
                                val stageHeight = maxHeight.coerceAtMost(688.dp)
                                Box(Modifier.fillMaxWidth().height(stageHeight)) {
                                    HomeScreen(
                                        home,
                                        sessionResolved,
                                        onRetryHome,
                                        { onSelectDestination(PrimaryDestination.PERSONAL) },
                                        { onSelectCapability(CapabilityRoute.CHAT) },
                                        { onSelectCapability(CapabilityRoute.PRACTICE) },
                                        { onSelectDestination(PrimaryDestination.PROJECTS) },
                                    )
                                }
                            }
                        } else {
                            HomeScreen(
                                home,
                                sessionResolved,
                                onRetryHome,
                                { onSelectDestination(PrimaryDestination.PERSONAL) },
                                { onSelectCapability(CapabilityRoute.CHAT) },
                                { onSelectCapability(CapabilityRoute.PRACTICE) },
                                { onSelectDestination(PrimaryDestination.PROJECTS) },
                            )
                        }
                    }
                    PrimaryDestination.PERSONAL -> PersonalScreen(personal, sessionResolved, onRetryPersonal)
                    PrimaryDestination.STORE -> TransitionalStoreScreen()
                    PrimaryDestination.PROJECTS -> TransitionalProjectsScreen(projects, onCreateProject)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(onMenu: () -> Unit, routeName: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PressableGlass(
            onMenu,
            Modifier.size(50.dp).testTag("open-capabilities").semantics {
                contentDescription = "Open Veltrix capabilities"
            },
            999.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("≡", color = VeltrixColors.Ink, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "Veltrix Hom",
            color = VeltrixColors.Ink,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
        GlassSurface(Modifier.heightIn(min = 46.dp), 18.dp) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    routeName.lowercase().replaceFirstChar { it.uppercaseChar().toString() },
                    color = VeltrixColors.InkMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("active-route"),
                )
            }
        }
    }
}

@Composable
private fun PrimaryNavLens(selected: PrimaryDestination, onSelect: (PrimaryDestination) -> Unit) {
    val destinations = listOf(
        PrimaryDestination.HOME,
        PrimaryDestination.PERSONAL,
        PrimaryDestination.STORE,
        PrimaryDestination.PROJECTS,
    )
    val policy = rememberVeltrixEffectPolicy()
    GlassSurface(Modifier.fillMaxWidth().height(68.dp), 28.dp, true) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
            val itemWidth = maxWidth / destinations.size.toFloat()
            val index = destinations.indexOf(selected).coerceAtLeast(0)
            val x by animateDpAsState(
                itemWidth * index.toFloat(),
                if (policy.reducedMotion) snap() else spring(dampingRatio = .82f, stiffness = 430f),
                label = "primary-nav-lens",
            )
            Box(
                Modifier
                    .offset(x = x)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(23.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xEFFFFFFF), Color(0xBCEAF2FF), Color(0xC9EEFFF9)),
                        ),
                    )
                    .semantics { contentDescription = "Selected destination lens" },
            )
            Row(Modifier.fillMaxSize()) {
                destinations.forEach { destination ->
                    val active = destination == selected
                    Box(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .selectable(
                                selected = active,
                                role = Role.Tab,
                                onClick = { onSelect(destination) },
                            )
                            .semantics { this.selected = active }
                            .testTag("nav-${destination.name}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(if (active) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(if (active) VeltrixColors.Sky else Color(0xFF9AABC2)),
                            )
                            Text(
                                destination.label(),
                                color = if (active) VeltrixColors.Ink else VeltrixColors.InkMuted,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryRail(
    selected: PrimaryDestination,
    onMenu: () -> Unit,
    onSelect: (PrimaryDestination) -> Unit,
) {
    Column(
        Modifier.fillMaxHeight().width(116.dp).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PressableGlass(onMenu, Modifier.size(52.dp), 999.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("≡", fontWeight = FontWeight.Bold, color = VeltrixColors.Ink)
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryDestination.entries.forEach { destination ->
            val active = selected == destination
            PressableGlass(
                { onSelect(destination) },
                Modifier.fillMaxWidth().heightIn(min = 62.dp).semantics {
                    role = Role.Tab
                    this.selected = active
                },
                22.dp,
                active,
            ) {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(if (active) VeltrixColors.Sky else Color(0xFF9AABC2)),
                    )
                    Text(
                        destination.label(),
                        color = if (active) VeltrixColors.Ink else VeltrixColors.InkMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    onCapability: (CapabilityRoute) -> Unit,
    onDestination: (PrimaryDestination) -> Unit,
) {
    Box(Modifier.fillMaxHeight().widthIn(max = 340.dp).fillMaxWidth(.86f).padding(10.dp)) {
        GlassSurface(Modifier.fillMaxSize(), 34.dp, true) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Text(
                    "Veltrix",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VeltrixColors.Ink,
                    fontWeight = FontWeight.Bold,
                )
                Text("Global capabilities", color = VeltrixColors.InkMuted)
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0x1F4A638A))
                LazyColumn(
                    Modifier.weight(1f).testTag("capability-list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(CapabilityRoute.entries) { route ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .selectable(
                                    selected = false,
                                    role = Role.Button,
                                    onClick = { onCapability(route) },
                                )
                                .padding(horizontal = 12.dp)
                                .testTag("capability-${route.name}"),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                route.name.lowercase().replace('_', ' ')
                                    .replaceFirstChar { it.uppercaseChar().toString() },
                                color = VeltrixColors.Ink,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0x1F4A638A))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PrimaryDestination.HOME, PrimaryDestination.PERSONAL).forEach { destination ->
                        PressableGlass(
                            { onDestination(destination) },
                            Modifier.weight(1f).heightIn(min = 48.dp),
                            18.dp,
                        ) {
                            Box(
                                Modifier.fillMaxSize().padding(10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(destination.label(), color = VeltrixColors.Ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun PrimaryDestination.label() =
    name.lowercase().replaceFirstChar { it.uppercaseChar().toString() }
