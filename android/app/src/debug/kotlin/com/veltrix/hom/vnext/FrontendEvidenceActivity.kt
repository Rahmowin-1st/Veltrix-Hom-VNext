package com.veltrix.hom.vnext

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Debug-only deterministic presentation harness for screenshot/motion proof. */
class FrontendEvidenceActivity : ComponentActivity() {
    private var scenario by mutableStateOf("HOME_FOCUS")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scenario = intent.getStringExtra("scenario") ?: "HOME_FOCUS"
        setContent {
            VeltrixTheme {
                VeltrixWorldBackground {
                    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        when {
                            scenario.startsWith("HOME") -> Part2HomeScreen(homeEvidenceState(scenario), gameEvidenceState(), projectListEvidenceState(scenario), true, {}, {}, {}, {}, {})
                            scenario.startsWith("PERSONAL") -> Part2PersonalScreen(personalEvidenceState(scenario), gameEvidenceState(), mapEvidenceState(scenario), true, {}, {}, { _, _ -> })
                            scenario.startsWith("PROJECTS") -> ProjectsEvidence(scenario)
                            scenario.startsWith("CHAT") -> ChatEvidence(scenario)
                            scenario.startsWith("LIBRARY") -> LibraryEvidence(scenario)
                            scenario.startsWith("TESTING") -> AssessmentEvidence(false, scenario)
                            scenario.startsWith("QUIZ") -> AssessmentEvidence(true, scenario)
                            scenario.startsWith("PRACTICE") -> PracticeEvidence(scenario)
                            scenario.startsWith("FLASHCARD") -> FlashcardEvidence()
                            scenario.startsWith("MISTAKES") -> MistakesEvidence(scenario)
                            scenario.startsWith("STORE") -> StoreEvidence(scenario)
                            scenario.startsWith("SEARCH") -> SearchEvidence(scenario)
                            scenario.startsWith("HISTORY") -> HistoryEvidence(scenario)
                            else -> Part2HomeScreen(homeEvidenceState("HOME_FOCUS"), gameEvidenceState(), projectListEvidenceState("HOME_FOCUS"), true, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scenario = intent.getStringExtra("scenario") ?: "HOME_FOCUS"
    }
}

private fun homeEvidenceState(scenario: String): RepositoryState<HomeFinalModel> {
    if (scenario == "HOME_ERROR") return RepositoryState(null, DataFreshness.FRESH, errorCode = "HTTP_503", retryable = true)
    val sparse = scenario == "HOME_SPARSE"
    val unlocked = scenario == "HOME_UNLOCKED"
    val model = HomeFinalModel(
        "evidence-account", if (sparse) "New learner" else "Alex", if (sparse) "" else "avatar-noob-default",
        if (sparse) 1 else 12, if (sparse) 0 else 14_250, if (sparse) 0 else 420, if (sparse) 100 else 1_000,
        if (sparse) 100 else 580, if (sparse) 0 else 230, if (sparse) 0 else 9, if (sparse) 0 else 7,
        if (sparse) null else "Retest Newton's second law", if (sparse) "COLD" else "SUFFICIENT",
        if (unlocked) "ACTIVE" else "LOCKED", if (unlocked) "fixture-current-unit" else null,
        if (unlocked) "fixture-season" else null, if (sparse) 0 else 2,
        if (sparse) emptyList() else listOf("WEAK_REVIEW", "PROJECT_FOCUS"), if (sparse) emptyList() else listOf("RETEST", "XP_REMAINING"), 18,
    )
    return RepositoryState(model, if (scenario == "HOME_OFFLINE") DataFreshness.OFFLINE else DataFreshness.FRESH, serverRevision = model.revision)
}

private fun personalEvidenceState(scenario: String): RepositoryState<PersonalFinalModel> {
    if (scenario == "PERSONAL_ERROR") return RepositoryState(null, DataFreshness.FRESH, errorCode = "HTTP_409", retryable = true)
    val sparse = scenario == "PERSONAL_SPARSE"
    val unlocked = scenario == "PERSONAL_UNLOCKED" || scenario == "PERSONAL_MAP_ACTIVE"
    val model = PersonalFinalModel(
        "evidence-account", if (sparse) "New learner" else "Alex", if (sparse) "" else "avatar-noob-default",
        if (sparse) 1 else 12, if (sparse) 0 else 14_250, if (sparse) 0 else 230,
        if (sparse) "COLD" else "SUFFICIENT",
        if (sparse) emptyList() else listOf("Algebra", "Pattern recognition", "Worked examples"),
        if (sparse) emptyList() else listOf("Mechanics", "Unit conversion"),
        if (sparse) emptyList() else listOf("Physics", "AI"),
        if (sparse) emptyList() else listOf("Improve physics", "Finish mechanics review"),
        if (unlocked) "ACTIVE" else "LOCKED", if (unlocked) "fixture-season" else null,
        if (sparse) 0 else 3, if (sparse) 0 else 2, if (sparse) 0 else 7, 19,
    )
    return RepositoryState(model, if (scenario == "PERSONAL_OFFLINE") DataFreshness.OFFLINE else DataFreshness.FRESH, serverRevision = model.revision)
}

private fun gameEvidenceState() = RepositoryState(
    GameProfileUiModel(12, 14_250, 420, 1_000, 230, 7, "avatar-noob-default", "avatar/core", "CORE", 8, "ACTIVE", 20),
    DataFreshness.FRESH,
)

private fun projectListEvidenceState(scenario: String) = RepositoryState(
    if (scenario.contains("SPARSE")) emptyList() else listOf(
        ProjectCardModel("project-motion", "Motion Studio", "Understand mechanics deeply", "ACTIVE", 5, 8, "now", "now"),
        ProjectCardModel("project-ai", "AI Research", "Build a trusted source set", "ACTIVE", 3, 5, "now", "now"),
    ),
    if (scenario.contains("OFFLINE")) DataFreshness.OFFLINE else DataFreshness.FRESH,
)

private fun mapEvidenceState(scenario: String): RepositoryState<PersonalMapUiModel> {
    val active = scenario == "PERSONAL_UNLOCKED" || scenario == "PERSONAL_MAP_ACTIVE"
    val map = PersonalMapUiModel(
        if (active) "map-evidence" else null, "map-definition", 2, if (active) "ACTIVE" else "LOCKED",
        active, 8, "SUFFICIENT", active, active, if (active) "UNLOCKED" else "LOCKED",
        if (!active) emptyList() else listOf(
            MapUnitUiModel("u1", 1, "FOUNDATIONS", "Foundations", "COMPLETED", 10, 10, 2),
            MapUnitUiModel("u2", 2, "MOTION", "Motion", "ACTIVE", 4, 10, 3),
            MapUnitUiModel("u3", 3, "ENERGY", "Energy", "AVAILABLE", 0, 10, 1),
            MapUnitUiModel("u4", 4, "WAVES", "Waves", "LOCKED", 0, 10, 1),
        ), 9,
    )
    return RepositoryState(map, if (scenario == "PERSONAL_OFFLINE") DataFreshness.OFFLINE else DataFreshness.FRESH)
}

@androidx.compose.runtime.Composable
private fun ProjectsEvidence(scenario: String) {
    val empty = scenario == "PROJECTS_EMPTY"
    val project = ProjectCardModel("project-motion", "Motion Studio", "Understand mechanics deeply", "ACTIVE", 5, 8, "now", "now")
    val workspace = if (scenario == "PROJECTS_SPACE") ProjectWorkspaceUiModel(
        project,
        listOf(ProjectGoalModel("g1", "Master Newton's laws", "Use worked examples", "ACTIVE", 5, 3), ProjectGoalModel("g2", "Retest mechanics", null, "PAUSED", 2, 2)),
        listOf(ConversationUiModel("c1", project.id, "PROJECT", "Newton discussion", "GUIDED", true, true, true, false, 4, "now")),
        4, 2, 1, 8, 3, 2, 12, 21, "Answer with source-grounded worked examples.", listOf("PRACTICE", "REVIEW_SOURCE"), "Newton's laws", "GUIDED", 8,
    ) else null
    ProjectsWorldScreen(
        RepositoryState(if (empty) emptyList() else listOf(project), if (scenario == "PROJECTS_OFFLINE") DataFreshness.OFFLINE else DataFreshness.FRESH),
        emptyList(), RepositoryState(workspace, DataFreshness.FRESH), workspace?.project?.id, {}, { _, _ -> }, {}, {}, {},
    )
}

@androidx.compose.runtime.Composable
private fun ChatEvidence(scenario: String) {
    val conversation = ConversationUiModel("c1", "project-motion", "PROJECT", "Mechanics", "GUIDED", true, true, true, false, 4, "now")
    val messages = if (scenario == "CHAT_EMPTY") emptyList() else listOf(
        ChatMessageUiModel("m1", "c1", "USER", "COMPLETED", "Explain why force changes acceleration.", true, 2, "now"),
        ChatMessageUiModel("m2", "c1", "ASSISTANT", if (scenario == "CHAT_ERROR") "FAILED" else "COMPLETED", "With mass held constant, greater net force produces greater acceleration. The relationship is F = ma.", true, 3, "now"),
    )
    val source = SourceUiModel("s1", "Mechanics Notes", "TEXT", "text/plain", "READY", 100, 2)
    val citationMap = if (scenario == "CHAT_CITATION") mapOf("m2" to listOf(CitationUiModel(1, "s1", "Newton's second law: F = ma", 2, "Dynamics", .98))) else emptyMap()
    ChatWorldScreen(
        RepositoryState(listOf(conversation), if (scenario == "CHAT_OFFLINE") DataFreshness.OFFLINE else DataFreshness.FRESH),
        RepositoryState(messages, if (scenario == "CHAT_OFFLINE") DataFreshness.OFFLINE else DataFreshness.FRESH),
        RepositoryState(listOf(source), DataFreshness.FRESH), if (scenario == "CHAT_EMPTY") null else "c1", "project-motion",
        scenario == "CHAT_STREAMING", if (scenario == "CHAT_STREAMING") "Building a grounded explanation from your selected source…" else "",
        if (scenario == "CHAT_ERROR") "HTTP_503" else null, setOf("s1"), citationMap, {}, {}, {}, {}, {}, {}, {}, {},
    )
}

@androidx.compose.runtime.Composable
private fun LibraryEvidence(scenario: String) {
    val source = when (scenario) {
        "LIBRARY_FAILED" -> SourceUiModel("s1", "Mechanics PDF", "FILE", "application/pdf", "FAILED", 42, 4)
        "LIBRARY_PROCESSING" -> SourceUiModel("s1", "Mechanics PDF", "FILE", "application/pdf", "PROCESSING", 64, 3)
        else -> SourceUiModel("s1", "Mechanics Notes", "TEXT", "text/plain", "READY", 100, 5)
    }
    LibraryWorldScreen(RepositoryState(listOf(source), DataFreshness.FRESH), {}, { _, _ -> }, {})
}

private fun assessmentEvidenceDetail() = AssessmentDetailUiModel(
    "a1", "QUIZ", "Motion checkpoint", "project-motion", "ACTIVE", 2, 4,
    listOf(
        AssessmentQuestionUiModel("q1", 0, "Which relation describes Newton's second law?", "MULTIPLE_CHOICE", listOf("F = ma", "E = mc²", "p = mv")),
        AssessmentQuestionUiModel("q2", 1, "Give the SI unit of force.", "SHORT_ANSWER", emptyList()),
    ),
)

@androidx.compose.runtime.Composable
private fun AssessmentEvidence(quiz: Boolean, scenario: String) {
    val detail = assessmentEvidenceDetail()
    val attempt = if (scenario.contains("ACTIVE")) AttemptUiModel("attempt-1", detail.id, detail.projectId, "IN_PROGRESS", null, 1) else null
    val result = if (scenario.contains("RESULT")) AssessmentResultUiModel(AttemptUiModel("attempt-1", detail.id, detail.projectId, "COMPLETED", .5, 2), .5, .5, listOf("q2"), listOf("mistake-1")) else null
    AssessmentWorldScreen(quiz, RepositoryState(emptyList(), DataFreshness.FRESH), RepositoryState(detail, DataFreshness.FRESH), attempt, result, {}, {}, {}, { _, _ -> }, {})
}

@androidx.compose.runtime.Composable
private fun PracticeEvidence(scenario: String) {
    val item = PracticeItemUiModel("pi1", 0, "A 2 kg object accelerates at 3 m/s². What is the net force?", "SHORT_ANSWER", if (scenario == "PRACTICE_FEEDBACK") "ANSWERED" else "ACTIVE", 2, "Motion", if (scenario == "PRACTICE_FEEDBACK") "6 N" else null, 2)
    val session = PracticeSessionUiModel("practice-1", "project-motion", "Motion", "IN_PROGRESS", 4, 0, 4, 2, true, "ON_REQUEST", "AFTER_CHECK", listOf(item), "{}")
    val check = if (scenario == "PRACTICE_FEEDBACK") PracticeCheckUiModel(item.copy(state = "COMPLETED"), true, 1.0, "Correct. Force equals mass times acceleration.", null, null) else null
    PracticeWorldScreen(RepositoryState(session, DataFreshness.FRESH), if (scenario == "PRACTICE_HINT") "Use F = ma." else null, check, null, {}, { _, _ -> }, {}, {}, {}, {})
}

@androidx.compose.runtime.Composable
private fun FlashcardEvidence() {
    FlashcardsWorldScreen(
        RepositoryState(listOf(FlashcardUiModel("card-1", "deck-1", "project-motion", "What is acceleration?", "Change in velocity per unit time", "Velocity change divided by elapsed time.", "now", 1, 1, 0, 4)), DataFreshness.FRESH), {}, { _, _ -> },
    )
}

@androidx.compose.runtime.Composable
private fun MistakesEvidence(scenario: String) {
    val list = if (scenario == "MISTAKES_EMPTY") emptyList() else listOf(
        MistakeUiModel("mistake-1", "project-motion", "s1", "Mechanics", "A 2 kg object accelerates at 3 m/s². Net force?", "5 N", "6 N", 3, "ACTIVE", 4),
        MistakeUiModel("mistake-2", "project-motion", null, "Units", "SI unit of force?", "kg", "N", 2, "RESOLVED", 3),
    )
    MistakesWorldScreen(RepositoryState(list, DataFreshness.FRESH), {}, {}, {}, {})
}

@androidx.compose.runtime.Composable
private fun StoreEvidence(scenario: String) {
    var purchased by remember { mutableStateOf(false) }
    val balance = if (scenario == "STORE_INSUFFICIENT") 20L else if (purchased) 110L else 230L
    val store = StoreCatalogUiModel("catalog-v1", balance, listOf(
        StoreItemUiModel("avatar-pro-focus", "AVATAR", 120, scenario == "STORE_OWNED" || purchased, true, "Level 3", "Focused learning identity"),
        StoreItemUiModel("avatar-elite-scholar", "AVATAR", 480, false, true, "Level 10", "Scholar identity"),
    ))
    val avatars = listOf(
        AvatarCatalogUiModel("avatar-noob-default", "Core", "avatar/core", "CORE", true, !purchased, null, "catalog-v1", "Core learner"),
        AvatarCatalogUiModel("avatar-pro-focus", "Focus", "avatar/focus", "PRO", scenario == "STORE_OWNED" || purchased, purchased, 120, "catalog-v1", "Focused learner"),
    )
    StoreWorldScreen(
        RepositoryState(store, DataFreshness.FRESH),
        RepositoryState(if (purchased || scenario == "STORE_OWNED") listOf(InventoryItemUiModel("avatar-pro-focus", "AVATAR", "STORE", "now", 1, "{}", 2)) else emptyList(), DataFreshness.FRESH),
        RepositoryState(avatars, DataFreshness.FRESH),
        RepositoryState(GameProfileUiModel(12, 14_250, 420, 1_000, balance, 7, if (purchased) "avatar-pro-focus" else "avatar-noob-default", if (purchased) "avatar/focus" else "avatar/core", if (purchased) "PRO" else "CORE", 4, "ACTIVE", 8), DataFreshness.FRESH),
        null, {}, { if (scenario != "STORE_INSUFFICIENT") purchased = true }, { _, _ -> },
    )
}

@androidx.compose.runtime.Composable
private fun SearchEvidence(scenario: String) {
    val list = if (scenario == "SEARCH_EMPTY") emptyList() else listOf(
        SearchUiModel("PROJECT", "project-motion", "Motion Studio", "Understand mechanics deeply", "project-motion", "/projects/project-motion", .99),
        SearchUiModel("CHAT", "c1", "Mechanics", "Force and acceleration", "project-motion", "/chats/c1", .92),
        SearchUiModel("SOURCE", "s1", "Mechanics Notes", "Newton's second law", "project-motion", "/sources/s1", .88),
    )
    SearchWorldScreen(RepositoryState(list, DataFreshness.FRESH), {}, {})
}

@androidx.compose.runtime.Composable
private fun HistoryEvidence(scenario: String) {
    val list = if (scenario == "HISTORY_EMPTY") emptyList() else listOf(
        ActivityUiModel("e1", "PRACTICE_COMPLETED", "2026-08-15T12:10:00Z", "project-motion", "practice-1", true, "/practice/practice-1"),
        ActivityUiModel("e2", "ASSESSMENT_COMPLETED", "2026-08-15T11:45:00Z", "project-motion", "a1", true, "/assessments/a1"),
    )
    HistoryWorldScreen(RepositoryState(list, DataFreshness.FRESH), {}, {})
}
