package com.veltrix.hom.vnext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier

/**
 * Debug-only deterministic presentation harness used by CI to prove visual state coverage.
 * It reuses the exact production Home/Personal composables and supplies repository-level
 * fixture states. No business rule or production runtime path depends on this activity.
 */
class FrontendEvidenceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scenario = intent.getStringExtra("scenario") ?: "HOME_FOCUS"
        setContent {
            VeltrixTheme {
                VeltrixWorldBackground {
                    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        if (scenario.startsWith("PERSONAL")) {
                            PersonalScreen(personalEvidenceState(scenario), true, {})
                        } else {
                            HomeScreen(homeEvidenceState(scenario), true, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
    }
}

private fun homeEvidenceState(scenario: String): RepositoryState<HomeFinalModel> {
    if (scenario == "HOME_ERROR") {
        return RepositoryState(null, DataFreshness.FRESH, errorCode = "HTTP_503", retryable = true)
    }
    val sparse = scenario == "HOME_SPARSE"
    val unlocked = scenario == "HOME_UNLOCKED"
    val model = HomeFinalModel(
        accountId = "evidence-account",
        displayName = if (sparse) "New learner" else "Alex",
        avatarId = if (sparse) "" else "avatar-noob-default",
        level = if (sparse) 1 else 12,
        lifetimeXp = if (sparse) 0 else 14_250,
        currentLevelXp = if (sparse) 0 else 420,
        nextLevelXp = if (sparse) 100 else 1_000,
        remainingXp = if (sparse) 100 else 580,
        coins = if (sparse) 0 else 230,
        qualifiedActiveDays = if (sparse) 0 else 9,
        consistency = if (sparse) 0 else 7,
        currentFocus = if (sparse) null else "Retest Newton's second law",
        memoryMaturity = if (sparse) "COLD" else "SUFFICIENT",
        mapState = if (unlocked) "ACTIVE" else "LOCKED",
        currentMapUnit = if (unlocked) "fixture-current-unit" else null,
        seasonId = if (unlocked) "fixture-season" else null,
        unreadNotifications = if (sparse) 0 else 2,
        priorityKeys = if (sparse) emptyList() else listOf("WEAK_REVIEW", "PROJECT_FOCUS"),
        insightCodes = if (sparse) emptyList() else listOf("RETEST", "XP_REMAINING"),
        revision = 18,
    )
    return RepositoryState(
        value = model,
        freshness = if (scenario == "HOME_OFFLINE") DataFreshness.OFFLINE else DataFreshness.FRESH,
        serverRevision = model.revision,
    )
}

private fun personalEvidenceState(scenario: String): RepositoryState<PersonalFinalModel> {
    if (scenario == "PERSONAL_ERROR") {
        return RepositoryState(null, DataFreshness.FRESH, errorCode = "HTTP_409", retryable = true)
    }
    val sparse = scenario == "PERSONAL_SPARSE"
    val unlocked = scenario == "PERSONAL_UNLOCKED"
    val model = PersonalFinalModel(
        accountId = "evidence-account",
        displayName = if (sparse) "New learner" else "Alex",
        avatarId = if (sparse) "" else "avatar-noob-default",
        level = if (sparse) 1 else 12,
        lifetimeXp = if (sparse) 0 else 14_250,
        coins = if (sparse) 0 else 230,
        memoryMaturity = if (sparse) "COLD" else "SUFFICIENT",
        strengths = if (sparse) emptyList() else listOf("Algebra", "Pattern recognition", "Worked examples"),
        weaknesses = if (sparse) emptyList() else listOf("Mechanics", "Unit conversion"),
        interests = if (sparse) emptyList() else listOf("Physics", "AI"),
        goals = if (sparse) emptyList() else listOf("Improve physics", "Finish mechanics review"),
        mapState = if (unlocked) "ACTIVE" else "LOCKED",
        seasonId = if (unlocked) "fixture-season" else null,
        achievementCount = if (sparse) 0 else 3,
        inventoryCount = if (sparse) 0 else 2,
        currentConsistency = if (sparse) 0 else 7,
        revision = 19,
    )
    return RepositoryState(
        value = model,
        freshness = if (scenario == "PERSONAL_OFFLINE") DataFreshness.OFFLINE else DataFreshness.FRESH,
        serverRevision = model.revision,
    )
}
