package com.veltrix.hom.vnext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Root Reset Stage 70: real Store, Chat and retained global capabilities. */
@Composable
fun RootStoreWorldStage70(featureVm: AppViewModel, onMenu: () -> Unit) {
    val store by featureVm.store.collectAsStateWithLifecycle()
    val inventory by featureVm.inventory.collectAsStateWithLifecycle()
    val avatars by featureVm.avatars.collectAsStateWithLifecycle()
    val profile by featureVm.gameProfile.collectAsStateWithLifecycle()
    val feedback by featureVm.mutationFeedback.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { featureVm.refreshStore() }

    RootStage70Frame(
        title = "Store",
        context = "PREVIEW · OWN · EQUIP",
        onMenu = onMenu,
        modifier = Modifier.testTag("store-stage70"),
    ) {
        Box(Modifier.fillMaxSize().padding(bottom = 82.dp)) {
            StoreWorldScreen(
                store = store,
                inventory = inventory,
                avatars = avatars,
                profile = profile,
                feedback = feedback,
                onRetry = featureVm::refreshStore,
                onPurchase = featureVm::purchase,
                onEquip = featureVm::equipAvatar,
            )
        }
    }
}

@Composable
fun RootSecondaryHostStage70(
    name: String,
    featureVm: AppViewModel,
    projectId: String?,
    onMenu: () -> Unit,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val title = stage70Title(name)
    RootStage70Frame(
        title = title,
        context = if (projectId == null) "GLOBAL CAPABILITY" else "PROJECT CONTEXT",
        onMenu = onMenu,
        onSignOut = if (name == "SETTINGS") onSignOut else null,
        modifier = Modifier.testTag("secondary-stage70-$name"),
    ) {
        when (name) {
            "CHAT" -> Stage70Chat(featureVm, projectId)
            "LIBRARY" -> Stage70Library(featureVm)
            "TESTING" -> Stage70Assessment(featureVm, projectId, quizMode = false)
            "PRACTICE" -> Stage70Practice(featureVm, projectId)
            "QUIZZES" -> Stage70Assessment(featureVm, projectId, quizMode = true)
            "FLASHCARDS" -> Stage70Flashcards(featureVm)
            "MISTAKES" -> Stage70Mistakes(featureVm, onNavigate)
            "CALCULATOR" -> {
                val state by featureVm.calculator.collectAsStateWithLifecycle()
                val history by featureVm.calculatorHistory.collectAsStateWithLifecycle()
                CalculatorWorldScreen(state, history, featureVm::calculate)
            }
            "TRANSLATE" -> {
                val state by featureVm.translation.collectAsStateWithLifecycle()
                TranslateWorldScreen(state, projectId, featureVm::translate)
            }
            "NOTIFICATIONS" -> Stage70Notifications(featureVm)
            "SETTINGS" -> Stage70Settings(featureVm)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This capability is unavailable in the current frontend contract.", color = KineticColor.Muted)
            }
        }
    }
}

@Composable
private fun Stage70Chat(featureVm: AppViewModel, projectId: String?) {
    val chats by featureVm.chats.collectAsStateWithLifecycle()
    val messages by featureVm.messages.collectAsStateWithLifecycle()
    val sources by featureVm.sources.collectAsStateWithLifecycle()
    val streaming by featureVm.streaming.collectAsStateWithLifecycle()
    val streamingText by featureVm.streamingText.collectAsStateWithLifecycle()
    val streamError by featureVm.streamError.collectAsStateWithLifecycle()
    val selectedSources by featureVm.selectedSources.collectAsStateWithLifecycle()
    val citations by featureVm.citations.collectAsStateWithLifecycle()
    val createdConversationId by featureVm.createdConversationId.collectAsStateWithLifecycle()
    var conversationId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) {
        conversationId = null
        featureVm.refreshChats(projectId)
        featureVm.refreshSources()
    }
    LaunchedEffect(createdConversationId) {
        createdConversationId?.let {
            conversationId = it
            featureVm.consumeCreatedConversation()
        }
    }

    ChatWorldScreen(
        chats = chats,
        messages = messages,
        sources = sources,
        conversationId = conversationId,
        projectId = projectId,
        streaming = streaming,
        streamingText = streamingText,
        streamError = streamError,
        selectedSources = selectedSources,
        citations = citations,
        onRefresh = { featureVm.refreshChats(projectId); featureVm.refreshSources() },
        onNew = { featureVm.createChat(projectId) },
        onOpen = { id -> conversationId = id; featureVm.openConversation(id) },
        onToggleSource = featureVm::toggleSource,
        onSend = { text -> conversationId?.let { featureVm.sendChat(it, projectId, text) } },
        onRetry = { messageId -> conversationId?.let { featureVm.retryMessage(it, messageId) } },
        onRegenerate = { messageId -> conversationId?.let { featureVm.regenerateMessage(it, messageId) } },
        onLoadCitations = { messageId -> conversationId?.let { featureVm.loadCitations(it, messageId) } },
    )
}

@Composable
private fun Stage70Library(featureVm: AppViewModel) {
    val state by featureVm.sources.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { featureVm.refreshSources() }
    LibraryWorldScreen(
        state = state,
        onRetry = featureVm::refreshSources,
        onCreateText = featureVm::createTextSource,
        onRetrySource = featureVm::retrySource,
    )
}

@Composable
private fun Stage70Assessment(featureVm: AppViewModel, projectId: String?, quizMode: Boolean) {
    val search by featureVm.search.collectAsStateWithLifecycle()
    val detail by featureVm.assessment.collectAsStateWithLifecycle()
    val attempt by featureVm.attempt.collectAsStateWithLifecycle()
    val result by featureVm.assessmentResult.collectAsStateWithLifecycle()
    AssessmentWorldScreen(
        quizMode = quizMode,
        searchState = search,
        detail = detail,
        attempt = attempt,
        result = result,
        onSearch = { featureVm.search(it, projectId) },
        onOpen = featureVm::openAssessment,
        onStart = featureVm::startAssessment,
        onAnswer = featureVm::answerAssessment,
        onSubmit = featureVm::submitAssessment,
    )
}

@Composable
private fun Stage70Practice(featureVm: AppViewModel, projectId: String?) {
    val state by featureVm.practice.collectAsStateWithLifecycle()
    val hint by featureVm.practiceHint.collectAsStateWithLifecycle()
    val check by featureVm.practiceCheck.collectAsStateWithLifecycle()
    val complete by featureVm.practiceComplete.collectAsStateWithLifecycle()
    PracticeWorldScreen(
        state = state,
        hint = hint,
        check = check,
        complete = complete,
        onCreate = { topic -> featureVm.createPractice(projectId, topic) },
        onAttempt = featureVm::practiceAttempt,
        onHint = featureVm::practiceHint,
        onCheck = featureVm::practiceCheck,
        onSkip = featureVm::practiceSkip,
        onComplete = featureVm::completePractice,
    )
}

@Composable
private fun Stage70Flashcards(featureVm: AppViewModel) {
    val state by featureVm.flashcards.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { featureVm.refreshFlashcards() }
    FlashcardsWorldScreen(state, featureVm::refreshFlashcards, featureVm::reviewFlashcard)
}

@Composable
private fun Stage70Mistakes(featureVm: AppViewModel, onNavigate: (String) -> Unit) {
    val state by featureVm.mistakes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { featureVm.refreshMistakes() }
    MistakesWorldScreen(
        state = state,
        onRetry = featureVm::refreshMistakes,
        onResolve = featureVm::resolveMistake,
        onPractice = { id -> featureVm.practiceFromMistake(id); onNavigate("PRACTICE") },
        onFlashcard = featureVm::flashcardFromMistake,
    )
}

@Composable
private fun Stage70Notifications(featureVm: AppViewModel) {
    val intents by featureVm.notificationIntents.collectAsStateWithLifecycle()
    val prefs by featureVm.notificationPreferences.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { featureVm.refreshNotifications() }
    NotificationsWorldScreen(intents, prefs, featureVm::refreshNotifications, featureVm::updateNotificationPreference)
}

@Composable
private fun Stage70Settings(featureVm: AppViewModel) {
    val profile by featureVm.profileControls.collectAsStateWithLifecycle()
    val settings by featureVm.settingsControls.collectAsStateWithLifecycle()
    val export by featureVm.accountExport.collectAsStateWithLifecycle()
    val feedback by featureVm.mutationFeedback.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { featureVm.refreshSettings() }
    SettingsWorldScreen(
        profile = profile,
        settings = settings,
        exportState = export,
        feedback = feedback,
        onRefresh = featureVm::refreshSettings,
        onSaveProfile = featureVm::saveProfile,
        onSaveSetting = featureVm::saveSetting,
        onExport = featureVm::prepareAccountExport,
        onDelete = featureVm::deleteAccount,
    )
}

@Composable
private fun RootStage70Frame(
    title: String,
    context: String,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    onSignOut: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KineticGlass(radius = 20.dp, strong = true) {
                Text(
                    "≡",
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 11.dp).testTag("root-menu-stage70"),
                    color = KineticColor.Ink,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = KineticColor.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(context, color = KineticColor.Muted, style = MaterialTheme.typography.labelSmall)
            }
            if (onSignOut != null) {
                OutlinedButton(onClick = onSignOut, modifier = Modifier.heightIn(min = 46.dp).testTag("sign-out-stage70")) {
                    Text("Sign out")
                }
            }
        }
        Box(Modifier.fillMaxSize().navigationBarsPadding()) { content() }
    }
    LaunchedEffect(onMenu) { /* keeps callback stable for semantics owner */ }
}

private fun stage70Title(name: String): String = when (name) {
    "CHAT" -> "Chat"
    "LIBRARY" -> "Library"
    "TESTING" -> "Testing"
    "PRACTICE" -> "Practice"
    "QUIZZES" -> "Quizzes"
    "FLASHCARDS" -> "Flashcards"
    "MISTAKES" -> "Mistakes"
    "CALCULATOR" -> "Calculator"
    "TRANSLATE" -> "Translate"
    "NOTIFICATIONS" -> "Notifications"
    "SETTINGS" -> "Settings"
    else -> "Veltrix"
}
