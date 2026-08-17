package com.veltrix.hom.vnext

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun RootResetApp(root: RootResetViewModel = viewModel()) {
    val gate by root.gate.collectAsStateWithLifecycle()
    val auth by root.auth.collectAsStateWithLifecycle()
    val session by root.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var autoGoogleAttempted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(gate.kind, session?.accountId) {
        if (
            gate.kind == ProductGateKind.AUTH &&
            !autoGoogleAttempted &&
            !root.wasExplicitlySignedOut() &&
            BuildConfig.VELTRIX_GOOGLE_SERVER_CLIENT_ID.isNotBlank()
        ) {
            autoGoogleAttempted = true
            runAuthorizedGoogleCredentialFlow(
                context = context,
                onCredential = root::completeGoogleSignIn,
                onFailure = { },
            )
        }
    }

    AnimatedContent(targetState = gate.kind, label = "root-product-gate") { kind ->
        when (kind) {
            ProductGateKind.CHECKING -> RootResetBootstrapGate()
            ProductGateKind.AUTH -> RootResetAuthGateway(auth, false, root::setAuthMode, root::signIn, root::createAccount, root::completeGoogleSignIn, root::reportAuthError)
            ProductGateKind.SESSION_EXPIRED -> RootResetAuthGateway(auth, true, root::setAuthMode, root::signIn, root::createAccount, root::completeGoogleSignIn, root::reportAuthError)
            ProductGateKind.CONNECTION -> RootResetConnectionGate(gate.connectionIssue, gate.message, root::retryConnection)
            ProductGateKind.PRODUCT -> {
                val accountId = session?.accountId
                if (accountId == null) RootResetBootstrapGate()
                else RootAuthenticatedShell(root, viewModel(key = "features:$accountId"))
            }
        }
    }
}

@Composable
private fun RootAuthenticatedShell(root: RootResetViewModel, featureVm: AppViewModel) {
    val home by root.home.collectAsStateWithLifecycle()
    val personal by root.personal.collectAsStateWithLifecycle()
    val projects by root.projects.collectAsStateWithLifecycle()
    val store by root.store.collectAsStateWithLifecycle()
    val inventory by root.inventory.collectAsStateWithLifecycle()
    val avatars by root.avatars.collectAsStateWithLifecycle()
    val map by root.map.collectAsStateWithLifecycle()
    val game by root.game.collectAsStateWithLifecycle()
    val projectWorkspace by featureVm.workspace.collectAsStateWithLifecycle()
    val featureStore by featureVm.store.collectAsStateWithLifecycle()
    val featureInventory by featureVm.inventory.collectAsStateWithLifecycle()
    val featureAvatars by featureVm.avatars.collectAsStateWithLifecycle()
    val featureGame by featureVm.gameProfile.collectAsStateWithLifecycle()
    val storeFeedback by featureVm.mutationFeedback.collectAsStateWithLifecycle()

    var worldName by rememberSaveable { mutableStateOf(VeltrixWorld.HOME.name) }
    var secondaryName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSecondaryName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingWorldName by rememberSaveable { mutableStateOf<String?>(null) }

    val world = VeltrixWorld.entries.firstOrNull { it.name == worldName } ?: VeltrixWorld.HOME
    val secondary = secondaryName?.let { name -> RootSecondary.entries.firstOrNull { it.name == name } }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val continuity = rememberWorldContinuityCoordinator()
    val activeProject = projects.sortedWith(compareByDescending<ProjectCardModel> { it.priority }.thenByDescending { it.lastActiveAt }).firstOrNull()
    val projectWorkspaceOpen = world == VeltrixWorld.PROJECTS && (projectWorkspace.value != null || projectWorkspace.loading)
    val storeUi = featureStore.value?.takeIf { featureStore.freshness == DataFreshness.FRESH } ?: store
    val inventoryUi = featureInventory.value?.takeIf { featureInventory.freshness == DataFreshness.FRESH } ?: inventory
    val avatarsUi = featureAvatars.value?.takeIf { featureAvatars.freshness == DataFreshness.FRESH } ?: avatars
    val gameUi = featureGame.value?.takeIf { featureGame.freshness == DataFreshness.FRESH } ?: game
    val signOut: () -> Unit = { root.signOut { scope.launch { clearGoogleCredentialState(context) } } }

    LaunchedEffect(world) { continuity.enter(world) }
    LaunchedEffect(home?.avatarId, personal?.avatarId) { continuity.avatar(home?.avatarId ?: personal?.avatarId) }
    LaunchedEffect(featureGame.value?.revision) {
        if (featureGame.freshness == DataFreshness.FRESH && featureGame.value != null) root.refreshWorlds()
    }
    LaunchedEffect(secondaryName, secondary) {
        if (secondaryName != null && secondary == null) secondaryName = null
    }
    LaunchedEffect(drawer.currentValue, pendingSecondaryName, pendingWorldName) {
        if (drawer.currentValue != DrawerValue.Closed) return@LaunchedEffect
        pendingSecondaryName?.let { secondaryName = it; pendingSecondaryName = null }
        pendingWorldName?.let { worldName = it; secondaryName = null; pendingWorldName = null }
    }

    BackHandler(enabled = drawer.isOpen || secondary != null || projectWorkspaceOpen || world != VeltrixWorld.HOME) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            secondary != null -> { secondaryName = null; root.refreshWorlds() }
            projectWorkspaceOpen -> featureVm.clearWorkspace()
            else -> worldName = VeltrixWorld.HOME.name
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            RootSidebar(
                selected = secondaryName,
                onSecondary = { name -> pendingSecondaryName = name; pendingWorldName = null; scope.launch { drawer.close() } },
                onWorld = { target -> pendingWorldName = target.name; pendingSecondaryName = null; root.refreshWorlds(); scope.launch { drawer.close() } },
            )
        },
        scrimColor = Color(0x330F172A),
    ) {
        // Resting scene stays optically rich but static. Interaction motion remains in the moving
        // primary-world lens and direct-manipulation surfaces. This avoids a permanent full-screen
        // redraw loop on software-rendered/low-end paths while preserving the same visual frame.
        VeltrixKineticWorld(world = world, reducedMotion = true) {
            Box(Modifier.fillMaxSize()) {
                if (secondary == null) {
                    AnimatedContent(targetState = world, label = "primary-world") { target ->
                        when (target) {
                            VeltrixWorld.HOME -> RootHomeWorldStage40(
                                model = home,
                                game = game,
                                activeProject = activeProject,
                                onMenu = { scope.launch { drawer.open() } },
                                onNextMove = { route ->
                                    when (route) {
                                        RootHomeRoute.PROJECTS -> { continuity.project(activeProject?.id); worldName = VeltrixWorld.PROJECTS.name; secondaryName = null }
                                        RootHomeRoute.PERSONAL -> { worldName = VeltrixWorld.PERSONAL.name; secondaryName = null }
                                        RootHomeRoute.MISTAKES -> secondaryName = RootSecondary.MISTAKES.name
                                        RootHomeRoute.LIBRARY -> secondaryName = RootSecondary.LIBRARY.name
                                        RootHomeRoute.CHAT -> secondaryName = RootSecondary.CHAT.name
                                    }
                                },
                            )
                            VeltrixWorld.PERSONAL -> RootPersonalWorldStage50(personal, map, game, onMenu = { scope.launch { drawer.open() } })
                            VeltrixWorld.STORE -> RootStoreWorldStage70(
                                store = storeUi,
                                inventory = inventoryUi,
                                avatars = avatarsUi,
                                game = gameUi,
                                feedback = storeFeedback,
                                onMenu = { scope.launch { drawer.open() } },
                                onPurchase = featureVm::purchase,
                                onEquipAvatar = featureVm::equipAvatar,
                            )
                            VeltrixWorld.PROJECTS -> RootProjectsWorldStage60(
                                projects = projects,
                                workspace = projectWorkspace,
                                onMenu = { scope.launch { drawer.open() } },
                                onOpenProject = { id -> continuity.project(id); featureVm.openProject(id) },
                                onCloseProject = featureVm::clearWorkspace,
                            )
                        }
                    }
                    RootKineticBottomBar(
                        selectedWorld = world,
                        onSelected = { target -> worldName = target.name; secondaryName = null },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    RootCapabilityFrame(secondary.label, onMenu = { scope.launch { drawer.open() } }) {
                        RootSecondaryStage70Host(
                            item = secondary,
                            featureVm = featureVm,
                            onNavigate = { target -> secondaryName = target.name },
                            onSignOut = signOut,
                            onAccountTruthMayHaveChanged = root::refreshWorlds,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RootSidebar(selected: String?, onSecondary: (String) -> Unit, onWorld: (VeltrixWorld) -> Unit) {
    ModalDrawerSheet(modifier = Modifier.width(310.dp).testTag("root-sidebar"), drawerContainerColor = Color(0xFFF9FBFF)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("root-sidebar-list"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item(key = "brand") { Text("VELTRIX", color = KineticColor.Ink, fontWeight = FontWeight.Black, modifier = Modifier.padding(12.dp)) }
            item(key = "worlds-header") { Text("WORLDS", color = KineticColor.Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
            items(VeltrixWorld.entries, key = { it.name }) { target ->
                NavigationDrawerItem(
                    label = { Text(target.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    selected = false,
                    onClick = { onWorld(target) },
                    modifier = Modifier.testTag("drawer-world-${target.name}"),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                )
            }
            item(key = "tools-gap") { Spacer(Modifier.height(10.dp)) }
            item(key = "tools-header") { Text("TOOLS", color = KineticColor.Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
            items(RootSecondary.entries, key = { it.name }) { item ->
                NavigationDrawerItem(
                    label = { Text(item.label) },
                    selected = selected == item.name,
                    onClick = { onSecondary(item.name) },
                    modifier = Modifier.testTag("drawer-secondary-${item.name}"),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun RootKineticBottomBar(
    selectedWorld: VeltrixWorld,
    onSelected: (VeltrixWorld) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemWidth = 76.dp
    val index = VeltrixWorld.entries.indexOf(selectedWorld).coerceAtLeast(0)
    val policy = rememberVeltrixEffectPolicy()
    val lensX by animateDpAsState(
        targetValue = itemWidth * index,
        animationSpec = if (policy.reducedMotion) androidx.compose.animation.core.snap() else spring(dampingRatio = .78f, stiffness = 420f),
        label = "world-lens-position",
    )
    val accent = when (selectedWorld) {
        VeltrixWorld.HOME -> KineticColor.Sky
        VeltrixWorld.PERSONAL -> KineticColor.Violet
        VeltrixWorld.STORE -> KineticColor.Ember
        VeltrixWorld.PROJECTS -> KineticColor.Mint
    }

    KineticGlass(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
            .height(66.dp)
            .width(itemWidth * 4f)
            .testTag("primary-worlds")
            .semantics { contentDescription = "Primary worlds" },
        radius = 26.dp,
        strong = false,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Exactly one moving optical lens. The tray itself stays calm; selection owns the
            // thicker environment-responsive material and travels with spring continuity.
            KineticGlass(
                modifier = Modifier
                    .padding(start = lensX + 5.dp, top = 5.dp)
                    .size(itemWidth - 10.dp, 56.dp)
                    .testTag("world-lens"),
                radius = 21.dp,
                strong = true,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .padding(bottom = 7.dp)
                            .size(18.dp, 2.dp)
                            .clip(RoundedCornerShape(1.dp))
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawRoundRect(accent.copy(alpha = .76f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)) }
                    }
                }
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                VeltrixWorld.entries.forEach { item ->
                    val selected = item == selectedWorld
                    val itemAccent = when (item) {
                        VeltrixWorld.HOME -> KineticColor.Sky
                        VeltrixWorld.PERSONAL -> KineticColor.Violet
                        VeltrixWorld.STORE -> KineticColor.Ember
                        VeltrixWorld.PROJECTS -> KineticColor.Mint
                    }
                    Column(
                        Modifier
                            .width(itemWidth)
                            .fillMaxSize()
                            .clickable { onSelected(item) }
                            .testTag("world-${item.name}")
                            .semantics { this.selected = selected; role = Role.Tab },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                val c = if (selected) itemAccent else KineticColor.Muted.copy(alpha = .64f)
                                when (item) {
                                    VeltrixWorld.HOME -> {
                                        drawCircle(c.copy(alpha = if (selected) .18f else .08f), radius = size.minDimension * .47f)
                                        drawCircle(c, radius = size.minDimension * .18f)
                                    }
                                    VeltrixWorld.PERSONAL -> {
                                        drawCircle(c, radius = size.minDimension * .24f, center = androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .38f))
                                        drawArc(c, 205f, 130f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(size.minDimension * .13f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                                    }
                                    VeltrixWorld.STORE -> {
                                        drawCircle(c, radius = size.minDimension * .26f)
                                        drawCircle(Color.White.copy(alpha = if (selected) .78f else .42f), radius = size.minDimension * .10f)
                                    }
                                    VeltrixWorld.PROJECTS -> {
                                        drawRoundRect(c, topLeft = androidx.compose.ui.geometry.Offset(size.width * .13f, size.height * .27f), size = androidx.compose.ui.geometry.Size(size.width * .74f, size.height * .57f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .16f))
                                        drawRoundRect(c.copy(alpha = .62f), topLeft = androidx.compose.ui.geometry.Offset(size.width * .26f, size.height * .14f), size = androidx.compose.ui.geometry.Size(size.width * .46f, size.height * .18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .09f))
                                    }
                                }
                            }
                        }
                        Text(
                            item.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (selected) KineticColor.Ink else KineticColor.Muted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RootCapabilityFrame(title: String, onMenu: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxSize().padding(top = 62.dp)) { content() }
        KineticGlass(
            Modifier.padding(start = 14.dp, top = 8.dp).height(46.dp).clip(RoundedCornerShape(23.dp)).clickable(onClick = onMenu).testTag("root-menu"),
            radius = 23.dp,
        ) {
            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text("≡  $title", color = KineticColor.Ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
