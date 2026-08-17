package com.veltrix.hom.vnext

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal enum class RootSecondary(val label: String) {
    CHAT("Chat / History"),
    LIBRARY("Library / Sources"),
    TESTING("Testing"),
    PRACTICE("Practice"),
    QUIZZES("Quizzes"),
    FLASHCARDS("Flashcards"),
    MISTAKES("Mistakes"),
    CALCULATOR("Calculator"),
    TRANSLATE("Translate"),
    NOTIFICATIONS("Notifications"),
    SETTINGS("Settings / Account"),
}

/**
 * Root Reset Stage 70 secondary capability host.
 *
 * There are deliberately no "coming soon" bridges here. Every route is backed by an accepted
 * repository/control contract. Cached values are stripped whenever they are not FRESH so the
 * online-only root reset never presents stale product data as current truth.
 */
@Composable
internal fun RootSecondaryStage70Host(
    item: RootSecondary,
    featureVm: AppViewModel,
    onNavigate: (RootSecondary) -> Unit,
    onSignOut: () -> Unit,
) {
    val chats by featureVm.chats.collectAsStateWithLifecycle()
    val messages by featureVm.messages.collectAsStateWithLifecycle()
    val sources by featureVm.sources.collectAsStateWithLifecycle()
    val streaming by featureVm.streaming.collectAsStateWithLifecycle()
    val streamingText by featureVm.streamingText.collectAsStateWithLifecycle()
    val streamError by featureVm.streamError.collectAsStateWithLifecycle()
    val selectedSources by featureVm.selectedSources.collectAsStateWithLifecycle()
    val citations by featureVm.citations.collectAsStateWithLifecycle()
    val createdConversationId by featureVm.createdConversationId.collectAsStateWithLifecycle()

    val search by featureVm.search.collectAsStateWithLifecycle()
    val assessment by featureVm.assessment.collectAsStateWithLifecycle()
    val attempt by featureVm.attempt.collectAsStateWithLifecycle()
    val assessmentResult by featureVm.assessmentResult.collectAsStateWithLifecycle()

    val practice by featureVm.practice.collectAsStateWithLifecycle()
    val practiceHint by featureVm.practiceHint.collectAsStateWithLifecycle()
    val practiceCheck by featureVm.practiceCheck.collectAsStateWithLifecycle()
    val practiceComplete by featureVm.practiceComplete.collectAsStateWithLifecycle()
    val openedPracticeId by featureVm.openedPracticeId.collectAsStateWithLifecycle()

    val flashcards by featureVm.flashcards.collectAsStateWithLifecycle()
    val mistakes by featureVm.mistakes.collectAsStateWithLifecycle()

    val calculator by featureVm.calculator.collectAsStateWithLifecycle()
    val calculatorHistory by featureVm.calculatorHistory.collectAsStateWithLifecycle()
    val translation by featureVm.translation.collectAsStateWithLifecycle()
    val notificationIntents by featureVm.notificationIntents.collectAsStateWithLifecycle()
    val notificationPreferences by featureVm.notificationPreferences.collectAsStateWithLifecycle()
    val profile by featureVm.profileControls.collectAsStateWithLifecycle()
    val settings by featureVm.settingsControls.collectAsStateWithLifecycle()
    val exportState by featureVm.accountExport.collectAsStateWithLifecycle()
    val feedback by featureVm.mutationFeedback.collectAsStateWithLifecycle()
    val featureSession by featureVm.session.collectAsStateWithLifecycle()
    val sessionResolved by featureVm.sessionResolved.collectAsStateWithLifecycle()

    var conversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var practiceReturnName by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(item) {
        when (item) {
            RootSecondary.CHAT -> {
                featureVm.refreshChats(null)
                featureVm.refreshSources()
            }
            RootSecondary.LIBRARY -> featureVm.refreshSources()
            RootSecondary.TESTING, RootSecondary.QUIZZES -> {
                // Assessment search is user-driven. Clear old detail so each world opens at its
                // honest discovery state rather than leaking another route's detail surface.
                featureVm.clearAssessment()
            }
            RootSecondary.PRACTICE -> Unit
            RootSecondary.FLASHCARDS -> featureVm.refreshFlashcards()
            RootSecondary.MISTAKES -> featureVm.refreshMistakes()
            RootSecondary.CALCULATOR, RootSecondary.TRANSLATE -> Unit
            RootSecondary.NOTIFICATIONS -> featureVm.refreshNotifications()
            RootSecondary.SETTINGS -> featureVm.refreshSettings()
        }
        if (item != RootSecondary.PRACTICE && item != RootSecondary.MISTAKES) {
            practiceReturnName = null
        }
    }

    LaunchedEffect(createdConversationId) {
        createdConversationId?.let { id ->
            conversationId = id
            featureVm.consumeCreatedConversation()
        }
    }

    LaunchedEffect(openedPracticeId) {
        openedPracticeId?.let {
            practiceReturnName = RootSecondary.MISTAKES.name
            onNavigate(RootSecondary.PRACTICE)
            featureVm.consumeOpenedPractice()
        }
    }

    // AppViewModel owns the destructive account deletion contract. When that contract clears its
    // validated session, synchronize the account-first root gate instead of leaving a zombie UI.
    LaunchedEffect(item, sessionResolved, featureSession) {
        if (item == RootSecondary.SETTINGS && sessionResolved && featureSession == null) {
            onSignOut()
        }
    }

    BackHandler(enabled = item == RootSecondary.CHAT && conversationId != null) {
        conversationId = null
    }
    BackHandler(enabled = (item == RootSecondary.TESTING || item == RootSecondary.QUIZZES) && assessment.value != null) {
        featureVm.clearAssessment()
    }
    BackHandler(enabled = item == RootSecondary.PRACTICE && practiceReturnName != null) {
        val target = RootSecondary.entries.firstOrNull { it.name == practiceReturnName }
        practiceReturnName = null
        target?.let(onNavigate)
    }

    Box(Modifier.fillMaxSize().testTag("root-capability-${item.name}")) {
        when (item) {
            RootSecondary.CHAT -> {
                val liveChats = chats.current70()
                val liveMessages = messages.current70()
                val liveSources = sources.current70()
                val projectId = liveChats.value.orEmpty().firstOrNull { it.id == conversationId }?.projectId
                ChatWorldScreen(
                    chats = liveChats,
                    messages = liveMessages,
                    sources = liveSources,
                    conversationId = conversationId,
                    projectId = projectId,
                    streaming = streaming,
                    streamingText = streamingText,
                    streamError = streamError,
                    selectedSources = selectedSources,
                    citations = citations,
                    onRefresh = {
                        featureVm.refreshChats(projectId)
                        featureVm.refreshSources()
                    },
                    onNew = { featureVm.createChat(null) },
                    onOpen = { id ->
                        conversationId = id
                        featureVm.openConversation(id)
                    },
                    onToggleSource = featureVm::toggleSource,
                    onSend = { text -> conversationId?.let { featureVm.sendChat(it, projectId, text) } },
                    onRetry = { messageId -> conversationId?.let { featureVm.retryMessage(it, messageId) } },
                    onRegenerate = { messageId -> conversationId?.let { featureVm.regenerateMessage(it, messageId) } },
                    onLoadCitations = { messageId -> conversationId?.let { featureVm.loadCitations(it, messageId) } },
                )
            }

            RootSecondary.LIBRARY -> LibraryWorldScreen(
                state = sources.current70(),
                onRetry = featureVm::refreshSources,
                onCreateText = featureVm::createTextSource,
                onRetrySource = featureVm::retrySource,
            )

            RootSecondary.TESTING, RootSecondary.QUIZZES -> AssessmentWorldScreen(
                quizMode = item == RootSecondary.QUIZZES,
                searchState = search.current70(),
                detail = assessment.current70(),
                attempt = attempt,
                result = assessmentResult,
                onSearch = { featureVm.search(it, null) },
                onOpen = featureVm::openAssessment,
                onStart = featureVm::startAssessment,
                onAnswer = featureVm::answerAssessment,
                onSubmit = featureVm::submitAssessment,
            )

            RootSecondary.PRACTICE -> PracticeWorldScreen(
                state = practice.current70(),
                hint = practiceHint,
                check = practiceCheck,
                complete = practiceComplete,
                onCreate = { featureVm.createPractice(null, it) },
                onAttempt = featureVm::practiceAttempt,
                onHint = featureVm::practiceHint,
                onCheck = featureVm::practiceCheck,
                onSkip = featureVm::practiceSkip,
                onComplete = featureVm::completePractice,
            )

            RootSecondary.FLASHCARDS -> FlashcardsWorldScreen(
                state = flashcards.current70(),
                onRetry = featureVm::refreshFlashcards,
                onRate = featureVm::reviewFlashcard,
            )

            RootSecondary.MISTAKES -> MistakesWorldScreen(
                state = mistakes.current70(),
                onRetry = featureVm::refreshMistakes,
                onResolve = featureVm::resolveMistake,
                onPractice = featureVm::practiceFromMistake,
                onFlashcard = featureVm::flashcardFromMistake,
            )

            RootSecondary.CALCULATOR -> CalculatorWorldScreen(
                state = calculator,
                history = calculatorHistory,
                onCalculate = featureVm::calculate,
            )

            RootSecondary.TRANSLATE -> TranslateWorldScreen(
                state = translation,
                projectId = null,
                onTranslate = featureVm::translate,
            )

            RootSecondary.NOTIFICATIONS -> NotificationsWorldScreen(
                intents = notificationIntents.current70(),
                preferences = notificationPreferences.current70(),
                onRefresh = featureVm::refreshNotifications,
                onToggle = featureVm::updateNotificationPreference,
            )

            RootSecondary.SETTINGS -> Column(Modifier.fillMaxSize().testTag("settings-stage70")) {
                Box(Modifier.weight(1f)) {
                    SettingsWorldScreen(
                        profile = profile.current70(),
                        settings = settings.current70(),
                        exportState = exportState.current70(),
                        feedback = feedback,
                        onRefresh = featureVm::refreshSettings,
                        onSaveProfile = featureVm::saveProfile,
                        onSaveSetting = featureVm::saveSetting,
                        onExport = featureVm::prepareAccountExport,
                        onDelete = featureVm::deleteAccount,
                        initialSection = "Account",
                    )
                }
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .heightIn(min = 50.dp)
                        .testTag("settings-sign-out"),
                ) {
                    Text("Sign out")
                }
            }
        }
    }
}

private fun <T> RepositoryState<T>.current70(): RepositoryState<T> {
    if (freshness == DataFreshness.FRESH) return this
    return copy(
        value = null,
        loading = loading && value == null,
        errorCode = errorCode ?: if (loading) null else "SERVICE_UNAVAILABLE",
    )
}
