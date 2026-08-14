package com.veltrix.hom.vnext.server

import kotlinx.serialization.Serializable

@Serializable data class LearningModeDefinitionResponse(val id:String,val version:Int,val answerDepth:String,val guidingQuestions:Boolean,val revealAnswersImmediately:Boolean,val citationPreference:String,val correctionStyle:String,val promptPolicyJson:String,val toolPolicyJson:String,val assessmentPolicyJson:String)
@Serializable data class GoalDependencyRequest(val dependsOnGoalId:String)
@Serializable data class GoalDependencyResponse(val goalId:String,val dependsOnGoalId:String,val createdAt:String)
@Serializable data class GoalLinkRequest(val objectType:String,val objectId:String)
@Serializable data class GoalLinkResponse(val id:String,val goalId:String,val objectType:String,val objectId:String,val createdAt:String)
@Serializable data class GoalSuggestionCreateRequest(val parentGoalId:String?=null,val title:String,val description:String?=null,val provenanceJson:String="{}")
@Serializable data class GoalSuggestionDecisionRequest(val decision:String,val expectedRevision:Long)
@Serializable data class GoalSuggestionResponse(val id:String,val parentGoalId:String?,val title:String,val description:String?,val state:String,val acceptedGoalId:String?,val provenanceJson:String,val revision:Long,val createdAt:String)
@Serializable data class AssessmentAttemptHistoryItemResponse(val id:String,val state:String,val score:Double?,val startedAt:String?,val submittedAt:String?,val deadlineAt:String?,val durationSeconds:Int?,val retestOfAttemptId:String?,val revision:Long)
@Serializable data class AssessmentHistoryResponse(val assessmentId:String,val attempts:List<AssessmentAttemptHistoryItemResponse>,val bestScore:Double?,val latestScore:Double?,val improvementFromFirst:Double?)
@Serializable data class RetestRequest(val previousAttemptId:String?=null)
@Serializable data class RetestResponse(val attempt:AttemptResponse,val deadlineAt:String?,val retestOfAttemptId:String?)
@Serializable data class AccountExportFinalResponse(val accountId:String,val generatedAt:String,val schemaVersion:Int=3,val profile:AccountExportProfile,val entityCounts:Map<String,Long>,val entityPayloads:Map<String,String>,val payloadSha256:String)
@Serializable data class AccountDeletionLifecycleResponse(val state:String,val requestedAt:String,val purgeAfter:String,val completedAt:String?,val retryCount:Int)
