package com.veltrix.hom.vnext.server

import kotlinx.serialization.Serializable

/** Safe assessment read model: IDs/options required for clients, never expected answers. */
@Serializable
data class AssessmentQuestionPublicResponse(
    val id: String,
    val position: Int,
    val prompt: String,
    val type: String,
    val options: List<String>,
)

@Serializable
data class AssessmentDetailResponse(
    val assessment: AssessmentResponse,
    val questions: List<AssessmentQuestionPublicResponse>,
)
