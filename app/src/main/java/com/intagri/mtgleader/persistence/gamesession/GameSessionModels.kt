package com.intagri.mtgleader.persistence.gamesession

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GameCounterSnapshot(
    val templateId: Int,
    val amount: Int,
)
