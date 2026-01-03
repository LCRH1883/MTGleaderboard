package com.intagri.mtgleader.persistence.matches

import com.intagri.mtgleader.persistence.stats.StatsSummaryDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MatchCounterDto(
    val name: String,
    val amount: Int,
)

@JsonClass(generateAdapter = true)
data class MatchPlayerDto(
    @Json(name = "seat_index")
    val seatIndex: Int,
    @Json(name = "seat")
    val seat: Int? = null,
    @Json(name = "user_id")
    val userId: String? = null,
    @Json(name = "guest_name")
    val guestName: String? = null,
    @Json(name = "display_name")
    val displayName: String? = null,
    @Json(name = "profile_name")
    val profileName: String? = null,
    val life: Int? = null,
    val counters: List<MatchCounterDto> = emptyList(),
    val place: Int? = null,
    @Json(name = "eliminated_turn_number")
    val eliminatedTurnNumber: Int? = null,
    @Json(name = "eliminated_during_seat_index")
    val eliminatedDuringSeatIndex: Int? = null,
    @Json(name = "total_turn_time_ms")
    val totalTurnTimeMs: Long? = null,
    @Json(name = "turns_taken")
    val turnsTaken: Int? = null,
)

@JsonClass(generateAdapter = true)
data class MatchPayloadDto(
    val players: List<MatchPlayerDto>,
    @Json(name = "winner_seat")
    val winnerSeat: Int? = null,
    @Json(name = "duration_seconds")
    val durationSeconds: Long? = null,
    @Json(name = "tabletop_type")
    val tabletopType: String? = null,
)

@JsonClass(generateAdapter = true)
data class MatchCreateRequest(
    @Json(name = "client_match_id")
    val clientMatchId: String?,
    @Json(name = "updated_at")
    val updatedAt: String,
    val match: MatchPayloadDto,
)

@JsonClass(generateAdapter = true)
data class MatchDto(
    val id: String,
    @Json(name = "client_match_id")
    val clientMatchId: String? = null,
    @Json(name = "updated_at")
    val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class MatchCreateResponse(
    val match: MatchDto,
    @Json(name = "stats_summary")
    val statsSummary: StatsSummaryDto? = null,
)

@JsonClass(generateAdapter = true)
data class MatchConflictResponse(
    val match: MatchDto,
)
