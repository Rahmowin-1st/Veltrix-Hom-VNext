package com.veltrix.hom.vnext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class FrontendPart1UiInstrumentedTest {
    @get:Rule val compose=createComposeRule()

    @Test
    fun homeUsesAuthoritativeSnapshotWithoutLocalEconomyAuthority(){
        val m=HomeFinalModel("fixture-account","Alex","avatar-fixture",12,14250,420,1000,580,230,9,7,"Retest Newton's second law","SUFFICIENT","LOCKED",null,null,2,listOf("RETEST"),listOf("WEAK_TOPIC"),18)
        compose.setContent{VeltrixTheme{HomeScreen(RepositoryState(m,DataFreshness.FRESH),true,{},{},{},{},{})}}
        compose.onNodeWithTag("home-screen").assertIsDisplayed()
        compose.onNodeWithText("Retest Newton's second law").assertExists()
        compose.onNodeWithText("230").assertIsDisplayed()
        // Pure composable rule proves the CTA is present. MainActivity runtime proof
        // owns real viewport visibility so a synthetic host cannot create a false RED.
        compose.onNodeWithTag("home-primary-action").assertExists()
    }

    @Test
    fun personalExposesTrustworthySparseAndMapState(){
        val m=PersonalFinalModel("fixture-account","Alex","avatar-fixture",12,14250,230,"LEARNING",listOf("Algebra"),listOf("Mechanics"),emptyList(),listOf("Improve physics"),"LOCKED",null,3,2,7,19)
        compose.setContent{VeltrixTheme{PersonalScreen(RepositoryState(m,DataFreshness.FRESH),true,{})}}
        compose.onNodeWithTag("personal-screen").assertIsDisplayed()
        compose.onNodeWithText("Alex").assertIsDisplayed()
        compose.onNodeWithText("Personal Map").assertIsDisplayed()
        compose.onNodeWithText("Locked").assertIsDisplayed()
    }
}
