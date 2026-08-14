package com.veltrix.hom.vnext.server

import kotlinx.serialization.Serializable

@Serializable
data class CurrentSeasonResponse(
    val season: SeasonResponse?,
    val progress: SeasonProgressResponse?,
)
