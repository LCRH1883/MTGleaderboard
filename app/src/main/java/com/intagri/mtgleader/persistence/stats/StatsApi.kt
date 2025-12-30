package com.intagri.mtgleader.persistence.stats

import retrofit2.http.GET
import retrofit2.http.Path

interface StatsApi {
    @GET("v1/stats/summary")
    suspend fun getSummary(): StatsSummaryDto

    @GET("v1/stats/head-to-head/{id}")
    suspend fun getHeadToHead(@Path("id") opponentId: String): HeadToHeadDto
}
