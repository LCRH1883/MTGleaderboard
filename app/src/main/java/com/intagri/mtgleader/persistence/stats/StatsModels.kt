package com.intagri.mtgleader.persistence.stats

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StatsSummaryDto(
    @Json(name = "matches_played")
    val matchesPlayed: Int,
    val wins: Int,
    val losses: Int,
)

@JsonClass(generateAdapter = true)
data class HeadToHeadDto(
    val opponent: UserSummaryDto,
    val total: Int,
    val wins: Int,
    val losses: Int,
)

@JsonClass(generateAdapter = true)
data class UserSummaryDto(
    val id: String,
    val username: String? = null,
    @Json(name = "display_name")
    val displayName: String? = null,
    @Json(name = "avatar_url")
    val avatarUrl: String? = null,
)
